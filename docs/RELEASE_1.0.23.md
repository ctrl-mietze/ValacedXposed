# ValancedX 1.0.23 — Vencord Compiler

- Complete pinned Vencord AST inventory: 167 user plugins, 20 internal API/core definitions, 425 files, 308 parsed code files. Per-plugin source hashes, imports, dependencies, patches, settings and blockers.
- Two generated mobile implementations from supported source patterns: DontRoundMyTimestamps (reversible Moment rounding) and Unindent (bounded codeblock display and send/edit transformations). Five existing native ports retained.
- 24 existing Mobile equivalents: identified by feature/metadata, verified package size, SHA-256, ID and version. Explicit confirmation before native equivalent activation; standard dependency resolver with two verified library pins and no silent replacement of existing installations. Native equivalents are not generated Desktop translations.
- Full-source-tree verification and per-plugin isolated static scan; optional sequential batch scan; offline source inventory and filterable Mobile/manual catalog.
- 136 unsupported plugins retain their concrete blockers. No arbitrary Desktop JS evaluation or universal Webpack-to-Metro conversion.
- Runtime target binding is checked/logged by generated adapters; actual compatibility with the installed Discord build must be tested.
- Reproducible compiler in tools/vencord-compiler, matrix in docs/VENCORD_TRANSLATION_MATRIX.md. Original Vencord revision/credits preserved.
- Existing package ID, signing key, themes and plugin sources retained.
