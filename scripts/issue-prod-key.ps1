<#
.SYNOPSIS
  Issues a device API key from the PRODUCTION database on Railway and prints it once.

  Runs the API's issue-key script inside the deployed `api` service (railway ssh), so the database
  never needs a public address. Needs the Railway CLI logged in (`railway login`) and this repo
  linked once:  cd api; railway link   (project: home-app, environment: production, service: api)

.EXAMPLE
  .\scripts\issue-prod-key.ps1 -Name phone
  .\scripts\issue-prod-key.ps1 -Name tablet -Rotate     # replace an existing key for "tablet"
  # then send it to the device running the release build:
  .\scripts\set-device-key.ps1 -Release -Key home_...
#>
param(
  [Parameter(Mandatory)] [string]$Name,
  [switch]$Rotate
)
$ErrorActionPreference = 'Stop'

$nodeArgs = @('dist/scripts/issue-key.js', '--name', $Name)
if ($Rotate) { $nodeArgs += '--rotate' }

Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'api')
try {
  railway ssh --service api -- node @nodeArgs
} finally {
  Pop-Location
}
