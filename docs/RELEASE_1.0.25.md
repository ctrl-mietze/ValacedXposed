# ValancedX 1.0.25 — Mobile ABI and Context Compiler

- Fixes the shared message action resolver using the native sendMessage/receiveMessage export shape rather than a desktop-style module name. Handles native message records and older text arguments without replacing unrelated fields. The previous 1.0.24 device test confirmed the Moment and ChatRows bindings; Send/Edit were not yet bound.
- Three new AST-derived programs: CopyUserURLs (user-profile sheet clipboard action), CopyEmojiMarkdown (custom-emoji sheet action), and NoReplyMention (initial reply mention policy and local user/role lists).
- The installed Discord build contains the expected reply event and action-sheet identifiers; menu rendering and actual reply payload handling still need device verification.
- Reversible per-sheet wrappers, bounded React tree inspection, explicit user clipboard gesture, no automatic sends. A missing or incompatible menu is left unchanged.
- Compiler diagnostics distinguish registered hooks from actual matching reply events and rendered menu rows.
- 8 generated ports, 5 existing bundled ports, 24 verified mobile equivalents: 37 of 167 mapped, 130 manual.
- Existing settings, themes, sources, package and signing key retained.
