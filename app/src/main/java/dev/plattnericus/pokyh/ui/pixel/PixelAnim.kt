package dev.plattnericus.pokyh.ui.pixel

import kotlin.math.floor

/**
 * One property of a sprite animation over time, the way a CSS `@keyframes` rule animates it:
 * keyframes at offsets 0…1, and between two of them the value moves in [steps] jumps (CSS
 * `steps(n, end)` applies per keyframe interval). `steps = 1` holds each keyframe's value until
 * the next one — most of the web's sprite animations are written that way.
 */
class Track(private val steps: Int, private val frames: List<Pair<Double, Double>>) {
    fun at(p: Double): Double {
        if (p <= frames.first().first) return frames.first().second
        for (i in 0 until frames.size - 1) {
            val (k0, v0) = frames[i]
            val (k1, v1) = frames[i + 1]
            if (p < k1) {
                if (k1 <= k0) return v1
                val f = floor(((p - k0) / (k1 - k0)) * steps) / steps
                return v0 + (v1 - v0) * f
            }
        }
        return frames.last().second
    }
}

private fun tr(steps: Int, vararg frames: Pair<Double, Number>) = Track(steps, frames.map { it.first to it.second.toDouble() })

/**
 * A sprite animation — one of the web's `.px-*` / `.ms-*` / `.pxp-*` classes. Lengths are pixels
 * of the drawing. [baseOpacity] is the layer's own opacity outside the animation (before a
 * positive delay, after a one-shot animation ends); `reduced*` is what the web's
 * `prefers-reduced-motion` rules show instead.
 */
class PxAnim(
    val durationMs: Int,
    val delayMs: Int = 0,
    val infinite: Boolean = true,
    /** Keep the last keyframe after a one-shot animation ends, and the first during its delay (CSS `both`). */
    val fillBoth: Boolean = false,
    val tx: Track? = null,
    val ty: Track? = null,
    val opacity: Track? = null,
    /** Degrees, about the layer's own middle (CSS `transform-box: fill-box`). */
    val rotate: Track? = null,
    val baseOpacity: Double = 1.0,
    val reducedTx: Double = 0.0,
    val reducedTy: Double = 0.0,
    val reducedOpacity: Double? = null,
) {
    fun delayed(ms: Int) = PxAnim(durationMs, ms, infinite, fillBoth, tx, ty, opacity, rotate, baseOpacity, reducedTx, reducedTy, reducedOpacity)

    /** Where the animation is at [elapsedMs] since it started: the phase 0…1, or null for "not running". */
    private fun phase(elapsedMs: Long): Double? {
        val t = elapsedMs - delayMs
        if (t < 0) return if (fillBoth) 0.0 else null
        if (infinite) return (t % durationMs).toDouble() / durationMs
        if (t >= durationMs) return if (fillBoth) 1.0 else null
        return t.toDouble() / durationMs
    }

    /** Adds this animation's effect at [elapsedMs] to [f]. */
    fun apply(elapsedMs: Long, reduced: Boolean, f: Frame) {
        if (reduced) {
            f.tx += reducedTx
            f.ty += reducedTy
            f.alpha *= reducedOpacity ?: baseOpacity
            return
        }
        val p = phase(elapsedMs)
        if (p == null) {
            f.alpha *= baseOpacity
            return
        }
        tx?.let { f.tx += it.at(p) }
        ty?.let { f.ty += it.at(p) }
        f.alpha *= opacity?.at(p) ?: baseOpacity
        rotate?.let { f.rotate += it.at(p) }
    }
}

/** A layer's place at one moment: offset in drawing pixels, opacity, turn. */
class Frame {
    var tx = 0.0
    var ty = 0.0
    var alpha = 1.0
    var rotate = 0.0

    fun reset() {
        tx = 0.0; ty = 0.0; alpha = 1.0; rotate = 0.0
    }
}

