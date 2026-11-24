# ValancedX 1.0.26 — Vencord Compiler Studio & Clean Plugin Manager

## Compiler
- All 167 user plugin trees and 20 internal definitions remain inventoried at the pinned upstream revision.
- 57 Vencord entries map to 56 unique mobile plugins: 21 AST-derived generated ports, 5 bundled ports and 31 mapped Android equivalents (30 unique community packages).
- 13 new generated ports: BetterGifAltText, CopyFileContents, FixImagesQuality, GifPaste, ImageFilename, IrcColors, MessageLatency, QuickMention, ReplyTimestamp, ReverseImageSearch, ServerInfo, ViewRaw and VoiceDownload.
- Shared Metro action discovery, explicit selected-object menu actions, local renderer transforms and input insertion. Original desktop UI/settings are partially adapted; individual limitations are shown in reports.
- Seven additional Android-equivalent mappings verified against published size, SHA-256, ID, version and supported dependency pins: CustomIdle, FakeProfileThemes, ExpressionManager, FullSearchContext, TypingTweaks, ValidUser, ViewIcons.
- Runtime status separates binding from a rendered menu/row. New ports stay disabled by default. No blanket activation, arbitrary desktop evaluation, or permission bypass.
- 110 unmatched entries retained with concrete blockers, including 13 platform-bound entries and native Android voice messages.

## UI
- Vencord integration switch and Plugin Lab under Module; previous page selection migrates automatically.
- Normal Plugins tabs remain Installiert / Entdecken / Quellen / Legacy.
- Vencord integration makes prepared mobile targets available in the ordinary installed/discovery list. Switching it off does not disable or uninstall existing plugins.
- Compact plugin rows, switches, details menu, Vencord and active filters, lazy pagination and visible analysis progress.
- Duplicate repository candidates are grouped by plugin ID, but all repository/version choices remain accessible; exact verified artifact pins are required for translator activation.
- No sources, installed plugins, themes or plugin settings are removed. Existing package ID, signing key and live connection remain unchanged.

## Verification
- 1.0.25 was installed on the physical S25 Ultra. The structural message resolver bound send/edit, including the TextReplace and SendTimestamps send hooks.
- New compiler operations and lifecycle behavior are tested with synthetic fixtures. Actual UI/feature compatibility of each new port on Discord remains to be verified.
