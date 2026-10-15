[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PackageZip,
    [switch]$Headless
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $projectRoot 'scripts\ProjectTools.ps1')
$script:checks = 0

function Assert-Package {
    param([bool]$Condition, [string]$Message)

    $script:checks++
    if (-not $Condition) { throw $Message }
}

function Get-WindowsArguments {
    param([AllowEmptyCollection()][string[]]$Arguments)

    # ProcessStartInfo.Arguments uses Windows command-line quoting, not shell quoting.
    return (@(foreach ($argument in $Arguments) {
        $quoted = [regex]::Replace($argument, '(\\*)"', '$1$1\"')
        '"' + [regex]::Replace($quoted, '(\\+)$', '$1$1') + '"'
    }) -join ' ')
}

function New-IsolatedProcess {
    param([string]$Executable, [string[]]$Arguments, [string]$WorkingDirectory)

    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $Executable
    $start.Arguments = Get-WindowsArguments -Arguments $Arguments
    $start.WorkingDirectory = $WorkingDirectory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.EnvironmentVariables['PATH'] = (Join-Path $env:SystemRoot 'System32') + ';' + $env:SystemRoot
    $start.EnvironmentVariables['JAVA_HOME'] = Join-Path $WorkingDirectory 'No installed Java'
    foreach ($variable in @('JDK_HOME', 'JRE_HOME', 'CLASSPATH', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS')) {
        $start.EnvironmentVariables.Remove($variable)
    }
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $start
    return $process
}

function Invoke-BundledJava {
    param([string[]]$Arguments, [string]$Description)

    $process = New-IsolatedProcess -Executable $script:bundledJava -Arguments $Arguments -WorkingDirectory $script:extraction
    $started = $false
    try {
        $started = $process.Start()
        Assert-Package $started "$Description did not start."
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        Assert-Package ($process.WaitForExit(60000)) "$Description exceeded 60 seconds."
        $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        Assert-Package ($process.ExitCode -eq 0) "$Description failed (exit $($process.ExitCode)):`n$output"
        Write-Host $output.Trim()
    }
    finally {
        if ($started -and -not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
        $process.Dispose()
    }
}

function Test-NativeLauncher {
    param([string]$Executable, [string]$Version)

    $stream = [System.IO.File]::OpenRead($Executable)
    $reader = New-Object System.IO.BinaryReader($stream)
    try {
        Assert-Package ($reader.ReadUInt16() -eq 0x5A4D) 'The launcher is not a Windows PE executable.'
        $stream.Position = 0x3C
        $peOffset = $reader.ReadInt32()
        Assert-Package ($peOffset -ge 64 -and $peOffset -le $stream.Length - 26) 'The launcher PE header is invalid.'
        $stream.Position = $peOffset
        Assert-Package ($reader.ReadUInt32() -eq 0x4550) 'The launcher PE signature is invalid.'
        Assert-Package ($reader.ReadUInt16() -eq 0x8664) 'The launcher must be AMD64/x64.'
        $stream.Position = $peOffset + 24
        Assert-Package ($reader.ReadUInt16() -eq 0x20B) 'The launcher must use a PE32+ optional header.'
    }
    finally { $reader.Dispose() }

    $information = [System.Diagnostics.FileVersionInfo]::GetVersionInfo($Executable)
    $versionPattern = '^' + [regex]::Escape($Version) + '(\.0)?$'
    Assert-Package ($information.FileVersion -match $versionPattern) "Unexpected EXE file version: $($information.FileVersion)"
    Assert-Package ($information.ProductVersion -match $versionPattern) "Unexpected EXE product version: $($information.ProductVersion)"
}

function Initialize-NativeProcessInspection {
    if (('QuoridorPackageTest.ProcessTree' -as [type])) { return }
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;

namespace QuoridorPackageTest {
    public static class ProcessTree {
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct ProcessEntry {
            public uint Size;
            public uint Usage;
            public uint Id;
            public IntPtr Heap;
            public uint Module;
            public uint Threads;
            public uint Parent;
            public int Priority;
            public uint Flags;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)]
            public string Executable;
        }

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr CreateToolhelp32Snapshot(uint flags, uint process);
        [DllImport("kernel32.dll", EntryPoint = "Process32FirstW", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool First(IntPtr snapshot, ref ProcessEntry entry);
        [DllImport("kernel32.dll", EntryPoint = "Process32NextW", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool Next(IntPtr snapshot, ref ProcessEntry entry);
        [DllImport("kernel32.dll")]
        private static extern bool CloseHandle(IntPtr handle);

        public static Dictionary<int, int> Parents() {
            IntPtr snapshot = CreateToolhelp32Snapshot(2, 0);
            if (snapshot == new IntPtr(-1)) {
                throw new Win32Exception(Marshal.GetLastWin32Error());
            }
            try {
                Dictionary<int, int> parents = new Dictionary<int, int>();
                ProcessEntry entry = new ProcessEntry();
                entry.Size = (uint) Marshal.SizeOf(typeof(ProcessEntry));
                if (!First(snapshot, ref entry)) {
                    throw new Win32Exception(Marshal.GetLastWin32Error());
                }
                do {
                    parents[(int) entry.Id] = (int) entry.Parent;
                } while (Next(snapshot, ref entry));
                return parents;
            } finally {
                CloseHandle(snapshot);
            }
        }
    }
}
'@
}

function Update-OwnedLaunchers {
    param([System.Collections.Generic.Dictionary[int,System.Diagnostics.Process]]$Owned, [string]$Executable)

    $parents = [QuoridorPackageTest.ProcessTree]::Parents()
    do {
        $added = $false
        foreach ($processId in $parents.Keys) {
            $parentId = $parents[$processId]
            if ($Owned.ContainsKey($processId) -or -not $Owned.ContainsKey($parentId)) { continue }
            $candidate = $null
            try {
                $parent = $Owned[$parentId]
                if ($parent.HasExited) { continue }
                $candidate = [System.Diagnostics.Process]::GetProcessById($processId)
                if ($candidate.StartTime -ge $parent.StartTime -and
                    [StringComparer]::OrdinalIgnoreCase.Equals($candidate.MainModule.FileName, $Executable)) {
                    # Retain the handle while alive so .NET Framework can read the exit code later.
                    [void]$candidate.Handle
                    $Owned.Add($processId, $candidate)
                    $candidate = $null
                    $added = $true
                }
            }
            catch [ArgumentException] { }
            catch [InvalidOperationException] { }
            catch [System.ComponentModel.Win32Exception] { }
            finally { if ($null -ne $candidate) { $candidate.Dispose() } }
        }
    } while ($added)
}

function Test-NativeWindow {
    param([string]$Executable, [string]$Version)

    Initialize-NativeProcessInspection
    $process = New-IsolatedProcess -Executable $Executable -Arguments @() -WorkingDirectory $script:extraction
    $process.StartInfo.CreateNoWindow = $false
    $process.StartInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Normal
    $owned = New-Object 'System.Collections.Generic.Dictionary[int,System.Diagnostics.Process]'
    $started = $false
    try {
        $started = $process.Start()
        Assert-Package $started 'The native launcher did not start.'
        $owned.Add($process.Id, $process)
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $timer = [System.Diagnostics.Stopwatch]::StartNew()
        $window = $null
        do {
            Start-Sleep -Milliseconds 100
            # jpackage restarts into a same-EXE child when its app directory is absent from PATH.
            Update-OwnedLaunchers -Owned $owned -Executable $Executable
            foreach ($candidate in $owned.Values) {
                $candidate.Refresh()
                if (-not $candidate.HasExited -and $candidate.MainWindowHandle -ne [IntPtr]::Zero) {
                    $window = $candidate
                    break
                }
            }
        } while (-not $process.HasExited -and $null -eq $window -and $timer.Elapsed.TotalSeconds -lt 20)
        if ($process.HasExited -and $null -eq $window) {
            throw "The native launcher exited before showing a window: $($stdout.GetAwaiter().GetResult()) $($stderr.GetAwaiter().GetResult())"
        }
        Assert-Package ($null -ne $window) 'The native launcher tree did not display its own application window.'
        Assert-Package ($window.Responding) 'The native application window is not responding.'
        # Keep this script ASCII so Windows PowerShell 5.1 reads it without a BOM.
        $persianName = -join ([char[]]@(0x06A9, 0x0648, 0x0631, 0x06CC, 0x062F, 0x0648, 0x0631))
        $gameTitle = $window.MainWindowTitle -like '*Quoridor*' -or $window.MainWindowTitle.Contains($persianName)
        Assert-Package ($gameTitle -and $window.MainWindowTitle.Contains($Version)) 'The native application window does not identify the game and its version.'
        Write-Host "Native application window opened (owned PID $($window.Id), launcher PID $($process.Id)): $($window.MainWindowTitle)"
        Assert-Package ($window.CloseMainWindow()) 'The owned application window could not be closed.'
        Assert-Package ($window.WaitForExit(10000)) 'The native application did not exit after closing its window.'
        Assert-Package ($window.ExitCode -eq 0) "The native game exited with code $($window.ExitCode)."
        Assert-Package ($process.WaitForExit(10000)) 'The native launcher did not finish after the game closed.'
        Assert-Package ($process.ExitCode -eq 0) "The native application exited with code $($process.ExitCode)."
    }
    finally {
        if ($started) {
            Update-OwnedLaunchers -Owned $owned -Executable $Executable
            # Each descendant has an observed parent and an exact executable-path/start-time match.
            foreach ($candidate in @($owned.Values | Sort-Object @{ Expression = { if ($_.Id -eq $process.Id) { 1 } else { 0 } } })) {
                if (-not $candidate.HasExited) { $candidate.Kill(); [void]$candidate.WaitForExit(10000) }
                $candidate.Dispose()
            }
        }
        else { $process.Dispose() }
    }
}

try {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'This test requires Windows.' }
    Assert-ProjectTools -Names @('javac')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $version = (Get-Content -LiteralPath (Join-Path $projectRoot 'VERSION') -Raw).Trim()
    Assert-Package ($version -match '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') 'VERSION is invalid.'
    $zipPath = (Resolve-Path -LiteralPath $PackageZip).Path
    $validationRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'out\package-validation'))
    Assert-Package (-not $zipPath.StartsWith($validationRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) 'The input ZIP must not be inside the test output that is cleaned.'
    $validationRoot = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory 'package-validation' -Clean
    $script:extraction = Join-Path $validationRoot 'Extracted package with spaces'
    New-Item -ItemType Directory -Path $script:extraction | Out-Null

    $archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        $names = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
        foreach ($entry in $archive.Entries) {
            $name = $entry.FullName.Replace('\', '/')
            Assert-Package ($name.StartsWith('Quoridor/', [StringComparison]::Ordinal)) "ZIP entry is outside the Quoridor directory: $name"
            Assert-Package ($name -notmatch '(^|/)\.\.(/|$)|:') "Unsafe ZIP entry: $name"
            Assert-Package ($names.Add($name)) "Duplicate ZIP entry: $name"
            Assert-Package ($name -notmatch '(?i)(^|/)(vlc[^/]*|libvlc[^/]*|vlcj[^/]*|jna[^/]*|tests?|tools|src|\.git|\.idea|UML And Report)(/|$)') "Unwanted project/development file: $name"
            Assert-Package ($name -notmatch '(?i)\.(java|class|mp3|mp4|avi|mkv|wav)$') "Unwanted source or media file: $name"
            Assert-Package ($name -notmatch '(?i)(^|/)(javac|jpackage|jlink|jar|jshell|jdb|jcmd|jdeps|javadoc)\.exe$') "A development tool was bundled: $name"
        }
        foreach ($required in @('Quoridor.exe', 'app/Quoridor.jar', 'app/Quoridor.cfg', 'runtime/bin/java.exe', 'runtime/bin/server/jvm.dll', 'runtime/lib/modules', 'runtime/legal/java.base/LICENSE', 'runtime/legal/java.base/ASSEMBLY_EXCEPTION', 'VERSION', 'LICENSE', 'AUTHORS.md', 'README.md', 'README.fa.md')) {
            Assert-Package ($names.Contains('Quoridor/' + $required)) "Required package file is missing: $required"
        }
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zipPath, $script:extraction)
    }
    finally { $archive.Dispose() }

    $appRoot = Join-Path $script:extraction 'Quoridor'
    $launcher = Join-Path $appRoot 'Quoridor.exe'
    $jar = Join-Path $appRoot 'app\Quoridor.jar'
    $script:bundledJava = Join-Path $appRoot 'runtime\bin\java.exe'
    Assert-Package (((Get-Content -LiteralPath (Join-Path $appRoot 'VERSION') -Raw).Trim()) -eq $version) 'The bundled VERSION differs from the project.'
    foreach ($notice in @('LICENSE', 'AUTHORS.md')) {
        $expected = (Get-FileHash -LiteralPath (Join-Path $projectRoot $notice) -Algorithm SHA256).Hash
        $actual = (Get-FileHash -LiteralPath (Join-Path $appRoot $notice) -Algorithm SHA256).Hash
        Assert-Package ($actual -eq $expected) "The bundled $notice differs from the project."
    }
    Test-NativeLauncher -Executable $launcher -Version $version
    $configuration = Get-Content -LiteralPath (Join-Path $appRoot 'app\Quoridor.cfg') -Raw
    Assert-Package ($configuration.Contains('app.mainclass=Main')) 'The native launcher targets the wrong main class.'
    Assert-Package ($configuration.Contains('Quoridor.jar')) 'The native launcher does not reference the packaged JAR.'

    $jarArchive = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        foreach ($entry in $jarArchive.Entries) {
            Assert-Package ($entry.FullName -notmatch '(?i)(^|/)(tests?|tools|support|vlc[^/]*|libvlc[^/]*|vlcj[^/]*|jna[^/]*)(/|$)|Test\.class$|\.java$|\.(mp3|mp4|avi|mkv|wav)$') "Unwanted JAR content: $($entry.FullName)"
        }
    }
    finally { $jarArchive.Dispose() }

    $testClasses = Join-Path $validationRoot 'test-classes'
    New-Item -ItemType Directory -Path $testClasses | Out-Null
    $sources = Get-ProjectJavaSources -ProjectRoot $projectRoot -Directories @('tests')
    Invoke-ProjectCompilation -Sources $sources -Destination $testClasses -Classpath $jar
    $classpath = $jar + [System.IO.Path]::PathSeparator + $testClasses
    $testArguments = @("-Dquoridor.test.project=$projectRoot", "-Dquoridor.test.jar=$jar", "-Dquoridor.test.output=$validationRoot", '-ea', '-cp', $classpath)
    foreach ($testClass in @('VersionTest', 'GameStateTest', 'BotPlayerTest', 'SaveStoreTest', 'I18nTest')) {
        Invoke-BundledJava -Arguments (@('-Djava.awt.headless=true') + $testArguments + $testClass) -Description "Bundled runtime $testClass"
    }
    if ($Headless) {
        Write-Host 'Native window and graphical integration checks skipped because -Headless was specified.'
    }
    else {
        foreach ($testClass in @('BoardSmokeTest', 'LanguageUiTest', 'AppearanceUiTest', 'SaveDialogTest')) {
            Invoke-BundledJava -Arguments (@('-Djava.awt.headless=false') + $testArguments + $testClass) -Description "Bundled runtime $testClass"
        }
        Test-NativeWindow -Executable $launcher -Version $version
    }
    Write-Host "Windows package validation passed ($script:checks checks): $zipPath"
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
