# Vexcord 1.0.30 — Native Stereo Call

- Fixes Stereo Call's exclusion of dual-mode A2DP/LE headphones and removes dependence on the React Native audio wrapper.
- Stereo Call 1.1.0 uses process-local Android AudioManager hooks: mode, SCO requests, and communication-device selection.
- Automatic mode chooses available LE communication, then A2DP; separate A2DP (phone microphone) and LE-only modes.
- Real route diagnostics, hook counters and standard-routing recovery. No hooks in system_server, telephony, other apps or Bluetooth services.
- Existing plugin catalog archives, installed plugins, data, themes and BitChord Presence preserved.
- Device testing is required: OpenSL ES call output and music quality are hardware/audio-policy dependent. Reconnect the call after updating.
