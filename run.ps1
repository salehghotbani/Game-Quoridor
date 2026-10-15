[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'Missing java. Install JDK 17 or newer and add its bin directory to PATH.'
    }
    & (Join-Path $PSScriptRoot 'build.ps1')
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    & java -jar (Join-Path $PSScriptRoot 'out\Quoridor.jar')
    exit $LASTEXITCODE
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
