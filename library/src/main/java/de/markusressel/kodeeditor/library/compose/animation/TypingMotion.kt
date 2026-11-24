package de.markusressel.kodeeditor.library.compose.animation

import androidx.compose.animation.core.Easing
import androidx.compose.ui.util.lerp

/*
 * How the steps of somebody who types move, the same for their cursor (the even typing glide R5b and crisp slow typing
 * R5c of docs/reveal.md) and for the text behind their change (the typing slide, R6b). Keep these in step with the doc.
 */

/** the time between two frames at 60 Hz */
internal const val FRAME_MS = 16L

/** slows down towards the end like the cursors of other users, `1 - (1 - t)^3` */
internal val CubicEaseOut = Easing { 1f - (1f - it).let { rest -> rest * rest * rest } }

/** characters, a longer step is no key somebody types */
internal const val TYPING_MAX_CHARS = 3

/**
 * A step of at most [TYPING_MAX_CHARS] this soon after the one before is typed on: it moves at an even speed, so
 * typing does not pump, as it would start fast and slow down at each key otherwise
 */
internal const val TYPING_WINDOW_MS = 250L

/**
 * A step that is typed on takes this times the smoothed time between the keys, so it is still on its way when the next
 * key comes, and does not stop and start again at each key
 */
internal const val TYPING_SPAN_FACTOR = 1.5f

/** how much the time since the last key counts for the smoothed time between the keys, the rest is the one before */
internal const val TYPING_SMOOTHING = 0.3f

/** the shortest step, about two frames */
internal const val MIN_STEP_MS = 32L

/**
 * Up to this smoothed time between keys, a step glides at an even speed for 1.5 × that time, so fast typing does not
 * pump. From [CRISP_INTERVAL_MS] on, a step is quick and crisp, [CRISP_STEP_MS] with the cubic ease-out, so slow typing
 * does not drag. In between, both blend in a straight line.
 */
internal const val CONTINUOUS_INTERVAL_MS = 100f

/** see [CONTINUOUS_INTERVAL_MS] */
internal const val CRISP_INTERVAL_MS = 200f

/** how long a crisp step of slow typing takes, see [CONTINUOUS_INTERVAL_MS] */
internal const val CRISP_STEP_MS = 100f

/**
 * @param previousMs the smoothed time between the keys so far, below 0 for the first step of a run
 * @param sinceMs the time since the key before
 * @return the smoothed time between the keys with this one
 */
internal fun smoothedKeyInterval(previousMs: Float, sinceMs: Float): Float =
    if (previousMs < 0f) sinceMs else previousMs + (sinceMs - previousMs) * TYPING_SMOOTHING

/**
 * @param intervalMs the smoothed time between the keys
 * @param shortestMs the shortest step
 * @return how long a step takes as an even glide
 */
internal fun evenStepMs(intervalMs: Float, shortestMs: Float): Float =
    (intervalMs * TYPING_SPAN_FACTOR).coerceIn(shortestMs, TYPING_WINDOW_MS.toFloat())

/**
 * @param intervalMs the smoothed time between the keys
 * @return how crisp a step is, from 0 for an even glide to 1 for a crisp one, see [CONTINUOUS_INTERVAL_MS]
 */
internal fun crispnessOf(intervalMs: Float): Float =
    ((intervalMs - CONTINUOUS_INTERVAL_MS) / (CRISP_INTERVAL_MS - CONTINUOUS_INTERVAL_MS)).coerceIn(0f, 1f)

/**
 * @param evenMs how long the step takes as an even glide
 * @param crisp see [crispnessOf]
 * @return how long the step takes
 */
internal fun steadyStepMs(evenMs: Float, crisp: Float): Float = lerp(evenMs, CRISP_STEP_MS, crisp)

/** @return how far a step of typing is at the part [t] of its time, an even glide blended with the cubic ease-out */
internal fun steadyStepEase(t: Float, crisp: Float): Float = lerp(t, CubicEaseOut.transform(t), crisp)

/** @return how fast a step of typing moves at the part [t] of its time, for a whole step in a time of 1 */
internal fun steadyStepSpeed(t: Float, crisp: Float): Float = lerp(1f, 3f * (1f - t) * (1f - t), crisp)
