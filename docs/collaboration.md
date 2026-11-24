# Others in the editor: users, typists and agents

How to show what others write in a `KodeEditor`: other users who type, agents that send their answer in chunks, and
their cursors and names. The README shows each feature on its own, this guide shows how they work together, and what
to avoid. [reveal.md](reveal.md) has the exact rules and numbers behind it.

## The pieces

| What | API | Section |
|---|---|---|
| The text and its changes | `KodeEditorState`, `KodeEditorListener.onLocalEdit`, `applyRemote` | [The text](#the-text) |
| Text that somebody types | `applyRemote(splices, appear = true, appearTag = id)` | [Typists](#typists) |
| Text that an agent sends in chunks | `rememberKodeEditorReveal(state, tag)`, `insert` | [Agents](#agents) |
| Their cursors and names | `remoteCursors`, `remoteCursorLabel`, `KodeEditorRemoteCursor` | [Cursors and names](#cursors-and-names) |
| How all of it moves | `KodeEditorAnimations(appearance = KodeEditorAppearance())` | [Animations](#animations) |
| The view of the user | `keepViewOnRemoteChanges`, `KodeEditorViewState` | [The view](#the-view) |

## A complete example

A document with the user, another user who types, and an agent:

```kotlin
@Composable
fun SharedDocument(document: SharedDocument) {
    val state = rememberKodeEditorState(document.initialText)
    val agent = rememberKodeEditorReveal(state, tag = "agent")
    // counted up when the user picks somebody in the list of users, shows their name for a moment
    var showNames by remember { mutableIntStateOf(0) }

    // the user's changes go out, as they happen
    DisposableEffect(state) {
        val listener = object : KodeEditorListener {
            override fun onLocalEdit(splice: TextSplice) = document.send(splice)
        }
        state.addListener(listener)
        onDispose { state.removeListener(listener) }
    }

    // what comes in, on the main thread
    LaunchedEffect(document) {
        document.events.collect { event ->
            when (event) {
                // a user who types: each key as it comes, it fades in and their cursor glides
                is Typed -> state.applyRemote(event.splices, appear = true, appearTag = event.userId)
                // an agent: each chunk is hidden and revealed as if typed
                is AgentChunk -> agent.insert(event.position, event.text)
            }
        }
    }

    // the screen is closed for good: show what is still hidden
    DisposableEffect(agent) { onDispose { agent.stop() } }

    val cursors = buildList {
        document.users.forEach { user ->
            add(KodeEditorRemoteCursor(user.id, user.selections, user.color, user.color.copy(alpha = 0.3f),
                labelSignal = showNames))
        }
        // between its chunks the cursor of the agent stays at the end of what it wrote, until it is done
        val agentAt = agent.head ?: agent.end?.takeIf { document.agentIsWriting }
        agentAt?.let {
            add(KodeEditorRemoteCursor("agent", listOf(TextRange(it)), AgentColor, AgentColor.copy(alpha = 0.3f),
                labelSignal = showNames))
        }
    }

    KodeEditor(
        state = state,
        languageRuleBook = ruleBook,
        colorScheme = colorScheme,
        onTextLayout = { agent.layout = it },
        remoteCursors = cursors,
        remoteCursorLabel = { cursor ->
            Text(cursor.id, Modifier.background(cursor.caretColor).padding(horizontal = 2.dp), color = Color.Black)
        },
    )
}
```

## The text

`KodeEditorState` holds the only copy of the text. All changes happen on the main thread, each one at once, so changes
can not overtake each other.

- **Send what the user changes from `onLocalEdit`.** It is called within the change, for typing, the keyboard, paste,
  undo and the actions of the editor. Changes from `applyRemote`, `setText` and `KodeEditorReveal` are not reported, so
  what came from others is never sent back.
- **Apply what others change with `applyRemote`.** The cursor, the selection and the keyboard of the user move with the
  text, and a word the keyboard is composing is not broken. Each splice refers to the text after the one before it.
- **Don't use `setText` for changes of others.** It replaces the whole text: nothing animates, and it is meant for
  loading a document. `edit` is for actions of the app that the user can undo.
- **Positions are UTF-16 indices of `state.text`**, like `String` indices. A position the app keeps, e.g. where an agent
  writes, has to move with every change in front of it, from others and from the user.
- `state.text` contains hidden text too: it is in the document, it is only not drawn yet. Saving it saves all of it.

## Typists

A user who types shows best key by key: one `applyRemote` per key or per small batch, as it arrives, with
`appear = true` and their id as `appearTag`.

- Their characters fade in, their cursor glides at an even speed, and the text behind it slides along with it. Fast
  typing glides on, slow typing takes crisp steps ([reveal.md](reveal.md), the cursor glide R5 and the slide R6).
- The rate of each writer decides how long their text takes to fade in, so give each writer their own `appearTag`.
- A batch of many characters at once (a paste of somebody else) appears as one piece. That is right for a paste, but
  typing that is sent in batches looks like jumps: send small batches often if the sync allows it.
- Removed text is gone at once, and the text behind it closes the gap.

## Agents

An agent sends its answer in chunks of a few words, often in bursts with pauses. `KodeEditorReveal` puts each chunk
into the text at once, hidden, and reveals it at an even speed, as if typed. So the text below moves once per chunk, not
with every character.

- **One reveal per writer, with its own tag.** Two agents need two reveals. The tag tells their hidden and appearing
  text apart.
- **`insert(position, chunk)` for every chunk**, at the place in the current text. A chunk right behind the hidden text
  joins it.
- **Pass `onTextLayout = { reveal.layout = it }`**, so a folded part is revealed as a whole, not character by character.
- **The cursor of the agent is at `head`, and at `end` between chunks.** `head` is null while nothing is hidden. Showing
  the cursor only at `head` makes it go away and come back with each pause of the agent. Use `head ?: end` while the
  agent writes, and remove the cursor when it is done.
- **The user always wins.** A change of the user anywhere, and a cursor or selection that the user puts into text that
  is still revealed, show all of it at once, so the user can work there. The app has nothing to do for this.
- **Call `stop()` when the editor goes away for good**, e.g. the screen is closed. The reveal only notices changes while
  it runs. Not on a rotation: a reveal that runs again goes on where it was.
- **Keep the range styles of the reveal.** An app that sets its own range styles must keep those whose tag starts with
  the tag of the reveal, or their text shows at once:

  ```kotlin
  state.setRangeStyles(state.rangeStyles.filterNot { it.tag.startsWith(MY_TAG) } + myStyles)
  ```

- With wrapping, a word that is revealed at the end of a row is drawn there while it fits, and goes to the next row
  like a typed one (the wrap carry, R8).

## Cursors and names

`remoteCursors` are the cursors and selections of others, `remoteCursorLabel` draws the name at the first cursor of
each.

- **Keep the `id` of a user stable.** It keys their glide and their label. A new id is a new user, who fades in.
- **Update the cursors together with the text.** Their positions refer to the text of the state. A cursor that only
  moves with a change of the text does not glide.
- **Remove a user who left**, they fade out. A user who comes fades in.
- **The label stays out of the way on its own:** it goes below the cursor where it would cover what was just written,
  fades down while somebody writes steadily or while it covers the user's cursor, and shows fully again when they rest
  (the calm label, R9). With a mouse, pointing at it shows it.
- **`labelSignal` shows a label for a moment.** Count it up when the user should see who that is, e.g. when they pick
  somebody in a list of users, or when somebody asks for attention.
- **`labelVisibility`** is `Auto` by default. `Shown` keeps the label fully shown, `Hidden` never shows it, the colored
  cursor still shows the user.
- The label is composed again only when its `KodeEditorRemoteCursor` changes, so keep it cheap and don't put state that
  changes every frame into it.

## Animations

`KodeEditorAnimations(appearance = KodeEditorAppearance())` is the default. All of the above animates with it.

- `appearance = null` turns the motion off: text appears without a fade, nothing slides, cursors jump. A reveal still
  reveals at its speed, without fades. A system setting without animations turns off all of it, and shows the chunks of
  a reveal at once.
- `KodeEditorAppearance(durationMs, maxDurationMs)` are the fades of slow and of fast text, `cursorGlide` the glide of
  the cursors and the fades of users who come and go (`KodeEditorCursorGlide(fadeInMs, fadeOutMs)`).
- Keep the defaults unless there is a reason: the MkDocsEditor Android app and web client use the same numbers, so text
  looks the same everywhere.

## The view

- **Text that others insert in front of the cursor keeps the view where it is** (`keepViewOnRemoteChanges = true`, the
  default). Remove code in the app that scrolled to make up for that, it would move the view twice.
- **To follow somebody**, scroll with `KodeEditorViewState.scrollToPosition(theirCursor)`, and pause while the user
  moves the view, see the README.

## Checklist

- [ ] `onLocalEdit` sends, `applyRemote` receives, no `setText` for changes of others.
- [ ] Typists: `appear = true` and their own `appearTag`.
- [ ] Agents: one `rememberKodeEditorReveal` per agent, `insert` per chunk, `reveal.layout` from `onTextLayout`.
- [ ] The cursor of an agent at `head ?: end` while it writes, removed when it is done.
- [ ] `stop()` when the screen goes away for good.
- [ ] Own range styles keep those of the reveals.
- [ ] Stable ids for cursors, cursors updated with the text.
- [ ] No code of the app that keeps the view in place for changes of others.
