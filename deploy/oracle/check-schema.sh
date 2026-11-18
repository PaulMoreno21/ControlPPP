#!/bin/bash
set -eu
app_password="$(cat /run/secrets/oracle_app_password)"
[[ "$app_password" =~ ^[A-Za-z0-9]{24,}$ ]] || { echo 'Revisar la credencial Oracle sin mostrarla.' >&2; exit 1; }
sqlplus -s /nolog <<SQL
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT FAILURE
SET DEFINE OFF ECHO OFF FEEDBACK OFF SERVEROUTPUT ON
CONNECT CTRLPPP/"${app_password}"@//localhost:1521/FREEPDB1
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM user_tables WHERE table_name IN
    ('TCWROLES','TCWLABORATORIOS','TCWUSUARIOS','TCWUSUARIOSROLES',
     'TCWASIGNACIONES','TCWFERIADOS','TCWASISTENCIAS','TCWACTIVIDADES',
     'TCWENVIOACTIVIDADES','TCWREVISIONES','TCWAUDITORIAS');
  IF n <> 11 THEN raise_application_error(-20001,'Esquema incompleto; seguir recuperación del README.'); END IF;
  SELECT COUNT(*) INTO n FROM TCWROLES;
  IF n < 4 THEN raise_application_error(-20002,'Faltan roles iniciales; no repetir SQL sobre datos existentes.'); END IF;
  SELECT COUNT(*) INTO n FROM user_objects WHERE status = 'INVALID';
  IF n > 0 THEN raise_application_error(-20003,'Hay objetos inválidos; revisar con el responsable Oracle.'); END IF;
  dbms_output.put_line('Oracle: conexión, 11 tablas, roles y objetos válidos comprobados.');
END;
/
EXIT SUCCESS
SQL
