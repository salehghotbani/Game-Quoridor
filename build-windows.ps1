[CmdletBinding()]
param(
    [switch]$PortableOnly,
    [switch]$DownloadWix,
    [string]$WixDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\ProjectTools.ps1')
. (Join-Path $PSScriptRoot 'scripts\WindowsPackaging.ps1')

try {
    $projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
    $version = Get-ProjectVersion -ProjectRoot $projectRoot
    $tools = Get-WindowsPackagingTools
    $wixBin = $null
    if (-not $PortableOnly) {
        $wixBin = Get-Wix3Directory -ProjectRoot $projectRoot -WixDirectory $WixDirectory -Download:$DownloadWix
    }
    Invoke-ProjectBuild -ProjectRoot $projectRoot

    $work = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory "windows\v$version" -Clean
    $inputPath = Join-Path $work 'input'
    $imageOutput = Join-Path $work 'app-image'
    $runtime = Join-Path $work 'runtime'
    New-Item -ItemType Directory -Path $inputPath, $imageOutput | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot 'out\Quoridor.jar') -Destination (Join-Path $inputPath 'Quoridor.jar')
    foreach ($notice in @('VERSION', 'LICENSE', 'AUTHORS.md')) {
        Copy-Item -LiteralPath (Join-Path $projectRoot $notice) -Destination (Join-Path $inputPath $notice)
    }
    $icon = New-WindowsApplicationIcon -Source (Join-Path $projectRoot 'src\Files\Images\com.nozhaco.quoridor_512x512.png') -Destination (Join-Path $work 'Quoridor.ico')

    $runtimeArguments = @(
        '--add-modules', 'java.desktop,jdk.localedata',
        '--include-locales=en,fa', '--strip-debug', '--no-header-files', '--no-man-pages',
        '--compress=2', '--output', $runtime
    )
    Invoke-ProjectCommand -Command $tools.Jlink -Arguments $runtimeArguments -Description 'Bundled Java runtime creation'
    $imageArguments = @(
        '--type', 'app-image', '--name', 'Quoridor', '--app-version', $version,
        '--vendor', 'Quoridor contributors', '--description', 'Quoridor strategy board game',
        '--input', $inputPath, '--main-jar', 'Quoridor.jar', '--main-class', 'Main',
        '--runtime-image', $runtime, '--icon', $icon, '--dest', $imageOutput
    )
    Invoke-ProjectCommand -Command $tools.Jpackage -Arguments $imageArguments -Description 'Windows application image creation'
    $appImage = Join-Path $imageOutput 'Quoridor'
    Copy-WindowsDistributionFiles -ProjectRoot $projectRoot -AppImage $appImage

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $portableName = "Quoridor-$version-windows-x64.zip"
    $portableTemporary = Join-Path $work $portableName
    [System.IO.Compression.ZipFile]::CreateFromDirectory($appImage, $portableTemporary, [System.IO.Compression.CompressionLevel]::Optimal, $true)

    $installer = $null
    if (-not $PortableOnly) {
        $installerOutput = Join-Path $work 'installer'
        $installerTemporary = Join-Path $work 'installer-temp'
        New-Item -ItemType Directory -Path $installerOutput, $installerTemporary | Out-Null
        $installerArguments = @(
            '--type', 'exe', '--name', 'Quoridor', '--app-version', $version,
            '--vendor', 'Quoridor contributors', '--description', 'Quoridor strategy board game',
            '--app-image', $appImage, '--dest', $installerOutput, '--temp', $installerTemporary,
            '--license-file', (Join-Path $projectRoot 'LICENSE'), '--install-dir', 'Quoridor',
            '--win-per-user-install', '--win-dir-chooser', '--win-menu', '--win-menu-group', 'Quoridor',
            '--win-shortcut', '--win-shortcut-prompt', '--win-upgrade-uuid', 'ae4f1973-d0f8-45ea-9521-0b771b6e54b4'
        )
        if ($VerbosePreference -eq 'Continue') { $installerArguments += '--verbose' }
        $savedPath = $env:PATH
        try {
            $env:PATH = $wixBin + [System.IO.Path]::PathSeparator + $savedPath
            Invoke-ProjectCommand -Command $tools.Jpackage -Arguments $installerArguments -Description 'Windows installer creation'
        }
        finally { $env:PATH = $savedPath }
        $installer = Join-Path $installerOutput "Quoridor-$version.exe"
    }

    $releaseStage = Initialize-ProjectOutput -ProjectRoot $projectRoot -Directory "windows\v$version\release-stage"
    $jarName = "Quoridor-$version.jar"
    $jarStaged = Join-Path $releaseStage $jarName
    $portableStaged = Join-Path $releaseStage $portableName
    Copy-Item -LiteralPath (Join-Path $inputPath 'Quoridor.jar') -Destination $jarStaged
    Move-Item -LiteralPath $portableTemporary -Destination $portableStaged
    $releaseFiles = @($jarStaged, $portableStaged)
    if ($installer) {
        $installerStaged = Join-Path $releaseStage "Quoridor-$version-windows-x64-setup.exe"
        Copy-Item -LiteralPath $installer -Destination $installerStaged
        $releaseFiles += $installerStaged
    }
    Write-WindowsPackageChecksums -Files $releaseFiles -Destination (Join-Path $releaseStage 'SHA256SUMS.txt')
    $release = Publish-WindowsRelease -ProjectRoot $projectRoot -Version $version -StagingDirectory $releaseStage
    Write-Host "Application image: $appImage"
    Write-Host "Release assets: $release"
    exit 0
}
catch {
    Write-Error -Message $_ -ErrorAction Continue
    exit 1
}
