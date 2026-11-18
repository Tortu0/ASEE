$ErrorActionPreference = 'Stop'
$aseeProject = Split-Path -Parent $PSScriptRoot
$aseeJar = Join-Path $aseeProject 'target/estadisticas-0.0.1-SNAPSHOT.jar'
if (-not $env:ASEE_TEST_MONGO_URI) {
    if (-not (Test-Path -LiteralPath $aseeJar)) {
        throw 'Ejecuta primero .\mvnw.cmd package y arranca MongoDB con iniciar-mongodb.ps1.'
    }
    & java '-Dloader.main=com.asee.estadisticas.tools.LocalTools' -cp $aseeJar 'org.springframework.boot.loader.launch.PropertiesLauncher' init-mongo
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar el replica set local.' }
}
$aseePreviousIntegration = $env:ASEE_INTEGRATION
try {
    $env:ASEE_INTEGRATION = 'true'
    & (Join-Path $aseeProject 'mvnw.cmd') -f (Join-Path $aseeProject 'pom.xml') test
    if ($LASTEXITCODE -ne 0) { throw 'Las pruebas han fallado; revisa target/surefire-reports.' }
} finally {
    $env:ASEE_INTEGRATION = $aseePreviousIntegration
}
