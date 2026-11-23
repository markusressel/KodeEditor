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
- When a chunk arrives: `target = max(target, 60, hidden × 1000 / (4000 - 150))` characters per second, with `hidden`
  all hidden characters of the writer, the new chunk included. The target never drops while text is hidden, and goes
  back to 0 with the rate and the carry when nothing is hidden anymore.
- From a rest, `rate = target` at once. While text is revealed, the rate gets there smoothly instead of jumping:
  `rate += (target - rate) × min(1, elapsed / 150)` in each frame. The 150 ms less in the target make up for it.
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
- How long a piece takes to appear depends on how fast its writer writes, so a lot of text at once is smoothed more
  and slow text still shows soon:
  - Each writer (the tag of the reveal, or `appearTag` of `applyRemote`) has a rate: in the first frame a piece is
    drawn in, `rate = rate × e^(-elapsed / 1000 ms) + chars`, with `elapsed` the time since that writer's last piece
    and `chars` the characters of the pieces of that frame. So text that comes at r characters per second for a while
    has a rate of r, and one burst only nudges it.
  - The piece gets `duration = durationMs + (maxDurationMs - durationMs) × clamp((rate - 10) / (120 - 10), 0, 1)`
    (`slowCharsPerSecond` 10, `fastCharsPerSecond` 120), and keeps it. So no alpha changes afterwards.
  - Defaults: `durationMs` 320 ms, `maxDurationMs` 1000 ms. A person typing (about 7 characters per second) gets
    320 ms, a reveal at its slowest (60 per second) about 630 ms, a reveal that catches up (120 per second or more)
    1000 ms. 320 ms is the shortest with the 0.1 below: the first frame of a piece reaches `1 - (1 - 16/320)² ≈ 0.098`.
- The alpha of a piece is `1 - (1 - p)²` with `p = time since the piece came / duration` (ease-out quadratic: fast at
  first, so new text does not stay invisible for long, then slower until it is fully visible). Each piece has its own
  start, so pieces one behind the other differ by about one frame of the curve.
- Per character, not per piece: the pieces one behind the other in a row are drawn in one layer, with an alpha that
  goes in a straight line from the middle of one piece to the middle of the next (a horizontal gradient). As a piece is
  one or two characters at about one per frame, each character gets about the alpha of its own arrival, and there is no
  edge between pieces. With a slide (`KodeEditorAppearance.slide`), each piece is drawn in a layer of its own instead.
- No piece is shown at once before it appeared, however long the text that appears is. Only with more than
  `maxRanges` (256) appearing pieces, the two pieces one behind the other of the same writer whose alphas are the
  closest become one, with the alpha of the more visible one. So a character gets visible by that difference at once,
  which is tiny with that many pieces. Without such neighbours, the most visible piece is shown at once.
- So no character's alpha changes by more than 0.1 from one frame to the next, at 500 and 1000 ms, for typing and for
  reveals, and while a writer gets faster and slower again. Pieces next to each other with different durations differ
  a little in alpha, which the gradient smooths. A reveal has about one piece per frame alive, about 31 at 500 ms and 63 at 1000 ms, far
  below 256.

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
- R5b, typing: a step of at most 3 characters in the same row, at most 250 ms after the last new place of the cursor,
  glides at an even speed (linear) from where the cursor is drawn, for `clamp(1.5 × interval, 32, 250)` ms. The interval
  is the time between its steps, smoothed: the first step of a run takes the time since the last new place, each one
  after it `interval + 0.3 × (time since the last new place - interval)`. The factor 1.5 keeps it on its way when the
  next step comes, so it does not stop and start again at each one. So the cursor of somebody who types keeps moving
  between their keys instead of starting fast and stopping at each one ("pumping"). It ends about one key interval
  behind. The first step after a rest, and every larger move, keep the rules above. For a reveal at about one character
  per frame this is a glide of 32 ms, about 1 character behind the head.
- R5c, slow typing is crisp: the speed of the typing decides the curve and the time of such a step. With
  `crisp = clamp((interval - 100) / (200 - 100), 0, 1)`:
  - the time is `lerp(clamp(1.5 × interval, 32, 250), 100, crisp)` ms;
  - the way at the part `t` of it is `lerp(t, 1 - (1 - t)³, crisp)`, an even glide blended with the cubic ease-out.
  So fast typing (keys at most 100 ms apart) glides on evenly as above, and slow typing (200 ms or more) takes quick
  steps of 100 ms that slow down at their place, like a single key. In between, both blend, so a typist who gets
  faster or slower changes smoothly, without a jump of the cursor. Each step starts from where the cursor is drawn.
- R3 and R5 go together: revealed per frame at about one character, the cursor stays about 1 to 1.5 characters behind
  the head with an even speed. Revealed in steps of several characters, the Hermite glide is smooth but lags more, about
  6 characters at 8 characters every 32 ms.

### R6: the text behind the chunk slides

- With an appearance, the text behind text that somebody else inserted, removed or replaced slides to its new place in
  120 ms with the cubic ease-out, into the next rows too when the line breaks anew. As a chunk is hidden but keeps its
  space, this happens when it arrives. Removed text is gone at once, the text behind it closes the gap.
- A slide that starts while one runs starts from where the text is drawn, so the text never jumps: the one before
  stops where it is, and the new one takes the text on from there to its new place.
- Typing (R6b): an insertion of at most 3 characters right behind the one before, at most 250 ms after it, slides at an
  even speed (linear) for `clamp(1.5 × interval, 32, 250)` ms, with the time between the keys smoothed like in R5b. It
  starts a frame (16 ms) early, so the text does not stand still for a frame at each key. A cursor at the end of the
  insertion is drawn where the text behind it is drawn on its way, so the text behind the cursor of somebody who types
  moves exactly with their cursor, at the even speed of R5b. The time and the curve follow R5c, with the same
  numbers, so slow typing moves the text behind the cursor in crisp steps too.
