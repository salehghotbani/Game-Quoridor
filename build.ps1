[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')

try {
    Assert-ProjectTools -Names @('javac', 'jar')
    $projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $version = Get-ProjectVersion -ProjectRoot $projectRoot
    $classesPath = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory 'classes' -Clean
    $sources = Get-ProjectJavaSources -ProjectRoot $projectRoot -Directories @('src')
    Invoke-ProjectCompilation -Sources $sources -Destination $classesPath

    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $classesPath 'app-version.properties'), "version=$version`n", $encoding)
    $metadataPath = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory 'build-metadata' -Clean
    $manifestPath = Join-Path $metadataPath 'MANIFEST.MF'
    $manifest = "Implementation-Title: Quoridor`nImplementation-Version: $version`n"
    [System.IO.File]::WriteAllText($manifestPath, $manifest, $encoding)

    $icon = 'Files\Images\com.nozhaco.quoridor_512x512.png'
    $iconSource = Join-Path (Join-Path $projectRoot 'src') $icon
    if (Test-Path -LiteralPath $iconSource -PathType Leaf) {
        $iconDestination = Join-Path $classesPath $icon
        New-Item -ItemType Directory -Path (Split-Path -Parent $iconDestination) -Force | Out-Null
        Copy-Item -LiteralPath $iconSource -Destination $iconDestination
    }

    Copy-Item -LiteralPath (Join-Path $projectRoot 'src/Files/fonts') -Destination (Join-Path $classesPath 'Files/fonts') -Recurse

    $noticesPath = Join-Path $classesPath 'META-INF'
    New-Item -ItemType Directory -Path $noticesPath -Force | Out-Null
    foreach ($notice in @('LICENSE', 'AUTHORS.md')) {
        $source = Join-Path $projectRoot $notice
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Missing distribution notice: $notice"
        }
        Copy-Item -LiteralPath $source -Destination (Join-Path $noticesPath $notice)
    }

    $jarPath = Join-Path (Join-Path $projectRoot 'out') 'Quoridor.jar'
    $arguments = @('--create', '--file', $jarPath, '--main-class', 'Main', '--manifest', $manifestPath, '-C', $classesPath, '.')
    Invoke-ProjectCommand -Command 'jar' -Arguments $arguments -Description 'JAR packaging'
    Write-Host "Built Quoridor $version at $jarPath"
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
