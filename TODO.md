# TODO

Ideas for KodeEditor. Sizes are rough: S is about a day, M a few days, L a week or more.

## Ideas from sora-editor

From a look at [sora-editor](https://github.com/Rosemoe/sora-editor) (2026-11-23), a View based Android code editor.
It is licensed under LGPL-2.1, so take ideas only, no code. The features were seen in its README and file names, not
in its code. "App" is whether the MkDocsEditor Android app would use it.

### Likely useful

- [ ] **Search and replace**: highlight all matches, go to the next and previous one, replace one or all. The matches
  can be drawn with range styles, it needs an API to search and to scroll to a match. M, app: yes. An API proposal goes to
  Markus before it is built
- [ ] **Sticky headings**: the headings of the section at the top of the view stay there while scrolling. The fold
  providers already know the sections. M, app: yes, for markdown headings
- [ ] **Symbol bar above the keyboard**: a row of symbols that are hard to reach on a soft keyboard, like `#`, `*`,
  `` ` ``, `[]()` and `-`. Could be a composable of the app that calls `replaceSelection`. S, app: yes
- [ ] **Magnifier** while a selection handle is dragged, with `Modifier.magnifier`. S, app: yes

### Maybe

- [ ] **Auto-completion popup** with an API for providers, e.g. links to the other pages of a MkDocs site. L, app: maybe
- [ ] **Closing pairs**: typing `(`, `[`, `` ` `` or `**` adds the closing part. Must not get in the way while others
  type in the same document. S-M, app: maybe
- [ ] **Show non-printable characters**: spaces, tabs and line ends, drawn on top of the text. Two spaces at the end
  of a line are a line break in markdown. S-M, app: maybe
- [ ] **Incremental highlighting**: only highlight again what a change touched, instead of the whole text. Helps very
  large documents, and the cost of a change of the range styles, which goes through all styles of the text once. L,
  app: for large documents
- [ ] **Diagnostics on ranges**: underline a range and show a message when it is tapped. Underlines work with range
  styles already, the tooltip is missing. M, app: only with a markdown linter

### Less useful for markdown

- [ ] Highlight the matching bracket. S
- [ ] Indent guides, lines that show the indentation of blocks. S-M
- [ ] Minimap. M
- [ ] Inlay hints within a line, trailing texts of line decorations cover the end of a line. M
- [ ] Snippets with placeholders to jump through. L

## Decided against

Ideas that came up and were turned down, so they are not discussed again. From a look at
[klyx](https://github.com/klyx-dev/klyx) (2026-11-23), a GPL-3.0 editor app on top of
[SweetEditor](https://github.com/FinalScave/SweetEditor).

- **A bar of keys and symbols above the keyboard, and editing commands like indent, move line or toggle comment**:
  MkDocsEditor has its own, styled and integrated in the app. They stay in the app, not in the library.
- **Auto-completion and diagnostics from a language server**: no markdown language server or linter is planned.
- **Workspaces, tabs and saved sessions**: that is the structure of an app, MkDocsEditor has its own.
- **Deep links to open a file**: belong to the app.
- **Themes**: the highlighting rules live in KodeHighlighter, and colors belong to the app.
