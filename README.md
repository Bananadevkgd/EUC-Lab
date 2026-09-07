# EUC Lab — prototype v0.0.1

Working Android prototype for a modern EUC ride companion. The first hardware target is Veteran Sherman L.

## What is already implemented

- Jetpack Compose dashboard with demo/live modes.
- BLE scanning and connection.
- Veteran/LeaperKim GATT profile: service `FFE0`, notify/write characteristic `FFE1`.
- Veteran frame reassembly by `DC 5A 5C` magic and CRC32 validation for long frames.
- Base telemetry: speed, voltage, phase current, MOSFET temperature, pitch, PWM, trip/total distance, firmware raw value and charging flag.
- Raw BLE packet preview for field diagnostics.
- Foreground ride recorder that writes live telemetry to an internal CSV file.
- GitHub Actions workflow that builds a debug APK.

## Bench test on Sherman L

1. Turn the wheel on and keep the vendor app / WheelLog disconnected so only EUC Lab owns the BLE link.
2. Install and launch EUC Lab.
3. Tap `DEMO` to switch to live mode.
4. Tap `SCAN / CONNECT` and grant Bluetooth permissions.
5. Pick the wheel from the nearby BLE list. Likely EUC candidates are sorted first.
6. Expected link status: `Veteran stream armed · waiting for DC 5A 5C` and then `LIVE TELEMETRY`.
7. With the wheel safely stationary, compare voltage, temperature and pitch with a known-good app.
8. Lift-spin testing should only be done safely and with the wheel restrained. No control commands are implemented in this build.
9. If telemetry does not appear, capture a screenshot of the link status and the `RAW` hex line. That is enough to diagnose the next parser/connection change.

## Important limitations of v0.0.1

- This is a bench prototype, not a safety device.
- SOS/crash detection is NOT implemented yet.
- GPS is NOT implemented yet.
- Battery percentage and Smart-BMS cell UI are NOT implemented yet.
- The recorder currently stores wheel telemetry only; BLE lifetime across aggressive Android process/background management still needs real-device testing.
- No commands are sent to the wheel. This build is read-only.

## Build

Current toolchain baseline:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Kotlin 2.3.21
- Compose BOM 2026.08.00
- compileSdk 37 / targetSdk 36
- Java 17

### GitHub Actions

Push the project to a GitHub repository. The included workflow builds `app-debug.apk` and uploads it as the `euc-lab-debug-apk` artifact.

### Android Studio

Open the project in Android Studio Quail 4 or newer. If Android Studio asks for a Gradle distribution, select Gradle 9.6.0. Install Android SDK Platform 37 and Build Tools 36.0.0, sync, then build the `debug` variant.

## Next milestones

1. Real Sherman L BLE capture and parser correction if needed.
2. Move BLE lifetime into a dedicated connection foreground service.
3. GPS + local ride database.
4. Smart-BMS pages and battery model.
5. Crash detector using phone IMU + wheel telemetry + GPS.
6. SOS countdown and Telegram bot delivery with coordinates/address.
7. Map with route coloring by PWM/speed/current.
8. AI ride analysis.
