# Showcase

The animation at the top of the README, `screenshot/KodeEditor.webp`, shows what KodeEditor can do. The demo app plays
it by itself, and `scripts/showcase.sh` records it, so it can be made again after a change without anybody touching the
device.

## Scenario

The demo app starts with the sample text `app/src/main/res/raw/short_sample`, read-only and without its controls. The
scenario is `playShowcase` in `app/src/main/java/de/markusressel/kodeeditor/demo/Showcase.kt`. It only uses public API
of the library, like an app would.

| Time   | What happens                                                                                                  |
|--------|---------------------------------------------------------------------------------------------------------------|
| 0 s    | The text as it is. Jane, another person, has "syntax highlighting" selected.                                  |
| 1 s    | Jane types a line behind her selection, one character every 90 ms, which fades in. Her selection shrinks into her cursor, and the text behind it slides out of the way. |
| 1.7 s  | At the same time, an agent writes a new section above "## Try it": the text is there at once but hidden, and is revealed about 30 times per second. It fades in and rises 4 dp into its place. The text below it slides down once when the hidden text is inserted, and does not move while it is typed. |
| ~8 s   | Both are done. "## Folded away" folds, then the section of the agent.                                         |
| ~10 s  | The view zooms in to 1.8 and back to 1 around its middle, and the lines wrap again while it zooms.            |
| ~13 s  | Both sections unfold again.                                                                                   |
| ~15 s  | The end. The animation starts again from the beginning.                                                       |

The library features it shows:

- several people writing at the same time: `KodeEditorState.applyRemote(..., appear = true)`, range styles for the
  hidden text, `appear` and the appearance animation of `KodeEditorAnimations`
- folding with its animation: `KodeEditorViewState.setFolded`
- smooth zooming, with line wrapping: `KodeEditorViewState.zoomTo`
- Markdown highlighting, collapsed links and images, line numbers, warnings and the cursors of other people

## Output

| What         | Value                                                                                     |
|--------------|-------------------------------------------------------------------------------------------|
| File         | `screenshot/KodeEditor.webp`, an animated WebP that loops                                 |
| Size         | 480 x 1066 px, the whole screen of the device scaled down                                 |
| Frame rate   | 15 fps, identical frames are merged                                                       |
| Duration     | about 15 s                                                                                |
| File size    | at most 2 MB, it is 1.6 MB now, small enough for the README page on a phone              |

Why animated WebP: GitHub shows it in a README like any image and plays it. It is much smaller than a GIF of the same
quality, and has more than 256 colors. An MP4 can only be embedded as a file uploaded to an issue or PR, not from the
repository.

## Device

The emulator, AVD `Resizable_Experimental` (Android 36, `google_apis/x86_64`), in its phone mode: 1080 x 2400 px,
420 dpi, portrait. Any emulator works, the script sets the display size and density and resets them at the end. Don't
record on a real phone: the script installs the demo and changes settings.

The script changes these settings of the device, they stay:

- `animator_duration_scale` to 1, the appearance animation follows it
- `immersive_mode_confirmations` to `confirmed`, otherwise Android covers the top with a hint about the hidden system
  bars

## Making it again

Needs `adb` and `ffmpeg` with `libwebp_anim` (`ffmpeg -encoders | grep webp`), and an emulator that runs.

```bash
# start the emulator without a window, or in Android Studio
~/Android/Sdk/emulator/emulator -avd Resizable_Experimental -no-window -no-audio -no-snapshot-save -no-boot-anim &

scripts/showcase.sh                 # writes screenshot/KodeEditor.webp
KEEP_RAW=1 scripts/showcase.sh      # also keeps the raw MP4 next to it
ANDROID_SERIAL=emulator-5556 scripts/showcase.sh /tmp/try.webp
```

What the script does:

1. builds and installs the debug demo app (`de.markusressel.kodeeditor.local`)
2. starts the demo with `--ez showcase true`: system bars hidden, read-only, no controls, and the scenario waits
3. waits `SETTLE` seconds (5 by default) until the system bars are gone, a slow emulator needs about 3 s
4. starts `adb shell screenrecord`, then sends `--ez showcase_play true` to the running demo, which plays the scenario
5. cuts the recording at the log lines `KodeEditorShowcase: start` and `end`, with 0.3 s before the start
6. converts it with ffmpeg: 15 fps, scaled to 480 px wide with lanczos, `libwebp_anim` quality 55, loops forever

Check the result before committing it: the first frame has no system bars, the text is readable, and the file is under
2 MB. Look at single frames with e.g. Python and Pillow (`Image.open(path).seek(n)`); ffmpeg can't read animated WebP.

## Changing it

- The scenario: `playShowcase` in `Showcase.kt`. Keep it short and only use public API.
- The text: `short_sample`, which the demo also uses without the showcase mode. The scenario finds its places by the
  headings "## Try it" and "## Folded away", and the agent's "## Written by an agent" from `AGENT_TEXT`.
- How it looks: `WIDTH`, `FPS` and `QUALITY` in `scripts/showcase.sh`. The width changes the file size a lot,
  `QUALITY` hardly does (40 and 55 differ by about 10 %).
- After a change, update the times and values in this file.
