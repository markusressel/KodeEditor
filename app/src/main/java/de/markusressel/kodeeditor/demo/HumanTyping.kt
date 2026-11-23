package de.markusressel.kodeeditor.demo

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong

/**
 * How a person types, for Jane of the demo and for tests. docs/typing-pattern.md has the same numbers for other
 * clients. All times are in ms.
 */
internal object TypingPace {
    /** the usual time between two keys, the median of a log-normal distribution */
    const val MEDIAN_MS = 150.0

    /** how much the times between keys spread, the standard deviation of their logarithm */
    const val SPREAD = 0.4

    /** the shortest and the longest time between two keys, before the pauses below */
    val KEY_MS = 60L..400L

    /** how much faster the second key of a common pair of letters comes, like "th" or "in" */
    const val COMMON_PAIR_FACTOR = 0.7

    /** the pairs of letters that come faster */
    val COMMON_PAIRS = setOf(
        "th", "he", "in", "er", "an", "re", "on", "at", "en", "nd", "ti", "es", "or", "te", "of", "ed", "is", "it",
        "al", "ar", "st", "to", "nt", "ng", "se", "ha", "as", "ou", "io", "le", "ve", "co", "me", "de", "hi", "ri",
    )

    /** the extra time for the key after a space */
    val AFTER_SPACE_MS = 40L..120L

    /** the extra time for the key after a punctuation mark like "," or ".", the end of a sentence or a line */
    val AFTER_PUNCTUATION_MS = 300L..700L

    /** how often a person stops to think before a key, and for how long */
    const val THINK_CHANCE = 0.015
    val THINK_MS = 1000L..2000L

    /** how often a burst of fast keys starts, how many keys it has, and how much faster they come */
    const val BURST_CHANCE = 0.04
    val BURST_KEYS = 3..6
    const val BURST_FACTOR = 0.5

    /**
     * How often a letter is mistyped as a key next to it. Up to [TYPO_KEYS_UNTIL_NOTICED] more keys are typed before
     * the person notices it, waits [NOTICE_MS], removes the wrong keys with backspace one each [BACKSPACE_MS] and
     * types on.
     */
    const val TYPO_CHANCE = 0.03
    val TYPO_KEYS_UNTIL_NOTICED = 0..2
    val NOTICE_MS = 200L..500L
    val BACKSPACE_MS = 80L..160L

    /** the same keys and times each time */
    const val SEED = 6
}

/** a key a person presses */
internal sealed interface Keystroke {
    /** types [char] at the cursor */
    data class Type(val char: Char) : Keystroke

    /** removes the character in front of the cursor */
    data object Backspace : Keystroke
}

/**
 * @param delayMs the time since the key before
 * @param key the key
 */
internal data class TimedKeystroke(val delayMs: Long, val key: Keystroke)

/** keys next to each other on a QWERTY keyboard, for typos */
private val NEIGHBORS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").flatMap { row ->
    row.mapIndexed { index, char -> char to listOfNotNull(row.getOrNull(index - 1), row.getOrNull(index + 1)) }
}.toMap()

/**
 * The keys a person presses to type [text], with the times between them, see [TypingPace]. Typing them from the start
 * gives [text], with typos that are removed again on the way.
 *
 * @param text what the person types
 * @param random picks the times and the typos, the same one gives the same keys
 */
internal fun humanKeystrokes(text: String, random: Random = Random(TypingPace.SEED)): List<TimedKeystroke> {
    val keys = ArrayList<TimedKeystroke>()
    var burst = 0
    var index = 0
    while (index < text.length) {
        val char = text[index]
        val previous = text.getOrNull(index - 1)
        var delay = keyGap(random, previous, char, burst > 0)
        if (burst > 0) burst-- else if (random.nextDouble() < TypingPace.BURST_CHANCE) {
            burst = random.nextInt(TypingPace.BURST_KEYS)
        }
        if (previous == ' ') delay += random.nextLong(TypingPace.AFTER_SPACE_MS)
        if (previous != null && previous in PUNCTUATION) delay += random.nextLong(TypingPace.AFTER_PUNCTUATION_MS)
        if (random.nextDouble() < TypingPace.THINK_CHANCE) delay += random.nextLong(TypingPace.THINK_MS)

        val wrong = NEIGHBORS[char.lowercaseChar()]
        if (wrong != null && random.nextDouble() < TypingPace.TYPO_CHANCE) {
            // the wrong key, maybe a few right ones behind it, then back to the wrong one
            keys += TimedKeystroke(delay, Keystroke.Type(wrong.random(random)))
            val more = random.nextInt(TypingPace.TYPO_KEYS_UNTIL_NOTICED).coerceAtMost(text.length - index - 1)
            for (next in 1..more) {
                val gap = keyGap(random, text[index + next - 1], text[index + next], false)
                keys += TimedKeystroke(gap, Keystroke.Type(text[index + next]))
            }
            repeat(more + 1) { removed ->
                val wait = if (removed == 0) TypingPace.NOTICE_MS else TypingPace.BACKSPACE_MS
                keys += TimedKeystroke(random.nextLong(wait), Keystroke.Backspace)
            }
            delay = random.nextLong(TypingPace.BACKSPACE_MS)
        }
        keys += TimedKeystroke(delay, Keystroke.Type(char))
        index++
    }
    return keys
}

/** @return the time between the key of [previous] and that of [char], without the pauses */
private fun keyGap(random: Random, previous: Char?, char: Char, burst: Boolean): Long {
    // a log-normal distribution: the logarithm of the time is normally distributed around that of the median
    val normal = random.nextGaussian()
    var gap = exp(ln(TypingPace.MEDIAN_MS) + TypingPace.SPREAD * normal)
    val pair = "${previous ?: ' '}$char".lowercase()
    if (pair in TypingPace.COMMON_PAIRS) gap *= TypingPace.COMMON_PAIR_FACTOR
    if (burst) gap *= TypingPace.BURST_FACTOR
    return gap.toLong().coerceIn(TypingPace.KEY_MS)
}

/** a normally distributed number with a mean of 0 and a standard deviation of 1, by the Box-Muller transform */
private fun Random.nextGaussian(): Double {
    val u = 1.0 - nextDouble()
    val v = nextDouble()
    return kotlin.math.sqrt(-2.0 * ln(u)) * kotlin.math.cos(2.0 * Math.PI * v)
}

private const val PUNCTUATION = ".,;:!?)\n"

/** what the keys type, starting with an empty text */
internal fun List<TimedKeystroke>.typed(): String = buildString {
    this@typed.forEach { (_, key) ->
        when (key) {
            is Keystroke.Type -> append(key.char)
            Keystroke.Backspace -> if (isNotEmpty()) deleteCharAt(length - 1)
        }
    }
}