/** The web's sprite animations (app/globals.css), by their class names. */
object Px {
    // ── The state scenes (.px-*) ────────────────────────────────────────────
    val plug = PxAnim(2400, tx = tr(1, 0.0 to 0, .25 to 0, .30 to 2, .36 to 4, .62 to 4, .70 to 2, .78 to 0, 1.0 to 0))
    val spark = PxAnim(2400, baseOpacity = 0.0, reducedOpacity = 1.0,
        opacity = tr(1, 0.0 to 0, .38 to 0, .40 to 1, .46 to 0, .50 to 1, .56 to 0, 1.0 to 0))
    val sparkLate = spark.delayed(150)
    private val lensFrames = listOf(0.0 to (5 to 4), .12 to (5 to 4), .18 to (10 to 6), .30 to (10 to 6), .36 to (15 to 4), .48 to (15 to 4),
        .54 to (19 to 7), .66 to (19 to 7), .72 to (11 to 8), .84 to (11 to 8), .90 to (5 to 4), 1.0 to (5 to 4))
    val lens = PxAnim(3600,
        tx = Track(1, lensFrames.map { it.first to it.second.first.toDouble() }),
        ty = Track(1, lensFrames.map { it.first to it.second.second.toDouble() }),
        reducedTx = 5.0, reducedTy = 4.0)
    val question = PxAnim(3600, opacity = tr(1, 0.0 to 0, .60 to 0, .62 to 1, .74 to 1, .76 to 0, .78 to 1, .90 to 1, .92 to 0, 1.0 to 0))
    val shackle = PxAnim(1800, ty = tr(1, 0.0 to 0, .40 to 0, .45 to -1, .50 to -2, .62 to -2, .67 to -1, .72 to 0, 1.0 to 0))
    val tumble = PxAnim(4600, tx = tr(54, 0.0 to -11, .86 to 43, 1.0 to 43), reducedTx = 4.0)
    val hop = PxAnim(700, ty = tr(1, 0.0 to 0, .24 to 0, .25 to -1, .39 to -1, .40 to -2, .59 to -2, .60 to -1, .74 to -1, .75 to 0, 1.0 to 0))
    val turn = PxAnim(800, rotate = tr(4, 0.0 to 0, 1.0 to 360))
    val blink = PxAnim(1200, opacity = tr(1, 0.0 to 1, .59 to 1, .60 to .25, 1.0 to .25))
    val ray = PxAnim(1400, opacity = tr(1, 0.0 to 1, .49 to 1, .50 to 0, 1.0 to 0))
    val waves = PxAnim(2400, tx = tr(1, 0.0 to 0, .16 to 0, .17 to 1, .33 to 1, .34 to 2, .49 to 2, .50 to 3, .66 to 3, .67 to 4, .83 to 4, .84 to 5, 1.0 to 5))
    val cloud = PxAnim(9000, tx = tr(60, 0.0 to -16, 1.0 to 42), reducedTx = 2.0)
    val flag = PxAnim(900, opacity = tr(1, 0.0 to 1, .49 to 1, .50 to 0, 1.0 to 0))
    val flagAlt = PxAnim(900, opacity = tr(1, 0.0 to 0, .49 to 0, .50 to 1, 1.0 to 1), reducedOpacity = 0.0)
    val arrowR = PxAnim(1600, tx = tr(1, 0.0 to -1, .24 to -1, .25 to 0, .49 to 0, .50 to 1, .74 to 1, .75 to 0, 1.0 to 0))
    val arrowL = PxAnim(1600, tx = tr(1, 0.0 to 1, .24 to 1, .25 to 0, .49 to 0, .50 to -1, .74 to -1, .75 to 0, 1.0 to 0))
    val type = PxAnim(1500, baseOpacity = .25, reducedOpacity = 1.0,
        opacity = tr(1, 0.0 to 1, .30 to 1, .31 to .25, 1.0 to .25), ty = tr(1, 0.0 to -1, .30 to -1, .31 to 0, 1.0 to 0))
    val reply = PxAnim(3000, baseOpacity = 0.0, reducedOpacity = 1.0,
        opacity = tr(1, 0.0 to 0, .55 to 0, .56 to 1, .92 to 1, .93 to 0, 1.0 to 0),
        ty = tr(1, 0.0 to 1, .60 to 1, .61 to 0, 1.0 to 0))
    val z = PxAnim(2600, baseOpacity = 0.0, reducedOpacity = 1.0,
        opacity = tr(6, 0.0 to 0, .15 to 1, .80 to 1, 1.0 to 0), ty = tr(6, 0.0 to 5, 1.0 to -1))
    val swing = PxAnim(800, tx = tr(1, 0.0 to -1, .24 to -1, .25 to 0, .49 to 0, .50 to 1, .74 to 1, .75 to 0, 1.0 to 0))
    val clapper = PxAnim(800, tx = tr(1, 0.0 to 1, .24 to 1, .25 to 0, .49 to 0, .50 to -1, .74 to -1, .75 to 0, 1.0 to 0))
    val wave = PxAnim(800, baseOpacity = 0.0, reducedOpacity = 1.0, opacity = tr(1, 0.0 to 1, .30 to 1, .31 to 0, 1.0 to 0))
    val snow = PxAnim(2400,
        tx = tr(1, 0.0 to 0, .25 to 0, .37 to 1, .50 to 1, .62 to 0, .75 to 0, .88 to -1, 1.0 to -1),
        ty = tr(1, 0.0 to -2, .12 to -1, .25 to 0, .37 to 1, .50 to 2, .62 to 3, .75 to 4, .88 to 5, 1.0 to 6),
        opacity = tr(1, 0.0 to 0, .12 to 1, .88 to 1, 1.0 to 0))
    val flutter = PxAnim(2800,
        tx = tr(1, 0.0 to 0, .2 to -1, .4 to -2, .6 to -1, .8 to 1, 1.0 to 0),
        ty = tr(1, 0.0 to 0, .2 to -1, .4 to 0, .6 to 1, .8 to 0, 1.0 to 0))
    val fall = PxAnim(3300, baseOpacity = 0.0, reducedOpacity = 1.0,
        tx = tr(1, 0.0 to 0, .20 to 1, .35 to 2, .50 to 1, .65 to 0, .80 to 1, 1.0 to 2),
        ty = tr(1, 0.0 to 0, .20 to 1, .35 to 3, .50 to 4, .65 to 5, .80 to 6, 1.0 to 7),
        opacity = tr(1, 0.0 to 0, .08 to 1, .80 to 1, 1.0 to 0))

