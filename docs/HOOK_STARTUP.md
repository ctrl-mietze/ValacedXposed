# ValancedX: Hook-Start und Diagnose

## Die vier Stufen

1. **Framework / Scope:** Xposed muss den eigenen Module-Entrypoint `app.valenced.xposed.Main` im Discord-Hauptprozess ausführen. Fehlt das Framework oder ist `com.discord` nicht im Scope, läuft **kein Code dieses Moduls innerhalb von Discord**.
2. **Android-Lifecycle:** Der Module-Entrypoint registriert den bisherigen `attachBaseContext`-Hook plus `Application.attach(Context)` und `Instrumentation.callApplicationOnCreate(Application)`. Jeder erfolgreiche Pfad führt über denselben Context-Subscriber.
3. **Native Hooks:** Bei erfolgreicher lokaler Konfigurationsabfrage initialisiert ValancedX DexFinder, registriert Plugin-/Bridge-Tweaks und überwacht die React-Native-Skriptladefunktion.
4. **Hermes:** Das Bundle `assets://valenced.bundle` wird höchstens einmal pro Discord-Hauptprozess geladen.

## Status

| Ereignis | Aussage |
| --- | --- |
| Keine Rückmeldung | Keine verwertbare Meldung erhalten; Framework oder Scope möglicherweise inaktiv, Managerkanal möglicherweise blockiert |
| `framework_attached` | Modul hat Discord Application Context erreicht |
| `disabled` / `safe_mode` / `native_only` | Hook absichtlich begrenzt durch Manager |
| `module_path_missing` | Xposed-Entrypoint vorhanden, aber das eigene APK für die Bridge nicht gefunden |
| `script_loader_missing` | Discord-Skriptladefunktionen nicht gefunden; möglicherweise Discord-Version inkompatibel |
| `hooks_ready` | Registrierung lief durch; Bundle wird erst bei Discord-React-Native-Initialisierung ausgeführt |
| `bundle_injected` | Native Skriptladefunktion ist ohne gemeldete Exception zurückgekehrt |
| `bundle_error` | Fehler bei der Skriptladefunktion |

**Wichtig:** `bundle_injected` garantiert nicht, dass jede JavaScript-Erweiterung anschließend dauerhaft fehlerfrei läuft. Die Anzeige gibt den letzten beobachteten Zustand wieder, keinen Live-Funktionscheck.

## Grenzen

Ein Android-App-Prozess kann sich nicht ohne ein echtes Xposed-/LSPosed-Framework in einen anderen unmodifizierten App-Prozess einhängen. LSPatch ermöglicht das alternativ nur durch Patchen der Ziel-App, was **nicht** Teil des ValancedX-Projekts ist.

Diese Version repariert Android-Startpfade und meldet Fehler detaillierter, ersetzt aber kein inkompatibles oder inaktives Framework.
