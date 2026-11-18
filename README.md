# Control PPP — instalación y operación con Docker

Control PPP administra prácticas preprofesionales: usuarios y roles, laboratorios, asignaciones de tutores, jornadas, actividades, revisión, reportes y exportación de bitácoras. Utiliza Angular, Spring Boot con Java 21 y Oracle. Los roles disponibles son Administrador General, Administrador de Laboratorio, Tutor ESPE y Practicante.

Abre una terminal en la raíz del proyecto, donde están este README, compose.yaml, Frontend y Backend. Los pasos 1 a 6 preparan y ejecutan el sistema completo con Docker.

## Cómo seguir esta guía

Este README contiene un único procedimiento de instalación: **Docker Compose ejecuta Oracle, Backend y Frontend**. Sigue los pasos 1 a 5 para preparar y utilizar el sistema. El paso 6 explica los inicios posteriores y la parada opcional.

No necesitas instalar Java, Node.js o npm en el equipo anfitrión ni iniciar Backend y Frontend manualmente. Docker los compila y ejecuta. Tampoco necesitas crear `Backend/.env`. Si elegiste `PUBLIC_HOST=localhost` y el puerto 443, entrarás por **`https://localhost/login`** desde ese mismo equipo; para otros hosts/puertos, sigue el paso 5.

## Requisitos para comenzar

- Docker Engine con Docker Compose en Linux, o Docker Desktop con contenedores Linux en Windows.
- Acceso a Internet durante la construcción para descargar imágenes y dependencias.
- Recursos y espacio disponibles para Oracle y la construcción de la aplicación. La configuración limita Oracle a 3 GB y Backend a 1 GB; reserva capacidad adicional para el sistema operativo y las compilaciones.
- Puerto HTTPS 443 disponible, o un puerto alternativo configurado en el .env raíz después del setup.

Comprueba que Docker esté iniciado y que tu usuario tenga acceso:

```sh
docker info
docker compose version
```

## Archivos de configuración de la instalación

| Archivo | Uso | Cómo se prepara |
| --- | --- | --- |
| `.env` en la raíz | Puertos, dirección y subred de Docker Compose. | El instalador del paso 2 lo genera. |
| `secrets/` en la raíz | Contraseñas Oracle, clave de sesiones y certificado para Docker. | El mismo instalador lo genera; se conserva fuera de Git. |
| `Backend/.env.example` | Referencia de las variables del Backend para mantenimiento del código. | Se conserva como plantilla; no debes copiarla ni completarla para esta instalación Docker. |

Docker Compose no lee Backend/.env: proporciona sus variables y monta los archivos de secrets/. Su entrypoint carga las credenciales para el proceso Java. No copies el .env.example al .env raíz ni crees ese archivo raíz antes de ejecutar el instalador. Los archivos .env reales y secrets/ deben permanecer fuera del repositorio.

## 1. Construir la herramienta de instalación

```sh
docker compose -f compose.tools.yaml build tools
```

El Compose de herramientas es independiente: funciona aunque todavía no exista `.env`. No tiene acceso al socket de Docker y su ejecución no utiliza red.

## 2. Preparar configuración y certificados, una sola vez

Para acceder desde el mismo equipo, prepara la instalación con localhost:

```sh
docker compose -f compose.tools.yaml run --rm -e PUBLIC_HOST=localhost tools
```

Elige **una sola** de estas modalidades antes del setup. Los valores de IP y DNS son ejemplos: usa la dirección real del servidor, sin protocolo ni puerto.

| Modalidad | Valor en el comando de setup | Dirección que abrirás |
| --- | --- | --- |
| Solo el equipo servidor | `-e PUBLIC_HOST=localhost` | `https://localhost` |
| Equipos de la red, usando IP estable | `-e PUBLIC_HOST=192.168.1.50` | `https://192.168.1.50` |
| Equipos de la red, usando nombre DNS | `-e PUBLIC_HOST=control-ppp.universidad.edu.ec` | `https://control-ppp.universidad.edu.ec` |

Por ejemplo, para la segunda modalidad, ejecuta **en lugar del comando de localhost**:

```sh
docker compose -f compose.tools.yaml run --rm -e PUBLIC_HOST=192.168.1.50 tools
```

PUBLIC_HOST no crea registros DNS: configura en el DNS de la organización un registro que resuelva a la IP estable del servidor. Habilita el puerto HTTPS en su firewall y verifica que la red permita llegar a él. El script genera contraseñas aleatorias para Oracle, una clave de sesiones y un certificado TLS de prueba de 90 días. No genera ni imprime contraseñas de usuarios de la aplicación.

