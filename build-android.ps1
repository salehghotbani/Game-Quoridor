[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')][string]$Configuration = 'Release',
    [switch]$InitializeSigning,
    [switch]$Unsigned,
    [switch]$Offline,
    [string]$SdkPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')
. (Join-Path $PSScriptRoot 'scripts\AndroidTools.ps1')

try {
    if ($Unsigned -and $Configuration -ne 'Release') { throw '-Unsigned applies only to Release builds.' }
    $projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $version = Get-ProjectVersion -ProjectRoot $projectRoot
    $tools = Get-AndroidBuildTools -SdkPath $SdkPath
    $signing = $null
    if ($Configuration -eq 'Release' -and -not $Unsigned) {
        $signing = Get-AndroidSigningMaterial -ProjectRoot $projectRoot -Initialize:$InitializeSigning
    }
    elseif ($InitializeSigning) { throw '-InitializeSigning requires a signed Release build.' }
    Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory 'android-signing' | Out-Null
    Invoke-AndroidGradle -ProjectRoot $projectRoot -Tools $tools -Tasks @(':app:assemble' + $Configuration) -Offline:$Offline
    $variant = $Configuration.ToLowerInvariant()
    $apkDirectory = Join-Path $projectRoot "out\android\gradle\app\outputs\apk\$variant"
    $apk = Join-Path $apkDirectory $(if ($Configuration -eq 'Release') { 'app-release-unsigned.apk' } else { 'app-debug.apk' })
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "Gradle did not create the expected APK: $apk" }
    if ($signing) {
        $work = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory "android\signing-work\v$version" -Clean
        $aligned = Join-Path $work 'aligned.apk'
        $signed = Join-Path $work 'signed.apk'
        Invoke-ProjectCommand -Command $tools.Zipalign -Arguments @('-p', '-f', '4', $apk, $aligned) -Description 'Android APK alignment'
        $arguments = @('-jar', $tools.Apksigner, 'sign', '--ks', $signing.Keystore, '--ks-key-alias', $signing.Alias,
            '--ks-pass', ('file:' + $signing.PasswordFile), '--key-pass', ('file:' + $signing.PasswordFile), '--out', $signed, $aligned)
        Invoke-ProjectCommand -Command 'java' -Arguments $arguments -Description 'Android APK signing'
        $apk = $signed
        $name = "Quoridor-$version-android.apk"
    }
    elseif ($Configuration -eq 'Debug') { $name = "Quoridor-$version-android-debug.apk" }
    else { $name = "Quoridor-$version-android-unsigned.apk" }
    if (-not $Unsigned) {
        Invoke-ProjectCommand -Command 'java' -Arguments @('-jar', $tools.Apksigner, 'verify', '--verbose', $apk) -Description 'APK signature verification'
    }
    $published = Publish-AndroidApk -ProjectRoot $projectRoot -Version $version -Apk $apk -Name $name
    Write-Host "Created $published"
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
