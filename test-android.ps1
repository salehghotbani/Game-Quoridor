[CmdletBinding()]
param(
    [switch]$UnitOnly,
    [switch]$SkipBuild,
    [switch]$KeepEmulator,
    [switch]$ReuseOwnedEmulator,
    [switch]$Offline,
    [string]$SdkPath,
    [int]$AdbPort = 5038,
    [int]$EmulatorPort = 5580,
    [ValidateSet(21, 23, 35)][int]$ApiLevel = 35,
    [string]$SystemImageDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')
. (Join-Path $PSScriptRoot 'scripts\AndroidTools.ps1')
. (Join-Path $PSScriptRoot 'scripts\AndroidEmulator.ps1')
$session = $null
$server = $null

try {
    if ($UnitOnly -and $SkipBuild) { throw '-UnitOnly cannot be combined with -SkipBuild because no tests would run.' }
    $projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $tools = Get-AndroidBuildTools -SdkPath $SdkPath
    if (-not $SkipBuild) {
        $tasks = @(':app:testDebugUnitTest', ':app:lintDebug')
        if (-not $UnitOnly) { $tasks += @(':app:assembleDebug', ':app:assembleDebugAndroidTest') }
        Invoke-AndroidGradle -ProjectRoot $projectRoot -Tools $tools -Tasks $tasks -Offline:$Offline
    }
    if ($UnitOnly) {
        Write-Host 'Android host unit tests and lint passed; device tests skipped by -UnitOnly.'
        exit 0
    }
    $mainApk = Join-Path $projectRoot 'out\android\gradle\app\outputs\apk\debug\app-debug.apk'
    $testApk = Join-Path $projectRoot 'out\android\gradle\app\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    foreach ($apk in @($mainApk, $testApk)) {
        if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "Missing Android test APK: $apk" }
    }
    if ($ReuseOwnedEmulator) { $session = Get-OwnedAndroidEmulator -ProjectRoot $projectRoot -Tools $tools -ApiLevel $ApiLevel }
    else { $session = Start-OwnedAndroidEmulator -ProjectRoot $projectRoot -Tools $tools -AdbPort $AdbPort -EmulatorPort $EmulatorPort -ApiLevel $ApiLevel -SystemImageDirectory $SystemImageDirectory }
    Invoke-OwnedAndroidAdb -Session $session -Arguments @('install','-r','-t',$mainApk) | Out-Host
    Invoke-OwnedAndroidAdb -Session $session -Arguments @('install','-r','-t',$testApk) | Out-Host
    Invoke-OwnedAndroidAdb -Session $session -Arguments @('logcat','-c') | Out-Null
    $server = Start-AndroidInteropServer -ProjectRoot $projectRoot
    $result = (Invoke-OwnedAndroidAdb -Session $session -Arguments @('shell','am','instrument','-w','-r','-e','server',$server.Url,'com.quoridor.mobile.test/android.test.InstrumentationTestRunner')) -join "`n"
    [System.IO.File]::WriteAllText((Join-Path $session.Work 'instrumentation.log'), $result, [System.Text.UTF8Encoding]::new($false))
    Write-Host $result
    Save-OwnedAndroidCaptures -Session $session -ProjectRoot $projectRoot
    if ($result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' -or $result -notmatch 'OK \([1-9][0-9]* tests?\)') {
        throw 'Android instrumentation tests did not report a successful nonempty test run.'
    }
    Invoke-OwnedAndroidAdb -Session $session -Arguments @('shell','am','start','-W','-n','com.quoridor.mobile/.MainActivity') | Out-Host
    Save-OwnedAndroidScreenshot -Session $session -Path (Join-Path $session.Work 'android-menu.png')
    if ($SkipBuild) { Write-Host 'Android device tests passed; build/unit/lint were skipped by -SkipBuild.' }
    else { Write-Host 'Android host/device tests and lint passed.' }
    if ($KeepEmulator) { Write-Host "Owned emulator remains available; session details: $($session.Work)\session.json" }
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
finally {
    if ($server) { Stop-AndroidInteropServer -Server $server }
    if ($session -and -not $KeepEmulator) { Stop-OwnedAndroidEmulator -Session $session }
}
