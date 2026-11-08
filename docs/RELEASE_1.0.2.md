# ValancedX 1.0.2 – Original Xposed Bootstrap Repair

**Wichtigstes Update:** Der von Valenced übernommene native Loader registriert seine Hooks wieder **direkt in `IXposedHookLoadPackage.handleLoadPackage`**, statt erst nach dem Android-`Application.attach`.

## Fehlerbehebung

- **Originale Xposed-Reihenfolge wiederhergestellt:** `DexFinder.init` → alle nativen Tweaks in bewährter Valenced-Reihenfolge → `DexFinder.release`. Dazu gehören `valencedBridgeSupport`, `scriptLoader`, Versionserkennung, Caches, Pluginverwaltung und `valencedScriptLoader`.
- **Hermes-Bundle wieder zeitgerecht geladen:** Der ScriptInjector bleibt vor Discords React-Native-Skriptladefunktion registriert und läuft maximal einmal pro Prozess.
- **Manager weiter nutzbar:** Einstellungen werden über den vorhandenen lokalen ContentProvider abgerufen, sobald Discord einen Context hat; sie verschieben den nativen Bootstrap nicht länger.
- **Reparierte Schalter:** Aktivierung, Hermes und Recovery werden direkt vor dem Hermes-Script-Load ausgewertet. Die frühe native Registrierung bleibt bestehen, auch wenn die JavaScript-Injektion abgeschaltet ist.
- **IPC-Fallback:** Bei vorübergehend nicht erreichbarer Manager-IPC wird auf die explizite LSPosed-Modulfreigabe zurückgegriffen; der Fehler wird in Android-/Xposed-Logs sichtbar.
- **Lifecycle-Fallbacks:** `Application.attach` und `Instrumentation.callApplicationOnCreate` bleiben als ergänzende Context-Erkennung, nicht als Voraussetzung für den normalen Bootstrap.
- **Native ABI beibehalten:** Keine neue Bridge, keine veränderten Discord-Patches, keine gesonderte Hook-Laufzeit.
- **Signierte Aktualisierung:** `ctrl.mietze.valacedxposed`, Version 1.0.2, derselbe Release-Signaturschlüssel wie 1.0.0 und 1.0.1.

## Installation

1. Release-APK installieren. Eine vorhandene reguläre ValancedX-App kann damit aktualisiert werden; ihre lokalen Einstellungen bleiben erhalten.
2. Discord im LSPosed-Modul-Scope belassen.
3. Discord vollständig beenden und neu öffnen.
4. Prüfen, ob der Manager **Hooks zuletzt registriert** beziehungsweise **Hermes-Bundle zuletzt geladen** meldet.

Bei fehlenden Meldungen Framework-Logs auf `ValancedX` prüfen. Das Release reproduziert die ursprüngliche Architektur, ersetzt aber keinen Gerätetest auf der konkreten Discord-Version.

**Kein Anspruch auf universelle Discord-/Hermes-Kompatibilität.** CI prüft Build und Unit-Tests, nicht alle Laufzeitvarianten.

GPL-3.0 · Originalbasis: [Valenced/valenced](https://github.com/Valenced/valenced).
