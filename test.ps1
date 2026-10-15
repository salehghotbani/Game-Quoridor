[CmdletBinding()]
param(
    [switch]$Headless
)

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

    $projectPath = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $classesPath = Join-Path $projectPath 'out\classes'
    $testClassesPath = [System.IO.Path]::GetFullPath((Join-Path $projectPath 'out\test-classes'))
    $expectedPath = [System.IO.Path]::GetFullPath((Join-Path $projectPath 'out\test-classes'))
    if ($testClassesPath -ne $expectedPath -or -not $testClassesPath.StartsWith($projectPath + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean a directory outside this project's out\test-classes directory."
    }
    if ((Test-Path -LiteralPath $testClassesPath) -and ((Get-Item -LiteralPath $testClassesPath -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
        throw "Refusing to use a linked output directory: $testClassesPath"
    }
    if (Test-Path -LiteralPath $testClassesPath) {
        Remove-Item -LiteralPath $testClassesPath -Recurse -Force
    }
    New-Item -ItemType Directory -Path $testClassesPath -Force | Out-Null

    $testPaths = @(Get-ChildItem -LiteralPath (Join-Path $projectPath 'tests') -Filter '*.java' -File -Recurse | Sort-Object FullName | ForEach-Object { $_.FullName })
    if ($testPaths.Count -eq 0) {
        throw 'No Java test files were found in tests.'
    }
    & javac --release 17 -encoding UTF-8 -Xlint:all -cp $classesPath -d $testClassesPath @testPaths
    if ($LASTEXITCODE -ne 0) {
        throw "Test compilation failed (exit code $LASTEXITCODE)."
    }

    $classpath = $classesPath + [System.IO.Path]::PathSeparator + $testClassesPath
    foreach ($testClass in @('GameStateTest', 'BotPlayerTest', 'SaveStoreTest', 'I18nTest')) {
        & java '-Djava.awt.headless=true' "-Dquoridor.test.project=$projectPath" -ea -cp $classpath $testClass
        if ($LASTEXITCODE -ne 0) {
            throw "$testClass failed (exit code $LASTEXITCODE)."
        }
    }
    if ($Headless) {
        Write-Host 'Graphical interface tests skipped because -Headless was specified.'
    }
    else {
        foreach ($testClass in @('BoardSmokeTest', 'LanguageUiTest', 'AppearanceUiTest', 'SaveDialogTest')) {
            & java '-Djava.awt.headless=false' "-Dquoridor.test.output=$(Join-Path $projectPath 'out')" "-Dquoridor.test.project=$projectPath" -ea -cp $classpath $testClass
            if ($LASTEXITCODE -ne 0) {
                throw "$testClass failed (exit code $LASTEXITCODE)."
            }
        }
    }
    Write-Host 'All requested tests passed.'
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
