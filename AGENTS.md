# AGENTS.md

KodeEditor is an Android code editor library with syntax highlighting (via [KodeHighlighter](https://github.com/markusressel/KodeHighlighter)), line numbers and pinch to zoom. It is published through JitPack as `com.github.markusressel:KodeEditor`, the module `library`. Its own publication must keep the coordinates `com.github.markusressel.KodeEditor:<module>` that JitPack uses too, otherwise apps get every class twice.

## Layout

- `library`: the published library, package `de.markusressel.kodeeditor.library`.
  - `compose`: the Compose editor, `KodeEditor` and its colors (`KodeEditorColors`/`KodeEditorDefaults`). There is no text field: `KodeEditorState` holds the only copy of the text, `EditorInputConnection` connects the keyboard to it, and only visible lines are measured and drawn. Next to `KodeEditor`, the root package holds the glue between the packages: the touch state and gestures (`EditorTouch`), the view (`EditorViewController`, `KeepViewEffects`) and the resolved colors. Its packages, each with its public API and the `internal` code behind it:
    - `state`: `KodeEditorState`, `TextSplice`, the line index, undo and the timing of appearing text.
    - `view`: `KodeEditorViewState`, `ZoomLayout`, scrollbars, geometry, following the cursor.
    - `layout`: `KodeEditorLayout`, `EditorLineLayout`, rows, the drawn layers, typing runs and the scroll anchor of remote changes.
    - `animation`: `KodeEditorAnimations`/`KodeEditorAppearance`, `ReflowTransition` and `TypingMotion` (the typing rules of cursor and text in one place).
    - `reveal`: `KodeEditorReveal`. `cursor`: the local cursor, remote cursors (`KodeEditorRemoteCursor`, `RemoteCursorGlides`, `GlideCurves`), selection handles and the text menu.
    - `fold` and `fold.providers`: folding and the fold providers. `search`, `gutter` (`KodeEditorGutterColumn`, `KodeEditorLineDecoration`), `input` (keyboard, keys and key bindings, touch gestures, accessibility semantics).
- `app`: demo app, package `de.markusressel.kodeeditor.demo`. `ComposeMainActivity` (launcher) shows the editor.
- `build-logic`: Gradle convention plugins (`kodeeditor.android.library`, `...compose`, `...publishing`, ...). Versions live in `gradle/libs.versions.toml`.

## Build

- CI (`.github/workflows/build.yml`) runs `./gradlew app:assembleDebug` and `./gradlew library:testDebugUnitTest app:testDebugUnitTest` with JDK 21. JitPack also uses JDK 21 (`.jitpack.yml`).
- On `master`, the `Publish` job releases the release APK of the demo app, built with R8 and signed with the debug key, as a pre-release `build-<n>` for Obtainium and keeps the newest 5. Library releases stay normal releases with a version tag, so they remain the latest release.
- The version code of the demo app is the CI run number, and 1 for local builds.
- The application ID of the demo app is `de.markusressel.kodeeditor` on CI and `de.markusressel.kodeeditor.local` for local builds, so both can be installed next to each other. Local builds are called `KodeEditor DEV`.
- Compose UI tests of the library run on the JVM with Robolectric.
- `make build`, `make publish` (to Maven local) and `make run` exist for local use.

## Code style

- Kotlin, 4 spaces, max line length 120 (`.editorconfig`). Wildcard imports like `androidx.compose.runtime.*` are common.
- Composables: `modifier: Modifier = Modifier` first, then the required parameters, then optional parameters with defaults, trailing commas. Content slots go last.
- Public composables have a short KDoc with one `@param` line per parameter, in lower case and without a trailing period.
- Colors follow the Material pattern: an interface with `@Composable fun xColor(): State<Color>` and a `...Defaults` object with a factory function.
- Each composable file ends with a private `@Preview` function.
- New parameters of public composables need defaults, so existing consumers keep compiling.

## Shared rules

- [docs/reveal.md](docs/reveal.md) has the rules for text that arrives in chunks and the cursor at its head, with all numbers. The MkDocsEditor Android app and the web client follow them too, so change them only together with those, and keep the doc in step with `KodeEditorReveal`, `RemoteCursorGlides`, `GlideCurves`, `TypingMotion` and `RemoteLabelMotion`.
- [docs/collaboration.md](docs/collaboration.md) is the guide for apps that show what others write: the text, typists, agents, cursors and names together. Keep it in step with the API of `KodeEditorState`, `KodeEditorReveal` and `KodeEditorRemoteCursor`.

## README animation

- `screenshot/KodeEditor.webp` at the top of the README is recorded from a scripted showcase mode of the demo app. [docs/showcase.md](docs/showcase.md) describes the scenario, the device, the size budget and how to record it again with `scripts/showcase.sh`. Record it again when a change makes it outdated, and only on an emulator.

## Commits and PRs

- Commit messages are short and lower case, e.g. `fix crash`, `migrate library to material 3`.
- Dependency updates come from dependabot. Don't bump dependencies in feature PRs.
