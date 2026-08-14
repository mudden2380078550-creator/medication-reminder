$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& (Join-Path $projectRoot 'gradlew.bat') clean assembleRelease @args
exit $LASTEXITCODE
