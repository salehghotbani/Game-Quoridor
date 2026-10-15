[CmdletBinding()]
param(
    [switch]$UnitOnly,
    [switch]$SkipInstall,
    [string]$BrowserExecutablePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts/ProjectTools.ps1')
$webServer = $null
$savedTestServer = $env:QUORIDOR_TEST_SERVER
$savedBrowser = $env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH

function ConvertTo-WebArguments {
    param([string[]]$Arguments)
    return (@(foreach ($argument in $Arguments) {
        '"' + ([regex]::Replace(([regex]::Replace($argument, '(\\*)"', '$1$1\"')), '(\\+)$', '$1$1')) + '"'
    }) -join ' ')
}

function Start-WebTestServer {
    param([string]$ProjectRoot)
    $work = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'web/interop-server'
    $classes = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'web/server-classes' -Clean
    $sources = @(Get-ProjectJavaSources -ProjectRoot $ProjectRoot -Directories @('src')) + @(
        (Join-Path $ProjectRoot 'tools/WebInteropServer.java'),
        (Join-Path $ProjectRoot 'tests/OnlineCorsTest.java')
    )
    Invoke-ProjectCompilation -Sources $sources -Destination $classes
    Invoke-ProjectCommand -Command 'java' -Arguments @('-Djava.awt.headless=true', '-cp', $classes, 'Online.OnlineCorsTest') -Description 'Java CORS regressions' | Out-Host
    $endpointFile = Join-Path $work 'endpoint.txt'
    if (Test-Path -LiteralPath $endpointFile -PathType Leaf) { Remove-Item -LiteralPath $endpointFile -Force }
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Command java -CommandType Application | Select-Object -First 1).Source
    $start.Arguments = ConvertTo-WebArguments @('-cp', $classes, 'WebInteropServer', $endpointFile, 'http://127.0.0.1:4173', 'http://localhost:4173')
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { $process.Dispose(); throw 'The owned web interoperability server did not start.' }
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        while (-not (Test-Path -LiteralPath $endpointFile -PathType Leaf)) {
            if ($process.HasExited -or [DateTime]::UtcNow -ge $deadline) { throw 'The owned test server did not publish its endpoint.' }
            Start-Sleep -Milliseconds 100
        }
        $endpoint = [Uri][IO.File]::ReadAllText($endpointFile)
        if ($endpoint.Scheme -ne 'http' -or $endpoint.Host -ne '127.0.0.1' -or $endpoint.Port -lt 1) { throw 'Unexpected owned server endpoint.' }
        return [pscustomobject]@{ Process=$process; Stdout=$stdout; Stderr=$stderr; Work=$work; Url=$endpoint.AbsoluteUri.TrimEnd('/') }
    }
    catch {
        if (-not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
        $process.Dispose()
        throw
    }
}

function Stop-WebTestServer {
    param($Server)
    try {
        if (-not $Server.Process.HasExited) {
            $Server.Process.StandardInput.WriteLine('stop')
            $Server.Process.StandardInput.Close()
            if (-not $Server.Process.WaitForExit(5000)) { $Server.Process.Kill(); $Server.Process.WaitForExit() }
        }
        [IO.File]::WriteAllText((Join-Path $Server.Work 'stdout.log'), $Server.Stdout.GetAwaiter().GetResult())
        [IO.File]::WriteAllText((Join-Path $Server.Work 'stderr.log'), $Server.Stderr.GetAwaiter().GetResult())
    }
    finally { $Server.Process.Dispose() }
}

try {
    $projectRoot = [IO.Path]::GetFullPath($PSScriptRoot)
    $webDirectory = Join-Path $projectRoot 'web'
    $node = (Get-Command node -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    Invoke-ProjectCommand -Command $node -Arguments @((Join-Path $webDirectory 'scripts/build.mjs')) -Description 'Web production build'
    if (-not $UnitOnly) {
        if (-not $SkipInstall) {
            $npmCli = Join-Path (Split-Path -Parent $node) 'node_modules/npm/bin/npm-cli.js'
            if (-not (Test-Path -LiteralPath $npmCli -PathType Leaf)) { throw 'npm was not found beside Node. Install dependencies with npm ci in web, then use -SkipInstall.' }
            Push-Location $webDirectory
            try {
                Invoke-ProjectCommand -Command $node -Arguments @($npmCli, 'ci', '--ignore-scripts', '--no-audit', '--no-fund', '--cache', (Join-Path $projectRoot 'out/web/npm-cache')) -Description 'Pinned browser test dependency installation'
            }
            finally { Pop-Location }
        }
        $playwrightCli = Join-Path $webDirectory 'node_modules/@playwright/test/cli.js'
        if (-not (Test-Path -LiteralPath $playwrightCli -PathType Leaf)) { throw 'Missing Playwright test dependency. Run npm ci inside web.' }
        if ($BrowserExecutablePath) {
            if (-not (Test-Path -LiteralPath $BrowserExecutablePath -PathType Leaf)) { throw 'The selected browser executable does not exist.' }
            $env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH = [IO.Path]::GetFullPath($BrowserExecutablePath)
        }
        elseif (-not $env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH -and [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
            $browserCandidates = @(
                (Join-Path ${env:ProgramFiles} 'Google/Chrome/Application/chrome.exe'),
                (Join-Path ${env:ProgramFiles(x86)} 'Microsoft/Edge/Application/msedge.exe')
            )
            foreach ($candidate in $browserCandidates) {
                if (Test-Path -LiteralPath $candidate -PathType Leaf) { $env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH = $candidate; break }
            }
        }
        $webServer = Start-WebTestServer -ProjectRoot $projectRoot
        $env:QUORIDOR_TEST_SERVER = $webServer.Url
    }
    else { Remove-Item Env:QUORIDOR_TEST_SERVER -ErrorAction SilentlyContinue }
    $unitTests = @(Get-ChildItem -LiteralPath (Join-Path $webDirectory 'tests') -Filter '*.test.js' -File | Sort-Object Name | Select-Object -ExpandProperty FullName)
    if ($unitTests.Count -eq 0) { throw 'No web unit tests were found.' }
    Invoke-ProjectCommand -Command $node -Arguments (@('--test') + $unitTests) -Description 'Web unit and build tests'
    if (-not $UnitOnly) {
        Push-Location $webDirectory
        try { Invoke-ProjectCommand -Command $node -Arguments @($playwrightCli, 'test', '--config', (Join-Path $webDirectory 'playwright.config.mjs')) -Description 'Real browser game tests' }
        finally { Pop-Location }
    }
    Write-Host $(if ($UnitOnly) { 'Web unit/build tests passed; browser and server interoperability skipped by -UnitOnly.' } else { 'Web unit, Java CORS, interoperability and real browser tests passed.' })
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
finally {
    if ($webServer) { Stop-WebTestServer -Server $webServer }
    $env:QUORIDOR_TEST_SERVER = $savedTestServer
    $env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH = $savedBrowser
}
