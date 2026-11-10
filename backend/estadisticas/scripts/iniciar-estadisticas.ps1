$ErrorActionPreference = 'Stop'
$aseeProject = Split-Path -Parent $PSScriptRoot
$aseeJar = Join-Path $aseeProject 'target/estadisticas-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $aseeJar)) { throw 'Compila primero con .\mvnw.cmd package desde la carpeta del proyecto.' }
$aseeKeys = Join-Path $env:LOCALAPPDATA 'ASEE/estadisticas/keys'
function Invoke-AseeTool {
    param([string[]]$Arguments)
    & java '-Dloader.main=com.asee.estadisticas.tools.LocalTools' -cp $aseeJar 'org.springframework.boot.loader.launch.PropertiesLauncher' @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'No se ha podido preparar el entorno. Comprueba que MongoDB esta arrancado con --replSet asee-local.' }
}
if (-not $env:MONGODB_URI) { Invoke-AseeTool -Arguments @('init-mongo') }
if (-not $env:JWT_PUBLIC_KEY -and -not $env:JWT_JWK_SET_URI) {
    Invoke-AseeTool -Arguments @('keys', $aseeKeys)
    $env:JWT_PUBLIC_KEY = ([Uri](Join-Path $aseeKeys 'public.pem')).AbsoluteUri
    if (-not $env:JWT_ISSUER) { $env:JWT_ISSUER = 'asee-local' }
    if (-not $env:JWT_AUDIENCE) { $env:JWT_AUDIENCE = 'estadisticas' }
    Invoke-AseeTool -Arguments @('token', $aseeKeys, 'staff', '00000000-0000-0000-0000-000000000001', $env:JWT_ISSUER, $env:JWT_AUDIENCE, (Join-Path $env:LOCALAPPDATA 'ASEE/estadisticas/tokens/staff.jwt'))
}
Write-Output 'Estadisticas en http://localhost:8085/v1. Ctrl+C para detener.'
& java -jar $aseeJar
if ($LASTEXITCODE -ne 0) { throw 'Estadisticas no ha terminado correctamente; revisa los mensajes anteriores.' }
