[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')

try {
    Assert-ProjectTools -Names @('java')
    $projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
    Invoke-ProjectBuild -ProjectRoot $projectRoot
    $captureClasses = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory 'screenshot-tools' -Clean
    $classesPath = Join-Path $projectRoot 'out\classes'
    $source = Join-Path $projectRoot 'tools\CaptureScreenshots.java'
    Invoke-ProjectCompilation -Sources @($source) -Destination $captureClasses -Classpath $classesPath

    $classpath = $classesPath + [System.IO.Path]::PathSeparator + $captureClasses
    $destination = Join-Path $projectRoot 'docs\screenshots'
    $arguments = @('-Djava.awt.headless=false', '-cp', $classpath, 'CaptureScreenshots', $destination)
    Invoke-ProjectCommand -Command 'java' -Arguments $arguments -Description 'Screenshot capture'
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
