"""Security and recovery tests; synthetic files only, no real database."""
import base64
import contextlib
import io
import os
from pathlib import Path
import shutil
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import maintenance as m

PASSWORD = 'Respaldo-Solo-Pruebas-2026!'
DIGEST = 'gvenzl/oracle-free@sha256:' + 'a' * 64


class OperationsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)
        self.root = self.base / 'source'; self.root.mkdir()
        self.volume = self.base / 'data'; self.volume.mkdir()
        (self.volume / 'example.dbf').write_bytes(b'Oracle-fixture' * 1000)
        self.target = self.base / 'target'; self.target.mkdir()
        self.empty = self.base / 'empty'; self.empty.mkdir()
        (self.root / '.env').write_text('PUBLIC_HOST=localhost\nHTTPS_BIND=127.0.0.1\nHTTPS_PORT=19444\nPPP_SUBNET=172.30.80.0/24\nPPP_PROXY_IP=172.30.80.10\n')
        self.tls = self.root / 'secrets/tls'; self.tls.mkdir(parents=True)
        for name in m.REQUIRED_SECRETS:
            (self.root / 'secrets' / name).write_text(base64.b64encode(b'x' * 48).decode() if name == 'jwt_secret' else 'a' * 48)
        m.make_certificate(self.tls, 'localhost')
        self.environment = patch.dict(os.environ, BACKUP_ORACLE_IMAGE=DIGEST, LOCAL_UID=str(os.getuid()), LOCAL_GID=str(os.getgid()))
        self.environment.start()
        self.output = contextlib.redirect_stdout(io.StringIO()); self.output.__enter__()

    def tearDown(self):
        self.output.__exit__(None, None, None)
        self.environment.stop(); self.temporary.cleanup()

    def make_backup(self):
        source = m.backup(self.root, self.volume, PASSWORD)
        (self.target / 'backups').mkdir(exist_ok=True)
        dest = self.target / 'backups' / source.name
        shutil.copyfile(source, dest)
        return dest

    def test_encrypted_roundtrip_preserves_data_configuration_and_image(self):
        source = self.make_backup()
        self.assertNotIn(b'Oracle-fixture', source.read_bytes())
        m.restore(self.target, self.empty, source, PASSWORD)
        self.assertEqual((self.empty / 'example.dbf').read_bytes(), (self.volume / 'example.dbf').read_bytes())
        self.assertEqual((self.target / 'secrets/jwt_secret').read_bytes(), (self.root / 'secrets/jwt_secret').read_bytes())
        self.assertIn('ORACLE_IMAGE=' + DIGEST, (self.target / '.env').read_text())
        m.check(self.target)

    def test_wrong_password_and_corruption_write_no_destination(self):
        source = self.make_backup()
        with self.assertRaises(ValueError): m.restore(self.target, self.empty, source, 'Clave-Incorrecta-2026!')
        self.assertFalse(any(self.empty.iterdir())); self.assertFalse((self.target / 'secrets').exists())
        data = bytearray(source.read_bytes()); data[len(data)//2] ^= 0x40; source.write_bytes(data)
        with self.assertRaises(ValueError): m.restore(self.target, self.empty, source, PASSWORD)
        self.assertFalse(any(self.empty.iterdir()))

    def test_existing_configuration_and_nonempty_volume_are_rejected(self):
        source = self.make_backup()
        (self.empty / 'existing').write_text('keep')
        with self.assertRaises(ValueError): m.restore(self.target, self.empty, source, PASSWORD)
        (self.empty / 'existing').unlink(); (self.target / '.env').write_text('keep')
        with self.assertRaises(ValueError): m.restore(self.target, self.empty, source, PASSWORD)
        self.assertEqual((self.target / '.env').read_text(), 'keep')

    def test_different_code_is_rejected_before_data_extraction(self):
        source = self.make_backup()
        (self.target / 'Backend').mkdir(); (self.target / 'Backend/build.gradle').write_text('different')
        with self.assertRaisesRegex(ValueError, 'versión del código'): m.restore(self.target, self.empty, source, PASSWORD)
        self.assertFalse(any(self.empty.iterdir()))

    def test_renew_preserves_credentials_and_environment(self):
        before = {name: (self.root / 'secrets' / name).read_bytes() for name in m.REQUIRED_SECRETS}
        env = (self.root / '.env').read_bytes(); cert = (self.tls / 'server.crt').read_bytes()
        m.renew(self.root)
        self.assertNotEqual(cert, (self.tls / 'server.crt').read_bytes())
        self.assertEqual(env, (self.root / '.env').read_bytes())
        for name, value in before.items(): self.assertEqual(value, (self.root / 'secrets' / name).read_bytes())
        m.check(self.root)

    def test_institutional_certificate_not_replaced(self):
        with patch.object(m, 'certificate_info', return_value={'self_signed': False}):
            with self.assertRaisesRegex(ValueError, 'institucional'): m.renew(self.root)

    def test_expiring_certificate_can_be_renewed_for_a_new_host(self):
        m.run('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
              '-keyout', str(self.tls / 'server.key'), '-out', str(self.tls / 'server.crt'),
              '-days', '1', '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost')
        with self.assertRaisesRegex(ValueError, '14 días'): m.check(self.root)
        env = self.root / '.env'
        env.write_text(env.read_text().replace('PUBLIC_HOST=localhost', 'PUBLIC_HOST=nuevo.example'))
        m.renew(self.root)
        m.check(self.root)

    def test_unsafe_paths_and_links_rejected(self):
        for name in ('../outside', '/outside', 'data/../outside', 'data//duplicate'):
            with self.subTest(name=name), self.assertRaises(ValueError): m.validate_members([tarfile.TarInfo(name)])
        link = tarfile.TarInfo('data/link'); link.type = tarfile.SYMTYPE; link.linkname = '../../outside'
        with self.assertRaises(ValueError): m.validate_members([link])
        link.linkname = 'folder'
        with self.assertRaises(ValueError): m.validate_members([link, tarfile.TarInfo('data/link/file')])

    def test_host_and_certificate_mismatch(self):
        for host in ('https://localhost', 'host:443', '-invalid', 'a..b', ''):
            with self.subTest(host=host), self.assertRaises(ValueError): m.host_name(host)
        env = self.root / '.env'
        env.write_text(env.read_text().replace('PUBLIC_HOST=localhost', 'PUBLIC_HOST=other.example'))
        with self.assertRaises(Exception): m.certificate_info(self.root)


if __name__ == '__main__': unittest.main()
