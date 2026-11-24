# Vexcord 1.0.32 — Audio & Artwork

- Stereo Call 1.2.0: exact Discord setCommunicationModeOn(boolean) lifecycle observed, native AudioManager routes, per-track Android preferred-device requests for Java voice playback and microphone, actual-route verification and bounded fallback.
- LE is preferred automatically when a compatible communication device is available. A2DP/phone-mic mode is explicit; if split routing cannot be verified it returns to LE/system. The current tested Discord APK has an OpenSL ES voice player; Java track hooks cannot alone redirect that native player.
- BitChord Presence 1.2.0: recognizes current MediaItem ID, additional artwork URI candidates, safe YouTube thumbnail fallback, actual Android ApplicationAssetUtils/REST lookup, bounded retries and stale-track checks.
- Status in plugin information and existing plugin settings. Original plugin source, archive versions, settings, themes and package signature preserved.
- Tests use simulated activity/asset callbacks; actual same-headphone playback and remote album cover remain to be tested after installation. No active call was disconnected during development.
