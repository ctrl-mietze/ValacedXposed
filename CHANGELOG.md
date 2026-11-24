# Changelog

## 1.0.30 — Native Stereo Call

- Stereo Call 1.1.0 moves routing control to process-local Android AudioManager hooks, with Automatic / A2DP phone microphone / LE modes.
- Handles dual-mode headphones instead of excluding them; uses actual communication-device availability and preserves normal routing when unavailable.
- Restores standard routing on disable or failed LE selection. Exposes hook counts and actual communication device in settings.
- Existing plugin sources and BitChord Presence are unchanged. Restart Discord and reconnect the voice call to test.

## 1.0.29 — First-party plugins

- Vexcord repository catalog, versioned plugin archives and SHA-256 index.
- Stereo Call with route-aware Bluetooth A2DP preference, cleanup and normal routing fallback.
- BitChord Presence: dedicated media-browser bridge, listening activity, optional cover, timestamps and song link.
- Existing sources, installed plugins, compatibility IDs, and themes remain unchanged.

## 1.0.28 — Classic Motion

- Restores the original moving translucent circles from ValancedX 1.0.18 for all thirteen Motion themes, in addition to the existing image and ambient layers.
- Keeps original sizes, positions, motion paths, colors for the initial three presets and 12.4-second cycle.
- Respects reduced motion, background opacity and foreground lifecycle. No changes to the selected theme, plugins, source settings or data paths.

## 1.0.27 — Vexcord

- Standalone GitHub repository with preserved upstream history.
- Vexcord launcher identity, icon, manager branding and in-client settings labels.
- New Vexcord SDK v1 and compatibility aliases; existing Valenced plugin API retained.
- All previous compiler adapters, sources, theme assets and configuration paths preserved.
- Full source registry, SDK documentation, developer example and tests.
- Android production package and signing continuity retained for migration from ValancedX.

## Previous releases

The complete earlier release documentation is preserved under [docs](docs/), including [1.0.26](docs/RELEASE_1.0.26.md), [1.0.25](docs/RELEASE_1.0.25.md) and the initial project history.
