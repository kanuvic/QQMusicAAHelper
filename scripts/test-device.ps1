param(
    [string]$Serial,
    [ValidateSet('controls','recovery','reconnect','lifecycle-force-stop')][string]$Scenario = 'controls'
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$qqAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$qqDevices = & $qqAdb devices -l
$qqDevices | Write-Output
if (!$Serial) {
    $qqConnected = @($qqDevices | Where-Object { $_ -match '^\S+\s+device\b' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($qqConnected.Count -ne 1) { throw 'Specify -Serial when there is not exactly one authorized device.' }
    $Serial = $qqConnected[0]
}
if (($qqDevices -join "`n") -notmatch "(?m)^$([regex]::Escape($Serial))\s+device\b") { throw "Target $Serial is not authorized/connected" }
$qqRun = Join-Path 'artifacts' ("{0}-{1}" -f (Get-Date -Format 'yyyyMMdd-HHmmss'), $Scenario)
New-Item -ItemType Directory -Force $qqRun | Out-Null
foreach ($qqProperty in @('ro.product.model','ro.build.version.release','ro.build.version.sdk','ro.build.version.incremental')) {
    "$qqProperty=$(& $qqAdb -s $Serial shell getprop $qqProperty)" | Add-Content -Encoding utf8 "$qqRun/device.txt"
}
& $qqAdb -s $Serial shell dumpsys package com.tencent.qqmusic | Select-String 'versionName|versionCode' | Add-Content -Encoding utf8 "$qqRun/device.txt"
& $qqAdb -s $Serial shell dumpsys media_session | Out-File -Encoding utf8 "$qqRun/before.txt"
& $qqAdb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'App installation failed' }
& $qqAdb -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw 'Test installation failed' }
& $qqAdb -s $Serial logcat -c
$qqTests = switch ($Scenario) {
    'controls' { 'dev.qqmusic.aahelper.RealQQMusicTest#pausedSessionAndControls,dev.qqmusic.aahelper.RealQQMusicTest#nextPreviousAndArtwork,dev.qqmusic.aahelper.RealQQMusicTest#qqChangesTrackExternally,dev.qqmusic.aahelper.RealQQMusicTest#reconnectAndRapidControls' }
    'recovery' { 'dev.qqmusic.aahelper.RealQQMusicTest#missingSessionRecovery' }
    'reconnect' { 'dev.qqmusic.aahelper.RealQQMusicTest#reconnectAndRapidControls' }
    'lifecycle-force-stop' { 'dev.qqmusic.aahelper.RealQQMusicTest#sessionDestroyedAndRecreatedForceStop' }
}
$qqResult = & $qqAdb -s $Serial shell am instrument -w -r -e class $qqTests dev.qqmusic.aahelper.test/androidx.test.runner.AndroidJUnitRunner
$qqResult | Tee-Object "$qqRun/result.txt"
& $qqAdb -s $Serial shell dumpsys media_session | Out-File -Encoding utf8 "$qqRun/after.txt"
& $qqAdb -s $Serial logcat -d -s QQMusicAA AndroidRuntime MediaSession MediaController AndroidAuto | Out-File -Encoding utf8 "$qqRun/logcat.txt"
$qqPass = ($qqResult -join "`n") -match 'OK \(\d+ tests?\)' -and ($qqResult -join "`n") -notmatch 'FAILURES|INSTRUMENTATION_FAILED'
if (!$qqPass) { throw "Real-device scenario failed; evidence: $qqRun" }
Write-Output "PASS $Scenario; evidence: $qqRun. DHU UI is a separate test, not implied by this result."