En Linux se ejecuta con UID/GID 1000 por defecto. Si tu usuario tiene otros identificadores, consulta `id -u` e `id -g` y añade `--user UID:GID` después de `run --rm`, sustituyendo los números reales. Los archivos deben quedar bajo el control del operador. `secrets/` es privado en el host; sus archivos de credenciales son legibles dentro de los contenedores que los necesitan. Restringe también el acceso físico y los respaldos.

El setup se niega a sobrescribir `.env` o `secrets/`. Si ya están preparados, omite este paso. Un fallo parcial conserva lo generado para revisión: no vuelvas a ejecutar ni borres credenciales de una base ya inicializada.

## 3. Construir e iniciar los servicios

Espera a que termine cada comando antes de continuar:

```sh
docker compose -f compose.yaml config --quiet
docker compose -f compose.yaml up -d --build --wait --wait-timeout 900
docker compose -f compose.yaml ps
```

La primera construcción puede tardar varios minutos. Se ejecutan las pruebas locales del Backend al construir su imagen. Las pruebas que necesitan una base externa son optativas (RUN_ORACLE_CONTEXT_TESTS y RUN_ORACLE_READONLY_TESTS); la aplicación real valida el esquema al arrancar. Angular puede mostrar advertencias de presupuesto que no impiden la construcción si no se supera su límite de error.

Al mostrar los tres servicios saludables, Oracle, Backend y Frontend ya están ejecutándose. Continúa con la creación del primer administrador y el acceso web; no abras otras instancias manuales de la aplicación.

Oracle utiliza un volumen persistente. En el primer inicio se crea el esquema `CTRLPPP` en `FREEPDB1` y se ejecuta automáticamente `documentacion/CTRLPRAC_CREAR.sql` como propietario de ese esquema, deteniéndose ante un error. El SQL contiene las tablas, restricciones, secuencias, triggers, índices y roles iniciales. Al reutilizar el volumen, no se vuelve a ejecutar el esquema: es un script para una base vacía, no un mecanismo de actualización.

Si falla la inicialización, revisa `docker compose -f compose.yaml logs --tail 150 oracle`. Un volumen parcialmente inicializado exige diagnóstico antes de reintentar. No ejecutes el consolidado sobre una base con objetos ni borres volúmenes para corregir una contraseña.

Solo se publica HTTPS del Frontend (443 por defecto). Oracle 1521 y Backend 8080 permanecen sin publicar. Nginx sirve Angular y dirige `/api/` al Backend dentro de Docker.

## 4. Crear el primer Administrador General

Con Oracle disponible, desde una terminal interactiva:

```sh
docker compose -f compose.yaml run --rm --no-deps backend --crear-admin-general
```

Solicita identificación, nombres, apellidos, correo, contraseña normal y confirmación. La contraseña permanece oculta y debe tener al menos 12 caracteres. El comando genera BCrypt; no pide hashes. Guarda la cuenta LOCAL activa con rol ADMIN_GENERAL global, sin laboratorio, y una auditoría, dentro de una transacción.

Si ya existe un administrador general, aunque esté inactivo, se detiene sin modificarlo. También rechaza datos duplicados o inválidos. No es un comando para restablecer contraseñas. Después del alta, los demás usuarios y administradores se gestionan desde Usuarios. No uses `-T`, tuberías ni contraseñas en argumentos. El contenedor termina al concluir y no abre otro servidor web.

## 5. Entrar al sistema

Abre la dirección correspondiente a tu elección del paso 2: `https://localhost`, la IP o el DNS configurado. Si cambiaste `HTTPS_PORT`, añade `:puerto`; por ejemplo, `https://localhost:8443`. El certificado inicial es autofirmado: instala `secrets/tls/server.crt` como certificado de confianza en los equipos de prueba mediante el procedimiento de tu organización. Verifica su origen antes de confiar en él.

Si usaste `PUBLIC_HOST=localhost` y no cambiaste el puerto, escribe **`https://localhost/login`** en el navegador del mismo equipo que ejecuta Docker. Incluye `https://`: el Compose publica HTTPS en el puerto 443. `http://localhost:8080` no es la interfaz web de esta instalación; Backend y Oracle se comunican dentro de Docker y sus puertos no se publican hacia el equipo. Si abres el sistema desde otra computadora, `localhost` identifica esa computadora: utiliza la IP/DNS del servidor preparada en el paso 2.

### Si el navegador muestra «La conexión no es privada»

El certificado inicial es autofirmado. El aviso `NET::ERR_CERT_AUTHORITY_INVALID` significa que el navegador no confía en su emisor; no es un rechazo de permisos de la cuenta ni un fallo de Oracle. Para una comprobación puntual en tu propio equipo, después de verificar que abres la instalación que acabas de preparar, el navegador puede ofrecer **Opciones avanzadas → Acceder a localhost (sitio no seguro)**. Esa excepción no establece confianza permanente para todos los clientes.

