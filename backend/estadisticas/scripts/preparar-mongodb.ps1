$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$mongoVersion = '8.0.34'
$mongoRoot = Join-Path $env:LOCALAPPDATA 'ASEE/estadisticas/mongodb'
$mongoArchiveName = 'mongodb-windows-x86_64-' + $mongoVersion + '.zip'
$mongoUrl = 'https://fastdl.mongodb.org/windows/' + $mongoArchiveName
$mongoArchive = Join-Path $mongoRoot $mongoArchiveName
$mongoBinary = Join-Path $mongoRoot ('mongodb-win32-x86_64-windows-' + $mongoVersion + '/bin/mongod.exe')

if (Test-Path -LiteralPath $mongoBinary) {
    Write-Output ('MongoDB ya preparado: ' + $mongoBinary)
    exit 0
}

New-Item -ItemType Directory -Force -Path $mongoRoot | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Write-Output ('Descargando MongoDB Community ' + $mongoVersion + ' desde mongodb.org...')
Invoke-WebRequest -UseBasicParsing -Uri $mongoUrl -OutFile $mongoArchive
$mongoChecksumText = (Invoke-WebRequest -UseBasicParsing -Uri ($mongoUrl + '.sha256')).Content
$mongoMatch = [regex]::Match([string]$mongoChecksumText, '(?i)\b[0-9a-f]{64}\b')
if (-not $mongoMatch.Success) { throw 'No se pudo leer la suma SHA256 oficial.' }
$mongoActualChecksum = (Get-FileHash -LiteralPath $mongoArchive -Algorithm SHA256).Hash
if ($mongoActualChecksum -ine $mongoMatch.Value) { throw 'La suma SHA256 no coincide. No se extraera el archivo.' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$mongoZip = [IO.Compression.ZipFile]::OpenRead($mongoArchive)
try {
    $mongoResolvedRoot = [IO.Path]::GetFullPath($mongoRoot) + [IO.Path]::DirectorySeparatorChar
    foreach ($mongoEntry in $mongoZip.Entries) {
        $mongoDestination = [IO.Path]::GetFullPath((Join-Path $mongoRoot $mongoEntry.FullName))
        if (-not $mongoDestination.StartsWith($mongoResolvedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'El ZIP contiene una ruta fuera de la carpeta de destino.'
        }
        if ($mongoEntry.Name) {
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($mongoDestination)) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($mongoEntry, $mongoDestination, $false)
        }
    }
} finally { $mongoZip.Dispose() }

if (-not (Test-Path -LiteralPath $mongoBinary)) {
    throw ('No se encontro mongod.exe en la ruta esperada: ' + $mongoBinary)
}
Write-Output ('SHA256 verificado: ' + $mongoActualChecksum)
& $mongoBinary --version
if ($LASTEXITCODE -ne 0) { throw 'MongoDB no se puede ejecutar en este equipo.' }
Write-Output 'Preparado. Ejecuta .\scripts\iniciar-mongodb.ps1 para arrancar.'
