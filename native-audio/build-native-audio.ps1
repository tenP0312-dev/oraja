[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,
    [string]$WorkDirectory = (Join-Path ([System.IO.Path]::GetTempPath()) ("bmsir-native-audio-" + [guid]::NewGuid().ToString("N"))),
    [switch]$VerifyReproducible
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$InputsPath = Join-Path $ScriptDirectory "inputs.json"
$Inputs = Get-Content -Raw -Encoding UTF8 $InputsPath | ConvertFrom-Json
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$WorkDirectory = [System.IO.Path]::GetFullPath($WorkDirectory)

if (Test-Path -LiteralPath $OutputDirectory) {
    throw "Output directory already exists: $OutputDirectory"
}
if (Test-Path -LiteralPath $WorkDirectory) {
    throw "Work directory already exists: $WorkDirectory"
}
New-Item -ItemType Directory -Path $OutputDirectory, $WorkDirectory | Out-Null

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Program,
        [Parameter(Mandatory = $true)]
        [string[]]$CommandArguments
    )
    & $Program @CommandArguments 2>&1 | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        throw "$Program exited with code $LASTEXITCODE"
    }
}

function Get-VerifiedArchive {
    param(
        [Parameter(Mandatory = $true)]$Component,
        [Parameter(Mandatory = $true)][string]$Filename
    )
    $Destination = Join-Path $WorkDirectory $Filename
    Invoke-WebRequest -Uri $Component.url -OutFile $Destination
    $Actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash.ToLowerInvariant()
    if ($Actual -ne $Component.sha256) {
        throw "Source archive SHA-256 mismatch for ${Filename}: $Actual"
    }
    return $Destination
}

function Get-SingleDirectory {
    param([Parameter(Mandatory = $true)][string]$Parent)
    $Directories = @(Get-ChildItem -LiteralPath $Parent -Directory)
    if ($Directories.Count -ne 1) {
        throw "Expected one extracted source directory under $Parent"
    }
    return $Directories[0].FullName
}

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Invoke-NativeBuild {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$PortAudioSource,
        [Parameter(Mandatory = $true)][string]$AsioSource,
        [Parameter(Mandatory = $true)][string]$JavaHome
    )
    $BuildRoot = Join-Path $WorkDirectory $Name
    $ConfigureArguments = @(
        "-S", $ScriptDirectory,
        "-B", $BuildRoot,
        "-G", "Visual Studio 17 2022",
        "-A", "x64",
        "-DPORTAUDIO_SOURCE_DIR=$PortAudioSource",
        "-DASIOSDK_ROOT_DIR=$AsioSource",
        "-DPORTAUDIO_COMMIT=$($Inputs.portaudio.commit)",
        "-DJAVA_HOME=$JavaHome"
    )
    Invoke-Checked -Program cmake -CommandArguments $ConfigureArguments
    Invoke-Checked -Program cmake -CommandArguments @(
        "--build", $BuildRoot, "--config", "Release", "--target",
        "portaudio", "jportaudio", "--parallel"
    )

    $ArtifactDirectory = Join-Path $BuildRoot "artifacts"
    $PortAudio = Join-Path $ArtifactDirectory "portaudio_x64.dll"
    $JPortAudio = Join-Path $ArtifactDirectory "jportaudio_x64.dll"
    if (-not (Test-Path -LiteralPath $PortAudio -PathType Leaf)) {
        throw "PortAudio DLL was not produced at $PortAudio"
    }
    if (-not (Test-Path -LiteralPath $JPortAudio -PathType Leaf)) {
        throw "JPortAudio DLL was not produced at $JPortAudio"
    }
    return [pscustomobject]@{
        PortAudio = $PortAudio
        JPortAudio = $JPortAudio
    }
}

function New-FileRecord {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][System.IO.FileInfo]$File
    )
    $Relative = [System.IO.Path]::GetRelativePath($Root, $File.FullName).Replace("\", "/")
    return [ordered]@{
        path = $Relative
        size = $File.Length
        sha256 = Get-FileSha256 $File.FullName
    }
}

$PortAudioArchive = Get-VerifiedArchive $Inputs.portaudio "portaudio-$($Inputs.portaudio.commit).tar.gz"
$AsioArchive = Get-VerifiedArchive $Inputs.asio_sdk "ASIO-SDK_2.3.4_2025-10-15.zip"

$SourceRoot = Join-Path $WorkDirectory "sources"
$PortAudioExtract = Join-Path $SourceRoot "portaudio"
$AsioExtract = Join-Path $SourceRoot "asio"
New-Item -ItemType Directory -Path $PortAudioExtract, $AsioExtract | Out-Null
Invoke-Checked -Program tar -CommandArguments @(
    "-xzf", $PortAudioArchive, "-C", $PortAudioExtract
)
Expand-Archive -LiteralPath $AsioArchive -DestinationPath $AsioExtract
$PortAudioSource = Get-SingleDirectory $PortAudioExtract
$AsioSource = Join-Path $AsioExtract "ASIOSDK"
if (-not (Test-Path -LiteralPath (Join-Path $AsioSource "common/asio.h") -PathType Leaf)) {
    throw "The verified ASIO archive does not contain ASIOSDK/common/asio.h"
}

