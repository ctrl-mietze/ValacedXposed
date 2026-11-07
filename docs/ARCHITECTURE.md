# Architektur von ValacedXposed

## Module

- `android`: Android APK, native Xposed-Einbindung, ValancedX-Manager und lokaler IPC-Provider.
- `android/api`: Native Plugin-Schnittstelle der kompatiblen Quellcodebasis.
- `core`: Gemeinsamer Plugin- und Versionskern.
- `bundle`: Hermes/TypeScript-Bundle für die Laufzeit in Discord.

Die früheren iOS- und APK-Patching-Projekte sind bewusst nicht Teil dieses Forks.

## Bootfolge

1. Xposed lädt `app.valenced.xposed.Main` ausschließlich in `com.discord` (Hauptprozess).
2. Ein minimaler Lifecycle-Hook wartet auf den App-Context.
3. Die Manager-Einstellungen werden über einen auf den Discord-UID eingeschränkten ContentProvider gelesen.
4. Bei deaktiviertem Hauptschalter wird kein weiterer Hook registriert.
5. Andernfalls werden die Xposed-Tweaks isoliert registriert. DexKit-Ressourcen werden in einem `finally` freigegeben.
6. Der JS-Injector führt das integrierte Bundle höchstens einmal pro Prozess aus.
7. Der Hook sendet Status und optionale Fehlerdetails lokal an die Manager-App.

Die Bridge-IDs und die internen Paketnamen des Kernels bleiben zunächst bewusst stabil, da sie mit der vorhandenen Hermes-Bundle-ABI gekoppelt sind. Ein blindes Umbenennen würde das Bundle beschädigen.

## Sicherheitsgrenzen

Der Provider nimmt Konfigurationsabrufe nur vom eigenen UID oder dem echten `com.discord`-UID an. Statusmeldungen dürfen nur vom Discord-UID kommen. Es gibt keine externe Manager-API, kein Konto und keinen eigenen Analyse-Upload.

Wird eine fehlerhafte Kombination installiert, lässt sich die JavaScript-Injektion im Manager deaktivieren. Diese Einstellung greift erst nach einem kompletten Neustart von Discord.
