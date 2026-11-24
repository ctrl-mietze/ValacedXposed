# Vexcord 1.0.29 — Audio & BitChord

- Official Vexcord plugin source under plugins/, added once without deleting or replacing other repositories.
- Stereo Call: conditional Discord communication-mode hook when a classic Bluetooth A2DP output is available. Retains standard routing for wired, USB and LE Audio; reversible fallback. Do not enable the existing Bluetooth Audio Fix simultaneously.
- BitChord Presence: reads only com.music.bitchord's exported PlaybackService through the Android media-browser interface. No playback control or notification listener; expiring connection lease.
- BitChord listening activity uses the project's application ID, current title and artist, optional album, public artwork and timing. Clears its own activity on pause/stop/disable. Optional song link only when supplied by the session.
- Both plugins have native React Native settings with a live status and are installed from the normal plugin list. They remain disabled until explicitly enabled.
- Bluetooth hardware, MediaBrowser acceptance, Discord activity publication and cover rendering require actual runtime checks; simulated tests alone are not proof.
- Preserve existing package, signing key, plugin states, sources, themes and SDK compatibility.
