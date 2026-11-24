# Vexcord Plugins

Official, opt-in extensions for Vexcord 1.0.29 and newer. This is a format-1 repository using the existing Valenced-compatible plugin distribution format.

**Repository URL:** https://raw.githubusercontent.com/ctrl-mietze/Vexcord/main/plugins/index.json

| Plugin | Purpose |
| --- | --- |
| [Stereo Call](stereo-call/) | Prefer classic Bluetooth A2DP during Discord voice sessions, while preserving ordinary routing for wired, USB and LE Audio. |
| [BitChord Presence](bitchord-presence/) | Dedicated BitChord media-session connection and a listening activity with optional artwork, album, elapsed time and public track URL. |

The app adds this source once alongside all existing sources. Install and enable each plugin through the normal manager. They are not automatically activated.

## Audio
Stereo Call 1.1.0 requires Vexcord 1.0.30+. The native backend is confined to Discord's process. Automatic prefers a genuinely available LE communication device, otherwise A2DP with phone microphone; the user can select either mode explicitly. A2DP/LE dual-mode devices are eligible. LE route acceptance and actual selected device are distinguished. No fake SCO-connected signal is emitted. The native backend returns to normal routing when an LE request fails or the selected device disappears. Reconnect Discord voice after changing the mode. OpenSL ES voice playback remains controlled by Android audio policy; the same-headset call output and media quality must be verified in a real call. Do not enable the old Bluetooth Audio Fix simultaneously.

## BitChord
The plugin connects only to `com.music.bitchord/.playback.PlaybackService` using the Android MediaBrowser service. It reads current playback state but issues no playback commands. The project's application ID is used for the Discord activity. Artwork is optional, limited to recognized public image hosts, and depends on Discord accepting the external asset. The activity uses its own socket identifier and clears itself on pause, stop or plugin shutdown. No notification listener or user-token access.

## Build
Run `python tools/vexcord-plugins/build.py` from the repository. Each versioned ZIP includes its manifest and JS entrypoint. The catalog contains exact artifact sizes, SHA-256 hashes and dependencies. Do not overwrite already released versioned ZIPs with changed content; bump the plugin version.
