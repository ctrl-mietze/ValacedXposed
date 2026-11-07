# ValancedX 0.1.0 – Alpha

Dies ist der erste eigenständig gebaute **Teststand von ValacedXposed**. Die App **ValancedX** ist ein Android-Xposed-Modul und Manager für den offiziellen Discord-Client.

## Enthalten

- Neuer Android-Manager mit eigenständiger Oberfläche und X-Logo.
- Xposed-Anbindung ausschließlich für den Discord-Hauptprozess.
- Ein-/Ausschalter für den nativen Hook und das integrierte Hermes-Bundle.
- Lokale letzte Hook-Rückmeldung, Zeitstempel und optionale Fehlermeldungen.
- Einrichtungsanleitung für LSPosed bzw. kompatible Frameworks.
- Einmalige Script-Injektion pro Prozess sowie Fehlerbehandlung beim Laden.
- Integrierte Plugin-/Theme-Grundlage aus dem Valenced-Quellcode.
- Kein iOS-Build, kein Discord-APK-Patcher und kein übernommener Release.

## Installation

1. `ValancedX-v0.1.0-alpha-debug.apk` installieren.
2. ValancedX im Xposed-Manager aktivieren und **nur `com.discord`** als Scope auswählen.
3. Discord vollständig beenden und neu starten.
4. In ValancedX den letzten Status prüfen.

**Wichtig:** Dies ist eine von GitHub Actions erzeugte **Debug-APK** mit `ctrl.mietze.valacedxposed.debug`, nicht die endgültig signierte Produktions-App. Unterschiedliche CI-Debug-Signaturen können bei Updates eine Neuinstallation erfordern. Die Build- und Unit-Tests waren erfolgreich; eine Garantie für alle Discord-Versionen oder alle LSPosed-Varianten besteht nicht.

## Herkunft und Lizenz

Eigenes Logo, eigene Release-Notizen und neuer Manager. Der Core ist ein Fork von [Valenced/valenced](https://github.com/Valenced/valenced) unter GPL-3.0. Die entsprechenden Lizenz- und Herkunftshinweise bleiben erhalten.
