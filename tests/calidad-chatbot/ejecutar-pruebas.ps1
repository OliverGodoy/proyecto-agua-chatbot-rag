<#
HU-39: pruebas de calidad de respuestas del chatbot.
Envía cada caso de casos.json al ApiRest, verifica la respuesta y guarda la
evidencia en resultados/resultados-AAAA-MM-DD_HHmm.md.
Funciona en Windows PowerShell 5.1 y en PowerShell 7.

Uso (con el ApiRest corriendo):
  .\tests\calidad-chatbot\ejecutar-pruebas.ps1
  .\tests\calidad-chatbot\ejecutar-pruebas.ps1 -Repeticiones 3
  .\tests\calidad-chatbot\ejecutar-pruebas.ps1 -Url http://localhost:3000/api/v1/chat

Con -Repeticiones N cada pregunta se envía N veces y el caso solo se aprueba si
todas las respuestas son correctas (prueba de estabilidad: el LLM no siempre
responde igual).
#>
param(
    [string]$Url = "http://localhost:8081/api/v1/chat",
    [string]$Casos = (Join-Path $PSScriptRoot "casos.json"),
    [ValidateRange(1, 10)]
    [int]$Repeticiones = 1
)

$ErrorActionPreference = "Stop"

# Minúsculas y sin tildes, para comparar sin depender de la redacción exacta.
function Normalizar([string]$texto) {
    if (-not $texto) { return "" }
    $descompuesto = $texto.Normalize([Text.NormalizationForm]::FormD)
    $sb = New-Object Text.StringBuilder
    foreach ($c in $descompuesto.ToCharArray()) {
        if ([Globalization.CharUnicodeInfo]::GetUnicodeCategory($c) -ne [Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$sb.Append($c)
        }
    }
    return $sb.ToString().ToLowerInvariant()
}

function Preguntar([string]$pregunta) {
    $cuerpo = [Text.Encoding]::UTF8.GetBytes((@{ question = $pregunta } | ConvertTo-Json -Compress))
    $reloj = [Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-WebRequest -Uri $Url -Method Post -ContentType "application/json; charset=utf-8" `
            -Body $cuerpo -UseBasicParsing -TimeoutSec 90
        $texto = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        return @{ estado = [int]$r.StatusCode; json = ($texto | ConvertFrom-Json); ms = $reloj.ElapsedMilliseconds }
    } catch {
        $respuesta = $_.Exception.Response
        if ($null -eq $respuesta) {
            return @{ estado = 0; json = $null; ms = $reloj.ElapsedMilliseconds; error = $_.Exception.Message }
        }
        $texto = $_.ErrorDetails.Message
        if (-not $texto -and ($respuesta | Get-Member -Name GetResponseStream)) {
            $lector = New-Object IO.StreamReader($respuesta.GetResponseStream(), [Text.Encoding]::UTF8)
            $texto = $lector.ReadToEnd()
        }
        $json = $null
        if ($texto) { try { $json = $texto | ConvertFrom-Json } catch { } }
        return @{ estado = [int]$respuesta.StatusCode; json = $json; ms = $reloj.ElapsedMilliseconds }
    }
}

function Celda([string]$texto) {
    if (-not $texto) { return "" }
    return ($texto -replace "\r?\n", " " -replace "\|", "\|")
}

$definicion = Get-Content -Raw -Encoding UTF8 $Casos | ConvertFrom-Json
$resultados = @()

# Evalúa una respuesta contra el caso y devuelve la lista de fallos.
function Evaluar($caso, $res) {
    $esperado = if ($caso.estadoEsperado) { [int]$caso.estadoEsperado } else { 200 }
    $fallos = @()
    if ($res.estado -ne $esperado) {
        $detalle = if ($res.error) { $res.error } elseif ($res.json.code) { $res.json.code } else { "" }
        $fallos += "HTTP $($res.estado) (se esperaba $esperado) $detalle".Trim()
    } elseif ($res.estado -eq 200) {
        $normalizada = Normalizar ([string]$res.json.answer)
        foreach ($patron in @($caso.debeContener)) {
            if ($patron -and $normalizada -notmatch $patron) { $fallos += "no contiene: $patron" }
        }
        foreach ($patron in @($caso.noDebeContener)) {
            if ($patron -and $normalizada -match $patron) { $fallos += "contiene lo que no debía: $patron" }
        }
        if ($caso.sinFuentes -and @($res.json.sources | Where-Object { $_ }).Count -gt 0) {
            $fallos += "devolvió fuentes, pero no tiene la información"
        }
        if ($caso.fuenteEsperada) {
            $documentos = (@($res.json.sources | Where-Object { $_ } | ForEach-Object { $_.document }) -join " ")
            if ($documentos -notmatch $caso.fuenteEsperada) { $fallos += "falta la fuente: $($caso.fuenteEsperada)" }
        }
    } elseif ($caso.codigoEsperado -and $res.json.code -ne $caso.codigoEsperado) {
        $fallos += "código $($res.json.code) (se esperaba $($caso.codigoEsperado))"
    }
    return ,$fallos
}

function Describir($res) {
    if ($res.estado -eq 200) { return [string]$res.json.answer }
    if ($res.json) { return "HTTP $($res.estado) $($res.json.code): $($res.json.message)" }
    return "HTTP $($res.estado) $($res.error)"
}

foreach ($caso in $definicion.casos) {
    $intentos = @()
    for ($n = 1; $n -le $Repeticiones; $n++) {
        $res = Preguntar $caso.pregunta
        $intentos += [pscustomobject]@{ Numero = $n; Res = $res; Fallos = (Evaluar $caso $res) }
    }

    $correctos = @($intentos | Where-Object { $_.Fallos.Count -eq 0 })
    $aprobado = $correctos.Count -eq $Repeticiones
    # Se reporta el primer intento fallido, o el primero si todos fueron correctos.
    $muestra = if ($aprobado) { $intentos[0] } else { @($intentos | Where-Object { $_.Fallos.Count -gt 0 })[0] }
    $fallos = @()
    foreach ($intento in $intentos) {
        foreach ($f in $intento.Fallos) {
            $prefijo = if ($Repeticiones -gt 1) { "intento $($intento.Numero): " } else { "" }
            $fallos += "$prefijo$f"
        }
    }
    $ms = [math]::Round((@($intentos | ForEach-Object { $_.Res.ms }) | Measure-Object -Average).Average)
    $fuentes = @()
    if ($muestra.Res.estado -eq 200) {
        $fuentes = @($muestra.Res.json.sources | Where-Object { $_ } | ForEach-Object { "$($_.document) — $($_.section)" })
    }

    $color = if ($aprobado) { "Green" } else { "Red" }
    $marca = if ($aprobado) { "OK  " } else { "FALLA" }
    $estabilidad = "$($correctos.Count)/$Repeticiones"
    Write-Host ("[{0}] {1} {2} ({3} ms) {4}" -f $marca, $caso.id, $estabilidad, $ms, $caso.pregunta) -ForegroundColor $color
    foreach ($f in $fallos) { Write-Host "        $f" -ForegroundColor Yellow }

    $resultados += [pscustomobject]@{
        Id = $caso.id; Tipo = $caso.tipo; Pregunta = $caso.pregunta; Esperada = $caso.respuestaEsperada
        Obtenida = (Describir $muestra.Res); Fuentes = $fuentes; Estado = $muestra.Res.estado; Ms = $ms
        Aprobado = $aprobado; Fallos = $fallos; Estabilidad = $estabilidad
    }
}

# Evidencia en Markdown
$total = $resultados.Count
$aprobados = @($resultados | Where-Object { $_.Aprobado }).Count
$fecha = Get-Date
$md = New-Object Text.StringBuilder
[void]$md.AppendLine("# Resultados de calidad del chatbot (HU-39)")
[void]$md.AppendLine("")
[void]$md.AppendLine("- Fecha: $($fecha.ToString('yyyy-MM-dd HH:mm'))")
[void]$md.AppendLine("- Endpoint: $Url")
[void]$md.AppendLine("- Repeticiones por caso: $Repeticiones (un caso se aprueba solo si todas sus respuestas son correctas)")
[void]$md.AppendLine("- Resultado: **$aprobados de $total casos aprobados**")
foreach ($grupo in ($resultados | Group-Object Tipo)) {
    $ok = @($grupo.Group | Where-Object { $_.Aprobado }).Count
    [void]$md.AppendLine("  - $($grupo.Name): $ok de $($grupo.Count)")
}
$tiempos = @($resultados | Where-Object { $_.Estado -eq 200 } | ForEach-Object { $_.Ms })
if ($tiempos.Count -gt 0) {
    $promedio = [math]::Round(($tiempos | Measure-Object -Average).Average)
    [void]$md.AppendLine("- Tiempo promedio de respuesta: $promedio ms")
}
[void]$md.AppendLine("")
[void]$md.AppendLine("## Resumen")
[void]$md.AppendLine("")
[void]$md.AppendLine("| ID | Tipo | Pregunta | Estabilidad | Resultado | Tiempo promedio |")
[void]$md.AppendLine("|---|---|---|---|---|---|")
foreach ($r in $resultados) {
    $estado = if ($r.Aprobado) { "Aprobado" } else { "Falla: " + (Celda ($r.Fallos -join "; ")) }
    [void]$md.AppendLine("| $($r.Id) | $($r.Tipo) | $(Celda $r.Pregunta) | $($r.Estabilidad) | $estado | $($r.Ms) ms |")
}
[void]$md.AppendLine("")
[void]$md.AppendLine("## Detalle")
foreach ($r in $resultados) {
    [void]$md.AppendLine("")
    [void]$md.AppendLine("### $($r.Id): $($r.Pregunta)")
    [void]$md.AppendLine("")
    [void]$md.AppendLine("- Respuesta esperada: $($r.Esperada)")
    [void]$md.AppendLine("- Respuesta obtenida: $(Celda $r.Obtenida)")
    [void]$md.AppendLine("- Intentos correctos: $($r.Estabilidad)")
    if ($r.Fuentes.Count -gt 0) { [void]$md.AppendLine("- Fuentes: $($r.Fuentes -join '; ')") }
    $veredicto = if ($r.Aprobado) { "Aprobado" } else { "Falla (" + ($r.Fallos -join "; ") + ")" }
    [void]$md.AppendLine("- Resultado: $veredicto")
}

$carpeta = Join-Path $PSScriptRoot "resultados"
New-Item -ItemType Directory -Force $carpeta | Out-Null
$sufijo = if ($Repeticiones -gt 1) { "-x$Repeticiones" } else { "" }
$archivo = Join-Path $carpeta ("resultados-" + $fecha.ToString("yyyy-MM-dd_HHmm") + $sufijo + ".md")
[IO.File]::WriteAllText($archivo, $md.ToString(), (New-Object Text.UTF8Encoding($false)))

Write-Host ""
$colorTotal = if ($aprobados -eq $total) { "Green" } else { "Yellow" }
Write-Host "Aprobados: $aprobados de $total" -ForegroundColor $colorTotal
Write-Host "Evidencia guardada en: $archivo"
if ($aprobados -ne $total) { exit 1 }
