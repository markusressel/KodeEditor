# How a person types

Fake users and end-to-end tests of KodeEditor, MkDocsEditor-Android and the web client type like this, so animations
are judged with realistic timing. A steady interval hides some effects and exaggerates others. KodeEditor has it in
`HumanTyping.kt` of the demo app (`TypingPace`, `humanKeystrokes`), where Jane of "Jane types" uses it. All times are
in ms, and the same seed gives the same keys.

## The time between two keys

1. A log-normal time: `exp(ln(150) + 0.4 × n)`, with `n` from a standard normal distribution. So the median is 150 ms.
2. × 0.7 for the second letter of a common pair: `th he in er an re on at en nd ti es or te of ed is it al ar st to
   nt ng se ha as ou io le ve co me de hi ri` (case does not matter).
3. × 0.5 inside a burst. After each key outside a burst, a burst of the next 3 to 6 keys starts with a chance of 4 %.
4. Clamped to 60–400 ms.
5. Then added:
   - after a space: 40–120 ms;
   - after a punctuation mark `. , ; : ! ? )` or a line break: 300–700 ms;
   - with a chance of 1.5 %, a pause to think: 1,000–2,000 ms.

All ranges are uniform and include both ends.

## Typos

- A letter that has a neighbor on a QWERTY keyboard (in its row: `qwertyuiop`, `asdfghjkl`, `zxcvbnm`) is mistyped as
  one of its neighbors with a chance of 3 %. The wrong key comes after the time the right one would have.
- Then 0–2 of the following right keys are typed, each after a time from steps 1, 2 and 4.
- The person notices it after 200–500 ms and presses backspace, then backspace again for each key typed after the
  wrong one, 80–160 ms apart.
- Then the right key comes after 80–160 ms, and typing goes on.

So the typed text ends up as intended, with removals on the way: the text behind the cursor must not slide for a
removal, and the cursor of the person moves backwards.

## Check

For `AGENT_LONG_TEXT` of the demo and seed 6, KodeEditor tests that: the keys give the text, the median time behind a
letter is 110–170 ms, keys after `.` and `,` come at least 300 ms later, and there are pauses to think, typos and
bursts.
