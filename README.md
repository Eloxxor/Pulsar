# Pulsar

Pulsar ist eine lokale Android-App für Erholung, Schlaf und Trainingsbelastung. Sie liest Gesundheitsdaten über Health Connect, berechnet daraus verständliche Tageswerte und stellt sie in einem dunklen, neonbetonten Dashboard dar.

> Pulsar liefert Wellness- und Trainingskontext. Die angezeigten Werte sind keine medizinische Diagnose.

## Funktionen

- täglicher Erholungswert auf Basis persönlicher Ausgangswerte
- Belastungs-, Schlaf-, HRV- und Ruhepuls-Auswertung
- globale Tagesauswahl für Home, Schlaf und Aktivität
- nachvollziehbare Vital-Age-Schätzung mit gewichteten Einflussfaktoren
- Journal mit Auswertung möglicher Zusammenhänge zur Erholung
- Drei-Ringe-Widget für Erholung, Schlaf und Belastung
- optionale morgendliche Zusammenfassung als Benachrichtigung
- CSV-Export der lokal gespeicherten Tageswerte
- deutsche und englische Benutzeroberfläche

## Datenschutz

Pulsar benötigt kein Benutzerkonto und keine eigene Cloud. Gesundheitsdaten werden über Health Connect gelesen und auf dem Gerät verarbeitet. Exporte werden nur auf ausdrückliche Aktion des Nutzers erstellt.

## Technischer Aufbau

- Kotlin und Jetpack Compose
- Material 3
- Health Connect
- Room
- DataStore
- WorkManager
- MVVM mit Kotlin Flows und Coroutines

## Voraussetzungen

- Android-Gerät mit Health Connect
- Android Studio mit JDK 17
- Android SDK 36

## Lokal bauen

Repository klonen und im Projektverzeichnis ausführen:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Die Debug-APK liegt anschließend unter:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Installation auf einem per ADB verbundenen Gerät:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Projektstatus

Pulsar befindet sich in aktiver Entwicklung. Scoring und Vital Age sind transparente, app-eigene Wellness-Schätzungen und ersetzen keine professionelle medizinische Bewertung.

## Lizenz

Dieses Repository besitzt derzeit keine Open-Source-Lizenz. Alle Rechte bleiben vorbehalten.
