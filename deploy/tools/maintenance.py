"""Offline operational tools. Docker orchestration stays in the host launchers."""
import argparse
import contextlib
import datetime as dt
import getpass
import hashlib
import http.client
import io
import ipaddress
import json
import os
from pathlib import Path, PurePosixPath
import posixpath
import re
import shutil
import socket
import ssl
import subprocess
import sys
import tarfile
import tempfile
import uuid

REQUIRED_SECRETS = ('oracle_admin_password', 'oracle_app_password', 'jwt_secret')
IMAGE = re.compile(r'^[a-z0-9./:_-]+@sha256:[a-f0-9]{64}$')


def run(*args, **options):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **options).stdout


def host_name(value):
    value = value.strip()
    try:
        ipaddress.ip_address(value)
    except ValueError:
        if len(value) > 253 or not all(re.fullmatch(r'[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?', label)
                                       for label in value.split('.')):
            raise ValueError('El host debe ser una IP o DNS válido, sin protocolo, ruta ni puerto.')
    return value


def configuration(root):
    file = root / '.env'
    if not file.is_file():
        raise ValueError('Falta .env raíz. Si hay secretos o volumen previo, seguir la recuperación del README; no repetir setup.')
    values = {}
    for line in file.read_text(encoding='utf-8-sig').splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key, separator, value = line.partition('=')
            if separator:
                values[key.strip()] = value.strip()
    host_name(values.get('PUBLIC_HOST', ''))
    if not 1 <= int(values.get('HTTPS_PORT', '0')) <= 65535:
        raise ValueError('HTTPS_PORT debe estar entre 1 y 65535.')
    ipaddress.IPv4Address(values.get('HTTPS_BIND', ''))
    network = ipaddress.IPv4Network(values.get('PPP_SUBNET', ''), strict=True)
    proxy = ipaddress.IPv4Address(values.get('PPP_PROXY_IP', ''))
    if proxy not in network or proxy in (network.network_address, network.broadcast_address):
        raise ValueError('PPP_PROXY_IP no es una dirección utilizable dentro de PPP_SUBNET.')
    return values


def certificate_info(root, check_host=True):
    tls = root / 'secrets/tls'
    cert, key = tls / 'server.crt', tls / 'server.key'
    if not cert.is_file() or not key.is_file():
        raise ValueError('Falta el certificado o su clave. No regenerar credenciales Oracle para corregir TLS.')
    public = run('openssl', 'x509', '-in', str(cert), '-pubkey', '-noout').strip()
    if public != run('openssl', 'pkey', '-in', str(key), '-pubout').strip():
        raise ValueError('El certificado y su clave privada no coinciden.')
    host = configuration(root)['PUBLIC_HOST']
    try:
        ipaddress.ip_address(host)
        option = '-checkip'
    except ValueError:
        option = '-checkhost'
    if check_host:
        run('openssl', 'x509', '-in', str(cert), option, host, '-noout')
    expiry = run('openssl', 'x509', '-in', str(cert), '-enddate', '-noout').decode().strip().split('=', 1)[1]
    expires = dt.datetime.strptime(expiry, '%b %d %H:%M:%S %Y %Z').replace(tzinfo=dt.timezone.utc)
    identity = run('openssl', 'x509', '-in', str(cert), '-issuer', '-subject', '-noout').decode().splitlines()
    names = [line.split('=', 1)[1].strip() for line in identity]
    return {'expires': expires, 'self_signed': len(names) == 2 and names[0] == names[1]}


def make_certificate(folder, host):
    host = host_name(host)
    try:
        ipaddress.ip_address(host)
        san = 'IP:' + host
    except ValueError:
        san = 'DNS:' + host
    run('openssl', 'req', '-x509', '-newkey', 'rsa:3072', '-sha256', '-nodes',
        '-keyout', str(folder / 'server.key'), '-out', str(folder / 'server.crt'),
        '-days', '90', '-subj', '/CN=' + host,
        '-addext', 'subjectAltName=' + san + ',DNS:localhost,IP:127.0.0.1')
    (folder / 'server.key').chmod(0o600)
    (folder / 'server.crt').chmod(0o644)


