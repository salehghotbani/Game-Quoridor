[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')

try {
    Assert-ProjectTools -Names @('javac', 'jar')
    $projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $classesPath = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory 'classes' -Clean
    $sources = Get-ProjectJavaSources -ProjectRoot $projectRoot -Directories @('src')
    Invoke-ProjectCompilation -Sources $sources -Destination $classesPath

    $icon = 'Files\Images\com.nozhaco.quoridor_512x512.png'
    $iconSource = Join-Path (Join-Path $projectRoot 'src') $icon
    if (Test-Path -LiteralPath $iconSource -PathType Leaf) {
        $iconDestination = Join-Path $classesPath $icon
        New-Item -ItemType Directory -Path (Split-Path -Parent $iconDestination) -Force | Out-Null
        Copy-Item -LiteralPath $iconSource -Destination $iconDestination
    }

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
    $arguments = @('--create', '--file', $jarPath, '--main-class', 'Main', '-C', $classesPath, '.')
    Invoke-ProjectCommand -Command 'jar' -Arguments $arguments -Description 'JAR packaging'
    Write-Host "Built $jarPath"
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
