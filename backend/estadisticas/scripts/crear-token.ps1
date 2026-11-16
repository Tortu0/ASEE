param(
    [ValidateSet('staff','admin','visitante','socio')][string]$Rol = 'staff',
    [Guid]$UsuarioId = '00000000-0000-0000-0000-000000000001'
)
$ErrorActionPreference = 'Stop'
$aseeJar = Join-Path (Split-Path -Parent $PSScriptRoot) 'target/estadisticas-0.0.1-SNAPSHOT.jar'
$aseeKeys = Join-Path $env:LOCALAPPDATA 'ASEE/estadisticas/keys'
$aseeToken = Join-Path $env:LOCALAPPDATA ('ASEE/estadisticas/tokens/' + $Rol + '.jwt')
& java '-Dloader.main=com.asee.estadisticas.tools.LocalTools' -cp $aseeJar 'org.springframework.boot.loader.launch.PropertiesLauncher' token $aseeKeys $Rol $UsuarioId.ToString() 'asee-local' 'estadisticas' $aseeToken
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el token. Arranca primero mediante iniciar-estadisticas.ps1 para preparar las claves locales.' }