def check(root):
    values = configuration(root)
    import base64
    for name in REQUIRED_SECRETS:
        file = root / 'secrets' / name
        if not file.is_file() or not file.read_text().strip():
            raise ValueError('Configuración incompleta: falta ' + name + '. Conservar los archivos y consultar recuperación.')
    if len(base64.b64decode((root / 'secrets/jwt_secret').read_text().strip(), validate=True)) < 32:
        raise ValueError('La clave de sesiones no tiene la longitud requerida.')
    info = certificate_info(root)
    remaining = (info['expires'] - dt.datetime.now(dt.timezone.utc)).days
    print('Configuración y claves: correctas (no se muestran sus valores).')
    print('Certificado válido para el host configurado. Vence:', info['expires'].isoformat())
    print('Dirección de acceso: https://' + ('[' + values['PUBLIC_HOST'] + ']' if ':' in values['PUBLIC_HOST'] else values['PUBLIC_HOST'])
          + (':' + values['HTTPS_PORT'] if values['HTTPS_PORT'] != '443' else ''))
    if remaining < 14:
        raise ValueError('El certificado vence en menos de 14 días o ya venció. Renovarlo antes de continuar.')


def renew(root):
    host = configuration(root)['PUBLIC_HOST']
    info = certificate_info(root, check_host=False)
    if not info['self_signed']:
        raise ValueError('No se sustituye un certificado institucional. Renovarlo con su emisor y seguir el README.')
    tls = root / 'secrets/tls'
    with tempfile.TemporaryDirectory(dir=tls, prefix='.renovacion-') as temporary:
        folder = Path(temporary)
        make_certificate(folder, host)
        previous = tls / ('anterior-' + dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:6])
        previous.mkdir(mode=0o700)
        for name in ('server.crt', 'server.key'):
            shutil.copy2(tls / name, previous / name)
        try:
            for name in ('server.key', 'server.crt'):
                os.replace(folder / name, tls / name)
        except Exception:
            for name in ('server.key', 'server.crt'):
                shutil.copy2(previous / name, tls / name)
            raise
    print('Certificado de pruebas renovado por 90 días. Se conservan credenciales Oracle, clave JWT y .env.')
    print('Copia anterior conservada en secrets/tls/' + previous.name)
    print('Actualizar la confianza del certificado en los clientes y recrear solo Frontend.')


def live_check(root):
    host = configuration(root)['PUBLIC_HOST']
    cert = root / 'secrets/tls/server.crt'
    context = ssl.create_default_context()
    context.load_verify_locations(str(cert))
    context.verify_flags |= ssl.VERIFY_X509_PARTIAL_CHAIN
    expected = run('openssl', 'x509', '-in', str(cert), '-outform', 'DER')
    for path, expected_status in (('/healthz', 200), ('/login', 200),
                                   ('/api/v1/auth/csrf', 204), ('/api/v1/auth/session', 401)):
        with socket.create_connection(('127.0.0.1', 443), timeout=15) as raw:
            with context.wrap_socket(raw, server_hostname=host) as connection:
                if connection.getpeercert(binary_form=True) != expected:
                    raise ValueError('Nginx no está usando el certificado configurado.')
                connection.sendall(('GET ' + path + ' HTTP/1.1\r\nHost: ' + host
                                    + '\r\nConnection: close\r\n\r\n').encode())
                response = http.client.HTTPResponse(connection)
                response.begin()
                if response.status != expected_status:
                    raise ValueError('Resultado inesperado al comprobar ' + path + ': ' + str(response.status))
    print('HTTPS, certificado servido, ruta Angular y comunicación con API/CSRF: correctos.')
    print('La prueba es desde Docker; comprobar también acceso desde los equipos y redes de los usuarios.')


