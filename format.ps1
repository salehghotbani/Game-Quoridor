[CmdletBinding()]
param([switch]$Check)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')

try {
    Assert-ProjectTools -Names @('java')
    $projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $toolDirectory = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory 'tools'
    $version = '1.24.0'
    $filename = "google-java-format-$version-all-deps.jar"
    $formatterPath = Join-Path $toolDirectory $filename
    $expectedHash = '812F805F58112460EDF01BF202A8E61D0FD1F35C0D4FABD54220640776EC57A1'

    if ((Test-Path -LiteralPath $formatterPath) -and
        ((Get-Item -LiteralPath $formatterPath -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
        throw "Refusing to use a linked formatter file: $formatterPath"
    }
    if (-not (Test-Path -LiteralPath $formatterPath -PathType Leaf)) {
        $downloadPath = Join-Path $toolDirectory ([System.IO.Path]::GetRandomFileName())
        try {
            $uri = "https://github.com/google/google-java-format/releases/download/v$version/$filename"
            Write-Host "Downloading google-java-format $version from its official release."
            Invoke-WebRequest -Uri $uri -OutFile $downloadPath -UseBasicParsing
            if ((Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash -ne $expectedHash) {
                throw 'The downloaded formatter does not match its pinned SHA-256 checksum.'
            }
            Move-Item -LiteralPath $downloadPath -Destination $formatterPath
        }
        finally {
            if (Test-Path -LiteralPath $downloadPath) {
                Remove-Item -LiteralPath $downloadPath -Force
            }
        }
    }
    if ((Get-FileHash -LiteralPath $formatterPath -Algorithm SHA256).Hash -ne $expectedHash) {
        throw 'The cached formatter does not match its pinned SHA-256 checksum.'
    }

    $sources = Get-ProjectJavaSources -ProjectRoot $projectRoot -Directories @('src', 'tests', 'tools', 'android/app/src')
    $arguments = @('-jar', $formatterPath, '--aosp', '--skip-reflowing-long-strings')
    if ($Check) {
        $arguments += @('--dry-run', '--set-exit-if-changed')
    }
    else {
        $arguments += '--replace'
    }
    Invoke-ProjectCommand -Command 'java' -Arguments ($arguments + $sources) -Description 'Java formatting'
    Write-Host $(if ($Check) { 'Java formatting is consistent.' } else { 'Java formatting updated.' })
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