$JavaHome = if ($env:JAVA_HOME_17_X64) { $env:JAVA_HOME_17_X64 } else { $env:JAVA_HOME }
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome "bin/javac.exe") -PathType Leaf)) {
    throw "A Windows x64 JDK is required in JAVA_HOME_17_X64 or JAVA_HOME"
}
$env:JAVA_HOME = $JavaHome
$env:SOURCE_DATE_EPOCH = [string]$Inputs.source_date_epoch

$First = Invoke-NativeBuild "build-a" $PortAudioSource $AsioSource $JavaHome
if ($VerifyReproducible) {
    $Second = Invoke-NativeBuild "build-b" $PortAudioSource $AsioSource $JavaHome
    foreach ($Name in @("PortAudio", "JPortAudio")) {
        $FirstHash = Get-FileSha256 $First.$Name
        $SecondHash = Get-FileSha256 $Second.$Name
        if ($FirstHash -ne $SecondHash) {
            throw "$Name is not reproducible: $FirstHash != $SecondHash"
        }
    }
}

$NativeDirectory = Join-Path $OutputDirectory "natives"
$LicenseDirectory = Join-Path $OutputDirectory "licenses"
$PackagedSourceDirectory = Join-Path $OutputDirectory "source/native-audio"
New-Item -ItemType Directory -Path $NativeDirectory, $LicenseDirectory, $PackagedSourceDirectory | Out-Null
Copy-Item -LiteralPath $First.PortAudio -Destination (Join-Path $NativeDirectory "portaudio_x64.dll")
Copy-Item -LiteralPath $First.JPortAudio -Destination (Join-Path $NativeDirectory "jportaudio_x64.dll")
Copy-Item -LiteralPath (Join-Path $PortAudioSource "LICENSE.txt") -Destination (Join-Path $LicenseDirectory "PORTAUDIO-19.7.0-MIT.txt")
Copy-Item -LiteralPath (Join-Path $AsioSource "LICENSE.txt") -Destination (Join-Path $LicenseDirectory "STEINBERG-ASIO-SDK-2.3.4.txt")
Copy-Item -LiteralPath (Join-Path $ScriptDirectory "STEINBERG-ASIO-SDK-2.3.4-BSD-3-CLAUSE.txt") -Destination $LicenseDirectory
Copy-Item -LiteralPath $PortAudioArchive -Destination $PackagedSourceDirectory
Copy-Item -LiteralPath $AsioArchive -Destination $PackagedSourceDirectory
foreach ($Name in @(
    "inputs.json",
    "CMakeLists.txt",
    "build-native-audio.ps1",
    "SOURCE_INFO.md",
    "STEINBERG-ASIO-SDK-2.3.4-BSD-3-CLAUSE.txt"
)) {
    Copy-Item -LiteralPath (Join-Path $ScriptDirectory $Name) -Destination $PackagedSourceDirectory
}
Copy-Item -LiteralPath (Join-Path $ScriptDirectory "SOURCE_INFO.md") -Destination (Join-Path $OutputDirectory "native-audio-SOURCE_INFO.md")

$VsWhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio/Installer/vswhere.exe"
$VisualStudioPath = if (Test-Path -LiteralPath $VsWhere) {
    (& $VsWhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath | Select-Object -First 1)
} else { "" }
$Compiler = if ($VisualStudioPath) {
    Get-ChildItem -LiteralPath (Join-Path $VisualStudioPath "VC/Tools/MSVC") -Recurse -Filter cl.exe |
        Where-Object { $_.FullName -match "Hostx64[/\\]x64[/\\]cl\.exe$" } |
        Sort-Object FullName |
        Select-Object -Last 1
} else { $null }
$CompilerVersion = if ($Compiler) { $Compiler.VersionInfo.FileVersion } else { "unknown" }
$CMakeVersion = ((& cmake --version | Select-Object -First 1) -replace "^cmake version\s+", "").Trim()
$JavaVersion = ((& (Join-Path $JavaHome "bin/java.exe") -version 2>&1) -join " ").Trim()

