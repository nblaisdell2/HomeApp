<#
.SYNOPSIS
  Sends a device API key to the Home app over adb, so you don't have to type it on the device.
  Only fills in a missing key; to replace an existing key, use Settings in the app.

.EXAMPLE
  # 1. Issue a key (local API, or against Railway: see README)
  cd api; npm run issue-key -- --name tablet
  # 2. Send it to the connected device
  .\scripts\set-device-key.ps1 -Key home_xxx -Device emulator-5556
  .\scripts\set-device-key.ps1 -Key home_xxx -Release     # the release build ("Home")
#>
param(
  [Parameter(Mandatory)] [string]$Key,
  [string]$Device,
  [switch]$Release
)
$ErrorActionPreference = 'Stop'

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$package = if ($Release) { 'com.nblaisdell.home' } else { 'com.nblaisdell.home.debug' }
$deviceArgs = if ($Device) { @('-s', $Device) } else { @() }

& $adb @deviceArgs shell am start -n "$package/com.nblaisdell.home.MainActivity" --es apiKey $Key
if ($LASTEXITCODE -ne 0) { throw 'adb failed. Is the device connected and the app installed?' }
Write-Host 'Sent. The app shows a message saying whether the server accepted the key.'
