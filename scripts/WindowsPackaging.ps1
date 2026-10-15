function Get-WindowsPackagingTools {
    if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
        throw 'Windows packages must be built on Windows.'
    }
    Assert-ProjectTools -Names @('jpackage')
    $jpackagePath = (Get-Command jpackage -CommandType Application).Source
    $jdkBin = Split-Path -Parent $jpackagePath
    $jlinkPath = Join-Path $jdkBin 'jlink.exe'
    $jdkRoot = Split-Path -Parent $jdkBin
    if (-not (Test-Path -LiteralPath $jlinkPath -PathType Leaf)) {
        throw 'The JDK containing jpackage must also contain jlink.exe.'
    }
    $jdkRelease = Get-Content -LiteralPath (Join-Path $jdkRoot 'release') -Raw
    if ($jdkRelease -notmatch '(?m)^OS_ARCH="(?:amd64|x86_64)"\s*$') {
        throw 'The Windows x64 release requires an x64 JDK.'
    }
    return [pscustomobject]@{ Jpackage = $jpackagePath; Jlink = $jlinkPath }
}

function Get-Wix3Directory {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [string]$WixDirectory,
        [switch]$Download
    )

    $cachedBin = Join-Path $ProjectRoot 'out\tools\wix-3.14.1\bin'
    $candidates = @()
    if ($WixDirectory) { $candidates += [System.IO.Path]::GetFullPath($WixDirectory) }
    $candleCommand = Get-Command candle.exe -CommandType Application -ErrorAction SilentlyContinue
    $lightCommand = Get-Command light.exe -CommandType Application -ErrorAction SilentlyContinue
    if ($candleCommand -and $lightCommand) {
        $candleBin = Split-Path -Parent $candleCommand.Source
        if ($candleBin -eq (Split-Path -Parent $lightCommand.Source)) { $candidates += $candleBin }
    }
    if ($env:WIX) { $candidates += @($env:WIX, (Join-Path $env:WIX 'bin')) }
    $candidates += $cachedBin
    foreach ($candidate in $candidates) {
        $candle = Join-Path $candidate 'candle.exe'
        $light = Join-Path $candidate 'light.exe'
        if ((Test-Path -LiteralPath $candle -PathType Leaf) -and (Test-Path -LiteralPath $light -PathType Leaf)) {
            $toolVersion = (Get-Item -LiteralPath $candle).VersionInfo.ProductVersion
            if ($toolVersion -notmatch '^3\.') { throw "WiX 3 is required; found $toolVersion at $candidate" }
            return [System.IO.Path]::GetFullPath($candidate)
        }
        if ($WixDirectory -and $candidate -eq [System.IO.Path]::GetFullPath($WixDirectory)) {
            throw "WiX directory must contain candle.exe and light.exe: $candidate"
        }
    }
    if (-not $Download) {
        throw 'WiX 3 was not found. Use -DownloadWix to fetch pinned build tools, -WixDirectory to select them, or -PortableOnly to omit the installer.'
    }

    $cache = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'tools\wix-3.14.1'
    $archive = Join-Path $cache 'wix314-binaries.zip'
    $url = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'
    $expectedHash = '6AC824E1642D6F7277D0ED7EA09411A508F6116BA6FAE0AA5F2C7DAA2FF43D31'
    if ((Test-Path -LiteralPath $archive) -and
        ((Get-Item -LiteralPath $archive -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
        throw "Refusing to use a linked WiX archive: $archive"
    }
    $cacheIsValid = (Test-Path -LiteralPath $archive -PathType Leaf) -and
        ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -eq $expectedHash)
    if (-not $cacheIsValid) {
        Write-Host 'Downloading WiX 3.14.1 into out/tools (no Windows installation).'
        $temporaryArchive = Join-Path $cache ('wix314-' + [guid]::NewGuid().ToString('N') + '.download')
        try {
            $curl = Get-Command curl.exe -CommandType Application -ErrorAction SilentlyContinue
            if ($curl) {
                Invoke-ProjectCommand -Command $curl.Source -Arguments @('--fail', '--location', '--retry', '2', '--connect-timeout', '20', '--max-time', '180', '--output', $temporaryArchive, $url) -Description 'WiX download'
            }
            else {
                Invoke-WebRequest -Uri $url -OutFile $temporaryArchive -UseBasicParsing
            }
            if ((Get-FileHash -LiteralPath $temporaryArchive -Algorithm SHA256).Hash -ne $expectedHash) {
                throw 'Downloaded WiX archive does not match the pinned 3.14.1 release.'
            }
            Move-Item -LiteralPath $temporaryArchive -Destination $archive -Force
        }
        finally {
            if (Test-Path -LiteralPath $temporaryArchive -PathType Leaf) {
                Remove-Item -LiteralPath $temporaryArchive -Force
            }
        }
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expectedHash) {
        throw "WiX archive checksum does not match the pinned 3.14.1 release: $archive"
    }
    $bin = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'tools\wix-3.14.1\bin' -Clean
    Expand-Archive -LiteralPath $archive -DestinationPath $bin
    if (-not (Test-Path -LiteralPath (Join-Path $bin 'candle.exe') -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $bin 'light.exe') -PathType Leaf)) {
        throw 'The verified WiX archive did not contain the required build tools.'
    }
    return $bin
}

