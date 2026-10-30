function ConvertTo-AndroidProcessArguments {
    param([string[]]$Arguments)

    return (@(foreach ($argument in $Arguments) {
        $quoted = [regex]::Replace($argument, '(\\*)"', '$1$1\"')
        '"' + [regex]::Replace($quoted, '(\\+)$', '$1$1') + '"'
    }) -join ' ')
}

function Invoke-OwnedAndroidAdb {
    param(
        [Parameter(Mandatory = $true)]$Session,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    # Windows PowerShell treats native stderr as ErrorRecords. During boot ADB
    # legitimately reports an offline device; let the explicit exit-code policy
    # below handle it instead of bypassing -AllowFailure via ErrorAction Stop.
    $savedErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Session.Adb -P $Session.AdbPort -s $Session.Serial @Arguments 2>&1
        $adbExitCode = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $savedErrorPreference }
    if ($adbExitCode -ne 0 -and -not $AllowFailure) {
        throw "Owned emulator ADB command failed: $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return $output
}

function Start-OwnedAndroidEmulator {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)]$Tools,
        [int]$AdbPort = 5038,
        [int]$EmulatorPort = 5580,
        [ValidateSet(21, 23, 35)][int]$ApiLevel = 35,
        [string]$SystemImageDirectory
    )

    if ($AdbPort -lt 1024 -or $AdbPort -gt 65535 -or $EmulatorPort -lt 5554 -or $EmulatorPort -gt 5682 -or $EmulatorPort % 2 -ne 0) {
        throw 'Use an unprivileged ADB port and an even emulator console port within5554..5682.'
    }
    $ports = @([System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | ForEach-Object Port)
    if ($AdbPort -in $ports -or $EmulatorPort -in $ports -or ($EmulatorPort + 1) -in $ports) {
        throw 'The selected test ports are already in use; existing servers/devices will not be reused or stopped.'
    }
    foreach ($tool in @($Tools.Adb, $Tools.Emulator)) {
        if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Missing Android emulator tool: $tool" }
    }
    $tag = if ($ApiLevel -eq 35) { 'google_apis_playstore' } else { 'default' }
    $systemImage = if ($SystemImageDirectory) { [IO.Path]::GetFullPath($SystemImageDirectory) }
        else { Join-Path $Tools.Sdk "system-images\android-$ApiLevel\$tag\x86_64" }
    if (-not (Test-Path -LiteralPath (Join-Path $systemImage 'system.img') -PathType Leaf)) {
        throw "Install the Android $ApiLevel $tag x86_64 system image or pass -SystemImageDirectory."
    }
    $properties = Get-Content -LiteralPath (Join-Path $systemImage 'source.properties') -Raw
    if ($properties -notmatch "(?m)^AndroidVersion.ApiLevel=$ApiLevel\s*$" -or $properties -notmatch '(?m)^SystemImage.Abi=x86_64\s*$') {
        throw 'The test system image API/ABI does not match the requested configuration.'
    }
    $output = if ($ApiLevel -eq 35) { 'android\emulator' } else { "android\emulator-api$ApiLevel" }
    $work = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory $output
    $avdHome = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory "$output\avd"
    $emulatorHome = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory "$output\home"
    $avd = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory "$output\avd\QuoridorQA.avd"
    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText((Join-Path $avdHome 'QuoridorQA.ini'), "avd.ini.encoding=UTF-8`npath=$avd`ntarget=android-$ApiLevel`n", $encoding)
    $configuration = @(
        'AvdId=QuoridorQA', 'avd.ini.displayname=Quoridor QA', 'avd.ini.encoding=UTF-8',
        'abi.type=x86_64', 'hw.cpu.arch=x86_64', 'hw.cpu.ncore=2', 'hw.ramSize=2048',
        'hw.device.name=pixel_7', 'hw.device.manufacturer=Google', 'hw.lcd.width=1080',
        $(if ($ApiLevel -eq 35) { 'hw.lcd.height=2400' } else { 'hw.lcd.height=1920' }),
        $(if ($ApiLevel -eq 35) { 'hw.lcd.density=420' } else { 'hw.lcd.density=480' }),
        'hw.keyboard=yes', 'hw.mainKeys=no',
        'hw.gpu.enabled=yes', 'hw.gpu.mode=swiftshader', 'hw.camera.back=none', 'hw.camera.front=none',
        'hw.audioInput=no', 'hw.sdCard=no', 'disk.dataPartition.size=2G', 'vm.heapSize=256',
        ('image.sysdir.1=' + $systemImage.Replace('\', '/') + '/'), "tag.id=$tag",
        $(if ($ApiLevel -eq 35) { 'tag.display=Google Play' } else { 'tag.display=Default' }),
        $(if ($ApiLevel -eq 35) { 'PlayStore.enabled=true' } else { 'PlayStore.enabled=false' }),
        'showDeviceFrame=no'
    )
    [System.IO.File]::WriteAllLines((Join-Path $avd 'config.ini'), $configuration, $encoding)
    $session = [pscustomobject]@{
        Adb = $Tools.Adb; AdbPort = $AdbPort; EmulatorPort = $EmulatorPort
        Serial = "emulator-$EmulatorPort"; AvdName = 'QuoridorQA'; Work = $work
        Process = $null; OwnsAdb = $false
    }
    $variables = @('ANDROID_HOME','ANDROID_SDK_ROOT','ANDROID_AVD_HOME','ANDROID_EMULATOR_HOME','ANDROID_USER_HOME','ANDROID_ADB_SERVER_PORT')
    $saved = @{}
    foreach ($variable in $variables) { $saved[$variable] = [Environment]::GetEnvironmentVariable($variable) }
    try {
        $env:ANDROID_HOME = $Tools.Sdk
        $env:ANDROID_SDK_ROOT = $Tools.Sdk
        $env:ANDROID_AVD_HOME = $avdHome
        $env:ANDROID_EMULATOR_HOME = $emulatorHome
        $env:ANDROID_USER_HOME = $emulatorHome
        $env:ANDROID_ADB_SERVER_PORT = [string]$AdbPort
        & $Tools.Adb -P $AdbPort start-server
        if ($LASTEXITCODE -ne 0) { throw 'The private test ADB server did not start.' }
        $session.OwnsAdb = $true
        $arguments = @('-avd','QuoridorQA','-port',[string]$EmulatorPort,'-no-window','-no-audio','-no-boot-anim',
            '-no-snapshot','-wipe-data','-gpu','swiftshader','-memory','2048','-cores','2')
        $session.Process = Start-Process -FilePath $Tools.Emulator -ArgumentList (ConvertTo-AndroidProcessArguments $arguments) -PassThru -WindowStyle Hidden `
            -RedirectStandardOutput (Join-Path $work 'emulator.stdout.log') -RedirectStandardError (Join-Path $work 'emulator.stderr.log')
    }
    catch {
        Stop-OwnedAndroidEmulator -Session $session
        throw
    }
    finally {
        foreach ($variable in $variables) { [Environment]::SetEnvironmentVariable($variable, $saved[$variable], 'Process') }
    }
    try {
        $deadline = [DateTime]::UtcNow.AddMinutes(4)
        $booted = $false
        while ([DateTime]::UtcNow -lt $deadline) {
            if ($session.Process.HasExited) {
                $log = Get-Content -LiteralPath (Join-Path $work 'emulator.stderr.log') -Tail 20 -ErrorAction SilentlyContinue
                throw "Owned emulator exited before boot: $($log -join "`n")"
            }
            $state = (Invoke-OwnedAndroidAdb -Session $session -Arguments @('get-state') -AllowFailure) -join ''
            if ($state.Trim() -eq 'device') {
                $completed = (Invoke-OwnedAndroidAdb -Session $session -Arguments @('shell','getprop','sys.boot_completed') -AllowFailure) -join ''
                if ($completed.Trim() -eq '1') { $booted = $true; break }
            }
            Start-Sleep -Seconds 1
        }
        if (-not $booted) { throw 'Owned emulator did not finish booting within4minutes.' }
        $runningApi = ((Invoke-OwnedAndroidAdb -Session $session -Arguments @('shell','getprop','ro.build.version.sdk')) -join '').Trim()
        if ($runningApi -ne [string]$ApiLevel) { throw 'The owned emulator booted an unexpected Android API level.' }
        $identity = (Invoke-OwnedAndroidAdb -Session $session -Arguments @('emu','avd','name')) -join "`n"
        if ($identity -notmatch '(?m)^QuoridorQA\s*$') { throw 'Unexpected emulator identity; test installation was cancelled.' }
        Invoke-OwnedAndroidAdb -Session $session -Arguments @('shell','settings','put','global','device_provisioned','1') | Out-Null
        Invoke-OwnedAndroidAdb -Session $session -Arguments @('shell','settings','put','secure','user_setup_complete','1') | Out-Null
        Invoke-OwnedAndroidAdb -Session $session -Arguments @('shell','input','keyevent','82') | Out-Null
        [pscustomobject]@{ AvdName=$session.AvdName; Serial=$session.Serial; AdbPort=$session.AdbPort; ProcessId=$session.Process.Id; Started=$session.Process.StartTime.ToUniversalTime().ToString('o') } |
            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $work 'session.json') -Encoding UTF8
        Write-Host "Owned Android emulator ready: $($session.Serial), private ADB port$AdbPort."
        return $session
    }
    catch {
        Stop-OwnedAndroidEmulator -Session $session
        throw
    }
}

