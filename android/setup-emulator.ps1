# One-time: installs the emulator and an Android 35 system image, creates the
# "spent" AVD. Re-run safely.
$ErrorActionPreference = "Stop"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$sdkmanager = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"
$avdmanager = "$sdk\cmdline-tools\latest\bin\avdmanager.bat"
$image = "system-images;android-35;google_apis;x86_64"

if (-not (Test-Path "$sdk\emulator\emulator.exe")) { & $sdkmanager --sdk_root=$sdk "emulator" }
if (-not (Test-Path "$sdk\system-images\android-35\google_apis\x86_64")) { & $sdkmanager --sdk_root=$sdk $image }
if (-not (Test-Path "$env:USERPROFILE\.android\avd\spent.avd")) {
    "no" | & $avdmanager create avd --name spent --package $image --device "pixel_6a" --force
}
Write-Host "Emulator ready. Start with: & .\android\run-emulator.ps1"