function New-WindowsApplicationIcon {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    Add-Type -AssemblyName System.Drawing
    $sourceImage = [System.Drawing.Image]::FromFile($Source)
    $bitmap = [System.Drawing.Bitmap]::new(256, 256, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $png = [System.IO.MemoryStream]::new()
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.DrawImage($sourceImage, 0, 0, 256, 256)
        $bitmap.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
        $bytes = $png.ToArray()
        $writer = [System.IO.BinaryWriter]::new([System.IO.File]::Open($Destination, [System.IO.FileMode]::Create))
        try {
            $writer.Write([uint16]0)
            $writer.Write([uint16]1)
            $writer.Write([uint16]1)
            $writer.Write([byte]0)
            $writer.Write([byte]0)
            $writer.Write([byte]0)
            $writer.Write([byte]0)
            $writer.Write([uint16]1)
            $writer.Write([uint16]32)
            $writer.Write([uint32]$bytes.Length)
            $writer.Write([uint32]22)
            $writer.Write($bytes)
        }
        finally { $writer.Dispose() }
    }
    finally {
        $png.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
        $sourceImage.Dispose()
    }
    return $Destination
}

function Copy-WindowsDistributionFiles {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)][string]$AppImage
    )

    foreach ($name in @('VERSION', 'LICENSE', 'AUTHORS.md', 'README.md', 'README.fa.md', 'CONTRIBUTING.md', 'RELEASING.md', 'ONLINE.md', 'CHANGELOG.md', 'RELEASE_NOTES.md')) {
        $source = Join-Path $ProjectRoot $name
        if (Test-Path -LiteralPath $source -PathType Leaf) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $AppImage $name)
        }
        elseif ($name -in @('VERSION', 'LICENSE', 'AUTHORS.md', 'README.md')) {
            throw "Missing required distribution file: $name"
        }
    }
    $screenshots = Join-Path $ProjectRoot 'docs\screenshots'
    if (Test-Path -LiteralPath $screenshots -PathType Container) {
        $destination = Join-Path $AppImage 'docs\screenshots'
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        Get-ChildItem -LiteralPath $screenshots -Filter '*.png' -File | ForEach-Object {
            Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $destination $_.Name)
        }
    }
}

function Assert-WindowsOutputDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$InspectContents
    )

    $outputRoot = [System.IO.Path]::GetFullPath((Join-Path $ProjectRoot 'out'))
    $target = [System.IO.Path]::GetFullPath($Path)
    $separator = [System.IO.Path]::DirectorySeparatorChar
    if (-not $target.StartsWith($outputRoot + $separator, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Release directories must remain below this project's out directory: $target"
    }
    $current = $target
    while ($current.Length -ge $outputRoot.Length) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if (-not $item.PSIsContainer -or ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
                throw "Refusing to use a linked or non-directory output path: $current"
            }
        }
        if ($current -eq $outputRoot) { break }
        $current = Split-Path -Parent $current
    }
    if ($InspectContents -and (Test-Path -LiteralPath $target -PathType Container)) {
        $pending = [System.Collections.Generic.Stack[string]]::new()
        $pending.Push($target)
        while ($pending.Count -gt 0) {
            foreach ($item in Get-ChildItem -LiteralPath $pending.Pop() -Force) {
                if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
                    throw "Refusing to move or remove linked output contents: $($item.FullName)"
                }
                if ($item.PSIsContainer) { $pending.Push($item.FullName) }
            }
        }
    }
    return $target
}