def fingerprint(root):
    files = []
    for directory in ('Backend/src', 'Frontend/src', 'Frontend/resources', 'Frontend/public'):
        if (root / directory).is_dir():
            files.extend(p for p in (root / directory).rglob('*') if p.is_file())
    for relative in ('Backend/build.gradle', 'Backend/settings.gradle', 'Backend/Dockerfile',
                     'Backend/docker-entrypoint.sh', 'Frontend/Dockerfile', 'Frontend/nginx.conf',
                     'Frontend/package.json', 'Frontend/package-lock.json', 'Frontend/angular.json',
                     'compose.yaml', 'documentacion/CTRLPRAC_CREAR.sql'):
        if (root / relative).is_file():
            files.append(root / relative)
    digest = hashlib.sha256()
    for path in sorted(set(files)):
        data = path.read_bytes()
        if path.suffix.lower() not in ('.png', '.jpg', '.ico', '.jar'):
            data = data.replace(b'\r\n', b'\n')
        digest.update(path.relative_to(root).as_posix().encode() + b'\0' + hashlib.sha256(data).digest())
    return digest.hexdigest()


def passphrase(confirm=False):
    if not sys.stdin.isatty():
        raise ValueError('Se necesita una terminal interactiva para ingresar la contraseña del respaldo.')
    password = getpass.getpass('Contraseña del respaldo (no se muestra): ')
    if not 16 <= len(password) <= 1024 or '\n' in password or '\r' in password:
        raise ValueError('Usar una contraseña de respaldo de al menos 16 caracteres, sin saltos de línea.')
    if confirm and password != getpass.getpass('Confirma la contraseña del respaldo: '):
        raise ValueError('Las contraseñas del respaldo no coinciden.')
    return password


@contextlib.contextmanager
def gpg_options(password):
    with tempfile.TemporaryDirectory(prefix='ppp-gpg-') as home:
        read_fd, write_fd = os.pipe()
        try:
            os.write(write_fd, (password + '\n').encode())
        finally:
            os.close(write_fd)
        try:
            yield ['gpg', '--homedir', home, '--batch', '--quiet', '--no-symkey-cache',
                   '--pinentry-mode', 'loopback', '--passphrase-fd', str(read_fd)], read_fd
        finally:
            os.close(read_fd)


def host_owner(path):
    if os.geteuid() == 0:
        os.chown(path, int(os.environ.get('LOCAL_UID', '1000')), int(os.environ.get('LOCAL_GID', '1000')))


def backup(root, volume, password=None):
    configuration(root)
    for path in (root / 'secrets').rglob('*'):
        if path.is_symlink():
            raise ValueError('El respaldo no admite enlaces simbólicos en secrets; usar archivos de certificado reales.')
    for name in REQUIRED_SECRETS:
        if not (root / 'secrets' / name).is_file():
            raise ValueError('Configuración de respaldo incompleta: ' + name)
    if not volume.is_dir() or not any(volume.iterdir()):
        raise ValueError('El volumen de Oracle está vacío o no está montado.')
    image = os.environ.get('BACKUP_ORACLE_IMAGE', '')
    if not IMAGE.fullmatch(image):
        raise ValueError('No se pudo fijar el digest de la imagen Oracle para una restauración compatible.')
    password = password if password is not None else passphrase(confirm=True)
    directory = root / 'backups'
    directory.mkdir(mode=0o700, exist_ok=True)
    host_owner(directory)
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    destination = directory / ('control-ppp-' + stamp + '-' + uuid.uuid4().hex[:6] + '.tar.gz.gpg')
    partial = destination.with_suffix('.partial')
    metadata = {'format': 1, 'created_utc': stamp, 'code_fingerprint': fingerprint(root),
                'architecture': os.uname().machine, 'oracle_image': image,
                'method': 'cold-volume', 'git_commit': os.environ.get('BACKUP_GIT_COMMIT', 'no-disponible')}
    process = None
    try:
        with gpg_options(password) as (options, descriptor), partial.open('xb') as output, tempfile.TemporaryFile() as errors:
            partial.chmod(0o600)
            process = subprocess.Popen(options + ['--symmetric', '--cipher-algo', 'AES256', '--compress-algo', 'none'],
                                       stdin=subprocess.PIPE, stdout=output, stderr=errors, pass_fds=(descriptor,))
            try:
                with tarfile.open(fileobj=process.stdin, mode='w|gz', compresslevel=1) as archive:
                    data = json.dumps(metadata).encode()
                    member = tarfile.TarInfo('metadata.json')
                    member.size = len(data)
                    member.mode = 0o600
                    archive.addfile(member, io.BytesIO(data))
                    archive.add(root / '.env', arcname='config/.env')
                    archive.add(root / 'secrets', arcname='config/secrets')
                    archive.add(volume, arcname='data')
            finally:
                process.stdin.close()
            if process.wait() != 0:
                raise ValueError('No se completó el cifrado del respaldo; no se publica el archivo parcial.')
        host_owner(partial)
        os.rename(partial, destination)
    except Exception:
        if process and process.poll() is None:
            process.kill()
            process.wait()
        partial.unlink(missing_ok=True)
        raise
    print('Respaldo cifrado creado: backups/' + destination.name)
    print('Conservar la contraseña en un lugar seguro, separado del archivo. Sin ella no hay recuperación.')
    return destination


