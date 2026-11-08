# ValancedX 1.0.0 — First Release

Die erste reguläre Release-Version von **ValancedX**, dem Android-Manager und Xposed-Modul des Open-Source-Projekts **ValacedXposed**.

## Highlights

- **Vollständig überarbeiteter Manager:** Dark-Matte-Oberfläche mit den Bereichen Start, Module, Diagnose und Info.
- **Nur Xposed:** Keine zweite Discord-App, kein APK-Patching und keine erneute Signatur von Discord.
- **Hook Health:** Letzter tatsächlicher Status, beobachtete Startversuche, erfolgreiche Bundle-Ladungen und gemeldete Fehler.
- **Recovery Mode:** Deaktiviert alle optionalen Discord-Hooks beim nächsten Start, lässt den Statuskanal aber bestehen.
- **Crash Shield:** Aktiviert Recovery nach drei nacheinander tatsächlich gemeldeten Hook-Ladefehlern. Es ist keine generische Crash-Erkennung.
- **Privacy by design:** Maximal 32 lokale Diagnose-Ereignisse, optional deaktivierbare Fehlerdetails und Historie, Kopieren und Teilen nur nach Nutzeraktion.
- **Hook-Härtung:** Der Xposed-Einstiegspunkt reagiert nur auf `com.discord` im Hauptprozess; fehlende Manager-Konfiguration führt zum sicheren Verzicht auf optionale Hooks.
- **Hermes-Guard:** Doppelte Script-Injektion pro Prozess wird verhindert, fehlende React-Native-Loader werden als eigener Fehlerstatus gemeldet.
- **Eigene Markenidentität:** Manager, Logo, Release-Texte und Dokumentation von ValacedXposed.

## Installation

1. `ValancedX-v1.0.0-release.apk` installieren.
2. In LSPosed oder einem kompatiblen Xposed-Framework das Modul **ValancedX** aktivieren.
3. Nur **com.discord** als Scope verwenden.
4. Discord vollständig schließen und neu starten.
5. Unter **Diagnose** die letzte Rückmeldung prüfen.

Diese Release-APK ist separat mit einem dauerhaften Projektschlüssel signiert, besitzt `ctrl.mietze.valacedxposed` als Paketnamen und ist **nicht** als Android-Debug-Build markiert.

## Kompatibilität und Grenzen

Die Android-Kompilierung und automatisierten Tests werden durch GitHub Actions geprüft. Die Kompatibilität mit einzelnen Discord-Versionen, LSPosed-Varianten, Root-Systemen und konkreten Samsung-/Pixel-Firmwares ist erst nach echten Gerätetests bestätigt. Ein erfolgreich gebautes Release bedeutet **nicht**, dass jeder Hook auf jeder Discord-Version funktioniert.

Der Manager verändert weder Discord-Accounts noch Zugangsdaten. Upstream-Pluginquellen sind technisch weiterhin Teil der vorhandenen Quellcodebasis. Das Projekt ist nicht offiziell mit Discord verbunden.

## Lizenz und Credits

Fork von [Valenced/valenced](https://github.com/Valenced/valenced), Lizenz **GNU GPL-3.0**. Vorhandene Lizenzvermerke und Rechte der Originalautoren bleiben bestehen.