function Stop-OwnedAndroidEmulator {
    param([Parameter(Mandatory = $true)]$Session)

    if ($Session.Process -and -not $Session.Process.HasExited) {
        $identity = (Invoke-OwnedAndroidAdb -Session $Session -Arguments @('emu','avd','name') -AllowFailure) -join "`n"
        if ($identity -match '(?m)^QuoridorQA\s*$') {
            Invoke-OwnedAndroidAdb -Session $Session -Arguments @('emu','kill') -AllowFailure | Out-Null
        }
        if (-not $Session.Process.WaitForExit(10000)) {
            & taskkill.exe /PID $Session.Process.Id /T /F | Out-Null
            $Session.Process.WaitForExit(5000) | Out-Null
        }
        $Session.Process.Dispose()
    }
    if ($Session.OwnsAdb) {
        & $Session.Adb -P $Session.AdbPort kill-server 2>&1 | Out-Null
        $Session.OwnsAdb = $false
    }
}

function Get-OwnedAndroidEmulator {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot, [Parameter(Mandatory = $true)]$Tools,
        [ValidateSet(21, 23, 35)][int]$ApiLevel = 35)

    $output = if ($ApiLevel -eq 35) { 'out\android\emulator' } else { "out\android\emulator-api$ApiLevel" }
    $work = Join-Path $ProjectRoot $output
    $metadata = Get-Content -LiteralPath (Join-Path $work 'session.json') -Raw | ConvertFrom-Json
    if ($metadata.AvdName -ne 'QuoridorQA' -or $metadata.Serial -notmatch '^emulator-[0-9]+$') {
        throw 'Invalid owned emulator session metadata.'
    }
    $process = Get-Process -Id $metadata.ProcessId -ErrorAction Stop
    $savedStart = if ($metadata.Started -is [DateTime]) { $metadata.Started.ToUniversalTime() }
        else { [DateTime]::Parse($metadata.Started, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime() }
    if ($process.StartTime.ToUniversalTime().Ticks -ne $savedStart.Ticks -or
        $process.Path -ne $Tools.Emulator) { throw 'The saved emulator process identity no longer matches.' }
    $session = [pscustomobject]@{
        Adb=$Tools.Adb; AdbPort=[int]$metadata.AdbPort; EmulatorPort=[int]($metadata.Serial -replace '^emulator-','')
        Serial=$metadata.Serial; AvdName=$metadata.AvdName; Work=$work; Process=$process; OwnsAdb=$true
    }
    $identity = (Invoke-OwnedAndroidAdb -Session $session -Arguments @('emu','avd','name')) -join "`n"
    if ($identity -notmatch '(?m)^QuoridorQA\s*$') { throw 'The saved serial is not the owned QuoridorQA emulator.' }
    $runningApi = ((Invoke-OwnedAndroidAdb -Session $session -Arguments @('shell','getprop','ro.build.version.sdk')) -join '').Trim()
    if ($runningApi -ne [string]$ApiLevel) { throw 'The saved emulator has a different Android API level.' }
    return $session
}

