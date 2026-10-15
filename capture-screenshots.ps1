[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'Missing java. Install JDK 17 or newer and add its bin directory to PATH.'
    }
    & (Join-Path $PSScriptRoot 'build.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $projectPath = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $outPath = Join-Path $projectPath 'out'
    $captureClassesPath = [System.IO.Path]::GetFullPath((Join-Path $outPath 'screenshot-tools'))
    if (-not $captureClassesPath.StartsWith($projectPath + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Screenshot classes must remain inside this project.'
    }
    foreach ($directory in @($outPath, $captureClassesPath)) {
        if ((Test-Path -LiteralPath $directory) -and ((Get-Item -LiteralPath $directory -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
            throw "Refusing to use a linked output directory: $directory"
        }
    }
    New-Item -ItemType Directory -Path $captureClassesPath -Force | Out-Null
    $classesPath = Join-Path $outPath 'classes'
    $sourcePath = Join-Path $projectPath 'tools\CaptureScreenshots.java'
    & javac --release 17 -encoding UTF-8 -Xlint:all -cp $classesPath -d $captureClassesPath $sourcePath
    if ($LASTEXITCODE -ne 0) { throw "Screenshot utility compilation failed (exit code $LASTEXITCODE)." }

    $classpath = $classesPath + [System.IO.Path]::PathSeparator + $captureClassesPath
    & java '-Djava.awt.headless=false' -cp $classpath CaptureScreenshots (Join-Path $projectPath 'docs\screenshots')
    if ($LASTEXITCODE -ne 0) { throw "Screenshot capture failed (exit code $LASTEXITCODE)." }
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
