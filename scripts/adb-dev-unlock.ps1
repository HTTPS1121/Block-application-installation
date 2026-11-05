# Dev/debug APK only. Unlock PIN or challenge screen from PC (no PIN in repo).
param(
    [ValidateSet("pin", "challenge")]
    [string]$Screen = "pin"
)

$pkg = "com.appguard.blocker"
$pinFile = Join-Path (Join-Path (Split-Path $PSScriptRoot -Parent) ".local") "dev-pin.txt"

if (Test-Path $pinFile) {
    $pin = (Get-Content $pinFile -Raw).Trim()
    if ($pin.Length -ge 4) {
        if ($Screen -eq "pin") {
            adb shell am start -n "$pkg/.ui.PinActivity" --activity-single-top --es adb_pin $pin
        } else {
            adb shell am start -n "$pkg/.ui.ProtectionChallengeActivity" --activity-single-top --es adb_pin $pin
        }
        exit 0
    }
}

$unlockFlag = if ($Screen -eq "pin") {
    "adb shell am start -n $pkg/.ui.PinActivity --activity-single-top --ez adb_unlock true"
} else {
    "adb shell am start -n $pkg/.ui.ProtectionChallengeActivity --activity-single-top --ez adb_unlock true"
}
Invoke-Expression $unlockFlag
