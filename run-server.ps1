[CmdletBinding()]
param([ValidateRange(1, 65535)][int]$Port = 8080)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')

try {
    Assert-ProjectTools -Names @('java')
    & (Join-Path $PSScriptRoot 'build-server.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Server build failed.' }
    $previousPort = $env:PORT
    try {
        $env:PORT = [string]$Port
        Invoke-ProjectCommand -Command 'java' -Arguments @('--add-modules', 'jdk.httpserver', '-jar', (Join-Path $PSScriptRoot 'out\Quoridor-server.jar')) -Description 'Room server'
    }
    finally { $env:PORT = $previousPort }
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
