# ValacedXposed

Ein eigenständiger Open-Source-Fork mit einer **Xposed-Erweiterung für den offiziellen Discord-Client** und dem nativen Android-Manager **ValancedX**.

![ValancedX](assets/logo.svg)

## ValancedX 1.0.0

ValancedX wird als normale Android-APK installiert. Mit einem kompatiblen Xposed-/LSPosed-Framework bindet sich das Modul ausschließlich in den Hauptprozess von `com.discord` ein. Die offizielle Discord-App bleibt unverändert; es wird **keine Discord-APK gepatcht**.

### Manager

- Moderne dunkle Oberfläche mit **Start**, **Module**, **Diagnose** und **Info**.
- Konfiguration von Xposed-Hook, Hermes-Bundle und Recovery.
- Statusanzeige auf Basis der letzten tatsächlich empfangenen Meldung.
- Zähler für Startversuche, gemeldete Fehler und ausgeführte Bundle-Ladungen.
- Lokale Timeline (maximal 32 Ereignisse) mit abschaltbarer Historie.
- Diagnosebericht manuell kopieren oder teilen; Fehlerdetails optional.
- Einrichtungsassistent und direkter Discord-Schnellstart.

### Zuverlässigkeit

Der Hook wird nur in `com.discord` im Hauptprozess initialisiert. Eine fehlerhafte oder nicht erreichbare Manager-Konfiguration wird nicht als Freigabe interpretiert. Recovery und Minimalmodus verhindern das Laden aller optionalen Discord-Hooks.

Doppelte Hermes-Injektionen werden pro Prozess unterbunden. Die Registrierung des React-Native-Loaders wird separat verfolgt. Nach drei **tatsächlich gemeldeten** Boot-/Hermes-Fehlern in Folge schaltet die Manager-Konfiguration automatisch in den Recovery-Modus; das ist kein allgemeines Crash-Detection-System.

Die vorhandenen Plugin- und Hermes-ABI-Schnittstellen bleiben intern zur ursprünglichen Implementierung kompatibel.

## Installation

1. Falls die alte Alpha-Debug-App installiert ist: Dieses Xposed-Modul zuerst deaktivieren oder deinstallieren, damit keine doppelten Hooks entstehen.
2. Die eigene, signierte **ValancedX 1.0.0 Release-APK** über [GitHub Releases](https://github.com/ctrl-mietze/ValacedXposed/releases) beziehen.
3. Die APK installieren und ValancedX im Xposed-Framework aktivieren.
4. Ausschließlich `com.discord` als Scope auswählen.
5. Discord vollständig beenden und neu starten.
6. Unter Diagnose die lokale Hook-Rückmeldung prüfen.

### Kompatibilität

Builds und Unit-Tests laufen automatisch über GitHub Actions. Ein erfolgreicher Build beweist nicht die Kompatibilität mit allen Discord-/React-Native-/Hermes-Versionen. Praktische Gerätetests sind separat erforderlich. Es gibt keine Aussage über alle LSPosed-Forks oder Root-Frameworks.

## Für Entwickler

Benötigt werden Java 25, Android SDK 36 und Deno 2.9.7.

```bash
./gradlew :android:assembleDebug :android:assembleRelease :android:testDebugUnitTest :bundle:testBundle
```

Die Release-Signatur wird bei der offiziellen CI über verschlüsselte GitHub-Repository-Secrets bereitgestellt. Keystore-Dateien und Kennwörter dürfen nie dem öffentlichen Repository hinzugefügt werden.

Der Release-Build enthält nur das zur APK gehörende Hermes-Bundle. Die Debug-spezifische Development-Bundle-Funktion ist nicht im Release-Loader aktiv.

## Herkunft und Lizenz

[Valenced/valenced](https://github.com/Valenced/valenced) ist die Ausgangsbasis. Der geänderte Quellcode verbleibt unter der **GNU GPL-3.0** und enthält die weiterhin erforderlichen Lizenz- und Herkunftshinweise. Logo, Release-Texte, neue Manager-Oberfläche und projektbezogene Ergänzungen von ValacedXposed wurden eigenständig erstellt.

ValacedXposed ist kein offizielles Discord-Projekt und steht nicht in offizieller Verbindung zu Discord.

[Architektur](docs/ARCHITECTURE.md) · [Kompatibilität und Diagnose](docs/COMPATIBILITY.md) · [Release 1.0.0](docs/RELEASE_1.0.0.md)
