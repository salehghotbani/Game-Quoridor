[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')

try {
    Assert-ProjectTools -Names @('java')
    Invoke-ProjectBuild -ProjectRoot $PSScriptRoot
    $jarPath = Join-Path $PSScriptRoot 'out\Quoridor.jar'
    Invoke-ProjectCommand -Command 'java' -Arguments @('-jar', $jarPath) -Description 'Application'
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
