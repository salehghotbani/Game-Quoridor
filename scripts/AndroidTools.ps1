function Get-AndroidBuildTools {
    param([string]$SdkPath)

    if ($SdkPath -and -not (Test-Path -LiteralPath (Join-Path $SdkPath 'platforms\android-35\android.jar') -PathType Leaf)) {
        throw 'The selected -SdkPath does not contain Android SDK platform35.'
    }
    $candidates = @($SdkPath, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $sdk = $null
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'platforms\android-35\android.jar') -PathType Leaf)) {
            $sdk = [System.IO.Path]::GetFullPath($candidate)
            break
        }
    }
    if (-not $sdk) { throw 'Android SDK platform35 was not found. Set ANDROID_HOME or pass -SdkPath.' }
    Assert-ProjectTools -Names @('java', 'javac', 'keytool')
    $suffix = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '.exe' } else { '' }
    $buildTools = Join-Path $sdk 'build-tools\35.0.0'
    $required = @(('aapt2' + $suffix), ('zipalign' + $suffix), 'lib\apksigner.jar')
    foreach ($name in $required) {
        if (-not (Test-Path -LiteralPath (Join-Path $buildTools $name) -PathType Leaf)) {
            throw "Android SDK Build Tools35.0.0 is incomplete: $name"
        }
    }
    return [pscustomobject]@{
        Sdk = $sdk
        Aapt2 = Join-Path $buildTools ('aapt2' + $suffix)
        Zipalign = Join-Path $buildTools ('zipalign' + $suffix)
        Apksigner = Join-Path $buildTools 'lib\apksigner.jar'
        Adb = Join-Path $sdk ('platform-tools\adb' + $suffix)
        Emulator = Join-Path $sdk ('emulator\emulator' + $suffix)
    }
}

function Get-AndroidGradleCommand {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    $suffix = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '.bat' } else { '' }
    $candidates = @()
    if ($env:GRADLE_HOME) { $candidates += $env:GRADLE_HOME }
    if ($env:USERPROFILE) {
        $distributions = Join-Path $env:USERPROFILE '.gradle\wrapper\dists'
        if (Test-Path -LiteralPath $distributions -PathType Container) {
            foreach ($distribution in Get-ChildItem -LiteralPath $distributions -Directory -Filter 'gradle-8.14.3-*') {
                foreach ($cache in Get-ChildItem -LiteralPath $distribution.FullName -Directory) {
                    $candidates += Join-Path $cache.FullName 'gradle-8.14.3'
                }
            }
        }
    }
    foreach ($candidate in $candidates) {
        $launcher = Join-Path $candidate ('bin\gradle' + $suffix)
        if ((Test-Path -LiteralPath $launcher -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $candidate 'lib\gradle-launcher-8.14.3.jar') -PathType Leaf)) {
            return $launcher
        }
    }
    return Join-Path $ProjectRoot ('android\gradlew' + $suffix)
}

function Invoke-AndroidGradle {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)]$Tools,
        [Parameter(Mandatory = $true)][string[]]$Tasks,
        [switch]$Offline
    )

    $cache = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'android\gradle-home'
    $projectCache = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'android\project-cache'
    $savedSdk = $env:ANDROID_HOME
    $savedGradle = $env:GRADLE_USER_HOME
    try {
        $env:ANDROID_HOME = $Tools.Sdk
        $env:GRADLE_USER_HOME = $cache
        $arguments = @('--no-daemon', '--console=plain', '--project-dir', (Join-Path $ProjectRoot 'android'), '--project-cache-dir', $projectCache,
            "-Pandroid.aapt2FromMavenOverride=$($Tools.Aapt2)") + $Tasks
        $verifiedInit = Get-AndroidVerifiedDependencyInit -ProjectRoot $ProjectRoot
        if ($verifiedInit) { $arguments += @('--init-script', $verifiedInit) }
        if ($Offline) { $arguments += '--offline' }
        Invoke-ProjectCommand -Command (Get-AndroidGradleCommand -ProjectRoot $ProjectRoot) -Arguments $arguments -Description 'Android Gradle build'
    }
    finally {
        $env:ANDROID_HOME = $savedSdk
        $env:GRADLE_USER_HOME = $savedGradle
    }
}

