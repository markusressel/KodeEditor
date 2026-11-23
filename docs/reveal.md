# Revealing text that arrives in chunks

How KodeEditor shows text that somebody else writes in chunks, like an agent, as if it was typed: `KodeEditorReveal`
for the text, and the gliding cursor at its head. The MkDocsEditor Android app and the web client follow the same
rules with the same numbers, so an agent looks the same everywhere.

## API

```kotlin
// one per writer, the tag tells its hidden and appearing text apart from that of others
val reveal = rememberKodeEditorReveal(state, tag = "agent")

reveal.insert(position, chunk) // inserts the chunk hidden, with applyRemote
reveal.head                    // where the text is revealed, null when nothing is hidden
reveal.isRevealing             // whether text is hidden
reveal.hiddenLength            // how many characters are hidden
reveal.stop()                  // shows all hidden text at once
reveal.layout = layout         // from KodeEditor(onTextLayout), shows collapsed parts as a whole
```

`KodeEditorReveal(state, tag = "reveal", minCharsPerSecond = 60f, catchUpMs = 4000)` can also be created directly, e.g.
next to a view model, then the app runs `keepRevealing()` in a coroutine of the composition, which
`rememberKodeEditorReveal` does.

- `keepRevealing()` waits for frames with the `MonotonicFrameClock` of its coroutine, so a test can run it with a clock
  of its own.
- It can be cancelled and run again, e.g. on rotation: the hidden text, the speed and the carry stay.
- `insert` works while it does not run, the text stays hidden until it runs again or `stop()` is called. Changes that
  show the hidden text (R1) are only noticed while it runs, so call `stop()` when the editor goes away.
- `insert` and `applyRemote` can be mixed in one call on the main thread, no frame comes in between.
- The reveal keeps all range styles that are not its own. An app that sets its own range styles must keep those of the
  reveal (their tag starts with the tag of the reveal), or their text shows at once. Two reveals keep each other's.

## Rules

### R1: a queue per writer, a chunk is hidden at once

- A chunk is inserted into the text at once, and hidden in the same change, so no frame shows it. KodeEditor hides it
  with a transparent range style, so it keeps its space and the text below does not move.
- The chunks of a writer wait in a queue, in the order they arrive.
- A new chunk never shows what is still hidden. Only a change of the user and `stop()` show all of it at once.
- A chunk right behind the end of the newest hidden chunk joins it. A chunk inside hidden text joins that text. Any
  other chunk is a new entry at the end of the queue, also one right at the start of hidden text.
- Nobody types into hidden text: a change of the user anywhere, and a change of anybody else inside the hidden text or
  right at its start or end, shows all hidden text of that writer at once and ends its appearing, like `stop()`.
- Other changes move the hidden text, like range styles: text inserted in front of it moves it.
- Only a pure insert of the writer is a chunk. The new text of a replace, also one of the writer, shows at once and
  appears like the text of others (`applyRemote(appear = true)`), and finishes the reveal if it touches hidden text.

### R2: the head walks the queue

- The head is the start of the hidden text of the first entry of the queue. When that entry is all shown, the head goes
  on to the start of the next one, in the same frame if there are steps left.
- The cursor of the writer is at the head. It is a remote cursor like any other, so it glides (R5).

### R3: the speed, per frame

- Each frame reveals `rate × elapsed` characters, with `elapsed` the time since the frame before. The fraction that is
  left is carried to the next frame. The first frame after a rest counts as 16 ms, so a chunk starts to show at once.
- When a chunk arrives: `rate = max(rate, 60, hidden × 1000 / 4000)` characters per second, with `hidden` all hidden
  characters of the writer, the new chunk included. The rate never drops while text is hidden, and goes back to 0 with
  the carry when nothing is hidden anymore.
- So all text that is hidden when a chunk arrives is shown within 4 s at the most, and text that arrives at 60
  characters per second or slower is revealed at 60 characters per second, about one in each frame.
- Why the rate does not drop: with `rate = max(60, hidden / 4 s)` taken anew in each frame, the hidden text shrinks
  exponentially, and 1,000 hidden characters would take about 10 s instead of 4 s.
- A collapsed part, like a folded link or folded lines, counts as one character and shows as a whole.
- With animations off (a duration scale of 0), all hidden text shows at once.

### R4: each piece appears

- The characters revealed in a frame appear as one piece, with the appearance animation of the editor
  (`KodeEditorAppearance`), if it has one.
- A new chunk does not end the pieces that are still appearing. Only `stop()` and a change of the user end them, with
  `finishAppearing(tag)`.

### R5: the cursor glides with its speed

- A cursor glides from where it is drawn to its new place in 120 ms (`KodeEditorCursorGlide.durationMs`) if the move
  fits: in its row by at most 0.4 × the width of the text, to the row above or below by at most 3 line heights.
  Anything else jumps.
- A cursor at rest glides with the cubic ease-out: `d(t) = d0 × (1 - t)³`, with `d0` how far it is drawn from its new
  place at the start, and `t` from 0 to 1 over the duration `T`.
- A cursor that gets a new place while it glides, as at the head of a reveal, starts a new glide from where it is drawn
  in the current frame and keeps its speed: `d(t) = h00(t) × d0 + h10(t) × T × v0`, with `h00 = 2t³ - 3t² + 1` and
  `h10 = t³ - 2t² + t` (a cubic Hermite curve that stops at its place). `v0` is its current speed projected onto the
  direction to the new place, clamped to `[0, 3 × |d0| / T]`. The upper end is the start speed of the ease-out, which
  is the same curve with `v0 = -3 × d0 / T`, so it never shoots past its place or swings sideways.
- Catching up: a glide that starts while one runs takes `clamp(1.5 × time since the last new place, 32, 120)` ms.
  A cursor that gets a new place within 90 ms after its glide ended catches up the same way. If that glide ended at
  most 20 ms before the frame, the new one starts in that frame, otherwise one frame (16 ms) earlier, so it moves in
  its first frame already. A jump forgets the time of the last new place.
- R3 and R5 go together: revealed per frame at about one character, the cursor stays about 1 to 1.5 characters behind
  the head with an even speed. Revealed in steps of several characters, the Hermite glide is smooth but lags more, about
  6 characters at 8 characters every 32 ms.

### R6: the text behind the chunk slides

- With an appearance, the text behind inserted text slides to its new place in 120 ms with the cubic ease-out, into the
  next rows too when the line breaks anew. As a chunk is hidden but keeps its space, this happens when it arrives.
- A slide that starts while one runs starts from where the text is drawn, so the text never jumps.

## Tests

KodeEditor tests these rules in `KodeEditorRevealTest` and `RemoteCursorsTest`, with chunks of 3 to 5 words (15 to 40
characters) at 0.5 to 2 s, some arriving while the chunk before is revealed, ending anywhere in a line, and one over
the end of a line:

- The head never goes back, and a new chunk shows nothing that was hidden.
- Each character is shown once, in the order of the text, and only the rest stays hidden.
- No character stays hidden longer than 4 s.
- 2,000 characters at once are shown within 4 s, at an even speed.
- The cursor at the head stays at most 2.5 and on average 1.5 characters behind it, and never ahead.
