[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')

try {
    Assert-ProjectTools -Names @('javac', 'jar')
    $projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $version = Get-ProjectVersion -ProjectRoot $projectRoot
    $classes = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory 'server-classes' -Clean
    $sources = Get-ProjectJavaSources -ProjectRoot $projectRoot -Directories @('src')
    Invoke-ProjectCompilation -Sources $sources -Destination $classes
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $classes 'app-version.properties'), "version=$version`n", $encoding)
    $notices = Join-Path $classes 'META-INF'
    New-Item -ItemType Directory -Path $notices -Force | Out-Null
    foreach ($name in @('LICENSE', 'AUTHORS.md')) {
        Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination (Join-Path $notices $name)
    }
    $metadata = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory 'server-metadata' -Clean
    $manifest = Join-Path $metadata 'MANIFEST.MF'
    [System.IO.File]::WriteAllText($manifest, "Implementation-Title: Quoridor room server`nImplementation-Version: $version`n", $encoding)
    $destination = Join-Path $projectRoot 'out\Quoridor-server.jar'
    Invoke-ProjectCommand -Command 'jar' -Arguments @('--create', '--file', $destination, '--main-class', 'Online.ServerMain', '--manifest', $manifest, '-C', $classes, '.') -Description 'Server JAR packaging'
    Write-Host "Built Quoridor server $version at $destination"
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
