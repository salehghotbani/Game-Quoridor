[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    foreach ($command in @('javac', 'jar')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "Missing $command. Install JDK 17 or newer and add its bin directory to PATH."
        }
    }

    $projectPath = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $outPath = Join-Path $projectPath 'out'
    $classesPath = [System.IO.Path]::GetFullPath((Join-Path $outPath 'classes'))
    $expectedPath = [System.IO.Path]::GetFullPath((Join-Path $projectPath 'out\classes'))
    if ($classesPath -ne $expectedPath -or -not $classesPath.StartsWith($projectPath + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean a directory outside this project's out\classes directory."
    }
    foreach ($directory in @($outPath, $classesPath)) {
        if ((Test-Path -LiteralPath $directory) -and ((Get-Item -LiteralPath $directory -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
            throw "Refusing to use a linked output directory: $directory"
        }
    }
    if (Test-Path -LiteralPath $classesPath) {
        Remove-Item -LiteralPath $classesPath -Recurse -Force
    }
    New-Item -ItemType Directory -Path $classesPath -Force | Out-Null

    $sourcePaths = @(Get-ChildItem -LiteralPath (Join-Path $projectPath 'src') -Filter '*.java' -File -Recurse | Sort-Object FullName | ForEach-Object { $_.FullName })
    if ($sourcePaths.Count -eq 0) {
        throw 'No Java source files were found in src.'
    }
    & javac --release 17 -encoding UTF-8 -Xlint:all -d $classesPath @sourcePaths
    if ($LASTEXITCODE -ne 0) {
        throw "Java compilation failed (exit code $LASTEXITCODE)."
    }

    foreach ($resource in @('Files\Images\com.nozhaco.quoridor_512x512.png')) {
        $sourcePath = Join-Path (Join-Path $projectPath 'src') $resource
        if (Test-Path -LiteralPath $sourcePath -PathType Leaf) {
            $destinationPath = Join-Path $classesPath $resource
            New-Item -ItemType Directory -Path (Split-Path -Parent $destinationPath) -Force | Out-Null
            Copy-Item -LiteralPath $sourcePath -Destination $destinationPath
        }
    }

    $noticesPath = Join-Path $classesPath 'META-INF'
    New-Item -ItemType Directory -Path $noticesPath -Force | Out-Null
    foreach ($notice in @('LICENSE', 'AUTHORS.md')) {
        $noticeSource = Join-Path $projectPath $notice
        if (-not (Test-Path -LiteralPath $noticeSource -PathType Leaf)) {
            throw "Missing distribution notice: $notice"
        }
        Copy-Item -LiteralPath $noticeSource -Destination (Join-Path $noticesPath $notice)
    }

    $jarPath = Join-Path $outPath 'Quoridor.jar'
    & jar --create --file $jarPath --main-class Main -C $classesPath .
    if ($LASTEXITCODE -ne 0) {
        throw "JAR packaging failed (exit code $LASTEXITCODE)."
    }
    Write-Host "Built $jarPath"
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
