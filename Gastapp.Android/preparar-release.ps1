<#
.SYNOPSIS
    Compila el release del telefono firmado con gastappkeystore, comprueba la huella y deja
    en release\<version>\ los archivos del GitHub Release. No publica nada.

.DESCRIPTION
    Pasos:
      1. Compila :app (y :wear con -ConReloj) en release.
      2. Compara la huella SHA-256 del APK contra la de gastappkeystore. Si no coincide se
         detiene: un APK firmado con otra llave no se puede instalar encima de la version
         publicada (ya paso una vez con la llave de debug de Xamarin).
      3. Copia el APK con el nombre que espera el API (com.binc.gastapp-Signed.apk; el del
         reloj lleva "wear" en el nombre para que el API no lo ofrezca al telefono), escribe
         version.json y guarda mapping.txt si el release va minificado.
      4. Imprime el comando de gh para publicarlo. Publicar lo decide el usuario.

    Las credenciales del keystore salen de local.properties (gastappKeystore,
    gastappKeyAlias, gastappKeystorePassword, gastappKeyPassword) o de las variables
    GASTAPP_KEYSTORE, GASTAPP_KEY_ALIAS, GASTAPP_KEYSTORE_PASSWORD y GASTAPP_KEY_PASSWORD.

.PARAMETER Etiqueta
    Etiqueta del release en GitHub. Por defecto v<versionName>, p. ej. v2.0.1.

.PARAMETER ConReloj
    Compila y agrega tambien el APK del reloj (solo si cambio :wear).

.EXAMPLE
    .\preparar-release.ps1 -Etiqueta v2.0.1-alpha1
#>
param(
    [string]$Etiqueta,
    [switch]$ConReloj
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# Huella del certificado de gastappkeystore (CN="Cesar Maat"), la misma de la app MAUI
# publicada. Se compara completa: que no sea una llave de debug conocida no prueba nada.
$huellaEsperada = '2bdd2f06a93bf2f036c2394746da0b8eddf479ca9f6b9430d8da6b5195d5a667'

function Get-BuildTools {
    $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools'
    $ultima = Get-ChildItem $sdk -Directory | Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } | Select-Object -Last 1
    if ($null -eq $ultima) { throw "No se encontro build-tools en $sdk" }
    return $ultima.FullName
}

function Assert-Firma([string]$apk) {
    $apksigner = Join-Path (Get-BuildTools) 'apksigner.bat'
    $ErrorActionPreference = 'Continue'
    $salida = & $apksigner verify --print-certs $apk 2>$null | Out-String
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw "apksigner no pudo verificar $apk (sin firmar?)" }
    # El formato cambia entre versiones de build-tools ("Signer #1 certificate ..." en la 36,
    # "V2 Signer: certificate ..." en la 37): se toman todas las huellas que aparezcan.
    $huellas = @([regex]::Matches($salida, 'certificate SHA-256 digest:\s*([0-9a-f]{64})') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique)
    if ($huellas.Count -ne 1 -or $huellas[0] -ne $huellaEsperada) {
        throw "Firma equivocada en $apk`n  esperada: $huellaEsperada`n  obtenida: $($huellas -join ', ')`nNo publicar."
    }
    Write-Host "  Firma correcta (gastappkeystore): $apk" -ForegroundColor Green
}

function Read-Version([string]$carpetaApk) {
    $meta = Get-Content (Join-Path $carpetaApk 'output-metadata.json') -Raw | ConvertFrom-Json
    return $meta.elements[0]
}

$tareas = @(':app:assembleRelease')
if ($ConReloj) { $tareas += ':wear:assembleRelease' }

Write-Host "Compilando $($tareas -join ', ')..."
# Gradle escribe avisos en stderr; con 'Stop' PowerShell 5.1 los tomaria como error.
$ErrorActionPreference = 'Continue'
& .\gradlew.bat @tareas
$codigo = $LASTEXITCODE
& .\gradlew.bat --stop 2>$null | Out-Null
$ErrorActionPreference = 'Stop'
if ($codigo -ne 0) { throw 'Fallo la compilacion.' }

$carpetaApp = 'app\build\outputs\apk\release'
$apkApp = Join-Path $carpetaApp 'app-release.apk'
if (-not (Test-Path $apkApp)) {
    throw "No hay $apkApp. Si salio app-release-unsigned.apk, faltan las credenciales del keystore."
}
Assert-Firma $apkApp

$version = Read-Version $carpetaApp
if (-not $Etiqueta) { $Etiqueta = "v$($version.versionName)" }

$destino = Join-Path 'release' $Etiqueta
New-Item -ItemType Directory -Force $destino | Out-Null
Copy-Item $apkApp (Join-Path $destino 'com.binc.gastapp-Signed.apk') -Force

# El API (AppUpdateService) lee este archivo del release para saber si hay version nueva.
$versionJson = [ordered]@{ versionCode = [int]$version.versionCode; versionName = $Etiqueta.TrimStart('v') }
$versionJson | ConvertTo-Json | Out-File (Join-Path $destino 'version.json') -Encoding ascii

$archivos = @("$destino\com.binc.gastapp-Signed.apk", "$destino\version.json")

if ($ConReloj) {
    $carpetaReloj = 'wear\build\outputs\apk\release'
    $apkReloj = Join-Path $carpetaReloj 'wear-release.apk'
    Assert-Firma $apkReloj
    $versionReloj = Read-Version $carpetaReloj
    $nombreReloj = "gastapp-wear-$($versionReloj.versionName).apk"
    Copy-Item $apkReloj (Join-Path $destino $nombreReloj) -Force
    $archivos += "$destino\$nombreReloj"
}

# Con R8 activo, el mapping traduce las pilas de error ofuscadas. No se publica: se guarda.
# Solo si salio del mismo build: con R8 apagado puede quedar uno viejo de otra compilacion
# (R8 lo escribe poco antes de empaquetar el APK, nunca despues).
$mapping = 'app\build\outputs\mapping\release\mapping.txt'
$mismoBuild = (Test-Path $mapping) -and
    ((Get-Item $mapping).LastWriteTime -gt (Get-Item $apkApp).LastWriteTime.AddMinutes(-10))
if ($mismoBuild) {
    Copy-Item $mapping (Join-Path $destino 'mapping.txt') -Force
    Write-Host "  mapping.txt guardado en $destino (no se sube al release)."
}

$tamano = [math]::Round((Get-Item $apkApp).Length / 1MB, 1)
Write-Host ''
Write-Host "Listo: $Etiqueta (versionCode $($version.versionCode), $tamano MB) en $destino" -ForegroundColor Green
Write-Host 'El versionCode tiene que ser mayor que el del release anterior o la app no avisara.'
Write-Host ''
Write-Host 'Para publicarlo (prerelease, como los anteriores):'
$lista = ($archivos | ForEach-Object { "`"$_`"" }) -join ' '
Write-Host "  gh release create $Etiqueta $lista --prerelease --title $Etiqueta --notes-file <notas.md>"
