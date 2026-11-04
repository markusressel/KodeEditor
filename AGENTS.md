# AGENTS.md

KodeEditor is an Android code editor library with syntax highlighting (via [KodeHighlighter](https://github.com/markusressel/KodeHighlighter)), line numbers and pinch to zoom. It is published through JitPack.

## Layout

- `library`: the published library, package `de.markusressel.kodeeditor.library`.
  - `compose`: the Compose editor (`KodeEditor`, `KodeEditorState`, `KodeEditorLayout`, `ZoomLayout`, `EditorGutter`, `EditorFolds`, `KodeEditorLineDecoration`, `KodeEditorColors`/`KodeEditorDefaults`). There is no text field: `KodeEditorState` holds the only copy of the text, `EditorInputConnection` connects the keyboard to it, and only visible lines are measured and drawn.
- `app`: demo app, package `de.markusressel.kodeeditor.demo`. `ComposeMainActivity` (launcher) shows the editor.
- `build-logic`: Gradle convention plugins (`kodeeditor.android.library`, `...compose`, `...publishing`, ...). Versions live in `gradle/libs.versions.toml`.

## Build

- CI (`.github/workflows/build.yml`) runs `./gradlew app:assembleDebug` and `./gradlew library:testDebugUnitTest app:testDebugUnitTest` with JDK 21. JitPack also uses JDK 21 (`.jitpack.yml`).
- On `master`, the `Publish` job releases the release APK of the demo app, built with R8 and signed with the debug key, as a pre-release `build-<n>` for Obtainium and keeps the newest 5. Library releases stay normal releases with a version tag, so they remain the latest release.
- The version code of the demo app is the CI run number, and 1 for local builds.
- The application ID of the demo app is `de.markusressel.kodeeditor` on CI and `de.markusressel.kodeeditor.local` for local builds, so both can be installed next to each other.
- Compose UI tests of the library run on the JVM with Robolectric.
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
