"""Explicit one-time setup. No passwords are printed, committed or preconfigured."""
import base64
import ipaddress
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys


def main():
    root = Path.cwd()
    destination = root / "secrets"
    if destination.exists() or (root / ".env").exists():
        raise ValueError("Ya existe secrets/ o .env. Se conserva la configuración; no repetir el setup.")
    host = os.environ.get("PUBLIC_HOST", "localhost").strip()
    try:
        ipaddress.ip_address(host)
        san = "IP:" + host
    except ValueError:
        if not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?", host):
            raise ValueError("PUBLIC_HOST debe ser un nombre de servidor o una dirección IP, sin protocolo ni puerto.")
        san = "DNS:" + host
    destination.mkdir(mode=0o700)
    tls = destination / "tls"
    tls.mkdir(mode=0o700)
    # Bind-mounted secret files must be readable by container users; the parent
    # directory remains private on the host (0700). Never publish this directory.
    for name, value in {
        "oracle_admin_password": secrets.token_hex(24),
        "oracle_app_password": secrets.token_hex(24),
        "jwt_secret": base64.b64encode(secrets.token_bytes(48)).decode("ascii"),
    }.items():
        file = destination / name
        file.write_text(value, encoding="utf-8")
        file.chmod(0o644)
    subprocess.run([
        "openssl", "req", "-x509", "-newkey", "rsa:3072", "-sha256", "-nodes",
        "-keyout", str(tls / "server.key"), "-out", str(tls / "server.crt"),
        "-days", "90", "-subj", "/CN=" + host,
        "-addext", "subjectAltName=" + san + ",DNS:localhost,IP:127.0.0.1",
    ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    (tls / "server.key").chmod(0o600)
    (tls / "server.crt").chmod(0o644)
    (root / ".env").write_text(
        "# Configuración de red; secretos en secrets/, fuera de Git.\n"
        f"PUBLIC_HOST={host}\nHTTPS_BIND=0.0.0.0\nHTTPS_PORT=443\n"
        "PPP_SUBNET=172.30.80.0/24\nPPP_PROXY_IP=172.30.80.10\n", encoding="utf-8")
    print("Configuración creada. No se generó ninguna cuenta de la aplicación.")
    print("Certificado de prueba: secrets/tls/server.crt (90 días). Conservar secrets/ y .env.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print("No se completó la preparación:", str(error), file=sys.stderr)
        print("Se conservan los archivos existentes. Revisar antes de reintentar.", file=sys.stderr)
        sys.exit(1)
