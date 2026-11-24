# ValancedX 1.0.24 — Message Compiler and Runtime Diagnostics

- Three additional AST-derived mobile IR programs: TextReplace (local rules and bounded regex), SendTimestamps (local time shortcuts and timestamp copy tool), SilentMessageToggle (explicit chat button and opt-in send prefix).
- Five generated ports, five existing ports, 24 verified mobile equivalents; 34 mapped of 167. 133 still require further porting.
- Shared reversible Messages.sendMessage hooks and native React Native settings, per-plugin local storage, no automatic message sending.
- Actual hook status (waiting, partial, bound, stopped) in the manager. Activation alone is not presented as runtime proof.
- Original Vencord settings and desktop UI are only partially translated; source reports describe limitations. Runtime tests in actual Discord still required.
- Existing themes, sources, package and signing retained.
