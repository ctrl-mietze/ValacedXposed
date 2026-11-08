# ValacedXposed

Eigenständiger Android-Xposed-Fork auf Basis von **Valenced**, mit der Manager-App **ValancedX**.

![ValancedX](assets/logo.svg)

## ValancedX 1.0.3 – Hermes- und Design-Kompatibilität

**Neu in 1.0.3:** Versionsabhängige Metro-Suche nach Discords Design-Modul besitzt jetzt einen strukturellen Fallback. UI-Dialoge und Badge-Stile werden erst beim tatsächlichen Einsatz importiert statt früh beim App-Start. Hermes-Laufzeitfehler aus dem bestehenden Error-Dialog erscheinen außerdem in der lokalen ValancedX-Diagnose. Valenceds ursprünglicher Xposed-Bootstrap bleibt unverändert erhalten.

### Grundlage: Xposed-Start aus 1.0.2

ValancedX erweitert die **offizielle Discord-App** über ein kompatibles Xposed-/LSPosed-Framework. Es gibt keinen Discord-APK-Patcher und keine zweite Discord-App.

Die native Architektur basiert weiterhin auf [Valenced/valenced](https://github.com/Valenced/valenced). Der entscheidende Startpfad wird **nicht** durch einen eigenen Application-Callback ersetzt: `handleLoadPackage` initialisiert DexFinder, registriert die nativen Tweaks in der ursprünglichen Reihenfolge und gibt DexFinder danach wieder frei. Das geschieht **vor** Discords Application-Start und vor dem React-Native-/Hermes-Bundle.

### Was der Manager kann

- Eigene dunkle Oberfläche mit Start, Module, Diagnose und Info.
- Steuerung der Hermes-/JavaScript-Injektion sowie Recovery, die das Hermes-Bundle verhindert, während der native Xposed-Bootstrap registriert bleibt.
- Lokale Hook-Ereignisse mit Zeitstempel, Zähler und bis zu 32 Verlaufseinträgen.
- Fehlerdetails optional, Diagnose manuell kopieren/teilen, ohne eigenes Telemetrie-Backend.
- Klassischer Xposed-Entrypoint, kompatible Bridge-IDs und interner Plugin-/Theme-Core.
- Kein alternatives Hook-Framework, keine manipulierte Discord-Installation.

### Warum 1.0.2?

Die erste eigene Manager-Integration hatte den nativen Bootstrap bis zum Android-`Application.attach` verzögert. Das ist für früh registrierte React-Native-Hooks ungünstig. **1.0.2 stellt Valenceds frühe Initialisierung wieder her.** Die beiden Android-Lifecycle-Fallbacks dienen nur noch dazu, den App-Context für Diagnose und Manager-Einstellungen zu bekommen.

Die Manager-Konfiguration wird **nach** der frühen Hook-Registrierung abgefragt. Sie schaltet die Hermes-Injektion unmittelbar vor Discords Skriptladevorgang frei oder aus. Ist die Manager-IPC vorübergehend nicht erreichbar, wird die explizite LSPosed-Scope-Aktivierung als Standard verwendet und eine Warnung protokolliert.

### Installation und Updates

1. Die [aktuelle signierte APK](https://github.com/ctrl-mietze/ValacedXposed/releases/latest) installieren. 1.0.2 aktualisiert das vorhandene 1.0.1 ohne Signaturwechsel.
2. ValancedX im vorhandenen Xposed-/LSPosed-Modulmanager aktivieren und ausschließlich `com.discord` in den Scope nehmen.
3. Discord vollständig schließen und neu starten.
4. In ValancedX unter **Diagnose** die letzte Meldung ansehen. Relevante Stufen: Xposed-Einstieg, native Hooks registriert und Hermes-Bundle geladen.

Bei einer noch installierten **0.1.0-Alpha-Debug-App** deren separates Modul zuvor deaktivieren, damit es nicht zu doppelten Hooks kommt.

Das Projekt verwendet weiterhin die **Legacy-Xposed-API** (XposedBridge, `assets/xposed_init`), passend zur ursprünglichen Valenced-Implementierung. Ein Umbau auf die moderne libxposed-API wäre ein eigenes Projekt und ist nicht Bestandteil dieses Updates.

### Tests und Grenzen

Automatisiert werden der Android-Build, die Unit-Tests und der Hermes-Bundle-Build geprüft. Ein grüner CI-Build beweist **nicht**, dass eine konkrete Discord-/LSPosed-Version erfolgreich injiziert. Prüfe nach einem Update den tatsächlichen Status im Discord-Prozess beziehungsweise die Framework-Logs.

Das Modul verändert keine Discord-Zugangsdaten. Es ist kein offizielles Discord-Produkt.

## Entwickeln

Java 25, Android SDK 36, Deno 2.9.7:

```bash
./gradlew :android:assembleDebug :android:testDebugUnitTest :bundle:testBundle
```

Release-Builds werden getrennt mit dem bestehenden Projektschlüssel signiert, der nicht im Repository gespeichert ist.

## Lizenz und Herkunft

Fork von [Valenced/valenced](https://github.com/Valenced/valenced), **GNU GPL-3.0**. Die ursprünglichen Lizenztexte und Credits bleiben erhalten. Branding, Manager-UI und Ergänzungen stammen aus ValacedXposed.

[Android-Hook-Start](docs/HOOK_STARTUP.md) · [Version 1.0.3](docs/RELEASE_1.0.3.md) · [Releases](https://github.com/ctrl-mietze/ValacedXposed/releases)
