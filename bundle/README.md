# Hermes-Bundle

Dieser Ordner enthält den TypeScript-/Hermes-Teil von ValacedXposed. Der Code wird im Discord-Prozess vom nativen Xposed-Loader gestartet und ist kein eigenständiges Programm.

Die IDs der ursprünglichen Bridge bleiben intern kompatibel. Eine vollständige ABI-Migration erfolgt erst nach separaten Tests.

## Entwickeln

Installiere Deno in der Version aus `.dvmrc`. Anschließend:

```bash
deno install --frozen
deno run build
deno run test
```

Der Android-Build bindet das Bundle automatisch als APK-Asset ein. Der Manager selbst benötigt keine Netzwerkverbindung.

Quellcode-Lizenz: GPL-3.0, siehe `LICENSE` und die Quellenangabe im Projekt-README.
