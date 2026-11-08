# ValancedX 1.0.1 – Discord Hook Repair

Diese Version behebt mehrere stille Abbruchstellen der Xposed-Initialisierung und verbessert die Diagnose, wenn Discord normal startet, aber kein ValancedX-Modul lädt.

## Änderungen

- **Redundanter Hook-Start:** Neben dem Discord-spezifischen `attachBaseContext`-Hook werden nun `Application.attach` und `Instrumentation.callApplicationOnCreate` als unabhängige Rückfallpunkte überwacht.
- **Keine stille Ablehnung bei fehlendem Zygote-Pfad:** Wenn ein kompatibles Xposed-Framework keinen Modulpfad über `initZygote` liefert, versucht ValancedX die installierte Modul-APK nach dem Android-Application-Attach aufzulösen. Schlägt das fehl, gibt es einen eigenen Diagnosezustand statt eines stillen Exits.
- **Verifizierbare Stufen:** `framework_attached`, `module_path_missing`, `script_loader_missing`, `hooks_ready`, `bundle_injected`. So lassen sich Framework-Injektion, Android-Hook und Hermes-Bundle auseinanderhalten.
- **Bessere Fehleranalyse:** Die Manager-Oberfläche erklärt explizit, dass bei einer ausbleibenden Rückmeldung zunächst die tatsächliche Xposed-/LSPosed-Aktivierung und der Scope `com.discord` zu prüfen sind.
- **Kompatibilität:** Die Module bleiben weiterhin ausschließlich auf den offiziellen Discord-Hauptprozess begrenzt. Der Client wird nicht gepatcht.
- **Release-Signierung:** Unveränderter Projektschlüssel, kompatibel mit der bereits veröffentlichten 1.0.0-App. Das bisherige Upgrade kann damit die App und lokale Einstellungen behalten.

## Wenn Discord weiterhin ohne Mod lädt

1. Prüfe, dass ein auf deiner Android-Version funktionierendes **systemweites Xposed-/LSPosed-Framework** wirklich aktiv ist. Eine bloß installierte LSPatch-App führt **keine** Hooks in einer unveränderten Discord-APK aus.
2. Öffne den Xposed-Manager und aktiviere **ValancedX**. Als App-Scope nur `com.discord` auswählen.
3. Deaktiviere gegebenenfalls das alte **ValancedX Alpha-Debugmodul**, damit nicht zwei verschiedene Module denselben Prozess hooken.
4. Discord vollständig beenden und neu starten; im Manager **Diagnose** öffnen. Wenn „Xposed-Einstieg erkannt“ erscheint, hat der Hook den Prozess erreicht. „Hooks registriert“ bedeutet nicht zwangsläufig, dass das Hermes-Bundle bereits ausgeführt wurde.
5. Falls keine Rückmeldung erscheint, ist die Framework-/Scope-Injektion weiterhin unbestätigt; weitere Änderungen am Hermes-Bundle allein lösen das nicht.

## Teststatus

Android-CI, Build und Unit-Tests prüfen den Code. Sie können die Xposed-Injektion auf dem konkreten Discord-/Android-/Root-Framework nicht simulieren. Diese Kompatibilität muss auf einem Gerät getestet werden.

Lizenz: GPL-3.0. Basis: [Valenced/valenced](https://github.com/Valenced/valenced).
