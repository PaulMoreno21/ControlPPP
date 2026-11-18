#!/bin/sh
set -eu
export ORACLE_PASSWORD="$(cat /run/secrets/oracle_app_password)"
export APP_JWT_SECRET_BASE64="$(cat /run/secrets/jwt_secret)"
exec java ${JAVA_OPTS:--Xms128m -Xmx768m} -jar /app/app.jar "$@"
