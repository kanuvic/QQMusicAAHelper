$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$qqLocalJdk = Get-ChildItem .tools/jdk17 -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($qqLocalJdk) {
    $env:JAVA_HOME = $qqLocalJdk.FullName
}
if (!(Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw 'Set JAVA_HOME to JDK 17.' }
& ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
if ($LASTEXITCODE -ne 0) { throw 'Gradle failed' }
