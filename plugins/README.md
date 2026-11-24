# Vexcord Plugins

Official, opt-in extensions for Vexcord 1.0.29 and newer. This is a format-1 repository using the existing Valenced-compatible plugin distribution format.

**Repository URL:** https://raw.githubusercontent.com/ctrl-mietze/Vexcord/main/plugins/index.json

| Plugin | Purpose |
| --- | --- |
| [Stereo Call](stereo-call/) | Prefer classic Bluetooth A2DP during Discord voice sessions, while preserving ordinary routing for wired, USB and LE Audio. |
| [BitChord Presence](bitchord-presence/) | Dedicated BitChord media-session connection and a listening activity with optional artwork, album, elapsed time and public track URL. |

The app adds this source once alongside all existing sources. Install and enable each plugin through the normal manager. They are not automatically activated.

## Audio
Stereo Call adapts the existing Android communication-mode hook approach (credit: NoReplyUI5, Bluetooth Audio Fix) with Vexcord route capability checks and an explicit fallback. Do not run both plugins simultaneously. A2DP availability is not proof of the active route; Classic Bluetooth cannot guarantee simultaneous full-quality stereo output and headset microphone input. Wired/USB and LE Audio keep ordinary Android routing. If voice audio fails, restore standard routing and reconnect the call.

## BitChord
The plugin connects only to `com.music.bitchord/.playback.PlaybackService` using the Android MediaBrowser service. It reads current playback state but issues no playback commands. The project's application ID is used for the Discord activity. Artwork is optional, limited to recognized public image hosts, and depends on Discord accepting the external asset. The activity uses its own socket identifier and clears itself on pause, stop or plugin shutdown. No notification listener or user-token access.

## Build
Run `python tools/vexcord-plugins/build.py` from the repository. Each versioned ZIP includes its manifest and JS entrypoint. The catalog contains exact artifact sizes, SHA-256 hashes and dependencies. Do not overwrite already released versioned ZIPs with changed content; bump the plugin version.
