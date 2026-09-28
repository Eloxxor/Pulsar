# Pulsar

Pulsar is a local Android app for recovery, sleep, and training load. It reads health data through Health Connect, turns it into clear daily insights, and presents everything in a dark, neon-accented dashboard.

> Pulsar provides wellness and training context. Its insights are not a medical diagnosis.

## Features

- daily recovery score based on personal baselines
- training load, sleep, HRV, and resting heart rate insights
- shared date selection across Home, Sleep, and Activity
- transparent Vital Age estimate with weighted contributing factors
- journal with correlations between habits and recovery
- three-ring widget for recovery, sleep, and training load
- optional morning summary notification
- CSV export of locally stored daily data
- English and German user interface

## Privacy

Pulsar requires no user account and uses no proprietary cloud service. Health data is read through Health Connect and processed on the device. Exports are created only when explicitly requested by the user.

## Tech stack

- Kotlin and Jetpack Compose
- Material 3
- Health Connect
- Room
- DataStore
- WorkManager
- MVVM with Kotlin Flows and Coroutines

## Requirements

- Android device with Health Connect
- Android Studio with JDK 17
- Android SDK 36

## Build locally

Clone the repository and run the following commands from the project directory:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug APK will be available at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on an ADB-connected device with:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Project status

Pulsar is under active development. Its scoring and Vital Age features are transparent, app-specific wellness estimates and do not replace professional medical advice.

## License

This repository currently has no open-source license. All rights reserved.
