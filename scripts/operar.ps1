param(
    [Parameter(Mandatory=$true)][ValidateSet('comprobar','renovar-certificado','respaldar','restaurar')][string]$Accion,
    [string]$Archivo
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = Split-Path -Parent $PSScriptRoot
Push-Location $Root
function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw 'Docker no completó la operación. Revisar el mensaje anterior y el README.' }
}
function Compose { Invoke-Docker compose --ansi never --progress plain -f compose.yaml @args }
function Tool {
    param([string[]]$Arguments, [switch]$Interactive, [switch]$RootUser, [string]$Volume, [switch]$ReadOnly, [string]$Network='none', [string]$OracleImage='', [string]$Commit='')
    $uidValue = if ($env:LOCAL_UID) { $env:LOCAL_UID } else { '1000' }
    $gidValue = if ($env:LOCAL_GID) { $env:LOCAL_GID } else { '1000' }
    $userValue = if ($RootUser) { '0:0' } else { "${uidValue}:${gidValue}" }
    $mount = "type=bind,source=$Root,target=/workspace"
    if ($ReadOnly -and -not $RootUser) { $mount += ',readonly' }
    $cmdArgs = @('run','--rm','--network',$Network,'--user',$userValue,'--workdir','/workspace','--mount',$mount,
        '-e',"LOCAL_UID=$uidValue",'-e',"LOCAL_GID=$gidValue",'--entrypoint','python')
    if ($Interactive) { $cmdArgs += '-it' }
    if ($Volume) {
        $volumeMount = "type=volume,source=$Volume,target=/oracle-data"
        if ($ReadOnly) { $volumeMount += ',readonly' }
        $cmdArgs += @('--mount',$volumeMount)
    }
    if ($OracleImage) { $cmdArgs += @('-e',"BACKUP_ORACLE_IMAGE=$OracleImage",'-e',"BACKUP_GIT_COMMIT=$Commit") }
    Invoke-Docker @cmdArgs control-ppp-tools:local /tools/maintenance.py @Arguments
}
function Healthy {
    foreach ($service in @('oracle','backend','frontend')) {
        $id = @(Compose ps -q $service)
        if ($id.Count -ne 1 -or -not $id[0]) { throw "Falta $service. Iniciar servicios según README." }
        $state = (Invoke-Docker inspect $id[0] | ConvertFrom-Json)[0].State
        if (-not $state.Running -or $state.Health.Status -ne 'healthy') { throw "$service no está saludable. Consultar sus registros." }
    }
}
function Schema { Compose exec -T oracle bash /operations/check-schema.sh }
function Live {
    $id = Compose ps -q frontend
    Tool -Arguments @('live-check') -ReadOnly -Network "container:$id"
}
try {
    Invoke-Docker image inspect control-ppp-tools:local --format '{{.Id}}' | Out-Null
    if ($Accion -eq 'restaurar') {
        if (-not $Archivo -or [IO.Path]::GetFileName($Archivo) -ne $Archivo) { throw 'Indicar solo el nombre del archivo dentro de backups/.' }
        if ((Test-Path .env) -or (Test-Path secrets)) { throw 'Restaurar requiere una instalación vacía sin .env ni secrets.' }
        if ($env:ORACLE_IMAGE) { throw 'Quitar ORACLE_IMAGE del entorno; la restauración fija el digest del respaldo.' }
        if (@(Compose ps -aq).Count) { throw 'Este proyecto ya tiene contenedores. Usar otra instalación de recuperación vacía.' }
    } elseif ($Accion -eq 'comprobar') { Tool -Arguments @('check') -ReadOnly }
    if ($Accion -eq 'comprobar') {
        Healthy; Schema; Live
        Write-Host 'Diagnóstico completado. No se modificaron datos.'
    } elseif ($Accion -eq 'renovar-certificado') {
        Tool -Arguments @('renew')
        Compose up -d --no-deps --force-recreate --wait --wait-timeout 120 frontend
        Tool -Arguments @('check') -ReadOnly
        Live
    } elseif ($Accion -eq 'respaldar' -or $Accion -eq 'restaurar') {
        $config = Compose config --format json | ConvertFrom-Json
        $volumeName = $config.volumes.'oracle-data'.name
        $projectName = $config.name
        if ($Accion -eq 'respaldar') {
            Healthy; Schema
            $oracleId = Compose ps -q oracle
            $volumeInfo = (Invoke-Docker volume inspect $volumeName | ConvertFrom-Json)[0]
            if ($volumeInfo.Labels.'com.docker.compose.project' -ne $projectName) { throw 'El volumen no pertenece al proyecto; respaldo cancelado.' }
            $imageId = Invoke-Docker inspect $oracleId --format '{{.Image}}'
            $digest = Invoke-Docker image inspect $imageId --format '{{index .RepoDigests 0}}'
            $commit = 'no-disponible'
            if (Get-Command git -ErrorAction SilentlyContinue) {
                $candidate = & git rev-parse HEAD 2>$null
                if ($LASTEXITCODE -eq 0) { $commit = $candidate }
            }
            Write-Host 'Se detendrán temporalmente los tres servicios para un respaldo consistente.'
            try {
                Compose stop frontend backend oracle
                $running = @(Compose ps --status running -q)
                if ($running.Count) { throw 'Hay servicios todavía activos; se cancela el respaldo.' }
                Tool -Arguments @('backup') -Interactive -RootUser -Volume $volumeName -ReadOnly -OracleImage $digest -Commit $commit
            } catch { Write-Host "Respaldo no completado: $_"; throw } finally { Compose up -d --wait --wait-timeout 900 }
        } else {
            # Docker creates only this named recovery volume; existing contents are checked by Python.
            Invoke-Docker volume create --label "com.docker.compose.project=$projectName" --label 'com.docker.compose.volume=oracle-data' $volumeName | Out-Null
            $volumeInfo = (Invoke-Docker volume inspect $volumeName | ConvertFrom-Json)[0]
            if ($volumeInfo.Labels.'com.docker.compose.project' -ne $projectName) { throw 'El volumen no pertenece al proyecto; no se restaurará.' }
            Tool -Arguments @('restore','--file',$Archivo) -Interactive -RootUser -Volume $volumeName
            Write-Host 'Restaurado. Continuar con up --build y comprobar, según README.'
        }
    }
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
} finally { Pop-Location }
