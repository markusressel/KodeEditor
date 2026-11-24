package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.ui.geometry.Offset

/*
 * The way of a cursor that glides, R5 of docs/reveal.md. A glide is kept as the distance from the place of the cursor,
 * which shrinks to 0.
 */

/**
 * @param start how far it is from its place at the start
 * @param velocity how fast it moves at the start, in pixels per millisecond, null for the cubic ease-out
 * @param t the part of the glide that is over, from 0 to 1
 * @param durationMs how long the glide takes
 * @return how far it is from its place. It goes from its distance and its speed at the start to its place, where it
 * stops (a cubic Hermite curve). With the speed of the cubic ease-out, `3 × distance / duration` towards its place, the
 * curve is that ease-out: `distance × (1 - t)^3`
 */
internal fun glideDistance(start: Offset, velocity: Offset?, t: Float, durationMs: Long): Offset {
    velocity ?: return start * (1f - t).let { it * it * it }
    val h00 = 2 * t * t * t - 3 * t * t + 1
    val h10 = t * t * t - 2 * t * t + t
    return start * h00 + velocity * (h10 * durationMs)
}

/** @return how fast it moves at the part [t] of the glide of [glideDistance], in pixels per millisecond */
internal fun glideVelocity(start: Offset, velocity: Offset?, t: Float, durationMs: Long): Offset {
    val initial = velocity ?: (start * (-3f / durationMs))
    val dh00 = 6 * t * t - 6 * t
    val dh10 = 3 * t * t - 4 * t + 1
    return start * (dh00 / durationMs) + initial * dh10
}

/**
 * @param velocity how fast it moves now
 * @param move the way of the new glide
 * @param durationMs how long the new glide takes
 * @return the part of the [velocity] towards the end of a [move], at most the speed of the ease-out at its start, so
 * it does not shoot past its place or swing sideways. Moving away from it, it starts from rest
 */
internal fun keptGlideVelocity(velocity: Offset, move: Offset, durationMs: Long): Offset {
    val length = move.getDistance()
    if (length < MIN_GLIDE || durationMs <= 0) return Offset.Zero
    val direction = move / length
    val along = (velocity.x * direction.x + velocity.y * direction.y).coerceIn(0f, 3f * length / durationMs)
    return direction * along
}

/** pixels, a smaller move jumps */
internal const val MIN_GLIDE = 0.5f
