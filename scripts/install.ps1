<#
.SYNOPSIS
  Builds the Home app and installs it on every connected device (USB or wireless debugging).

.EXAMPLE
  .\scripts\install.ps1                 # debug build; talks to the API on this PC via adb reverse
  .\scripts\install.ps1 -Release        # signed release build (talks to the Railway API)
  .\scripts\install.ps1 -Device R5CT... # only one device (serial from `adb devices`)
#>
param(
  [switch]$Release,
  [string]$Device
)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }

$variant = if ($Release) { 'release' } else { 'debug' }
$task = if ($Release) { ':app:assembleRelease' } else { ':app:assembleDebug' }

Push-Location (Join-Path $root 'android')
try {
  & .\gradlew.bat $task
  if ($LASTEXITCODE -ne 0) { throw "Gradle build failed ($task)" }
} finally {
  Pop-Location
}

$apk = Get-ChildItem (Join-Path $root "android\app\build\outputs\apk\$variant") -Filter '*.apk' |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { throw "No APK found for $variant" }
if ($apk.Name -like '*unsigned*') {
  throw 'The release build is unsigned. Create the release keystore and android/keystore.properties first (README: Release signing).'
}

$serials = if ($Device) { @($Device) } else {
  & $adb devices | Select-String "`tdevice$" | ForEach-Object { ($_.Line -split "`t")[0] }
}
if (-not $serials) {
  throw 'No devices connected. Plug in over USB (accept the debugging prompt), or pair wirelessly: adb pair <ip:port>, then adb connect <ip:port>.'
}

foreach ($serial in $serials) {
  Write-Host "Installing $($apk.Name) on $serial..."
  & $adb -s $serial install -r $apk.FullName
  if ($LASTEXITCODE -ne 0) { throw "Install failed on $serial" }
  if (-not $Release) {
    # Debug builds call http://localhost:3000 on the device; forward that to the API on this PC.
    # (Lasts until the device disconnects or reboots; rerun this script, or just the line below.)
    & $adb -s $serial reverse tcp:3000 tcp:3000 | Out-Null
  }
}
Write-Host 'Done.'