function Assert-WindowsReleaseStage {
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][string]$Version
    )

    $required = @("Quoridor-$Version.jar", "Quoridor-$Version-server.jar", "Quoridor-$Version-windows-x64.zip")
    $allowed = $required + @("Quoridor-$Version-windows-x64-setup.exe", 'SHA256SUMS.txt')
    $entries = @(Get-ChildItem -LiteralPath $Directory -Force)
    foreach ($entry in $entries) {
        if ($entry.PSIsContainer -or $entry.Name -notin $allowed) {
            throw "Unexpected staged release entry: $($entry.Name)"
        }
    }
    foreach ($name in $required + @('SHA256SUMS.txt')) {
        if ($name -notin $entries.Name) { throw "Missing staged release file: $name" }
    }
    $assets = @($entries | Where-Object Name -ne 'SHA256SUMS.txt')
    $checksums = @{}
    foreach ($line in Get-Content -LiteralPath (Join-Path $Directory 'SHA256SUMS.txt') -Encoding UTF8) {
        if ($line -notmatch '^(?<hash>[0-9a-f]{64})  (?<name>[^\\/]+)$' -or $checksums.ContainsKey($Matches.name)) {
            throw 'The staged checksum manifest contains an invalid or duplicate entry.'
        }
        $checksums[$Matches.name] = $Matches.hash
    }
    if ($checksums.Count -ne $assets.Count) { throw 'The staged checksum manifest does not match the release assets.' }
    foreach ($asset in $assets) {
        $hash = (Get-FileHash -LiteralPath $asset.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if (-not $checksums.ContainsKey($asset.Name) -or $checksums[$asset.Name] -ne $hash) {
            throw "The staged checksum does not match $($asset.Name)."
        }
    }
}

function Move-WindowsOutputDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    $checkedSource = Assert-WindowsOutputDirectory -ProjectRoot $ProjectRoot -Path $Source -InspectContents
    $checkedDestination = Assert-WindowsOutputDirectory -ProjectRoot $ProjectRoot -Path $Destination
    [System.IO.Directory]::Move($checkedSource, $checkedDestination)
}

function Publish-WindowsRelease {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)][string]$Version,
        [Parameter(Mandatory = $true)][string]$StagingDirectory
    )

    if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') { throw 'Invalid release version.' }
    $stage = Assert-WindowsOutputDirectory -ProjectRoot $ProjectRoot -Path $StagingDirectory -InspectContents
    $expectedStage = [System.IO.Path]::GetFullPath((Join-Path $ProjectRoot "out\windows\v$Version\release-stage"))
    if ($stage -ne $expectedStage) { throw "Unexpected release staging directory: $stage" }
    Assert-WindowsReleaseStage -Directory $stage -Version $Version
    $parent = Initialize-ProjectOutput -ProjectRoot $ProjectRoot -Directory 'releases'
    $destination = Assert-WindowsOutputDirectory -ProjectRoot $ProjectRoot -Path (Join-Path $parent "v$Version") -InspectContents
    $backup = Assert-WindowsOutputDirectory -ProjectRoot $ProjectRoot -Path (Join-Path $parent ('.v' + $Version + '-previous-' + [guid]::NewGuid().ToString('N')))
    if ([System.IO.Path]::GetPathRoot($stage) -ne [System.IO.Path]::GetPathRoot($destination)) {
        throw 'Release promotion requires staging and destination on the same volume.'
    }
    $hasPrevious = Test-Path -LiteralPath $destination -PathType Container
    if ($hasPrevious) { Move-WindowsOutputDirectory -ProjectRoot $ProjectRoot -Source $destination -Destination $backup }
    try {
        Move-WindowsOutputDirectory -ProjectRoot $ProjectRoot -Source $stage -Destination $destination
    }
    catch {
        $promotionError = $_
        if ($hasPrevious) {
            try { Move-WindowsOutputDirectory -ProjectRoot $ProjectRoot -Source $backup -Destination $destination }
            catch { throw "Release promotion failed; the previous release remains at $backup. Rollback error: $_. Promotion error: $promotionError" }
        }
        throw $promotionError
    }
    if ($hasPrevious) {
        try {
            $checkedBackup = Assert-WindowsOutputDirectory -ProjectRoot $ProjectRoot -Path $backup -InspectContents
            Remove-Item -LiteralPath $checkedBackup -Recurse -Force
        }
        catch { Write-Warning "Release published successfully; the previous copy could not be removed: $backup. $_" }
    }
    Write-Host "Published complete release: $destination"
    return $destination
}

function Write-WindowsPackageChecksums {
    param(
        [Parameter(Mandatory = $true)][string[]]$Files,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if ((Test-Path -LiteralPath $Destination) -and
        ((Get-Item -LiteralPath $Destination -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
        throw "Refusing to replace a linked checksum file: $Destination"
    }
    $lines = @($Files | Sort-Object | ForEach-Object {
        $digest = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()
        $name = [System.IO.Path]::GetFileName($_)
        "$digest  $name"
    })
    [System.IO.File]::WriteAllLines($Destination, $lines, [System.Text.UTF8Encoding]::new($false))
    Write-Host "Created $Destination"
}