function Get-AndroidVerifiedDependencyInit {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    $repository = Join-Path $ProjectRoot 'out\android\verified-dependencies\repository'
    if (-not (Test-Path -LiteralPath $repository -PathType Container)) { return $null }
    Assert-AndroidGeneratedDirectory -ProjectRoot $ProjectRoot -Path $repository | Out-Null
    # This optional local fallback uses the same pinned artifacts as Google's Maven repository.
    # Normal builds still use Google's repository and Maven Central from settings.gradle.
    $expected = @{
        'desugar_jdk_libs_nio/2.1.5/desugar_jdk_libs_nio-2.1.5.jar' = 'd8044befae095781b9a80bf1faa92edc30382d75d437476784c1bf991598a976'
        'desugar_jdk_libs_nio/2.1.5/desugar_jdk_libs_nio-2.1.5.pom' = '1ba5db067a494d09f5c4a94f4cb4d58fc6fd33e0a55417e0e46d3b288328f3bc'
        'desugar_jdk_libs_configuration_nio/2.1.5/desugar_jdk_libs_configuration_nio-2.1.5.jar' = '7db51661cd07d1fd5cf12769a75ba201910624bb3801c09363dd3cb28e31c51b'
        'desugar_jdk_libs_configuration_nio/2.1.5/desugar_jdk_libs_configuration_nio-2.1.5.pom' = '6e5e9ec6db8093d26c469c18b9231b033e670333a770852debda90c222fa4835'
    }
    foreach ($relative in $expected.Keys) {
        $file = Join-Path $repository ('com/android/tools/' + $relative)
        if (-not (Test-Path -LiteralPath $file -PathType Leaf) -or (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected[$relative]) {
            throw "Verified local Android dependency is missing or changed: $relative"
        }
    }
    $uri = ([System.Uri]::new($repository + [System.IO.Path]::DirectorySeparatorChar)).AbsoluteUri.Replace("'", "\'")
    $content = @"
settingsEvaluated { settings ->
    settings.dependencyResolutionManagement.repositories {
        exclusiveContent {
            forRepository { maven { name = 'QuoridorVerifiedLocalDesugar'; url = uri('$uri') } }
            filter {
                includeModule('com.android.tools', 'desugar_jdk_libs_nio')
                includeModule('com.android.tools', 'desugar_jdk_libs_configuration_nio')
            }
        }
        // Optional cached publisher POMs let an offline lint invocation reuse
        // the installed AGP lint artifacts without changing committed repositories.
        maven { name = 'QuoridorCachedPublisherMetadata'; url = uri('$uri') }
    }
}
"@
    $init = Join-Path $ProjectRoot 'out\android\verified-dependencies\verified-repository.init.gradle'
    [System.IO.File]::WriteAllText($init, $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host 'Using SHA256-verified local desugar dependencies (provenance in out/android/verified-dependencies).'
    return $init
}

function Get-AndroidSigningMaterial {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [switch]$Initialize
    )

    $directory = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'android-signing'
    $keystore = Join-Path $directory 'quoridor-local-release.p12'
    $passwordFile = Join-Path $directory 'store-password.txt'
    foreach ($path in @($keystore, $passwordFile)) {
        if ((Test-Path -LiteralPath $path) -and
            ((Get-Item -LiteralPath $path -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
            throw "Refusing linked signing material: $path"
        }
    }
    $hasKey = Test-Path -LiteralPath $keystore -PathType Leaf
    $hasPassword = Test-Path -LiteralPath $passwordFile -PathType Leaf
    if ($hasKey -ne $hasPassword) { throw 'Signing material is incomplete. Restore both original files; existing material will not be overwritten.' }
    if (-not $hasKey) {
        if (-not $Initialize) {
            throw 'No local release key exists. Use -InitializeSigning once, or -Unsigned. Keep the signing directory private and backed up for updates.'
        }
        if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
            $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
            $access = [System.Security.AccessControl.DirectorySecurity]::new()
            $access.SetOwner($identity)
            $access.SetAccessRuleProtection($true, $false)
            foreach ($principal in @($identity, [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'), [System.Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))) {
                $rule = [System.Security.AccessControl.FileSystemAccessRule]::new($principal, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
                $access.AddAccessRule($rule)
            }
            Set-Acl -LiteralPath $directory -AclObject $access
        }
        $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        $bytes = New-Object byte[] 32
        try { $random.GetBytes($bytes) } finally { $random.Dispose() }
        [System.IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($bytes), [System.Text.UTF8Encoding]::new($false))
        try {
            $arguments = @('-genkeypair', '-keystore', $keystore, '-storetype', 'PKCS12', '-alias', 'quoridor-local',
                '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000', '-dname', 'CN=Quoridor Local Distribution',
                '-storepass:file', $passwordFile, '-keypass:file', $passwordFile, '-noprompt')
            Invoke-ProjectCommand -Command 'keytool' -Arguments $arguments -Description 'Local Android signing key creation'
        }
        catch {
            if (Test-Path -LiteralPath $keystore -PathType Leaf) { Remove-Item -LiteralPath $keystore -Force }
            Remove-Item -LiteralPath $passwordFile -Force
            throw
        }
        Write-Host 'Created a private local distribution key under out/android-signing. Back up this directory securely; it is never included in an APK or release.'
    }
    return [pscustomobject]@{ Keystore = $keystore; PasswordFile = $passwordFile; Alias = 'quoridor-local' }
}

function Publish-AndroidApk {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)][string]$Version,
        [Parameter(Mandatory = $true)][string]$Apk,
        [Parameter(Mandatory = $true)][string]$Name
    )

    if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$' -or [System.IO.Path]::GetFileName($Name) -ne $Name) {
        throw 'Invalid Android release version or filename.'
    }
    $stagePath = Join-Path $ProjectRoot "out\android\release-stage\v$Version"
    Assert-AndroidGeneratedDirectory -ProjectRoot $ProjectRoot -Path $stagePath | Out-Null
    $stage = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory "android\release-stage\v$Version" -Clean
    Copy-Item -LiteralPath $Apk -Destination (Join-Path $stage $Name)
    $hash = (Get-FileHash -LiteralPath (Join-Path $stage $Name) -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText((Join-Path $stage 'SHA256SUMS.txt'), "$hash  $Name`n", [System.Text.UTF8Encoding]::new($false))
    $parent = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'android\releases'
    $release = Assert-AndroidGeneratedDirectory -ProjectRoot $ProjectRoot -Path (Join-Path $parent "v$Version")
    $backup = Assert-AndroidGeneratedDirectory -ProjectRoot $ProjectRoot -Path (Join-Path $parent ('.v' + $Version + '-previous-' + [guid]::NewGuid().ToString('N')))
    $hasPrevious = Test-Path -LiteralPath $release -PathType Container
    if ($hasPrevious) {
        [System.IO.Directory]::Move($release, $backup)
    }
    try { [System.IO.Directory]::Move($stage, $release) }
    catch {
        if ($hasPrevious) { [System.IO.Directory]::Move($backup, $release) }
        throw
    }
    if ($hasPrevious) {
        try {
            $checkedBackup = Assert-AndroidGeneratedDirectory -ProjectRoot $ProjectRoot -Path $backup
            Remove-Item -LiteralPath $checkedBackup -Recurse -Force
        }
        catch { Write-Warning "APK published; previous copy retained at $backup. $_" }
    }
    Write-Host "Android release assets: $release"
    return Join-Path $release $Name
}

function Assert-AndroidGeneratedDirectory {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot, [Parameter(Mandatory = $true)][string]$Path)

    $root = [System.IO.Path]::GetFullPath((Join-Path $ProjectRoot 'out'))
    $target = [System.IO.Path]::GetFullPath($Path)
    if (-not $target.StartsWith($root + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Android output must remain below this project's out directory: $target"
    }
    $current = $target
    while ($current.Length -ge $root.Length) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if (-not $item.PSIsContainer -or ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
                throw "Refusing linked or non-directory Android output: $current"
            }
        }
        if ($current -eq $root) { break }
        $current = Split-Path -Parent $current
    }
    if (Test-Path -LiteralPath $target -PathType Container) {
        $pending = [System.Collections.Generic.Stack[string]]::new()
        $pending.Push($target)
        while ($pending.Count -gt 0) {
            foreach ($item in Get-ChildItem -LiteralPath $pending.Pop() -Force) {
                if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) { throw "Refusing linked Android output contents: $($item.FullName)" }
                if ($item.PSIsContainer) { $pending.Push($item.FullName) }
            }
        }
    }
    return $target
}
