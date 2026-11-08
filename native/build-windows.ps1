# Builds limesuiteng-legacyapi.dll for the host architecture with MSVC and
# copies it to dist\native\windows-<arch>.
#
# Usage: native\build-windows.ps1 -Arch x64|ARM64 [-Ref <limesuiteng-git-ref>]
#
# The ref may be a branch, a tag or a full commit hash. On Windows LimeSuiteNG
# talks to the LimeSDR-USB through the Cypress FX3 SDK (CyAPI) and to FT601
# devices such as the LimeSDR Mini through FTDI's D3XX library. Both are linked
# statically, as is the C runtime, so the DLL needs nothing installed beyond the
# device drivers. The FX3 SDK has no ARM64 library, so the ARM64 DLL supports
# FT601 devices only. See jlimesdr.cmake for how LimeSuiteNG is linked.
param(
    [Parameter(Mandatory = $true)][ValidateSet('x64', 'ARM64')][string]$Arch,
    [string]$Ref = ''
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

$Root = Split-Path -Parent $PSScriptRoot
$Versions = @{}
Get-Content "$Root\native\versions.env" | Where-Object { $_ -match '^[A-Z0-9_]+=' } | ForEach-Object {
    $Key, $Value = $_ -split '=', 2
    $Versions[$Key] = $Value
}
if (-not $Ref) { $Ref = $Versions.LIMESUITENG_REF }

$Platform = if ($Arch -eq 'x64') { 'windows-x86_64' } else { 'windows-aarch64' }
$Out = "$Root\dist\native\$Platform"
$Licenses = "$Root\dist\native\licenses"
$Work = "$Root\build\native-work"
Remove-Item -Recurse -Force $Work, $Out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Work, $Out, $Licenses | Out-Null
Set-Location $Work

function Slash([string]$Path) { return $Path.Replace('\', '/') }

Write-Host "=== LimeSuiteNG $Ref ==="
# Fetching by ref, rather than cloning a branch, also accepts a commit hash.
# The .git directory stays: LimeSuiteNG puts the commit in its version string.
git init --quiet limesuiteng | Out-Null
git -C limesuiteng fetch --quiet --depth 1 $Versions.LIMESUITENG_REPOSITORY $Ref | Out-Null
git -c advice.detachedHead=false -C limesuiteng checkout --quiet FETCH_HEAD | Out-Null
$Commit = (git -C limesuiteng rev-parse HEAD).Trim()
# jLimeSDR's changes to LimeSuiteNG. A patch that doesn't apply fails a build of
# the pinned commit, but only warns when tracking upstream develop.
foreach ($Patch in Get-ChildItem "$Root\native\patches\*.patch" -ErrorAction SilentlyContinue) {
    git -C limesuiteng apply --check $Patch.FullName 2>$null
    if ($LASTEXITCODE -eq 0) {
        git -C limesuiteng apply $Patch.FullName
        if ($LASTEXITCODE -ne 0) { throw "Failed to apply $($Patch.Name)" }
        Write-Host "Applied $($Patch.Name)"
    } elseif ($Ref -eq $Versions.LIMESUITENG_REF) {
        throw "$($Patch.Name) doesn't apply to $Ref"
    } else {
        Write-Host "::warning::$($Patch.Name) doesn't apply to $Ref"
    }
}
$Project = [regex]::Match((Get-Content -Raw 'limesuiteng\CMakeLists.txt'), '(?m)^project\([^)]*?\bVERSION\s+([0-9.]+)')
if (-not $Project.Success) { throw "No project version in CMakeLists.txt at $Ref" }
$UpstreamVersion = $Project.Groups[1].Value
if ($Ref -eq $Versions.LIMESUITENG_REF -and $UpstreamVersion -ne $Versions.LIMESUITENG_VERSION) {
    throw "CMakeLists.txt at $Ref names version $UpstreamVersion, not $($Versions.LIMESUITENG_VERSION)"
}

$Fx3Args = @()
if ($Arch -eq 'x64') {
    Write-Host "=== Cypress FX3 SDK ==="
    $Archive = 'FX3SDK.zip'
    Invoke-WebRequest $Versions.FX3SDK_URL -OutFile $Archive
    $Hash = (Get-FileHash $Archive -Algorithm SHA1).Hash
    if ($Hash -ine $Versions.FX3SDK_SHA1) { throw "FX3 SDK checksum mismatch: $Hash" }
    Expand-Archive $Archive -DestinationPath fx3sdk
    $Fx3Args = @("-DFX3_SDK_PATH=$(Slash "$Work\fx3sdk")")
    $Fx3 = 'yes'
} else {
    # An empty source directory stands in for the download, so the SDK is not
    # found and LimeSuiteNG leaves FX3 support out instead of linking x64 code.
    New-Item -ItemType Directory -Force -Path no-fx3sdk | Out-Null
    $Fx3Args = @("-DFETCHCONTENT_SOURCE_DIR_CYAPI=$(Slash "$Work\no-fx3sdk")")
    $Fx3 = 'no'
}

cmake -S limesuiteng -B build -A $Arch `
    "-DCMAKE_PROJECT_limesuiteng_INCLUDE=$(Slash "$Root\native\jlimesdr.cmake")" `
    -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded `
    -DBUILD_SHARED_LIBS=OFF `
    -DBUILD_LEGACYAPI=OFF `
    -DBUILD_CLI=OFF `
    -DBUILD_GUI=OFF `
    -DBUILD_PLUGINS=OFF `
    -DBUILD_DOCS=OFF `
    -DBUILD_DRIVERS=OFF `
    -DENABLE_UNIT_TESTS=OFF `
    @Fx3Args
cmake --build build --config Release --target limesuiteng-legacyapi --parallel

Write-Host "=== packaging $Platform ==="
$Dll = Get-ChildItem -Recurse -Path build -Filter limesuiteng-legacyapi.dll | Select-Object -First 1
if (-not $Dll) { throw 'limesuiteng-legacyapi.dll was not produced' }
Copy-Item $Dll.FullName "$Out\limesuiteng-legacyapi.dll"
Copy-Item 'limesuiteng\COPYING' "$Licenses\limesuiteng-LICENSE"
if ($Fx3 -eq 'yes') {
    Copy-Item 'fx3sdk\license\license.txt' "$Licenses\cypress-fx3sdk-LICENSE.txt"
}
@(
    "limesuiteng.ref=$Ref",
    "limesuiteng.commit=$Commit",
    "limesuiteng.version=$UpstreamVersion",
    "usb.fx3=$Fx3",
    "usb.ft601=yes"
) | Set-Content -Encoding ascii "$Out\build-info.properties"
Get-ChildItem $Out
