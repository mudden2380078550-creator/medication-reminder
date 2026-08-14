param(
    [switch]$InstallEmulator
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$jdkUri = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk'
$gradleUri = 'https://services.gradle.org/distributions/gradle-9.4.1-bin.zip'
$gradleSha256 = '2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb'
$sdkUri = 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip'
$sdkSha256 = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$toolchainRoot = Join-Path $projectRoot 'work/toolchain'
$jdkRoot = Join-Path $toolchainRoot 'jdk'
$gradleRoot = Join-Path $toolchainRoot 'gradle-9.4.1'
$sdkRoot = Join-Path $toolchainRoot 'android-sdk'

New-Item -ItemType Directory -Force -Path $toolchainRoot, $sdkRoot | Out-Null

function Get-Download {
    param([string]$Uri, [string]$Path)

    $temporaryPath = "$Path.download"
    if (Test-Path -LiteralPath $Path) {
        try {
            $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
            $archive.Dispose()
        } catch {
            if (-not (Test-Path -LiteralPath $temporaryPath)) {
                Move-Item -LiteralPath $Path -Destination $temporaryPath
            } else {
                Remove-Item -LiteralPath $Path -Force
            }
        }
    }
    if (-not (Test-Path -LiteralPath $Path)) {
        $downloadUri = (& curl.exe --location --fail --silent --show-error --head --output NUL --write-out '%{url_effective}' $Uri).Trim()
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($downloadUri)) {
            throw "Could not resolve a download URL for $Uri."
        }
        $headers = & curl.exe --fail --silent --show-error --head --dump-header - --output NUL $downloadUri
        if ($LASTEXITCODE -ne 0) {
            throw "Could not read the content length for $downloadUri."
        }
        $lengthMatches = [regex]::Matches(($headers -join "`n"), '(?im)^Content-Length:\s*(\d+)\s*$')
        if ($lengthMatches.Count -eq 0) {
            throw "The download server did not return a content length for $downloadUri."
        }
        [int64]$contentLength = $lengthMatches[$lengthMatches.Count - 1].Groups[1].Value
        [int64]$downloadedLength = if (Test-Path -LiteralPath $temporaryPath) {
            (Get-Item -LiteralPath $temporaryPath).Length
        } else {
            0
        }
        if ($downloadedLength -gt $contentLength) {
            Remove-Item -LiteralPath $temporaryPath -Force
            $downloadedLength = 0
        }

        $chunkPath = "$temporaryPath.chunk"
        [int64]$chunkSize = 4MB
        while ($downloadedLength -lt $contentLength) {
            [int64]$endByte = [Math]::Min($downloadedLength + $chunkSize - 1, $contentLength - 1)
            Remove-Item -LiteralPath $chunkPath -Force -ErrorAction SilentlyContinue
            & curl.exe --fail --retry 3 --retry-delay 2 --connect-timeout 30 --max-time 120 --range "$downloadedLength-$endByte" --silent --show-error --output $chunkPath $downloadUri
            if ($LASTEXITCODE -ne 0) {
                throw "Download failed for bytes $downloadedLength-$endByte from $downloadUri."
            }
            [int64]$expectedLength = $endByte - $downloadedLength + 1
            [int64]$actualLength = (Get-Item -LiteralPath $chunkPath).Length
            if ($actualLength -ne $expectedLength) {
                throw "Expected $expectedLength bytes but received $actualLength bytes from $downloadUri."
            }
            $source = [System.IO.File]::OpenRead($chunkPath)
            $destination = [System.IO.File]::Open($temporaryPath, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
            try {
                $source.CopyTo($destination)
            } finally {
                $destination.Dispose()
                $source.Dispose()
            }
            Remove-Item -LiteralPath $chunkPath -Force
            $downloadedLength += $actualLength
        }
        Move-Item -LiteralPath $temporaryPath -Destination $Path
    }
}

function Assert-Sha256 {
    param([string]$Path, [string]$Expected)

    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
    if ($actual -ne $Expected) {
        throw "SHA-256 mismatch for $Path. Expected $Expected but received $actual."
    }
}

function Assert-Sha1 {
    param([string]$Path, [string]$Expected)

    $actual = (Get-FileHash -Algorithm SHA1 -LiteralPath $Path).Hash.ToLowerInvariant()
    if ($actual -ne $Expected) {
        throw "SHA-1 mismatch for $Path. Expected $Expected but received $actual."
    }
}

function Install-SdkArchive {
    param(
        [string]$ArchiveUri,
        [string]$ArchiveSha1,
        [string]$PackagePath
    )

    $packageDirectory = Join-Path $sdkRoot ($PackagePath.Replace(';', '\\'))
    $packageXml = Join-Path $packageDirectory 'package.xml'
    if (Test-Path -LiteralPath $packageXml) {
        return
    }

    $archiveDirectory = Join-Path $toolchainRoot 'sdk-archives'
    New-Item -ItemType Directory -Force -Path $archiveDirectory | Out-Null
    $archivePath = Join-Path $archiveDirectory ([System.IO.Path]::GetFileName($ArchiveUri))
    Get-Download -Uri $ArchiveUri -Path $archivePath
    Assert-Sha1 -Path $archivePath -Expected $ArchiveSha1

    $extractDirectory = Join-Path $toolchainRoot '.sdk-package-extract'
    Remove-Item -LiteralPath $extractDirectory -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -LiteralPath $archivePath -DestinationPath $extractDirectory -Force
    $packageXmlFile = Get-ChildItem -LiteralPath $extractDirectory -Filter package.xml -Recurse | Select-Object -First 1
    if ($null -eq $packageXmlFile) {
        throw "The $PackagePath archive did not contain package.xml."
    }
    [xml]$packageXmlContent = Get-Content -LiteralPath $packageXmlFile.FullName
    $archivePackagePath = $packageXmlContent.repository.localPackage.path
    if ($archivePackagePath -ne $PackagePath) {
        throw "Archive package path $archivePackagePath did not match $PackagePath."
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $packageDirectory) | Out-Null
    if (Test-Path -LiteralPath $packageDirectory) {
        Remove-Item -LiteralPath $packageDirectory -Recurse -Force
    }
    Move-Item -LiteralPath $packageXmlFile.Directory.FullName -Destination $packageDirectory
    Remove-Item -LiteralPath $extractDirectory -Recurse -Force
}

$jdkZip = Join-Path $toolchainRoot 'jdk-17.zip'
if (-not (Test-Path -LiteralPath (Join-Path $jdkRoot 'bin/java.exe'))) {
    Get-Download -Uri $jdkUri -Path $jdkZip
    $jdkExtract = Join-Path $toolchainRoot '.jdk-extract'
    Remove-Item -LiteralPath $jdkExtract -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -LiteralPath $jdkZip -DestinationPath $jdkExtract -Force
    $jdkDirectory = Get-ChildItem -LiteralPath $jdkExtract -Directory | Select-Object -First 1
    if ($null -eq $jdkDirectory) { throw 'The JDK archive did not contain a directory.' }
    Remove-Item -LiteralPath $jdkRoot -Recurse -Force -ErrorAction SilentlyContinue
    Move-Item -LiteralPath $jdkDirectory.FullName -Destination $jdkRoot
    Remove-Item -LiteralPath $jdkExtract -Recurse -Force
}
$env:JAVA_HOME = $jdkRoot
$env:Path = "$(Join-Path $jdkRoot 'bin')$([System.IO.Path]::PathSeparator)$env:Path"

$gradleZip = Join-Path $toolchainRoot 'gradle-9.4.1-bin.zip'
 $gradle = Join-Path $gradleRoot 'bin/gradle.bat'
if (Test-Path -LiteralPath $gradle) {
    $gradleVersion = & $gradle --version 2>&1
    $gradleVersionText = $gradleVersion -join "`n"
    if ($LASTEXITCODE -ne 0 -or $gradleVersionText -notmatch '(?m)^Gradle 9\.4\.1$') {
        throw 'The installed Gradle distribution is not Gradle 9.4.1.'
    }
} else {
    Get-Download -Uri $gradleUri -Path $gradleZip
    Assert-Sha256 -Path $gradleZip -Expected $gradleSha256
    Expand-Archive -LiteralPath $gradleZip -DestinationPath $toolchainRoot -Force
}

$sdkZip = Join-Path $toolchainRoot 'commandlinetools-win-15859902_latest.zip'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools/latest/bin/sdkmanager.bat'
if (Test-Path -LiteralPath $sdkManager) {
    & $sdkManager --version
    if ($LASTEXITCODE -ne 0) {
        throw 'The installed Android SDK command-line tools could not report a version.'
    }
} else {
    Get-Download -Uri $sdkUri -Path $sdkZip
    Assert-Sha256 -Path $sdkZip -Expected $sdkSha256
    $sdkExtract = Join-Path $toolchainRoot '.sdk-extract'
    $latestTools = Join-Path $sdkRoot 'cmdline-tools/latest'
    Remove-Item -LiteralPath $sdkExtract -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $latestTools -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -LiteralPath $sdkZip -DestinationPath $sdkExtract -Force
    New-Item -ItemType Directory -Force -Path (Split-Path $latestTools) | Out-Null
    Move-Item -LiteralPath (Join-Path $sdkExtract 'cmdline-tools') -Destination $latestTools
    Remove-Item -LiteralPath $sdkExtract -Recurse -Force
}

$authorization = Read-Host 'Type YES to authorize downloading Android SDK packages and accepting their licenses'
if ($authorization -cne 'YES') {
    throw 'Android SDK download and license acceptance were not authorized.'
}

1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" --licenses
if ($LASTEXITCODE -ne 0) { throw 'Android SDK license acceptance failed.' }

& $sdkManager "--sdk_root=$sdkRoot" 'platform-tools' 'platforms;android-37.0' 'build-tools;36.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK package installation failed.' }

