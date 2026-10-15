function Assert-ProjectTools {
    param([Parameter(Mandatory = $true)][string[]]$Names)

    foreach ($name in $Names) {
        if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
            throw "Missing $name. Install JDK 17 or newer and add its bin directory to PATH."
        }
    }
}

function Get-ProjectVersion {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    $versionPath = Join-Path $ProjectRoot 'VERSION'
    if (-not (Test-Path -LiteralPath $versionPath -PathType Leaf)) {
        throw 'Missing VERSION file.'
    }
    $version = [System.IO.File]::ReadAllText($versionPath).Trim()
    if ($version -notmatch '^(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,4})$') {
        throw 'VERSION must contain a numeric MAJOR.MINOR.PATCH without leading zeros.'
    }
    $parts = $version.Split('.')
    if ([int]$parts[0] -gt 255 -or [int]$parts[1] -gt 255 -or [int]$parts[2] -gt 65535) {
        throw 'VERSION exceeds Windows installer limits: 255.255.65535.'
    }
    return $version
}

function Initialize-ProjectOutput {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)][string]$Directory,
        [switch]$Clean
    )

    $outputRoot = [System.IO.Path]::GetFullPath((Join-Path $ProjectRoot 'out'))
    $target = [System.IO.Path]::GetFullPath((Join-Path $outputRoot $Directory))
    $separator = [System.IO.Path]::DirectorySeparatorChar
    if (-not $target.StartsWith($outputRoot + $separator, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Generated output must remain in a child directory of this project''s out directory.'
    }

    $current = $target
    while ($current.Length -ge $outputRoot.Length) {
        if ((Test-Path -LiteralPath $current) -and
            ((Get-Item -LiteralPath $current -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
            throw "Refusing to use a linked output directory: $current"
        }
        if ($current -eq $outputRoot) {
            break
        }
        $current = Split-Path -Parent $current
    }

    if ($Clean -and (Test-Path -LiteralPath $target)) {
        Remove-Item -LiteralPath $target -Recurse -Force
    }
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    return $target
}

function Invoke-ProjectCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$Description
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed (exit code $LASTEXITCODE)."
    }
}

function Get-ProjectJavaSources {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)][string[]]$Directories
    )

    $sources = @(foreach ($directory in $Directories) {
        Get-ChildItem -LiteralPath (Join-Path $ProjectRoot $directory) -Filter '*.java' -File -Recurse
    })
    if ($sources.Count -eq 0) {
        throw 'No Java source files were found.'
    }
    return @($sources | Sort-Object FullName | ForEach-Object { $_.FullName })
}

function Invoke-ProjectCompilation {
    param(
        [Parameter(Mandatory = $true)][string[]]$Sources,
        [Parameter(Mandatory = $true)][string]$Destination,
        [string]$Classpath
    )

    $arguments = @('--release', '17', '-encoding', 'UTF-8', '-Xlint:all')
    if ($Classpath) {
        $arguments += @('-cp', $Classpath)
    }
    $arguments += @('-d', $Destination) + $Sources
    Invoke-ProjectCommand -Command 'javac' -Arguments $arguments -Description 'Java compilation'
}

function Invoke-ProjectBuild {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    & (Join-Path $ProjectRoot 'build.ps1')
    if ($LASTEXITCODE -ne 0) {
        throw "Project build failed (exit code $LASTEXITCODE)."
    }
}
