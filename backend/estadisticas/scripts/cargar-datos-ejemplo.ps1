# Carga estadisticas de ejemplo registrando eventos por la API real (POST /partidos/{id}/eventos).
# Pasa por la validacion de Deportivo: los partidos o jugadores que Deportivo no conozca se omiten.
# Se puede repetir sin duplicar: los eventos ya cargados responden 409 y se ignoran.
# Requisitos: Estadisticas y Deportivo arrancados, y un token staff (scripts/crear-token.ps1).
param(
    [string]$Base = 'http://localhost:8085/v1',
    [string]$Token,
    [Guid[]]$PartidoIds = @('20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000003'),
    [Guid[]]$JugadorIds = @(1..12 | ForEach-Object { '10000000-0000-0000-0000-{0:D12}' -f $_ })
)
$ErrorActionPreference = 'Stop'
if (-not $Token) {
    $aseeTokenFile = Join-Path $env:LOCALAPPDATA 'ASEE/estadisticas/tokens/staff.jwt'
    if (-not (Test-Path $aseeTokenFile)) { throw 'No hay token. Ejecuta antes scripts/crear-token.ps1 o pasa -Token.' }
    $Token = (Get-Content -Raw $aseeTokenFile).Trim()
}
$headers = @{ Authorization = "Bearer $Token" }
$tipos = @('tiro_2_anotado', 'tiro_2_anotado', 'tiro_2_fallado', 'tiro_3_anotado', 'tiro_3_fallado',
    'tiro_libre_anotado', 'tiro_libre_fallado', 'rebote_ofensivo', 'rebote_defensivo', 'rebote_defensivo',
    'asistencia', 'robo', 'perdida', 'tapon', 'falta_personal')

function Send-Evento([Guid]$Partido, $Jugador, [string]$Tipo, [int]$Periodo, [int]$Segundos, [string]$Clave) {
    $datos = @{ clientEventoId = $Clave; tipo = $Tipo; periodo = $Periodo; segundosRestantes = $Segundos }
    if ($Jugador) { $datos.jugadorId = $Jugador.ToString() }
    try {
        $r = Invoke-WebRequest -Method Post -Uri "$Base/partidos/$Partido/eventos" -Headers $headers `
            -ContentType 'application/json' -Body ($datos | ConvertTo-Json) -UseBasicParsing
        return [pscustomobject]@{ Codigo = [int]$r.StatusCode; Mensaje = '' }
    } catch {
        if (-not $_.Exception.Response) { throw "No se pudo conectar con Estadisticas en $Base" }
        $codigo = [int]$_.Exception.Response.StatusCode
        $mensaje = "$($_.ErrorDetails.Message)"
        try { $mensaje = ($mensaje | ConvertFrom-Json).mensaje } catch { }
        return [pscustomobject]@{ Codigo = $codigo; Mensaje = $mensaje }
    }
}
function Test-Fallo($Resultado) {
    if ($Resultado.Codigo -notin 201, 404, 409) {
        throw "Peticion rechazada ($($Resultado.Codigo)): $($Resultado.Mensaje)"
    }
}

$creados = 0; $existentes = 0; $rechazados = 0
for ($pi = 0; $pi -lt $PartidoIds.Count; $pi++) {
    $partido = $PartidoIds[$pi]
    $res = Send-Evento $partido $null 'inicio_partido' 1 600 'demo-inicio'
    Test-Fallo $res
    if ($res.Codigo -eq 404) { Write-Host "Partido $partido no existe en Deportivo: omitido"; continue }
    if ($res.Codigo -eq 201) { $creados++ }
    elseif ($res.Mensaje -like '*ya registrado*') { $existentes++ }
    else { $rechazados++; Write-Warning "Partido ${partido} inicio: $($res.Mensaje)" }
    $cargados = 0
    for ($ji = 0; $ji -lt $JugadorIds.Count; $ji++) {
        $rnd = [System.Random]::new(($pi + 1) * 100 + $ji)
        $eventos = 14 + $rnd.Next(0, 9); $faltas = 0
        for ($n = 1; $n -le $eventos; $n++) {
            $tipo = $tipos[$rnd.Next(0, $tipos.Count)]
            if ($tipo -eq 'falta_personal') { if ($faltas -ge 4) { $tipo = 'rebote_defensivo' } else { $faltas++ } }
            $periodo = [Math]::Min(4, 1 + [int][Math]::Floor(($n - 1) * 4 / $eventos))
            $res = Send-Evento $partido $JugadorIds[$ji] $tipo $periodo $rnd.Next(30, 600) ("demo-{0}-{1}" -f $JugadorIds[$ji], $n)
            Test-Fallo $res
            if ($res.Codigo -eq 404) { break }
            if ($res.Codigo -eq 201) { $creados++; $cargados++ }
            elseif ($res.Mensaje -like '*ya registrado*') { $existentes++ }
            else { $rechazados++; Write-Warning "Jugador $($JugadorIds[$ji]) evento ${n}: $($res.Mensaje)" }
        }
    }
    Write-Host "Terminado. Creados: $creados. Ya existian: $existentes. Rechazados por reglas: $rechazados."
}
Write-Host "Terminado. Eventos creados: $creados. Ya existian: $existentes."
Write-Host "Comprueba: GET $Base/partidos/$($PartidoIds[0])/estadisticas"
