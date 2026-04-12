# UV Index App

Eine Android-App zur Überwachung des UV-Index am aktuellen Standort – mit Widgets, täglichen Benachrichtigungen und intelligentem Caching.

## Features

**UV-Index Anzeige**
- Aktueller UV-Index und Temperatur mit Farbcodierung nach Stärke
- Prognose für die nächsten Stunden
- Tagesmaximum mit Uhrzeit des Höchstwerts

**Warnungen & Benachrichtigungen**
- Tägliche Benachrichtigung um 06:30 Uhr (nur wenn UV-Maximum ≥ 4)
- Stündliche Warnungen bei hohem UV-Index
- Warnung bei UV-Übergang von moderat auf hoch

**Homescreen-Widgets**
- 1×1 Widget: Tages-Maximum UV-Index
- 4×1 Widget: Stündlicher Verlauf mit UV-Werten
- Automatische Aktualisierung alle 15 Minuten

**Standort & Caching**
- Ungefährer Standort (COARSE_LOCATION) für Akku-Schonung
- Wetterdaten-Cache (3 Stunden), Update nur bei Standortwechsel > 20 km
- Offline-Modus mit gecachten Daten

## Technologie-Stack

| Bereich | Technologie |
|---|---|
| Architektur | MVVM |
| UI | Jetpack Compose + Material Design 3 |
| API | Open-Meteo (kostenlos, kein API-Key) |
| Netzwerk | Retrofit + OkHttp |
| Serialisierung | Kotlinx Serialization |
| Lokaler Speicher | DataStore |
| Standort | Google Play Services Location |
| Hintergrund-Tasks | WorkManager |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |

## Projekt-Struktur

```
app/
├── data/
│   ├── api/              # Retrofit API Services
│   ├── local/            # DataStore für Caching
│   ├── location/         # Location Services
│   ├── model/            # Data Models
│   └── repository/       # Repository Pattern
├── ui/
│   ├── screen/           # Compose UI Screens
│   ├── theme/            # Material Design Theme
│   └── viewmodel/        # ViewModels
├── widget/               # Homescreen-Widgets + WorkManager Scheduler
└── worker/               # WorkManager Workers (UV-Check, Notifications)
```

## Installation

1. Projekt in Android Studio öffnen
2. Gradle Sync durchführen
3. App auf Gerät oder Emulator installieren
4. Standortberechtigung beim ersten Start gewähren

## Berechtigungen

| Permission | Zweck |
|---|---|
| `INTERNET` | API-Anfragen |
| `ACCESS_COARSE_LOCATION` | Ungefährer Standort |
| `POST_NOTIFICATIONS` | Tägliche Benachrichtigungen (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Worker nach Neustart wiederherstellen |

## API

Die App nutzt die kostenlose [Open-Meteo API](https://open-meteo.com/):
- Keine Registrierung oder API-Key erforderlich
- Stündliche UV-Index- und Temperaturprognosen
- Automatische Zeitzone

Endpunkt: `https://api.open-meteo.com/v1/forecast`

## Entwicklung

Diese App wurde mit Unterstützung von [Claude Code](https://claude.ai/code) von Anthropic entwickelt.
