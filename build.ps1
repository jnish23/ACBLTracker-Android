param([switch]$Check, [switch]$Release)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (Test-Path '.tools/jdk') { $env:JAVA_HOME = (Get-ChildItem '.tools/jdk' -Directory | Select-Object -First 1).FullName }
    if (Test-Path '.tools/android-sdk') { $env:ANDROID_HOME = Join-Path $PSScriptRoot '.tools/android-sdk' }
    if (Test-Path '.tools/android-user') { $env:ANDROID_USER_HOME = Join-Path $PSScriptRoot '.tools/android-user' }
    if (Test-Path '.tools/gradle-home') { $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.tools/gradle-home' }
    $variant = if ($Release) { 'Release' } else { 'Debug' }
    $tasks = @("assemble$variant")
    if ($Check) { $tasks += @("test${variant}UnitTest", "lint$variant") }
    & './gradlew.bat' @tasks
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    New-Item -ItemType Directory -Force 'dist' | Out-Null
    $variantPath = $variant.ToLowerInvariant()
    Copy-Item "app/build/outputs/apk/$variantPath/app-$variantPath.apk" 'dist/ACBLTracker-android.apk' -Force
    Write-Host "APK: $PSScriptRoot/dist/ACBLTracker-android.apk"
} finally { Pop-Location }
