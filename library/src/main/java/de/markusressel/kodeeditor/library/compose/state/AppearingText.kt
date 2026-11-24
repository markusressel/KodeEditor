package de.markusressel.kodeeditor.library.compose.state

import androidx.compose.ui.text.AnnotatedString
import de.markusressel.kodeeditor.library.compose.animation.APPEAR_RATE_MS
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAppearance
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorLocalAnimations
import kotlin.math.abs
import kotlin.math.exp

/**
 * The appearing ranges with [added], at most [KodeEditorAppearance.maxRanges] of them. Nothing is shown at once that
 * is still far from visible: with too many, the two pieces one behind the other of the same writer whose alphas are
 * the closest become one, which goes on with the alpha of the more visible one. So a piece only gets a bit more visible
 * at once. Only without such pieces the most visible range is shown at once.
 */
internal fun limitAppearing(
    ranges: List<AnnotatedString.Range<Float>>,
    added: AnnotatedString.Range<Float>,
    config: KodeEditorAppearance,
): List<AnnotatedString.Range<Float>> {
    val all = (ranges + added).toMutableList()
    val max = config.maxRanges.coerceAtLeast(1)
    while (all.size > max) {
        var best = -1
        var bestOther = -1
        var bestDifference = Float.MAX_VALUE
        all.forEachIndexed { index, range ->
            val tag = range.tag.appearTag()
            val other = all.indexOfFirst { it.start == range.end && it.tag.appearTag() == tag }
            if (other < 0) return@forEachIndexed
            val difference = abs(all[other].item - range.item)
            if (difference < bestDifference) {
                best = index
                bestOther = other
                bestDifference = difference
            }
        }
        if (best < 0) {
            all.removeAt(all.indices.maxBy { all[it].item })
            continue
        }
        val first = all[best]
        val second = all[bestOther]
        // the tag keeps the time the more visible one started at, the other one does not get less visible
        val kept = if (first.item >= second.item) first else second
        val merged = AnnotatedString.Range(kept.item, first.start, second.end, kept.tag)
        all[minOf(best, bestOther)] = merged
        all.removeAt(maxOf(best, bestOther))
    }
    return all
}

private const val APPEAR_TAG_SEPARATOR = '/'

/** the appear tag of the big changes of the user, see [KodeEditorLocalAnimations.bigChanges] */
internal const val LOCAL_APPEAR_TAG = "kodeeditor.local"

/** the tag the application gave to [KodeEditorState.appear], behind the id that makes each range unique */
internal fun String.appearTag() = substringAfter(APPEAR_TAG_SEPARATOR)

/**
 * @param startMs the time of the frame a range started to appear in
 * @param durationMs how long it takes
 */
private class AppearTiming(val startMs: Long, val durationMs: Long)

/**
 * The characters per second that appear for a writer, an exponential average over [APPEAR_RATE_MS]: n characters add
 * n × 1000 / [APPEAR_RATE_MS], and the rate decays by e^(-elapsed / [APPEAR_RATE_MS]). So text that comes at r
 * characters per second for a while has a rate of r.
 */
internal class AppearRate(var charsPerSecond: Float, private var lastMs: Long) {
    /** @param chars how many characters arrived at [nowMs] */
    fun add(chars: Int, nowMs: Long) {
        val elapsed = (nowMs - lastMs).coerceAtLeast(0L)
        charsPerSecond = charsPerSecond * exp(-elapsed / APPEAR_RATE_MS) + chars * 1000f / APPEAR_RATE_MS
        lastMs = nowMs
    }
}

/**
 * When the appearing ranges of a [KodeEditorState] started and how long they take, by the tags of the ranges, and the
 * rate of each writer. A range gets its duration in the first frame it is drawn in, from the rate of its writer or a
 * fixed one, and keeps it, see [KodeEditorAppearance].
 */
internal class AppearTimings {
    /** the time of the frame each appearing range started in and how long it takes */
    private val starts = HashMap<String, AppearTiming>()

    /** how long the appearing ranges take that do not follow the rate of their writer */
    private val fixedMs = HashMap<String, Long>()

    /** how many characters per second appear for each writer, smoothed, by the tag of the writer */
    private val rates = HashMap<String, AppearRate>()
    private var nextId = 0

    /**
     * @param tag the tag of the writer
     * @param durationMs how long the range takes, null for the rate of the writer
     * @return the tag of a new appearing range, unique, which ends with [tag]
     */
    fun newTag(tag: String, durationMs: Long?): String {
        val unique = "${nextId++}$APPEAR_TAG_SEPARATOR$tag"
        if (durationMs != null) fixedMs[unique] = durationMs
        return unique
    }

    /** forgets the ranges that are not appearing anymore */
    fun retain(tags: Set<String>) {
        starts.keys.retainAll(tags)
        fixedMs.keys.retainAll(tags)
    }

    /** @return how long the appearing range with [tag] takes, null if it did not start yet */
    fun durationOf(tag: String): Long? = starts[tag]?.durationMs

    /**
     * @param ranges the appearing ranges
     * @param nowMs the time of the frame
     * @param scale scales the durations
     * @param config how text appears
     * @return the ranges with their alphas in this frame, without those that appeared
     */
    fun onFrame(
        ranges: List<AnnotatedString.Range<Float>>,
        nowMs: Long,
        scale: Float,
        config: KodeEditorAppearance,
    ): List<AnnotatedString.Range<Float>> {
        // the rates first, with all that arrived in this frame
        ranges.forEach { range ->
            if (range.tag in starts || range.tag in fixedMs) return@forEach
            val rate = rates.getOrPut(range.tag.appearTag()) { AppearRate(0f, nowMs) }
            rate.add(range.end - range.start, nowMs)
        }
        ranges.forEach { range ->
            if (range.tag in starts) return@forEach
            val duration = fixedMs.remove(range.tag)
                ?: config.durationFor(rates.getValue(range.tag.appearTag()).charsPerSecond).toLong()
            starts[range.tag] = AppearTiming(nowMs, (duration * scale).toLong())
        }
        return ranges.mapNotNull { range ->
            val timing = starts.getValue(range.tag)
            val progress = if (timing.durationMs > 0) (nowMs - timing.startMs).toFloat() / timing.durationMs else 1f
            if (progress >= 1f) return@mapNotNull null
            // fast at first, so new text does not stay invisible for long
            val remaining = 1f - progress.coerceAtLeast(0f)
            AnnotatedString.Range(1f - remaining * remaining, range.start, range.end, range.tag)
        }
    }
}
