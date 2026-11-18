#!/bin/bash
set -e
app_password="$(cat /run/secrets/oracle_app_password)"
# Setup generates hexadecimal credentials: reject other input before embedding in CONNECT.
if [[ ! "$app_password" =~ ^[A-Za-z0-9]{24,}$ ]]; then
  echo 'Credencial de instalación inválida; revisar secrets sin mostrar su contenido.' >&2
  exit 1
fi
sqlplus -s /nolog <<SQL
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
SET DEFINE OFF
SET ECHO OFF
CONNECT CTRLPPP/"${app_password}"@//localhost:1521/FREEPDB1
@/schema/CTRLPRAC_CREAR.sql
EXIT SUCCESS
SQL
unset app_password
