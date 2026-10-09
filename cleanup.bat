@echo off
setlocal
set "QQAA_CLEANUP_SCRIPT=%~f0"
set "QQAA_CLEANUP_DRY_RUN="
if /I "%~1"=="--dry-run" set "QQAA_CLEANUP_DRY_RUN=1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$text = [IO.File]::ReadAllText($env:QQAA_CLEANUP_SCRIPT); $marker = '# POWERSHELL' + ' START'; & ([scriptblock]::Create($text.Substring($text.IndexOf($marker) + $marker.Length)))"
set "QQAA_CLEANUP_RESULT=%ERRORLEVEL%"
if not defined QQAA_CLEANUP_DRY_RUN pause
exit /b %QQAA_CLEANUP_RESULT%

# POWERSHELL START
$ErrorActionPreference = 'Stop'
try {
    $project = [IO.Path]::GetFullPath((Split-Path -Parent $env:QQAA_CLEANUP_SCRIPT))
    if (!(Test-Path -LiteralPath (Join-Path $project 'settings.gradle.kts')) -or
        !(Test-Path -LiteralPath (Join-Path $project 'app\src\main\AndroidManifest.xml'))) {
        throw 'Project files not found. Keep cleanup.bat in the project root.'
    }
    $workspace = Split-Path -Parent $project
    $targets = @('.gradle', '.kotlin', 'build', 'app\build', '.research') |
        ForEach-Object { Join-Path $project $_ }
    # This workspace also keeps disposable decompilation output beside the repo.
    if ((Split-Path -Leaf $project) -eq 'QQMusicAAHelper' -and
        (Test-Path -LiteralPath (Join-Path $workspace 'PROJECT-PROGRESS.md'))) {
        $targets += Join-Path $workspace '.research'
    }
    $targets = @($targets | Where-Object { Test-Path -LiteralPath $_ })
    $allowed = @($targets | ForEach-Object { [IO.Path]::GetFullPath($_) })
    $preserved = Join-Path $project 'artifacts\preserved-apks'
    foreach ($target in $targets) {
        $full = [IO.Path]::GetFullPath($target)
        $resolved = (Resolve-Path -LiteralPath $target).ProviderPath
        if ($allowed -notcontains $resolved -or $resolved -ne $full) {
            throw "Unexpected cleanup path: $resolved"
        }
        # Refuse junctions/symlinks so cleanup cannot follow them outside the target.
        $items = @((Get-Item -Force -LiteralPath $target)) +
            @(Get-ChildItem -Force -LiteralPath $target -Recurse)
        if ($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) {
            throw "Cleanup target contains a junction or symlink: $target"
        }
    }
    # Preserve every APK in disposable directories before deleting any directory.
    foreach ($target in $targets) {
        foreach ($apk in @(Get-ChildItem -LiteralPath $target -Filter '*.apk' -File -Recurse)) {
            $relative = $apk.FullName.Substring($workspace.Length).TrimStart('\')
            $destination = Join-Path $preserved $relative
            if ($env:QQAA_CLEANUP_DRY_RUN) {
                Write-Host "Preserve APK: $($apk.FullName) -> $destination"
            } else {
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
                Copy-Item -LiteralPath $apk.FullName -Destination $destination -Force
                if ((Get-FileHash -LiteralPath $apk.FullName).Hash -ne
                    (Get-FileHash -LiteralPath $destination).Hash) {
                    throw "APK copy verification failed: $destination"
                }
            }
        }
    }
    foreach ($target in $targets) {
        if ($env:QQAA_CLEANUP_DRY_RUN) {
            Write-Host "Would remove: $target"
        } else {
            Remove-Item -LiteralPath $target -Recurse -Force
            Write-Host "Removed: $target"
        }
    }
    Write-Host 'Done. Source, Git history, signing files, shared tools and release artifacts are preserved.'
    if (!$env:QQAA_CLEANUP_DRY_RUN) { Write-Host "Saved build APKs: $preserved" }
} catch {
    Write-Host "Cleanup stopped: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'If files are in use, close Android Studio and stop Gradle before trying again.'
    exit 1
}