Para confiar en el certificado de esta instalación de pruebas en Windows, desde PowerShell en la raíz del proyecto:

1. Consulta los datos del certificado generado y confirma su origen antes de importarlo:

```powershell
$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new((Resolve-Path .\secrets\tls\server.crt).Path)
$cert | Format-List Subject, Issuer, NotAfter, Thumbprint
```

2. Si es el certificado de tu instalación, impórtalo en las entidades de certificación raíz de confianza de **tu usuario actual**. Acepta la confirmación de Windows si aparece:

```powershell
Import-Certificate -FilePath .\secrets\tls\server.crt -CertStoreLocation Cert:\CurrentUser\Root
```

3. Cierra todas las ventanas del navegador, vuelve a abrirlo y entra por la dirección configurada, por ejemplo `https://localhost/login`. En equipos administrados, la política de confianza puede requerir intervención de TI. Un navegador con almacén propio puede necesitar importar allí el certificado mediante su procedimiento autorizado.

El archivo que se distribuye a los clientes es únicamente `server.crt`; `server.key` es privado y permanece en el servidor. En otro equipo, obtén el certificado público del operador por un canal de confianza y verifica su identidad antes de importarlo; no necesitas trasladar toda la carpeta `secrets/`. Tras renovar el certificado autofirmado debes actualizar la confianza en los clientes. Para producción institucional, usa el certificado del dominio aprobado y la política de confianza de la organización.

