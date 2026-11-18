[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts/ProjectTools.ps1')

Push-Location $PSScriptRoot
try {
    git diff --quiet HEAD --
    if ($LASTEXITCODE -ne 0) { throw 'Commit or stash tracked changes before validating a push.' }
    git show --check --format= HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Whitespace validation failed.' }
    $shell = (Get-Process -Id $PID).Path
    $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'test-web.ps1'))
    # Same production build, Node/parity, Java CORS, server and browser suite as CI.
    Invoke-ProjectCommand -Command $shell -Arguments $arguments -Description 'Pre-push web verification'
    Write-Host 'Pre-push checks passed. No site was published.'
}
finally { Pop-Location }