$PortAudioOutput = Join-Path $NativeDirectory "portaudio_x64.dll"
$JPortAudioOutput = Join-Path $NativeDirectory "jportaudio_x64.dll"
$Spdx = [ordered]@{
    spdxVersion = "SPDX-2.3"
    dataLicense = "CC0-1.0"
    SPDXID = "SPDXRef-DOCUMENT"
    name = "Arena-oraja-$($Inputs.distribution_version)-windows-native-audio"
    documentNamespace = "https://www.bms-ir.org/spdx/arena-oraja/$($Inputs.distribution_version)/windows-native-audio"
    creationInfo = [ordered]@{
        created = "2025-10-15T00:00:00Z"
        creators = @("Tool: native-audio/build-native-audio.ps1")
    }
    packages = @(
        [ordered]@{
            name = "PortAudio"
            SPDXID = "SPDXRef-Package-PortAudio"
            versionInfo = $Inputs.portaudio.version
            downloadLocation = $Inputs.portaudio.url
            filesAnalyzed = $false
            licenseConcluded = "MIT"
            licenseDeclared = "MIT"
            checksums = @([ordered]@{ algorithm = "SHA256"; checksumValue = $Inputs.portaudio.sha256 })
        },
        [ordered]@{
            name = "JPortAudio"
            SPDXID = "SPDXRef-Package-JPortAudio"
            versionInfo = "$($Inputs.portaudio.version)-$($Inputs.portaudio.commit)"
            downloadLocation = $Inputs.portaudio.url
            filesAnalyzed = $false
            licenseConcluded = "MIT"
            licenseDeclared = "MIT"
        },
        [ordered]@{
            name = "Steinberg ASIO SDK"
            SPDXID = "SPDXRef-Package-ASIO-SDK"
            versionInfo = $Inputs.asio_sdk.version
            downloadLocation = $Inputs.asio_sdk.url
            filesAnalyzed = $false
            licenseConcluded = "GPL-3.0-only AND BSD-3-Clause"
            licenseDeclared = "GPL-3.0-only AND BSD-3-Clause"
            checksums = @([ordered]@{ algorithm = "SHA256"; checksumValue = $Inputs.asio_sdk.sha256 })
        },
        [ordered]@{
            name = "JNA and JNA Platform"
            SPDXID = "SPDXRef-Package-JNA"
            versionInfo = $Inputs.jna.version
            downloadLocation = "https://github.com/java-native-access/jna/tree/5.13.0"
            filesAnalyzed = $false
            licenseConcluded = "Apache-2.0"
            licenseDeclared = "Apache-2.0"
        }
    )
    files = @(
        [ordered]@{
            fileName = "./natives/portaudio_x64.dll"
            SPDXID = "SPDXRef-File-portaudio-x64-dll"
            checksums = @([ordered]@{ algorithm = "SHA256"; checksumValue = Get-FileSha256 $PortAudioOutput })
            licenseConcluded = "GPL-3.0-only"
        },
        [ordered]@{
            fileName = "./natives/jportaudio_x64.dll"
            SPDXID = "SPDXRef-File-jportaudio-x64-dll"
            checksums = @([ordered]@{ algorithm = "SHA256"; checksumValue = Get-FileSha256 $JPortAudioOutput })
            licenseConcluded = "GPL-3.0-only"
        }
    )
    relationships = @(
        [ordered]@{ spdxElementId = "SPDXRef-DOCUMENT"; relationshipType = "DESCRIBES"; relatedSpdxElement = "SPDXRef-Package-PortAudio" },
        [ordered]@{ spdxElementId = "SPDXRef-DOCUMENT"; relationshipType = "DESCRIBES"; relatedSpdxElement = "SPDXRef-Package-JPortAudio" },
        [ordered]@{ spdxElementId = "SPDXRef-DOCUMENT"; relationshipType = "DESCRIBES"; relatedSpdxElement = "SPDXRef-Package-ASIO-SDK" },
        [ordered]@{ spdxElementId = "SPDXRef-DOCUMENT"; relationshipType = "DESCRIBES"; relatedSpdxElement = "SPDXRef-Package-JNA" }
    )
}
$SpdxPath = Join-Path $OutputDirectory "native-audio.spdx.json"
$Spdx | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $SpdxPath -Encoding UTF8

$BundleFiles = @(Get-ChildItem -LiteralPath $OutputDirectory -Recurse -File |
    Where-Object { $_.Name -ne "native-audio-manifest.json" } |
    Sort-Object FullName |
    ForEach-Object { New-FileRecord $OutputDirectory $_ })
$Manifest = [ordered]@{
    schema_version = 1
    distribution_version = $Inputs.distribution_version
    target = $Inputs.target
    license_route = "GPL-3.0-only"
    sources = [ordered]@{
        portaudio = $Inputs.portaudio
        asio_sdk = $Inputs.asio_sdk
        jna = $Inputs.jna
    }
    features = @("ASIO", "WASAPI", "WMME", "DirectSound")
    toolchain = [ordered]@{
        runner_image = $env:ImageOS
        runner_image_version = $env:ImageVersion
        visual_studio = "Visual Studio 2022"
        compiler_file_version = $CompilerVersion
        cmake_version = $CMakeVersion
        java_version = $JavaVersion
        architecture = "x64"
        configuration = "Release"
        reproducibility_flags = @("/Brepro", "/pathmap", "/INCREMENTAL:NO")
        source_date_epoch = $Inputs.source_date_epoch
        double_build_verified = [bool]$VerifyReproducible
    }
    files = $BundleFiles
}
$ManifestPath = Join-Path $OutputDirectory "native-audio-manifest.json"
$Manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $ManifestPath -Encoding UTF8

Write-Host "Native audio bundle: $OutputDirectory"
Write-Host "PortAudio SHA-256: $(Get-FileSha256 $PortAudioOutput)"
Write-Host "JPortAudio SHA-256: $(Get-FileSha256 $JPortAudioOutput)"
