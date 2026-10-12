# One-time local toolchain setup: Gradle 8.11.1 and the Android SDK (platform
# 35, build-tools 35, platform-tools). Idempotent; re-run safely.
$ErrorActionPreference = "Stop"
$local = $env:LOCALAPPDATA
$gradleHome = "$local\gradle-8.11.1"
$sdk = "$local\Android\Sdk"
$java = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:JAVA_HOME = $java
$env:Path = "$java\bin;$env:Path"

if (-not (Test-Path "$gradleHome\bin\gradle.bat")) {
    Write-Host "Downloading Gradle 8.11.1..."
    $zip = "$env:TEMP\gradle-8.11.1-bin.zip"
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip" -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath "$local\gradle-tmp" -Force
    Move-Item "$local\gradle-tmp\gradle-8.11.1" $gradleHome -Force
    Remove-Item "$local\gradle-tmp" -Recurse -Force
}
Write-Host "Gradle: $gradleHome"

$sdkmanager = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkmanager)) {
    Write-Host "Downloading Android command-line tools..."
    $zip = "$env:TEMP\cmdline-tools.zip"
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $zip
    New-Item -ItemType Directory -Force "$sdk\cmdline-tools" | Out-Null
    Expand-Archive -Path $zip -DestinationPath "$sdk\cmdline-tools\tmp" -Force
    Move-Item "$sdk\cmdline-tools\tmp\cmdline-tools" "$sdk\cmdline-tools\latest" -Force
    Remove-Item "$sdk\cmdline-tools\tmp" -Recurse -Force
}

# Pre-accept the SDK licenses (same hashes the Android Gradle plugin checks).
New-Item -ItemType Directory -Force "$sdk\licenses" | Out-Null
"`n24333f8a63b6825ea9c5514f83c2829b004d1fee`n8933bad161af4178b1185d1a37fbf41ea5269c55`nd56f5187479451eabf01fb78af6dfcb131a6481e" | Out-File -Encoding ascii "$sdk\licenses\android-sdk-license"
"`n84831b9409646a918e30573bab4c9c91346d8abd" | Out-File -Encoding ascii "$sdk\licenses\android-sdk-preview-license"

if (-not (Test-Path "$sdk\platforms\android-35")) {
    Write-Host "Installing SDK platform 35, build-tools 35.0.0, platform-tools..."
    & $sdkmanager --sdk_root=$sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0"
}
Write-Host "SDK: $sdk"

"sdk.dir=" + ($sdk -replace '\\', '\\') | Out-File -Encoding ascii "$PSScriptRoot\local.properties"
Write-Host "Toolchain ready. Now run: & .\android\build-local.ps1"
