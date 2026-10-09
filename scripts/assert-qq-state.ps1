param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][int]$ExpectedState,
    [Parameter(Mandatory=$true)][string]$Evidence
)
$ErrorActionPreference = 'Stop'
$qqAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$qqDeadline = [DateTime]::UtcNow.AddSeconds(10)
do {
    $qqDump = (& $qqAdb -s $Serial shell dumpsys media_session) -join "`n"
    $qqOffset = $qqDump.IndexOf('package=com.tencent.qqmusic')
    if ($qqOffset -ge 0) {
        $qqSource = $qqDump.Substring($qqOffset)
        $qqMatch = [regex]::Match($qqSource, 'state=PlaybackState \{state=(?:[A-Z_]+\()?([0-9]+)')
        if ($qqMatch.Success -and [int]$qqMatch.Groups[1].Value -eq $ExpectedState) {
            $qqDump | Set-Content -Encoding utf8 -LiteralPath $Evidence
            Write-Output "PASS actual com.tencent.qqmusic state=$ExpectedState; evidence=$Evidence"
            return
        }
    }
    Start-Sleep -Milliseconds 350
} while ([DateTime]::UtcNow -lt $qqDeadline)
$qqDump | Set-Content -Encoding utf8 -LiteralPath $Evidence
throw "QQ Music did not reach expected state $ExpectedState; evidence=$Evidence"
