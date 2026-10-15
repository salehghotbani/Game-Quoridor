[CmdletBinding()]
param([switch]$Headless)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')

try {
    Assert-ProjectTools -Names @('java')
    $projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
    Invoke-ProjectBuild -ProjectRoot $projectRoot
    $classesPath = Join-Path $projectRoot 'out\classes'
    $testClassesPath = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory 'test-classes' -Clean
    $testSources = Get-ProjectJavaSources -ProjectRoot $projectRoot -Directories @('tests')
    Invoke-ProjectCompilation -Sources $testSources -Destination $testClassesPath -Classpath $classesPath
    $classpath = $classesPath + [System.IO.Path]::PathSeparator + $testClassesPath
    $projectProperty = "-Dquoridor.test.project=$projectRoot"

    $headlessTests = @('GameStateTest', 'BotPlayerTest', 'SaveStoreTest', 'I18nTest')
    foreach ($testClass in $headlessTests) {
        $arguments = @('-Djava.awt.headless=true', $projectProperty, '-ea', '-cp', $classpath, $testClass)
        Invoke-ProjectCommand -Command 'java' -Arguments $arguments -Description $testClass
    }

    if ($Headless) {
        Write-Host 'Graphical interface tests skipped because -Headless was specified.'
    }
    else {
        $outputProperty = '-Dquoridor.test.output=' + (Join-Path $projectRoot 'out')
        $graphicalTests = @('BoardSmokeTest', 'LanguageUiTest', 'AppearanceUiTest', 'SaveDialogTest')
        foreach ($testClass in $graphicalTests) {
            $arguments = @('-Djava.awt.headless=false', $projectProperty, $outputProperty, '-ea', '-cp', $classpath, $testClass)
            Invoke-ProjectCommand -Command 'java' -Arguments $arguments -Description $testClass
        }
    }

    Write-Host 'All requested tests passed.'
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
