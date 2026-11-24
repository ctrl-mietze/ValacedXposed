# ValancedX 1.0.13 — Theme bridge startup and response fix

- Native boot starts the combined internal/external graph in dependency order, including installed Theme API replacements.
- Manager theme bridge no longer requires Discord's optional settings UI to load first.
- Dedicated direct native-to-JS wakeup, independently correlated completion, and explicit 6-second snapshot timeout, replacing the background JS polling timer.
- Optional in-Discord plugin alert and registration failures no longer abort the essential plugin API startup.
- Actual live engine errors shown in the Theme Studio, with stale catalog preserved.
- Existing Android HTTPS transport, Veyra UI, package ID and signing key retained.
