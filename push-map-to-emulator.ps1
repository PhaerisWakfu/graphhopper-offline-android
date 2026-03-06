# Push OSM + graph-cache to emulator Download folder.
# Usage:
#   1) Start emulator, then in PowerShell:
#      cd D:\workspace\graphhopper-master
#      ..\graphhopper-android\push-map-to-emulator.ps1
#   2) Or specify GraphHopper root:
#      & "D:\workspace\graphhopper-android\push-map-to-emulator.ps1" -GraphHopperRoot "D:\workspace\graphhopper-master"

param(
    [string]$GraphHopperRoot = ""
)

function Resolve-AdbExe {
    if ($env:ANDROID_HOME) {
        $p = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
        if (Test-Path $p) { return $p }
    }

    $lp = Join-Path $PSScriptRoot "local.properties"
    if (Test-Path $lp) {
        foreach ($line in (Get-Content $lp -ErrorAction SilentlyContinue)) {
            if ($line -like "sdk.dir=*") {
                $sdk = $line.Substring("sdk.dir=".Length).Trim()
                # local.properties might contain escaped backslashes like: D\:\\path\\to\\Sdk
                $sdk = $sdk -replace '\\\\', '\'
                if ($sdk -match '^[A-Za-z]:\\' -or $sdk -match '^[A-Za-z]:/') {
                    $p = Join-Path $sdk "platform-tools\\adb.exe"
                    if (Test-Path $p) { return $p }
                }
            }
        }
    }

    if (Test-Path "D:\program\android-sdk\platform-tools\adb.exe") {
        return "D:\program\android-sdk\platform-tools\adb.exe"
    }

    return "adb"
}

$adbExe = Resolve-AdbExe

function Run-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Args)
    & $adbExe @Args
    return $LASTEXITCODE
}

# If not specified, try current dir or sibling graphhopper-master
if (-not $GraphHopperRoot) {
    $graphCacheHere = Test-Path ".\graph-cache" -PathType Container
    $mapHere = Test-Path ".\map" -PathType Container
    if ($graphCacheHere -and $mapHere) {
        $GraphHopperRoot = (Get-Location).Path
    } else {
        $sibling = Join-Path (Split-Path (Get-Location).Path -Parent) "graphhopper-master"
        if (Test-Path $sibling) {
            $GraphHopperRoot = $sibling
        } else {
            Write-Host "Please cd to graphhopper-master (contains map/ and graph-cache/) and run again."
            Write-Host "Or: .\\push-map-to-emulator.ps1 -GraphHopperRoot \"D:\\workspace\\graphhopper-master\""
            exit 1
        }
    }
}

$mapDir = Join-Path $GraphHopperRoot "map"
$cacheDir = Join-Path $GraphHopperRoot "graph-cache"

if (-not (Test-Path $mapDir -PathType Container)) {
    Write-Host "ERROR: map dir not found: $mapDir"
    exit 1
}
if (-not (Test-Path $cacheDir -PathType Container)) {
    Write-Host "ERROR: graph-cache dir not found: $cacheDir"
    exit 1
}

# 直接从 map/ 里找 OSM 文件（优先 china-260125.osm.pbf，否则任意 *.osm.pbf），不再复制到根目录
$osmInMap = Join-Path $mapDir "china-260125.osm.pbf"
if (-not (Test-Path $osmInMap)) {
    $firstPbf = Get-ChildItem -Path $mapDir -Filter "*.osm.pbf" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $firstPbf) {
        Write-Host "ERROR: no *.osm.pbf found under: $mapDir"
        exit 1
    }
    $osmFile = $firstPbf.FullName
} else {
    $osmFile = $osmInMap
}
$osmFileName = Split-Path $osmFile -Leaf

Write-Host "adb: $adbExe"
$devices = & $adbExe devices
Write-Host $devices

Write-Host "Pushing to /sdcard/Download/ ..."
Write-Host "  - $osmFileName (from map/)"
$err = Run-Adb push $osmFile /sdcard/Download/
if ($err -ne 0) { exit 1 }

Write-Host "  - graph-cache/"
$err = Run-Adb push $cacheDir /sdcard/Download/
if ($err -ne 0) { exit 1 }

Write-Host "Done. Open the app on emulator and grant storage / all-files permission."