if ($InstallEmulator) {
    Install-SdkArchive `
        -ArchiveUri 'https://dl.google.com/android/repository/emulator-windows_x64-15917651.zip' `
        -ArchiveSha1 '54fa750822ff462d57e04fc8e98e60f08df2bb61' `
        -PackagePath 'emulator'
    Install-SdkArchive `
        -ArchiveUri 'https://dl.google.com/android/repository/sys-img/google_apis/x86_64-37.0_r06.zip' `
        -ArchiveSha1 '629e507fd5b737c2c836b12b52c81cd0e3b12399' `
        -PackagePath 'system-images;android-37.0;google_apis;x86_64'
}

$avdManager = Join-Path $sdkRoot 'cmdline-tools/latest/bin/avdmanager.bat'
if ($InstallEmulator) {
    $existingAvds = & $avdManager list avd
    if ($existingAvds -notmatch '(?m)^\s*Name:\s*AnxinApi37\s*$') {
        'no' | & $avdManager create avd -n 'AnxinApi37' -k 'system-images;android-37.0;google_apis;x86_64' --device 'pixel_5'
        if ($LASTEXITCODE -ne 0) { throw 'Android virtual device creation failed.' }
    }
}

$sdkPropertiesPath = Join-Path $projectRoot 'local.properties'
$sdkPropertyValue = $sdkRoot.Replace('\', '\\').Replace(':', '\:')
Set-Content -LiteralPath $sdkPropertiesPath -Value "sdk.dir=$sdkPropertyValue" -NoNewline

& $gradle --no-daemon wrapper --gradle-version 9.4.1 --distribution-type bin
if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper generation failed.' }

$java = Join-Path $jdkRoot 'bin/java.exe'
$adb = Join-Path $sdkRoot 'platform-tools/adb.exe'
& cmd.exe /d /c "`"$java`" -version 2>&1"
& $sdkManager --version
& $adb version
& $gradle --version
