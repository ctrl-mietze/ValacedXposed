# Install & migrate

## Update ValancedX to Vexcord

Vexcord starts at 1.0.27 (versionCode 10027), following ValancedX 1.0.26. Its launcher name, icon, in-client branding and source repository are new. The production APK retains the original application ID `ctrl.mietze.valacedxposed`, existing Android component and Xposed entrypoint names, preference keys, bridge compatibility aliases and Discord-side storage paths.

Install the signed update over the existing app. Android verifies that its signing certificate matches. Do not clear app data or uninstall the previous package just to change the brand. If Android rejects an update because of a signing mismatch, stop and use the matching release.

After installing, fully restart Discord. Confirm the manager header reports a live connection. Check the Sources tab, enabled plugin state and theme selection. There is no automated conversion or deletion of existing repository entries.

## Fresh installation

- Use a compatible Xposed implementation and scope the module to `com.discord`.
- Install the signed production APK, not a debug build.
- Restart Discord; check Diagnose for the native and Hermes status.
- In Module, use the Vencord integration switch to reveal available ports in Plugins.
- Sources are managed individually. The [source registry](PLUGIN_SOURCES.md) lists supported discovery presets, including all ten previously used repositories.

## Recovery

If Discord fails to initialize, use Module → Recovery mode and restart the client. Existing plugin configuration is retained. The original internal logging tag names are kept for compatibility with prior diagnostic tooling.

## Signing continuity

The original project signing key is required for in-place upgrades. Until the key is provisioned directly to the new repository by its maintainer, Vexcord can use the existing ValacedXposed signing workflow on a dedicated branch containing the exact reviewed Vexcord commit. Only the signed artifact and checksums are transferred; signing credentials are not exported. The main branch of the former repository does not need to change.