def validate_members(members):
    seen = set()
    link_paths = {m.name for m in members if m.issym() or m.islnk()}
    for member in members:
        path = PurePosixPath(member.name)
        if (path.is_absolute() or '..' in path.parts or member.name in seen
                or path.as_posix() != member.name or any(str(p) in link_paths for p in path.parents)):
            raise ValueError('El respaldo contiene una ruta inválida o duplicada.')
        seen.add(member.name)
        if not path.parts or path.parts[0] not in ('data', 'config', 'metadata.json'):
            raise ValueError('Contenido no permitido en el respaldo.')
        if not (member.isfile() or member.isdir() or member.issym() or member.islnk()):
            raise ValueError('El respaldo contiene un tipo de archivo no permitido.')
        if member.issym() or member.islnk():
            if path.parts[0] != 'data' or posixpath.isabs(member.linkname):
                raise ValueError('Enlace no permitido en el respaldo.')
            resolved = posixpath.normpath(posixpath.join(posixpath.dirname(member.name), member.linkname)
                                          if member.issym() else member.linkname)
            if not resolved.startswith('data/') or any(str(p) in link_paths for p in (PurePosixPath(resolved), *PurePosixPath(resolved).parents)):
                raise ValueError('Enlace fuera del volumen de datos.')
    required = {'metadata.json', 'config/.env', *(f'config/secrets/{name}' for name in REQUIRED_SECRETS),
                'config/secrets/tls/server.crt', 'config/secrets/tls/server.key'}
    if not required.issubset(seen):
        raise ValueError('El respaldo no contiene todos los archivos requeridos.')
    if any(not m.isfile() for m in members if m.name in required):
        raise ValueError('Los archivos de configuración y metadatos deben ser archivos regulares.')


