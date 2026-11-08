# ValancedX 1.0.3 — Hermes Design Compatibility Repair

This is a targeted compatibility update for the error:

```text
TypeError: target is not an object
    at get (native)
    at get (Valenced:1044:50)
    at anonymous (Valenced:5247:68)
```

The trace was mapped against a **reproducible 1.0.2 release JavaScript bundle**. `Valenced:1044` is the `Reflect.get` trap in `lib/utils/src/proxy.ts`. `Valenced:5247` is an eager destructure of the Discord design barrel (`AlertModal`, `AlertActionButton`, `Text`) from `src/plugins/start/settings.plugins/components/PluginClearDataConfirmationAlert.tsx`.

The `Design` lazy proxy had no resolved object at this instant, so Hermes raised its native `Reflect.get` TypeError. The legacy dependency fingerprint used to find `design/native.tsx` was tightly coupled to one Discord Metro dependency map.

## Repairs

- **Preserved the original Valenced design lookup** as the preferred, fast path.
- **Added a structural fallback**: when that version-specific dependency fingerprint misses, search already initialized Metro modules for the design barrel's characteristic exports (`TableRow`, `Button`, `AlertModal`, `AlertActionButton`, `Stack`, `Text`), without blindly requiring arbitrary uninitialized modules.
- **Deferred settings alert imports:** the plugin manager no longer eagerly evaluates many design-dependent dialog components on Discord startup. Dialog code is loaded when its action is actually invoked.
- **Deferred state and install alerts:** plugin-install, update-required and failed-start dialog modules are also loaded only when needed, retaining the event handlers and UI behavior.
- **Deferred user-badge styles and dialog components:** the badge plugin keeps its normal hooks but avoids forcing the design barrel during plugin registration.
- **Local JS runtime diagnostics:** the existing native `valenced.alertError` dialog also records `js_runtime_error` in the manager's local, bounded event log. No new network telemetry.
- **Unchanged legacy Xposed bootstrap:** version 1.0.2's restored `handleLoadPackage` flow, native bridge, bundled Hermes injection and plugin APIs are preserved.
- **Same release signing key:** compatible in-place update for `ctrl.mietze.valacedxposed`.

## Install

Update from the existing ValancedX 1.0.2 release APK, retain the Discord scope in LSPosed, and fully restart Discord. There is no need to uninstall Discord or clear its account data.

The user-reported error demonstrates that the native loader reached Hermes; it is **not** evidence of a missing LSPosed installation.

## Verification limits

CI compiles and tests Android and the bundled Hermes JavaScript. Exact line mapping of the 1.0.2 trace is verified. Whether the updated `Design` search successfully matches a particular future Discord version still requires a real on-device runtime test; do not interpret a successful build as universal Discord compatibility.

Project: ValacedXposed, fork of Valenced. License: GPL-3.0; upstream notices retained.