Referencia de Windows: [Import-Certificate](https://learn.microsoft.com/en-us/powershell/module/pki/import-certificate?view=windowsserver2025-ps).

Para acceso institucional utiliza un certificado válido para el dominio aprobado: reemplaza `server.crt` por la cadena PEM y `server.key` por su clave correspondiente en `secrets/tls/`, conserva la clave privada protegida y recrea Frontend. No distribuyas la clave privada. El certificado de prueba vence a los 90 días y su renovación no está automatizada. Esto no detiene los contenedores ni borra datos; afecta la confianza del navegador y puede bloquear el acceso hasta renovarlo. Renuévalo antes de vencer sin reinstalar Oracle.

Ingresa con el correo y la contraseña elegidos. Luego registra laboratorios, usuarios, asignaciones de tutor y feriados. Comprueba jornadas, actividades, revisión y exportación. La fotografía facial del SRS sigue pendiente y no se solicita en esta versión. El acceso institucional ESPE y las restricciones de red de asistencia siguen pendientes de aprobación/configuración; limita el piloto a la red privada o VPN autorizada.

## 6. Inicios posteriores, parada y registros

Con el paso 5 termina la instalación con Docker. Las secciones siguientes explican mantenimiento, diagnóstico y recuperación; no requieren iniciar Java o Angular manualmente.

Para volver a iniciar una instalación ya preparada, desde la raíz del proyecto, sin repetir setup ni crear otra vez el primer administrador:

```sh
docker compose -f compose.yaml up -d --build --wait --wait-timeout 900
docker compose -f compose.yaml ps
```

Para consultar los registros manteniendo la aplicación disponible:

```sh
docker compose -f compose.yaml logs --tail 100 backend frontend
```

**Ejecuta el siguiente comando únicamente cuando quieras detener el sistema.** Detiene y retira los contenedores; la aplicación deja de estar disponible hasta el siguiente `up`:

```sh
docker compose -f compose.yaml down
```

`down` conserva el volumen de Oracle. No añadas `--volumes` ni ejecutes limpiezas globales de Docker sobre este servidor. Las sesiones y los contadores de solicitudes son locales al Backend; reiniciarlo exige volver a iniciar sesión. Esta configuración usa una sola instancia del Backend.

## Configuración y red

`.env` guarda `PUBLIC_HOST`, `HTTPS_BIND`, `HTTPS_PORT`, `PPP_SUBNET` y `PPP_PROXY_IP`; los secretos están en `secrets/`, ignorados por Git. Compose los monta solo en los servicios que los requieren. La aplicación sigue usando su configuración de cookies seguras y CSRF, con Frontend y API bajo el mismo origen.

Nginx tiene la IP fija `172.30.80.10` y sobrescribe `X-Real-IP`. El Backend confía únicamente en esa IP para los límites de solicitudes. Si la subred `172.30.80.0/24` choca con la red del servidor, cambia conjuntamente `PPP_SUBNET` y `PPP_PROXY_IP` en `.env`, usando una IP disponible dentro de la nueva subred; después recrea los contenedores. Si añades otro proxy delante, revisa el origen real de las IP y su política de confianza.

Cambiar el archivo de contraseña de Oracle no cambia la contraseña de una base ya creada. Una rotación requiere actualizar Oracle y sus archivos de forma coordinada. Nunca subas `.env`, `secrets/` ni respaldos al repositorio. El secreto de sesiones también debe conservarse y rotarse con un procedimiento controlado.

## Herramientas de operación

Todos los comandos siguientes se ejecutan desde la raíz del proyecto. No necesitan Python, OpenSSL ni GnuPG instalados en el servidor: los proporciona la imagen `control-ppp-tools:local`. Reconstrúyela cuando actualices `deploy/tools/`:

```sh
docker compose -f compose.tools.yaml build tools
```

En Linux, usa `sh scripts/operar.sh ACCION`. En Windows, usa `powershell -NoProfile -File scripts/operar.ps1 -Accion ACCION`. Los scripts usan el mismo proyecto Compose que el inicio normal. Si necesitas distinguir instalaciones, establece `COMPOSE_PROJECT_NAME` antes de **todos** los comandos Compose y de operación: `$env:COMPOSE_PROJECT_NAME='control-ppp-recuperacion'` en PowerShell o `export COMPOSE_PROJECT_NAME=control-ppp-recuperacion` en Linux. No cambies el nombre de una instalación existente sin identificar antes su volumen.

En Linux, los scripts usan el UID/GID del operador; si preparaste los archivos con otra cuenta, corrige sus permisos con el responsable del servidor. En Windows se usa el UID/GID 1000 de Docker por defecto. No publiques `secrets/`, `.local/` ni `backups/`.

### Diagnóstico de solo lectura

Linux:

```sh
sh scripts/operar.sh comprobar
```

Windows:

```powershell
powershell -NoProfile -File scripts/operar.ps1 -Accion comprobar
```

Comprueba configuración, presencia de claves sin imprimir sus valores, correspondencia del certificado y su clave, host y vencimiento, salud de los tres servicios, conexión Oracle, tablas/roles/objetos válidos, HTTPS, ruta de Angular, CSRF y rechazo de sesión anónima. Avisa si el certificado vence en menos de 14 días. No inicia servicios ni modifica registros de negocio. Si falla, revisa el mensaje y los registros del servicio indicado; no borres volúmenes. La prueba HTTP ocurre dentro de Docker: prueba también IP/DNS, certificado y puerto desde un equipo de cada red autorizada.

### Renovar el certificado de pruebas

No repitas el setup para renovar TLS. Estos comandos sustituyen **solo** el certificado autofirmado y su clave, guardan el par anterior en `secrets/tls/anterior-*`, y recrean únicamente Frontend:

```sh
sh scripts/operar.sh renovar-certificado
```

```powershell
powershell -NoProfile -File scripts/operar.ps1 -Accion renovar-certificado
```

El nuevo certificado dura 90 días. Se conservan `.env`, contraseñas Oracle, clave de sesiones y datos. Instala el nuevo `server.crt` en los clientes según el procedimiento de confianza autorizado. Hay una breve interrupción de Frontend. El comando funciona también después del vencimiento, pero debes programar la renovación antes. No existe una tarea automática de renovación. Un vencimiento no elimina el despliegue ni la base.

Para un certificado institucional, solicita la renovación al emisor. El script se niega a reemplazar un certificado cuyo emisor difiere del titular. Conserva privadamente el par anterior, coloca la cadena PEM vigente en `secrets/tls/server.crt` y su clave correspondiente en `server.key`, mantén restringidos los permisos y ejecuta:

```sh
docker compose -f compose.yaml up -d --no-deps --force-recreate --wait --wait-timeout 120 frontend
```

Después ejecuta `comprobar`. No cambies las contraseñas ni reinstales Oracle para corregir un certificado. Si cambia la IP/DNS, actualiza `PUBLIC_HOST` y emite un certificado para esa dirección antes de recrear Frontend.

### Respaldo cifrado con parada controlada

Con los tres servicios saludables, ejecuta en una terminal interactiva:

```sh
sh scripts/operar.sh respaldar
```

```powershell
powershell -NoProfile -File scripts/operar.ps1 -Accion respaldar
```

Los servicios se detienen para copiar Oracle de forma consistente. La herramienta pide una contraseña de respaldo de al menos 16 caracteres y su confirmación, ambas ocultas. Produce `backups/control-ppp-FECHA-ID.tar.gz.gpg`: base completa, `.env`, `secrets/`, versión del código, arquitectura y digest exacto de Oracle. Se cifra con GnuPG/AES-256 mientras se genera; no deja una copia completa sin cifrar. Los scripts intentan reiniciar los servicios al terminar, también si falla el respaldo. Comprueba su salud y ejecuta `comprobar` después. Si el equipo pierde energía o se cierra Docker, inicia los servicios manualmente según el paso 6.

Planifica la interrupción y espacio libre suficiente para el archivo. Copia el `.gpg` a un almacenamiento independiente protegido; un archivo en el mismo disco no cubre su pérdida. Guarda su contraseña en un gestor de secretos separado: no se puede recuperar si la olvidas. No la pases por argumentos, variables de entorno ni historial de comandos. No hay programación automática de respaldos; define frecuencia, retención y responsable según el uso real. Un respaldo se considera comprobado después de restaurarlo en otro entorno.

### Restaurar en una instalación vacía

La recuperación es una copia física en frío, no una importación SQL. Requiere la misma arquitectura de CPU de la imagen Docker, la versión del código del respaldo, acceso a su imagen Oracle por digest y espacio para el archivo cifrado, el temporal descifrado y la base completa. El temporal se elimina al terminar; si se interrumpe el proceso o se pierde energía, revisa y elimina de forma controlada el directorio privado `.local/restauracion-*`, que puede contener datos sin cifrar. No se restaura encima de usuarios o datos existentes.

1. Prepara otra carpeta con la misma versión del repositorio. **No ejecutes setup ni crees el administrador**. Si usas el mismo servidor, establece un `COMPOSE_PROJECT_NAME` de recuperación distinto; esto crea un volumen separado.
2. Construye la imagen de herramientas. Crea `backups/` y copia allí el `.gpg` elegido.
3. Ejecuta uno de estos comandos, sustituyendo el nombre real del archivo:

```sh
sh scripts/operar.sh restaurar control-ppp-FECHA-ID.tar.gz.gpg
```

```powershell
powershell -NoProfile -File scripts/operar.ps1 -Accion restaurar -Archivo control-ppp-FECHA-ID.tar.gz.gpg
```

La herramienta exige ausencia de `.env` y `secrets/`, ningún contenedor del proyecto y un volumen vacío. Solicita la contraseña oculta y comprueba integridad, rutas, versión y arquitectura antes de extraer la base. Si la versión no coincide, muestra el commit de origen cuando se registró; prepara esa versión y reintenta. Un error durante la extracción puede dejar un volumen parcial: no lo inicies ni sobrescribas; conserva el diagnóstico y utiliza otra instalación vacía.

4. La restauración recupera credenciales, configuración y cuentas existentes, y fija `ORACLE_IMAGE` en `.env` con el digest del respaldo. No cambies ese valor para usar otra versión de Oracle sin un procedimiento de actualización validado.
5. En un entorno paralelo ajusta **antes del inicio** `HTTPS_BIND`/`HTTPS_PORT`, `PPP_SUBNET` y `PPP_PROXY_IP` para no colisionar con la instalación original. Si cambia `PUBLIC_HOST`, emite un certificado para el destino. No mantengas ambos entornos accesibles a usuarios reales con las mismas credenciales.
6. Inicia y comprueba:

```sh
docker compose -f compose.yaml config --quiet
docker compose -f compose.yaml up -d --build --wait --wait-timeout 900
```

Ejecuta `comprobar`, inicia sesión con una cuenta recuperada y verifica usuarios, jornadas, actividades y documentos. No recrees el primer administrador: la cuenta ya está en el respaldo. Si el certificado recuperado venció, usa `renovar-certificado` antes del diagnóstico final. Mantén la instalación anterior intacta hasta aprobar la recuperación.

### Recuperar una instalación incompleta

| Situación | Acción que conserva los datos |
| --- | --- |
| Falló una descarga o compilación | Conserva `.env`, `secrets/` y volumen. Repite `up -d --build --wait --wait-timeout 900` y consulta el primer error. |
| Existen `.env` y `secrets/`, pero no contenedores | Omite setup y continúa desde el paso 3. |
| Existe `secrets/` y falta `.env` | No generes credenciales nuevas. Recupera `.env` de tu respaldo; si nunca se creó, reconstruye solo sus cinco valores de red del paso 2, con el host del certificado existente. Comprueba TLS y conectividad. |
| Existe `.env` pero faltan secretos | Detén el inicio. Recupera los secretos originales desde respaldo. Generar otros archivos no cambia las contraseñas guardadas en Oracle. |
| Oracle inicia y Backend falla | Revisa los registros y ejecuta el diagnóstico. Distingue credenciales, esquema y configuración; no ejecutes el consolidado otra vez. |
| Falló el SQL del primer inicio | Conserva registros y volumen. Un especialista debe evaluar el esquema parcial o recuperar un respaldo en otro volumen vacío. El contenedor puede recordar la inicialización y no repetir el SQL. |
| Restauración interrumpida | Conserva el origen y el respaldo cifrado. No arranques el volumen parcial. Repite en otro proyecto y destino vacíos. |

Los comandos operativos no eliminan volúmenes ni regeneran credenciales Oracle. No uses `down --volumes`, `docker volume prune` ni limpiezas globales como solución de errores. Si no existe un respaldo de credenciales perdidas de una base inicializada, solicita recuperación o rotación coordinada a su responsable.

### Actualización y regreso a la versión anterior

Este procedimiento cubre cambios de aplicación **compatibles con el mismo esquema y configuración**. Antes de actualizar, comprueba que no haya cambios locales pendientes, que el respaldo se haya probado y que puedas recuperar la versión anterior. Guarda fuera del repositorio el resultado de `git rev-parse HEAD`. Conserva también las imágenes de aplicación actuales:

```sh
git status --short
git rev-parse HEAD
docker image tag control-ppp-backend:local control-ppp-backend:previous
docker image tag control-ppp-frontend:local control-ppp-frontend:previous
```

Ejecuta `respaldar` y copia el resultado fuera del servidor. Las etiquetas `previous` se sobrescriben al repetir estos comandos: no las reemplaces mientras puedas necesitar la recuperación de esa actualización. Para conservar también las imágenes fuera de Docker:

```sh
docker image save -o backups/aplicacion-anterior.tar control-ppp-backend:previous control-ppp-frontend:previous
```

Este archivo de imágenes no incluye datos ni credenciales de ejecución, y no sustituye al respaldo cifrado. Protégelo como código del proyecto. Actualiza después:

```sh
git pull --ff-only
docker compose -f compose.tools.yaml build tools
docker compose -f compose.yaml up -d --build --wait --wait-timeout 900
```

Ejecuta `comprobar` y prueba login, usuarios y un recorrido de actividad/revisión/exportación con cuentas de prueba. Si la actualización falla, conserva registros. Para regresar a la versión guardada, detén Frontend/Backend, vuelve al commit registrado y usa las imágenes preservadas:

```sh
docker compose -f compose.yaml stop frontend backend
git switch --detach COMMIT_ANTERIOR
docker compose -f compose.yaml -f compose.rollback.yaml up -d --no-build --wait --wait-timeout 900
```

Sustituye `COMMIT_ANTERIOR` por el identificador guardado. Si las imágenes ya no están en el motor, recupéralas antes mediante `docker image load -i backups/aplicacion-anterior.tar`. Mantén `compose.rollback.yaml` en los comandos de inicio mientras uses esas etiquetas; no reconstruyas ni vuelvas al Compose normal hasta decidir la siguiente versión. Ejecuta nuevamente `comprobar`.

Si cambió el modelo de datos, este regreso de imágenes puede ser incompatible: prepara y valida un procedimiento específico de evolución y recuperación de base antes de actualizar. Restaurar un respaldo previo pierde las escrituras posteriores a ese respaldo; planifica la intervención y validación con su responsable. Nunca vuelvas a ejecutar `CTRLPRAC_CREAR.sql` sobre una instalación existente.

## Contenido del repositorio

| Ruta | Uso |
| --- | --- |
| `compose.yaml` | Oracle, Backend, Frontend, red y volumen persistente. |
| `compose.tools.yaml`, `deploy/tools/` | Preparación, diagnóstico, certificado y respaldo/restauración cifrada. |
| `scripts/operar.ps1`, `scripts/operar.sh` | Operación del despliegue desde PowerShell o Linux. |
| `compose.rollback.yaml` | Regreso a las imágenes preservadas, con el procedimiento anterior. |
| `Backend/` | Código, pruebas, Gradle, comando de primer administrador y Dockerfile. |
| `Frontend/` | Código Angular, dependencias fijadas, recursos, Nginx y Dockerfile. |
| `deploy/oracle/` | Inicialización del esquema con SQL*Plus. |
| `documentacion/CTRLPRAC_CREAR.sql` | Esquema consolidado de instalación nueva. |
| `documentacion/SRS.pdf` | Requisitos, reglas y alcance del sistema. |
| `documentacion/` | SRS y manuales PDF de desarrollo y usuario. El procedimiento de instalación está completo en este README. |


## Verificación de la entrega

La instalación se verificó en un proyecto Docker aislado: construcción de imágenes, carga del esquema Oracle, alta del administrador por consola, acceso HTTPS, login, cookies, CSRF, sesión y conservación de la cuenta tras recrear contenedores. No equivale a una prueba de carga ni a aceptación de todos los recorridos. Antes de incorporar usuarios reales comprueba el acceso desde cada red autorizada, la creación de usuarios y el flujo de actividad/revisión/exportación.

Las herramientas operativas se comprobaron con nueve pruebas automatizadas de archivos sintéticos y una recuperación real de Oracle en otro volumen vacío: respaldo cifrado con parada/reinicio, restauración con imagen fijada, diagnóstico, login de la cuenta recuperada y cookie segura. También se probó la renovación de TLS conservando las credenciales. El recorrido completo se ejecutó con PowerShell y Docker Desktop; del launcher Linux se comprobó la sintaxis, sin acreditar todavía una ejecución completa en un servidor Linux. La actualización/regreso se documenta para versiones compatibles; no se simuló una actualización de negocio ni una evolución de esquema.

Fuentes técnicas: [imagen Oracle Free](https://github.com/gvenzl/oci-oracle-free), [secretos de Compose](https://docs.docker.com/compose/how-tos/use-secrets/), [orden de inicio](https://docs.docker.com/compose/how-tos/startup-order/). Las etiquetas de imágenes están declaradas en los Dockerfiles y Compose; fijar digests aprobados al promover una entrega institucional.

Las pruebas locales de las herramientas usan únicamente archivos sintéticos y pueden repetirse después de construir la imagen:

```sh
docker compose -f compose.tools.yaml run --rm --user 0:0 --entrypoint python tools -m unittest discover -s /workspace/deploy/tools -p test_maintenance.py -v
```

No sustituyen una recuperación real periódica en un entorno separado.

## Documentación complementaria

| Documento | Contenido |
| --- | --- |
| [SRS](documentacion/SRS.pdf) | Requisitos y alcance, con funciones futuras identificadas. |
| [Manual de desarrollo](documentacion/Manual_Desarrollador.pdf) | Arquitectura, datos, seguridad, API, pruebas y mantenimiento. |
| [Manual de usuario](documentacion/Manual_Usuario.pdf) | Procedimientos por rol y problemas frecuentes. |

La consulta de estos PDF es complementaria: la instalación y puesta en marcha se completan con este README.
## Comprobación rápida y solución de problemas

- Si intentaste arrancar Java manualmente y apareció `ORA-12541` en `localhost:1521`, usa los servicios iniciados por Compose y el acceso HTTPS del paso 5. Oracle está disponible dentro de la red Docker, sin publicar 1521 hacia el equipo. La instalación de esta guía no requiere una segunda instancia de Backend.
- Si aparece `NET::ERR_CERT_AUTHORITY_INVALID`, sigue el apartado de confianza del certificado del paso 5. Si aparece otro «permiso denegado», conserva el mensaje exacto, el comando o la URL para distinguir un bloqueo del navegador de una respuesta de la aplicación.
- `docker compose -f compose.yaml ps` debe mostrar oracle, backend y frontend saludables. Si un servicio no lo está, consulta sus registros antes de continuar.
- Para errores de base, ejecuta `docker compose -f compose.yaml logs --tail 150 oracle`. No borres el volumen ni repitas el SQL sobre datos existentes para intentar corregir un fallo.
- Para errores de aplicación, ejecuta `docker compose -f compose.yaml logs --tail 100 backend frontend` y revisa la primera causa de inicio.
- Si no abre la dirección, verifica IP/DNS, certificado, firewall y puerto HTTPS. En Windows puedes usar `Test-NetConnection NOMBRE_O_IP -Port 443`, sustituyendo la dirección. Otros segmentos de la universidad pueden estar aislados por VLAN o Wi-Fi.
- Si no permite ingresar, utiliza la cuenta creada por consola, confirma que sea Local y esté activa; la integración institucional no está habilitada todavía. No hay usuario o contraseña por defecto.
- El primer administrador se crea una sola vez; los siguientes se administran desde Usuarios. El comando de instalación no restablece contraseñas de cuentas existentes.
- La sesión se cierra tras 15 minutos de inactividad y tiene un máximo de 8 horas desde el login. Las interacciones del usuario mantienen el plazo de inactividad; las consultas automáticas no lo renuevan. Reiniciar Backend exige ingresar otra vez. Estos plazos son independientes de la vigencia del certificado HTTPS y no detienen el servicio. Se configuran con APP_SESSION_IDLE_TIMEOUT_SECONDS=900 y APP_SESSION_MAX_DURATION_SECONDS=28800 en el entorno del Backend; en Docker Compose pueden declararse en el .env de la raíz. Ambos deben ser positivos y la inactividad no debe superar la duración máxima. Después de actualizar Backend y Frontend, inicia sesión nuevamente.
- Por defecto se admiten, por ventana de 60 segundos, 300 solicitudes por IP, 120 por usuario y 10 intentos de login por IP. Si aparece un límite de solicitudes, espera antes de reintentar.


## Feriados: precarga anual y edición

La integración opcional con feriados.io obtiene las fechas para registrarlas en Oracle. El listado, la asistencia y las actividades consultan únicamente Oracle. La precarga del año actual se completa una sola vez por año: al iniciar se comprueba su constancia persistente y el 1 de enero a las 03:15 (Ecuador) se permite cargar el año nuevo. Si la instalación estuvo apagada, la carga pendiente se intenta en el siguiente inicio. No hay consultas diarias ni carga anticipada del año siguiente.

### Configurar en Docker

Después de crear el primer administrador general, añade estas variables al `.env` de la raíz, junto a `compose.yaml`:

```properties
APP_HOLIDAY_SYNC_ENABLED=true
FERIADOS_API_KEY=
APP_HOLIDAY_SYNC_ADMIN_ID=0
APP_HOLIDAY_SYNC_CRON=0 15 3 1 1 *
```

Completa la clave privada obtenida en [feriados.io](https://feriados.io/register) y sustituye `0` por el ID real de un ADMIN_GENERAL global activo de **esta base**. El `.env` del Backend se utiliza solo para ejecución nativa; Docker Compose lee estas variables del `.env` raíz y las entrega al Backend. La clave del entorno de desarrollo no se incorpora al repositorio ni se copia automáticamente a esta instalación.

Para consultar los administradores desde consola:

```bash
docker compose -f compose.yaml exec oracle sqlplus / as sysdba
```

Dentro de SQL*Plus ejecuta:

```sql
ALTER SESSION SET CONTAINER = FREEPDB1;
SELECT DISTINCT u.TCWUSUARIOS_ID, u.TCWUSUARIOS_NOMBRE, u.TCWUSUARIOS_APELLIDO
FROM CTRLPPP.TCWUSUARIOS u
JOIN CTRLPPP.TCWUSUARIOSROLES ur ON ur.TCWUSUARIOS_ID = u.TCWUSUARIOS_ID
JOIN CTRLPPP.TCWROLES r ON r.TCWROLES_ID = ur.TCWROLES_ID
WHERE u.TCWUSUARIOS_ACTIVO = 1
  AND r.TCWROLES_NOMBRE = 'ADMIN_GENERAL'
  AND ur.TCWLABORATORIOS_ID IS NULL
ORDER BY u.TCWUSUARIOS_ID;
EXIT;
```

La cuenta responsable satisface la asociación obligatoria de cada feriado a un administrador; no se usa su contraseña para importar. La auditoría identifica al Sistema como ejecutor.

Para aplicar una actualización de código o configuración:

```bash
docker compose -f compose.yaml up -d --build --wait --wait-timeout 900
```

Inicia sesión de nuevo después de recrear el Backend y consulta **Feriados**. En ejecución nativa, configura las mismas variables en `Backend/.env` y reinicia Java. La plantilla `Backend/.env.example` contiene los valores iniciales sin credenciales. Con clave vacía o responsable `0` no se llama a la API; `APP_HOLIDAY_SYNC_ENABLED=false` desactiva la función sin borrar datos. Si el año ya se completó, su constancia impide nuevas llamadas aunque se reinicie o cambie la clave.

### Funcionamiento y conservación

Se importan solo eventos nacionales con fecha y nombre válidos del país/año solicitado. Se omiten las fechas existentes, incluso manuales o inactivas, y se conservan los años anteriores. Los feriados y un único evento resumen `PRECARGA_ANUAL_FERIADOS`, con año y cantidad de registros nuevos, se confirman en la misma transacción. Ese evento es también la constancia anual: consérvalo al mantener la auditoría. Si falla la consulta o la transacción, se conserva lo existente y el año pendiente puede reintentarse en el siguiente inicio.

Después de completar un año puedes editar fecha/descripción, desactivar o eliminar feriados sin que se vuelvan a importar ese año. La tabla presenta Fecha, Descripción, Estado y Acciones, con un modal para Editar; conserva los datos originales de creación y rechaza fechas duplicadas. No muestra el comentario de precarga anual. Los eventos individuales de importaciones anteriores permanecen en la base, pero se excluyen del listado paginado de auditoría y de su conteo; el resumen anual y las acciones manuales siguen visibles.

La integración usa `GET https://api.feriados.io/v1/EC/holidays/{año}` con Bearer privado, tiempo máximo de 8 segundos, límite de 64 KiB y redirecciones deshabilitadas. El endpoint local `GET /api/v1/admin/holidays/synchronization` permite consultar el estado a ADMIN_GENERAL global sin exponer claves; la pantalla usa ese estado internamente para refrescar el listado al completar una carga. No se requieren tablas ni migraciones nuevas.

Verifica las fechas efectivas de descanso y las condiciones del proveedor antes de producción. La respuesta Free comprobada indica uso para desarrollo/pruebas y `production_use=false`; también informa un TTL de caché, que por sí solo no determina el tratamiento de registros históricos de negocio. El Sistema no borra feriados automáticamente. Consulta las [condiciones](https://feriados.io/terms) y el [plan](https://feriados.io/pricing) adecuado para el despliegue definitivo.

### Fechas de actividades

La creación individual y por lote rechaza fechas futuras según el reloj del Sistema. Este límite se aplica solo al crear; la edición conserva sus permisos anteriores. Se mantienen la prohibición de fines de semana/feriados, el límite de seis horas y la posibilidad de registrar actividades sin jornada.