def restore(root, volume, filename, password=None):
    if (root / '.env').exists() or (root / 'secrets').exists():
        raise ValueError('Restauración rechazada: ya existe .env o secrets. Usar una instalación de recuperación vacía.')
    if not volume.is_dir() or any(volume.iterdir()):
        raise ValueError('Restauración rechazada: el volumen destino debe estar vacío.')
    source = Path(filename).resolve()
    backups = (root / 'backups').resolve()
    if source.parent != backups or not source.is_file() or source.suffix != '.gpg':
        raise ValueError('Seleccionar el nombre de un respaldo .gpg dentro de backups/.')
    password = password if password is not None else passphrase()
    local = root / '.local'
    local.mkdir(mode=0o700, exist_ok=True)
    host_owner(local)
    with tempfile.TemporaryDirectory(dir=local, prefix='restauracion-') as temporary:
        temporary = Path(temporary)
        payload = temporary / 'payload.tar.gz'
        with gpg_options(password) as (options, descriptor):
            result = subprocess.run(options + ['--output', str(payload), '--decrypt', str(source)],
                                    pass_fds=(descriptor,), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if result.returncode:
                raise ValueError('Contraseña incorrecta o respaldo dañado; no se restaura ningún archivo.')
        with tarfile.open(payload, 'r:gz') as archive:
            members = archive.getmembers()
            validate_members(members)
            item = archive.getmember('metadata.json')
            if item.size > 65536:
                raise ValueError('Metadatos del respaldo no válidos.')
            metadata = json.load(archive.extractfile(item))
            if metadata.get('format') != 1 or metadata.get('method') != 'cold-volume':
                raise ValueError('Formato de respaldo no compatible.')
            if metadata.get('code_fingerprint') != fingerprint(root):
                raise ValueError('La versión del código no coincide con el respaldo. Preparar la versión original antes de restaurar. Commit: '
                                 + str(metadata.get('git_commit', 'no-disponible')))
            if metadata.get('architecture') != os.uname().machine or not IMAGE.fullmatch(metadata.get('oracle_image', '')):
                raise ValueError('Arquitectura o imagen Oracle del respaldo no compatible.')
            needed = sum(m.size for m in members if m.isfile() and m.name.startswith('data/'))
            if shutil.disk_usage(volume).free < needed + 256 * 1024 * 1024:
                raise ValueError('No hay espacio suficiente en el volumen destino.')
            directories, links = [], []
            for member in members:
                if member.name == 'metadata.json':
                    continue
                path = PurePosixPath(member.name)
                destination = (volume if path.parts[0] == 'data' else temporary / 'config').joinpath(*path.parts[1:])
                if member.isdir():
                    destination.mkdir(parents=True, exist_ok=True)
                    directories.append((member, destination))
                elif member.issym() or member.islnk():
                    links.append((member, destination))
                else:
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    with archive.extractfile(member) as stream, destination.open('xb') as output:
                        shutil.copyfileobj(stream, output, length=1024*1024)
                    destination.chmod(member.mode & 0o777)
                    if path.parts[0] == 'data':
                        os.chown(destination, member.uid, member.gid)
                    os.utime(destination, (member.mtime, member.mtime))
            for member, destination in links:
                destination.parent.mkdir(parents=True, exist_ok=True)
                if member.issym():
                    destination.symlink_to(member.linkname)
                else:
                    os.link(volume.joinpath(*PurePosixPath(member.linkname).parts[1:]), destination)
                os.chown(destination, member.uid, member.gid, follow_symlinks=False)
            for member, destination in reversed(directories):
                destination.chmod(member.mode & 0o777)
                if member.name == 'data' or member.name.startswith('data/'):
                    os.chown(destination, member.uid, member.gid)
            config = temporary / 'config'
            configuration(config)
            certificate_info(config)
            lines = [line for line in (config / '.env').read_text(encoding='utf-8').splitlines()
                     if not line.startswith('ORACLE_IMAGE=')]
            (config / '.env').write_text('\n'.join(lines) + '\nORACLE_IMAGE=' + metadata['oracle_image'] + '\n')
            for path in (config, *(config.rglob('*'))):
                host_owner(path)
            os.rename(config / 'secrets', root / 'secrets')
            os.rename(config / '.env', root / '.env')
    print('Respaldo restaurado en instalación vacía. Se conserva la imagen exacta de Oracle mediante ORACLE_IMAGE.')
    print('Iniciar servicios y ejecutar comprobar. No crear de nuevo el primer administrador.')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['check', 'live-check', 'renew', 'backup', 'restore', 'json-path'])
    parser.add_argument('--file')
    parser.add_argument('keys', nargs='*')
    args = parser.parse_args()
    root = Path.cwd()
    if args.action == 'json-path':
        value = json.load(sys.stdin)
        for key in args.keys:
            value = value[int(key)] if isinstance(value, list) else value[key]
        print(value)
    elif args.action == 'check': check(root)
    elif args.action == 'live-check': live_check(root)
    elif args.action == 'renew': renew(root)
    elif args.action == 'backup': backup(root, Path('/oracle-data'))
    elif args.action == 'restore': restore(root, Path('/oracle-data'), root / 'backups' / (args.file or ''))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, tarfile.TarError, KeyError) as error:
        # Do not dump subprocess output: it may contain private configuration.
        message = str(error) if not isinstance(error, subprocess.CalledProcessError) else 'Falló una comprobación criptográfica o de certificado.'
        print('Operación no completada:', message, file=sys.stderr)
        sys.exit(1)
