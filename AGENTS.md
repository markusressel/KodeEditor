# AGENTS.md

KodeEditor is an Android code editor library with syntax highlighting (via [KodeHighlighter](https://github.com/markusressel/KodeHighlighter)), line numbers and pinch to zoom. It is published through JitPack.

## Layout

- `library`: the published library, package `de.markusressel.kodeeditor.library`.
  - `compose`: the Compose editor (`KodeEditor`, `ZoomLayout`, `LineNumbers`, `KodeEditorColors`/`KodeEditorDefaults`). The text field itself is `KodeTextField` from KodeHighlighter.
  - `view`: the older View based editor (`CodeEditorLayout`, `CodeEditorView`, ...), configured with the `ke_*` attributes in `res/values/attributes.xml`.
- `app`: demo app, package `de.markusressel.kodeeditor.demo`. `ComposeMainActivity` shows the Compose editor, `MainActivity` (launcher) the View based one.
- `build-logic`: Gradle convention plugins (`kodeeditor.android.library`, `...compose`, `...publishing`, ...). Versions live in `gradle/libs.versions.toml`.

## Build

- CI (`.github/workflows/build.yml`) runs `./gradlew app:assembleDebug` and `./gradlew library:testDebugUnitTest` with JDK 21. JitPack also uses JDK 21 (`.jitpack.yml`).
- The library has no unit tests yet.
- `make build`, `make publish` (to Maven local) and `make run` exist for local use.

## Code style

- Kotlin, 4 spaces, max line length 120 (`.editorconfig`). Wildcard imports like `androidx.compose.runtime.*` are common.
- Composables: `modifier: Modifier = Modifier` first, then the required parameters, then optional parameters with defaults, trailing commas. Content slots go last.
- Public composables have a short KDoc with one `@param` line per parameter, in lower case and without a trailing period.
- Colors follow the Material pattern: an interface with `@Composable fun xColor(): State<Color>` and a `...Defaults` object with a factory function.
- Each composable file ends with a private `@Preview` function.
- New parameters of public composables need defaults, so existing consumers keep compiling.

## Commits and PRs

- Commit messages are short and lower case, e.g. `fix crash`, `migrate library to material 3`.
- Dependency updates come from dependabot. Don't bump dependencies in feature PRs.
