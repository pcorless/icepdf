# Translations of the JavaFX viewer

The viewer's text is in `src/main/resources/org/icepdf/fx/i18n/messages.properties` (English,
UTF-8) and read through `org.icepdf.fx.i18n.Messages`.  Keys are the area or class, then what
the text is (`searchPanel.matchCase`); an action's text is keyed by its id (`document.print`,
`document.print.tooltip`, `document.print.short`).  A value with `{0}` is a `MessageFormat`
pattern: an apostrophe is written `''`, plurals use `{0,choice,1#1 page|1<{0} pages}`.

| Script | What it does |
|---|---|
| `sync_translations.py` | Fills each `messages_<locale>.properties` from the Swing viewer's `MessageBundle` where the English matches. The first section of a locale file is rewritten; the section after `# ---- translated for the JavaFX viewer` (hand translations, drafts) is kept. |
| `check_keys.py` | Every key the code reads exists; `Messages.format` patterns escape apostrophes; how much of each locale is translated. |
| `find_strings.py` | Lists literals in the code that look like text a user reads but don't go through `Messages` yet. |

Adding text: put the key in `messages.properties`, read it with `Messages.get` / `Messages.format`,
run `check_keys.py` and `sync_translations.py`.  `MessagesTest` checks the translations keep the
English keys and arguments.  To look at a locale:
`./gradlew :viewer:viewer-fx:shellSmoke -PsmokeArgs="out;file.pdf" -PsmokeJvmArgs="-Duser.language=fr"`
(`;-Duser.country=CN` for Chinese).
