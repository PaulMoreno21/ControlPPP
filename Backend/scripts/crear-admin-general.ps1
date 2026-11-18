param([string]$Jar)
$ErrorActionPreference = 'Stop'
$backendDirectory = Split-Path -Parent $PSScriptRoot
if (-not $Jar) { $Jar = Join-Path $backendDirectory 'build/libs/practicas-0.0.1-SNAPSHOT.jar' }
$jarPath = (Resolve-Path -LiteralPath $Jar).Path
Push-Location -LiteralPath $backendDirectory
try {
    & java -jar $jarPath --crear-admin-general
    $commandExitCode = $LASTEXITCODE
} finally { Pop-Location }
exit $commandExitCode