    // ── The mascot (.ms-*) ──────────────────────────────────────────────────
    val breathe = PxAnim(1800, ty = tr(1, 0.0 to 0, .49 to 0, .50 to 1, 1.0 to 1))
    val msHop = PxAnim(300, infinite = false, ty = tr(3, 0.0 to 0, .40 to -3, 1.0 to 0))
    val msJump = PxAnim(700, infinite = false, ty = tr(7, 0.0 to 0, .15 to 1, .45 to -6, .70 to -2, 1.0 to 0))
    val msShake = PxAnim(500, infinite = false, tx = tr(1, 0.0 to 0, .15 to -2, .35 to 2, .55 to -1, .75 to 1, 1.0 to 0))
    val msOpen = PxAnim(4400, delayMs = 1000, opacity = tr(1, 0.0 to 1, .92 to 1, .93 to 0, .96 to 0, .97 to 1, 1.0 to 1))
    val msBlink = PxAnim(4400, delayMs = 1000, baseOpacity = 0.0, reducedOpacity = 0.0,
        opacity = tr(1, 0.0 to 0, .92 to 0, .93 to 1, .96 to 1, .97 to 0, 1.0 to 0))
    val frameA = PxAnim(840, opacity = tr(1, 0.0 to 1, .24 to 1, .25 to 0, 1.0 to 0))
    val frameB = PxAnim(840, baseOpacity = 0.0, reducedOpacity = 0.0,
        opacity = tr(1, 0.0 to 0, .24 to 0, .25 to 1, .49 to 1, .50 to 0, .74 to 0, .75 to 1, 1.0 to 1))
    val frameC = PxAnim(840, baseOpacity = 0.0, reducedOpacity = 0.0,
        opacity = tr(1, 0.0 to 0, .49 to 0, .50 to 1, .74 to 1, .75 to 0, 1.0 to 0))
    val fume = PxAnim(400, tx = tr(1, 0.0 to 0, .25 to -1, .75 to 1, 1.0 to 0))
    val throb = PxAnim(600, tx = tr(1, 0.0 to 0, .49 to 0, .50 to 1, 1.0 to 1), ty = tr(1, 0.0 to 0, .49 to 0, .50 to -1, 1.0 to -1))
    val steam = PxAnim(1200, ty = tr(4, 0.0 to 0, 1.0 to -4), opacity = tr(4, 0.0 to 1, 1.0 to 0))
    val buzz = PxAnim(320, tx = tr(1, 0.0 to -1, .25 to 1, .50 to -1, .75 to 1, 1.0 to -1), ty = tr(1, 0.0 to 0, .25 to -2, .50 to -3, .75 to -1, 1.0 to 0))
    val zapA = PxAnim(360, opacity = tr(1, 0.0 to 1, .49 to 1, .50 to 0, 1.0 to 0))
    val zapB = PxAnim(360, delayMs = 180, baseOpacity = 0.0, reducedOpacity = 0.0, opacity = tr(1, 0.0 to 1, .49 to 1, .50 to 0, 1.0 to 0))
    val pop = PxAnim(240, infinite = false, fillBoth = true, opacity = tr(3, 0.0 to 0, 1.0 to 1), ty = tr(3, 0.0 to 2, 1.0 to 0))
    val drip = PxAnim(1100, ty = tr(3, 0.0 to 0, 1.0 to 3), opacity = tr(3, 0.0 to 1, 1.0 to .4))

    // ── The Mensa plate (.pxp-*) ────────────────────────────────────────────
    val wisp = PxAnim(2800, baseOpacity = 0.0, reducedOpacity = 0.7,
        opacity = tr(7, 0.0 to 0, .20 to 1, .70 to .8, 1.0 to 0), ty = tr(7, 0.0 to 2, 1.0 to -5))
    val plateSpark = PxAnim(4000, delayMs = 800, baseOpacity = 0.0,
        opacity = tr(1, 0.0 to 0, .78 to 0, .80 to 1, .84 to .35, .88 to 1, .92 to 0, 1.0 to 0))
    val glint = PxAnim(5000, delayMs = 1400, baseOpacity = 0.0,
        opacity = tr(20, 0.0 to 0, .60 to 0, .61 to 1, .80 to 1, .81 to 0, 1.0 to 0),
        ty = tr(20, 0.0 to 0, .61 to 0, .80 to 20, 1.0 to 20))
}
