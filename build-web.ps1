[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    $webNode = (Get-Command node -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    & $webNode (Join-Path $PSScriptRoot 'web/scripts/build.mjs')
    if ($LASTEXITCODE -ne 0) { throw "Web build failed (exit code $LASTEXITCODE)." }
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