function Save-OwnedAndroidScreenshot {
    param([Parameter(Mandatory = $true)]$Session, [Parameter(Mandatory = $true)][string]$Path)

    Save-OwnedAndroidBinary -Session $Session -Path $Path -Arguments @('exec-out','screencap','-p')
}

function Save-OwnedAndroidCaptures {
    param([Parameter(Mandatory = $true)]$Session, [Parameter(Mandatory = $true)][string]$ProjectRoot)

    $outputRoot = [IO.Path]::GetFullPath((Join-Path $ProjectRoot 'out')) + [IO.Path]::DirectorySeparatorChar
    $capturePath = [IO.Path]::GetFullPath((Join-Path $Session.Work 'captures'))
    if (-not $capturePath.StartsWith($outputRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Owned Android captures must stay inside the project output directory.'
    }
    $output = $capturePath.Substring($outputRoot.Length)
    $destination = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory $output
    $files = Invoke-OwnedAndroidAdb -Session $Session -Arguments @('shell','run-as','com.quoridor.mobile','ls','files/qa') -AllowFailure
    foreach ($file in $files) {
        $name = ([string]$file).Trim()
        if ($name -cmatch '^android-[a-z0-9-]+\.png$') {
            Save-OwnedAndroidBinary -Session $Session -Path (Join-Path $destination $name) `
                -Arguments @('exec-out','run-as','com.quoridor.mobile','cat',"files/qa/$name")
        }
    }
}

function Save-OwnedAndroidBinary {
    param([Parameter(Mandatory = $true)]$Session, [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string[]]$Arguments)

    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Session.Adb
    $start.Arguments = ConvertTo-AndroidProcessArguments (@('-P',[string]$Session.AdbPort,'-s',$Session.Serial) + $Arguments)
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        if (-not $process.Start()) { throw 'Screenshot capture did not start.' }
        $errorOutput = $process.StandardError.ReadToEndAsync()
        $file = [System.IO.File]::Create($Path)
        try { $process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "Screenshot failed: $($errorOutput.GetAwaiter().GetResult())" }
    }
    finally { $process.Dispose() }
}

function Start-AndroidInteropServer {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    $work = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'android\interop-server'
    $classes = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'android\interop-server\classes' -Clean
    $sources = @(Get-ProjectJavaSources -ProjectRoot $ProjectRoot -Directories @('src')) + @((Join-Path $ProjectRoot 'tools\AndroidInteropServer.java'))
    Invoke-ProjectCompilation -Sources $sources -Destination $classes
    $portFile = Join-Path $work 'port.txt'
    if (Test-Path -LiteralPath $portFile -PathType Leaf) { Remove-Item -LiteralPath $portFile -Force }
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Command java -CommandType Application | Select-Object -First 1).Source
    $start.Arguments = ConvertTo-AndroidProcessArguments @('--add-modules','jdk.httpserver','-cp',$classes,'AndroidInteropServer',$portFile)
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { $process.Dispose(); throw 'Owned online test server did not start.' }
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds(10)
        while (-not (Test-Path -LiteralPath $portFile -PathType Leaf)) {
            if ($process.HasExited -or [DateTime]::UtcNow -ge $deadline) { throw 'Owned online test server did not publish its port.' }
            Start-Sleep -Milliseconds 100
        }
        $port = [int][System.IO.File]::ReadAllText($portFile)
        return [pscustomobject]@{ Process=$process; Stdout=$stdout; Stderr=$stderr; Work=$work; Port=$port; Url="http://10.0.2.2:$port" }
    }
    catch {
        if (-not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
        $process.Dispose()
        throw
    }
}

function Stop-AndroidInteropServer {
    param([Parameter(Mandatory = $true)]$Server)

    try {
        if (-not $Server.Process.HasExited) {
            $Server.Process.StandardInput.WriteLine('stop')
            $Server.Process.StandardInput.Close()
            if (-not $Server.Process.WaitForExit(5000)) { $Server.Process.Kill(); $Server.Process.WaitForExit() }
        }
        [System.IO.File]::WriteAllText((Join-Path $Server.Work 'stdout.log'), $Server.Stdout.GetAwaiter().GetResult())
        [System.IO.File]::WriteAllText((Join-Path $Server.Work 'stderr.log'), $Server.Stderr.GetAwaiter().GetResult())
    }
    finally { $Server.Process.Dispose() }
}