- While the text behind a key or a backspace slides, a cursor at the end of the change glides no extra way of its
  own: the slide carries it. So the cursor and the text behind it stay together whatever the curve.
- The same for backspace: a removal of at most 3 characters right in front of where the cursor was after the change
  before (the end of an insertion, the start of a removal), at most 250 ms after it, slides at an even speed to the
  left, with the same times. An insertion right after a removal at the same place is typed on too. So a typo that is
  removed and typed again moves the cursor and the text behind it back and forth without a jump.
- Chunks of an agent are no typing: each chunk slides once, in 120 ms, when it arrives, and nothing moves while it is
  revealed.
- Only the changes of others slide, those of the user are where they are at once.

### R7: nothing shows ahead of the cursor

- Appearing text that ends at the place of a remote cursor (several pieces one behind the other, and pieces right
  behind that place, whose cursor update comes a frame later) is drawn only left of where that cursor's caret is drawn
  in its row, and not at all in later rows. So a character shows exactly when the caret has passed it, also while the
  caret lags behind the head of a reveal, and the fade only sets the alpha of what the caret has passed.
- Where no shown text follows in the row (the end of the row, or hidden text), the cut reaches half a line height
  further, so the part of a letter that reaches beyond its place is cut too.
- Apps should apply the cursor of a person together with their change of the text, in one call before the next frame,
  so the insertion and the cursor that caused it are drawn together.

## Changes of the user

The user's own typing never animates: no cursor glide, no slide, no fade, also not with the keyboard (IME) or while
dragging a selection. The cursor and the text at it are where they belong in the same frame.

Big changes of the user animate, so it is easy to see what they changed. This is on by default
(`KodeEditorAppearance(local = KodeEditorLocalAnimations())`), `local = null` or `bigChanges = false` turns it off:

- Paste (the text menu and Ctrl+V), undo, redo, and replacing with `KodeEditorSearch.replaceCurrent` and `replaceAll`.
- The text behind the change slides to its new place like with the changes of others (R6), in 120 ms with the cubic
  ease-out, also across rows.
- The inserted or restored text fades in, in `highlightMs` (400 ms), with the alpha curve of R4. `replaceAll` is one
  change from the first to the last match, so all of that fades in. Removed text is gone at once, the text behind it
  closes the gap.
- The cursor, the selection and its handles are at their new place at once, also while the text behind them slides.
  The keyboard, accessibility, scrolling to the cursor and taps use the new layout.
- A paste through the keyboard (its clipboard) arrives like typing and does not animate.

## The view stays where the user looks

Text that others insert or remove in front of what the user looks at does not move it on the screen
(`KodeEditor(keepViewOnRemoteChanges = true)`, the default):

- The anchor is the cursor of the user while the editor has the focus and the cursor is in view, otherwise the start
  of the first visible row. It is taken right before the change.
- A change that ends at or before the anchor (and starts before it) scrolls the view by how far it moved the anchor,
  in the same frame: up and down always, sideways only without wrapping, where only a change in the row of the anchor
  moves it sideways. The widths come from the measured text before and after the change, with tabs, wide characters
  and collapsed parts. A change behind the anchor moves nothing.
- The slide of R6 starts where the text was on the screen, so the anchor does not move at all while it runs, and the
  text in front of the change moves out of the way instead.
- Near the end of the text the view cannot scroll further, there the anchor can move.
- A selection that moves only because of a change of others is no move of the user: the view does not follow it to
  keep the cursor visible.
- The user's own scrolling, selection and edits are unchanged.


KodeEditor tests these rules in `KodeEditorRevealTest` and `RemoteCursorsTest`, with chunks of 3 to 5 words (15 to 40
characters) at 0.5 to 2 s, some arriving while the chunk before is revealed, ending anywhere in a line, and one over
the end of a line:

- The head never goes back, and a new chunk shows nothing that was hidden.
- Each character is shown once, in the order of the text, and only the rest stays hidden.
- No character stays hidden longer than 4 s.
- 2,000 characters at once are shown within 4 s, at an even speed.
- The cursor at the head stays at most 2.5 and on average 1.5 characters behind it, and never ahead.
- `TypingTest`, on the pixels of each frame, with keys at the times of a person (log-normal around 150 ms, see
  [typing-pattern.md](typing-pattern.md)): the text behind the cursor of somebody who types keeps its distance to the
  caret (±2 px), no typed or revealed text shows right of the caret, and the speed of the caret while typing has a 95th
  percentile of at most 2.5 times its mean, for a fast typist (log-normal around 70 ms). For slow typing, standing
  still between keys is right. Keys 250 ms apart take steps of at most 120 ms. A typist who goes from 80 to 250 to 160
  and back to 80 ms between keys keeps the text behind the caret at its distance (±2 px), and the caret moves at most
  0.75 of a key per frame, never back. With `durationMs = 1000`, no character's alpha changes by more than 0.1
  between two frames, for a person typing and for a reveal of 400 characters, and neither at 500 ms. With a typo that
  is removed with backspace, the text behind the cursor keeps its distance (±2 px) and the caret speed has a 95th
  percentile of at most 2.5 times its mean. Text removed by somebody else slides the text behind it to the left.
- `RangeStylesTest`: a person at 7 keys per second gets 320 ms, a reveal at 190 characters per second 1000 ms. A writer
  who types, then writes fast, then types again never makes a character get less visible or more visible by over 0.1
  in a frame, and the duration goes up and then down, never back and forth. A burst of 5 keys raises it by at most
  50 ms.
