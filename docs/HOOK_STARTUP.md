# Xposed-Start bei ValancedX

## Originales Prinzip (wiederhergestellt in 1.0.2)

Das ursprüngliche Valenced-Modul nutzt die Legacy-Xposed-API `IXposedHookZygoteInit` und `IXposedHookLoadPackage`.

- `initZygote` liefert normalerweise den APK-Modulpfad.
- `handleLoadPackage` läuft früh im Discord-Hauptprozess. Hier registriert ValancedX **sofort** sämtliche nativen Hooks in der ursprünglichen Valenced-Reihenfolge.
- `DexFinder.init` wird vor den Tweaks ausgeführt, und `DexFinder.release` danach.
- `scriptLoader` hookt `loadScriptFromAssets` beziehungsweise `loadScriptFromFile` auf Discords `JSBundleLoaderDelegate`.
- Der Injector führt das eigene `valenced.bundle` vor Discords originalem Hermes-Bundle aus.
- Die vorhandenen JS-/Native-Bridge-IDs bleiben kompatibel zum Valenced-Core.

## Was die Manager-Integration hinzufügt

Der Android-Context wird mit dem ursprünglichen Lifecycle-Support sowie ergänzenden `Application.attach`-/`Instrumentation.callApplicationOnCreate`-Fallbacks erkannt.

**Diese Callbacks lösen nicht mehr die normale Registrierung der nativen Hooks aus.** Sie lesen nur die Manager-Konfiguration und senden Diagnosestatus.

Der Manager kann die nachfolgende Hermes-/JS-Injektion ein- oder ausschalten. Recovery verhindert die JS-Erweiterungen, aber nicht die frühe, notwendige Registrierung des nativen Xposed-Cores. Das ist eine bewusste Abgrenzung zur ursprünglichen, verzögerten 1.0.0-/1.0.1-Implementierung.

## Statusinterpretation

| Ereignis | Was es bedeutet |
| --- | --- |
| Keine Rückmeldung | Noch kein Status im Manager angekommen; prüfe gegebenenfalls LSPosed-Framework-Logs |
| `framework_attached` | Ein Android-Context von Discord wurde erkannt |
| `hooks_ready` | Native Hook-Registrierung ist ausgeführt und der Scriptloader wurde gefunden |
| `script_loader_missing` | Der React-Native-Skriptlade-Hook wurde nicht registriert |
| `bundle_injected` | Hermes-Script-Ausführung hat keine Exception zurückgegeben |
| `bundle_error` | Hermes-Skriptladefehler |
| `disabled` / `native_only` / `safe_mode` | Ein Manager-Modus blockiert das Hermes-Bundle |

Ein grüner Status garantiert nicht, dass sämtliche JS-Plugins mit jeder Discord-Version kompatibel sind. Das Modul hookt nur `com.discord` im Hauptprozess, nicht `:phoenix`.

## Diagnose auf einem Gerät

Im LSPosed-Framework-Log nach `ValancedX` suchen. Bei `handleLoadPackage` sollte ein Eintrag zum **frühen Registrieren der nativen Hooks** erscheinen. Weitere Statusausgaben unterscheiden Context-Empfang, Scriptloader und Hermes-Ausführung.

Diese Release erhält den ursprünglichen Hook-Core und wechselt **nicht** auf moderne libxposed-API oder ein anderes Xposed-Framework.
