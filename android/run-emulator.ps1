# Boots the "spent" AVD headless-friendly (window still shown), waits for boot.
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
Start-Process -FilePath "$sdk\emulator\emulator.exe" -ArgumentList "-avd spent -no-snapshot-load -gpu swiftshader_indirect -no-boot-anim" -WindowStyle Minimized
& "$sdk\platform-tools\adb.exe" wait-for-device
do { Start-Sleep 3; $booted = (& "$sdk\platform-tools\adb.exe" shell getprop sys.boot_completed 2>$null).Trim() } while ($booted -ne "1")
Write-Host "Emulator booted."
