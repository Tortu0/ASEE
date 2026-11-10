$ErrorActionPreference = 'Stop'
$mongoRoot = Join-Path $env:LOCALAPPDATA 'ASEE/estadisticas/mongodb'
$mongoBinary = Join-Path $mongoRoot 'mongodb-win32-x86_64-windows-8.0.34/bin/mongod.exe'
$mongoData = Join-Path $mongoRoot 'data'
$mongoLog = Join-Path $mongoRoot 'logs/mongod.log'
if (-not (Test-Path -LiteralPath $mongoBinary)) {
    throw 'Primero ejecuta .\scripts\preparar-mongodb.ps1.'
}
New-Item -ItemType Directory -Force -Path $mongoData,(Split-Path -Parent $mongoLog) | Out-Null
Write-Output 'MongoDB local en 127.0.0.1:27017. Manten esta terminal abierta.'
Write-Output 'Para detenerlo correctamente, pulsa Ctrl+C en esta terminal.'
Write-Output ('Datos: ' + $mongoData)
Write-Output ('Registro: ' + $mongoLog)
& $mongoBinary --bind_ip 127.0.0.1 --port 27017 --dbpath $mongoData --logpath $mongoLog --logappend --replSet asee-local
if ($LASTEXITCODE -ne 0) {
    throw ('MongoDB termino con codigo ' + $LASTEXITCODE + '. Revisa ' + $mongoLog)
}
