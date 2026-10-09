param([Parameter(Mandatory=$true)][string]$Serial)
$qqAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $qqAdb -s $Serial shell uiautomator dump /sdcard/qqaa-ui.xml 2>$null | Out-Null
[xml]$qqUi = (& $qqAdb -s $Serial shell cat /sdcard/qqaa-ui.xml) -join "`n"
$qqUi.SelectNodes('//node') | Where-Object { $_.text -ne '' -or $_.'content-desc' -ne '' } | ForEach-Object {
    "$($_.text) | $($_.'content-desc') | $($_.bounds) | clickable=$($_.clickable) checked=$($_.checked)"
}

