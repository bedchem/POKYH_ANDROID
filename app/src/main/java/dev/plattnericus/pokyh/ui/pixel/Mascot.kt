package dev.plattnericus.pokyh.ui.pixel

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * POKYH's mascot as a sprite — the web frontend's `components/pixel/mascot.ts`: a soft indigo
 * block, a little tilted, with big white eyes and dark upright pupils, pink blush under the eyes,
 * a tiny smile, stubby arms at the lower sides and two short legs with round feet. 40 × 37
 * pixels, the feet on row 33, its shadow below. A pose is built from the eyes, where they look, a
 * mouth and what the arms do; every pose is drawn once and kept.
 */
object Mascot {
    const val W = 40
    const val H = 37

    enum class Eyes { Open, Blink, Happy, Wide, Closed, Angry }
    enum class Mouth { Smile, Grin, O, Wobble, Flat, Frown }
    enum class Arms { Down, Wave, Wave2, Wave3, Cover, Peek, Thumb, Lens, Cheer, Fists }

    data class Pose(val eyes: Eyes, val look: Pair<Int, Int>, val mouth: Mouth, val arms: Arms)

    private fun p(x: Int, y: Int) = Pt(x, y)

    /** A rounded rectangle turned by [deg] about its middle, in whole pixels. */
    private fun tilted(cx: Double, cy: Double, w: Double, h: Double, r: Double, deg: Double): List<Pt> {
        val a = deg * PI / 180
        val c = cos(a)
        val s = sin(a)
        val out = ArrayList<Pt>()
        val reach = ceil(hypot(w, h) / 2).toInt() + 1
        for (y in floor(cy - reach).toInt()..ceil(cy + reach).toInt()) {
            for (x in floor(cx - reach).toInt()..ceil(cx + reach).toInt()) {
                val dx = x + 0.5 - cx
                val dy = y + 0.5 - cy
                val u = kotlin.math.abs(dx * c + dy * s)
                val v = kotlin.math.abs(-dx * s + dy * c)
                val ex = max(0.0, u - (w / 2 - r))
                val ey = max(0.0, v - (h / 2 - r))
                if (u <= w / 2 && v <= h / 2 && hypot(ex, ey) <= r) out.add(p(x, y))
            }
        }
        return out
    }

    /** Drops what the turning leaves sticking out of the top: a row one to three pixels wide above a much wider one. */
    private fun trimmed(shape: List<Pt>): List<Pt> {
        val rows = HashMap<Int, Int>()
        for (q in shape) rows[q.y] = (rows[q.y] ?: 0) + 1
        val top = rows.keys.min()
        return if ((rows[top] ?: 0) <= 3 && (rows[top + 1] ?: 0) > 6) shape.filter { it.y != top } else shape
    }

    // The block leans a little to the left: its right side is higher.
    private val BODY = trimmed(tilted(20.0, 13.6, 27.0, 25.4, 8.0, -5.0))
    private val EYE_L = 15.0 to 12.6
    private val EYE_R = 24.6 to 11.2

    // Stubby arms out of the lower sides, behind the body; one raised beside the head for a wave
    // (out, up, in towards the head), both up for a cheer, fists for anger.
    private val ARM_L = limb(7.4, 19.4, 4.4, 23.4, 2.6)
    private val ARM_R = limb(32.8, 17.8, 35.6, 21.8, 2.6)
    private fun hand(x0: Double, y0: Double, x1: Double, y1: Double) = limb(x0, y0, x1, y1, 2.4) + disc(x1, y1, 2.9)
    private val RAISED_R = hand(32.2, 13.4, 36.8, 9.4)
    private val RAISED_R2 = hand(32.2, 13.4, 35.8, 6.2)
    private val RAISED_R3 = hand(32.2, 13.4, 33.8, 4.2)
    private val CHEER_L = hand(9.0, 15.5, 4.2, 8.6)
    private val CHEER_R = hand(31.0, 14.0, 35.8, 7.0)
    private val FIST_L = hand(8.6, 19.0, 4.4, 14.4)
    private val FIST_R = hand(31.6, 17.4, 35.8, 12.8)

    private fun legs(s: Sprite) {
        s.mass(rect(13, 24, 5, 7) + ellipse(14.0, 31.0, 3.9, 2.0), "ac", deep = true)
        s.mass(rect(22, 23, 5, 8) + ellipse(26.0, 31.0, 3.9, 2.0), "ac", deep = true)
    }

    private val PUPIL = mapOf('1' to "pu1", '2' to "pu2", '3' to "pu3", '4' to "pu4")

    private fun eye(s: Sprite, centre: Pair<Double, Double>, kind: Eyes, look: Pair<Int, Int>, right: Boolean, big: Boolean = false) {
        val (cx, cy) = centre
        val (lx, ly) = look
        val x = floor(cx).toInt()
        val y = floor(cy).toInt()
        when (kind) {
            Eyes.Blink -> { s.fill(rect(x - 2, y + 1, 5, 1), "pu0"); return }
            // Shut tight: ◡
            Eyes.Closed -> { s.fill(listOf(p(x - 2, y), p(x - 1, y + 1), p(x, y + 1), p(x + 1, y + 1), p(x + 2, y)), "pu0"); return }
            Eyes.Angry -> {
                // Open, but a brow pushed down towards the middle cuts the top off.
                eye(s, centre, Eyes.Open, look, right, big)
                val inward = if (right) -1 else 1
                for (i in -4..4) {
                    val bx = x + i
                    val by = jsRound(y - 4 + (i * inward + 4) * 0.42)
                    var yy = by - 1
                    while (yy >= y - 6) {
                        val tone = s.get(bx, yy)
                        if (tone != null && (tone.startsWith("pp") || tone.startsWith("pu"))) s.set(bx, yy, "ac2")
                        yy--
                    }
                    s.set(bx, by, "pu0")
                    s.set(bx, by - 1, "pu0")
                }
                return
            }
            // Smiling eyes: ^, two pixels thick in the middle.
            Eyes.Happy -> { s.fill(listOf(p(x - 2, y + 1), p(x - 1, y), p(x, y - 1), p(x + 1, y), p(x + 2, y + 1), p(x, y)), "pu0"); return }
            else -> Unit
        }
        // The white, the right one a touch bigger (it is nearer), and a big dark upright pupil.
        val grow = (if (right) 0.2 else 0.0) + (if (kind == Eyes.Wide || big) 0.6 else 0.0)
        s.fill(ellipse(cx, cy, 3.7 + grow, 4.3 + grow), "pp3")
        if (big) {
            // Behind the magnifier: the pupil half as big again.
            val px = jsRound(cx + 0.4 + lx * 0.8) - 3
            val py = jsRound(cy - 0.3 + ly * 0.8) - 4
            s.rows(listOf(".1111.", "114411", "114111", "111122", "122222", "222233", "223333", ".3333."), px, py, PUPIL)
            return
        }
        val px = jsRound(cx + 0.4 + lx * 1.2) - 2
        val py = jsRound(cy - 0.3 + ly * 1.1) - 3
        s.rows(listOf(".11.", "1411", "1122", "2222", "2233", ".33."), px, py, PUPIL)
    }

    private fun mouth(s: Sprite, kind: Mouth) {
        when (kind) {
            Mouth.Smile -> s.fill(listOf(p(19, 17), p(20, 18), p(21, 18), p(22, 17)), "ac0")
            Mouth.Flat -> s.fill(rect(19, 18, 3, 1), "ac0")
            Mouth.Wobble -> s.fill(listOf(p(18, 18), p(19, 17), p(20, 18), p(21, 17), p(22, 18)), "ac0")
            Mouth.O -> s.fill(listOf(p(20, 17), p(21, 17), p(20, 18), p(21, 18)), "pu0")
            Mouth.Frown -> s.fill(listOf(p(18, 19), p(19, 18), p(20, 17), p(21, 17), p(22, 18), p(23, 19)), "pu0")
            Mouth.Grin -> {
                s.fill(rect(18, 17, 5, 1) + rect(19, 18, 3, 1), "pu0")
                s.set(20, 18, "pk1")
            }
        }
    }

    private fun cheeks(s: Sprite, flushed: Boolean) {
        for ((cx, cy) in listOf(11.3 to 18.6, 28.6 to 17.0)) {
            // Angry: red right across.
            s.fill(ellipse(cx, cy, if (flushed) 3.0 else 2.4, if (flushed) 1.5 else 1.2), if (flushed) "rd2" else "pk2")
            s.set(floor(cx).toInt() - 1, floor(cy).toInt(), if (flushed) "rd3" else "pk3")
        }
    }

    /** A hand in front of the body, with its own outline. */
    private fun mitt(s: Sprite, shape: List<Pt>, fingers: List<Pt> = emptyList()) {
        s.mass(shape, "ac", deep = true)
        s.fill(fingers, "ac1")
    }

    private val cache = HashMap<Pose, Sprite>()

    fun sprite(pose: Pose): Sprite = cache.getOrPut(pose) {
        val s = Sprite()
        legs(s)
        val a = pose.arms
        // Hanging arms go behind the body's outline (only what reaches out shows); raised ones in
        // front of its edge, so the waving hand is always seen.
        val raised = when (a) {
            Arms.Wave -> listOf(RAISED_R)
            Arms.Wave2 -> listOf(RAISED_R2)
            Arms.Wave3 -> listOf(RAISED_R3)
            Arms.Cheer -> listOf(CHEER_L, CHEER_R)
            Arms.Fists -> listOf(FIST_L, FIST_R)
            else -> emptyList()
        }
        val hanging = when (a) {
            Arms.Down -> listOf(ARM_L, ARM_R)
            Arms.Lens, Arms.Thumb, Arms.Wave, Arms.Wave2, Arms.Wave3 -> listOf(ARM_L)
            else -> emptyList()
        }
        for (arm in hanging) mitt(s, arm)
        s.mass(BODY, "ac", deep = true)
        for (arm in raised) mitt(s, arm)
        val lens = a == Arms.Lens
        eye(s, EYE_L, pose.eyes, pose.look, false)
        eye(s, EYE_R, pose.eyes, pose.look, true, lens && (pose.eyes == Eyes.Open || pose.eyes == Eyes.Wide))
        cheeks(s, pose.eyes == Eyes.Angry)
        mouth(s, pose.mouth)

        when (a) {
            Arms.Cover, Arms.Peek -> {
                // Hands flat over the eyes.
                mitt(s, limb(8.5, 20.0, 11.0, 16.5, 2.4) + soft(11, 9, 8, 8, 3.5), listOf(p(14, 9), p(16, 9)))
                if (a == Arms.Cover) mitt(s, limb(32.0, 18.5, 29.5, 15.0, 2.4) + soft(21, 7, 8, 8, 3.5), listOf(p(23, 7), p(25, 7)))
                else mitt(s, limb(32.0, 19.0, 30.5, 18.0, 2.4) + soft(26, 16, 7, 6, 2.5))
            }
            Arms.Thumb -> {
                // A little fist on the right, thumb up.
                mitt(s, limb(32.5, 18.0, 31.5, 16.5, 2.4) + soft(28, 12, 8, 7, 2.5), listOf(p(30, 15), p(31, 15), p(32, 15), p(33, 15)))
                mitt(s, soft(29, 7, 3, 6, 1.4))
            }
            Arms.Lens -> {
                // A magnifier held to the right eye, the eye big behind it.
                val (cx, cy) = EYE_R
                s.mass(minus(disc(cx, cy, 6.7), disc(cx, cy, 5.3)), "wd", round = cx to cy)
                s.mass(line(29, 16, 32, 19) + line(30, 16, 33, 19), "wd")
                mitt(s, limb(33.5, 22.0, 33.5, 20.5, 2.4) + soft(30, 18, 7, 6, 2.5), listOf(p(32, 20), p(33, 20)))
            }
            else -> Unit
        }
        s
    }

    /** The magnifier's glass over the mascot: a faint blue the eye shows through, a glint at its top left. */
    val LENS_GLASS: Sprite = Sprite().fill(disc(EYE_R.first, EYE_R.second, 4.8), "gl").fill(listOf(p(21, 8), p(22, 7), p(21, 9)), "pp4")

    /** Its shadow on the floor, apart from the body so it stays down when the body hops. */
    val SHADOW: Sprite = Sprite().fill(dither(ellipse(20.0, 34.5, 15.0, 1.6)), "sh").fill(dither(ellipse(20.0, 34.5, 9.0, 1.2), 1), "shd")
}

// ── The signs around it (the web's components/pixel/hallway.ts) ──────────────

/** Splits [text] into lines at most [maxWidth] pixels wide (at most [maxLines], the last one cut with "…"). */
fun wrapText(text: String, maxWidth: Int, maxLines: Int = 3): List<String> {
    fun width(t: String) = textPixels(t).width
    val lines = ArrayList<String>()
    var line = ""
    fun flush() { if (line.isNotEmpty()) lines.add(line); line = "" }
    val separators = Regex("(?<=[-._/])")
    for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        // A word breaks after its own separators first ("anna-maria." → "anna-", "maria.").
        word.split(separators).filter { it.isNotEmpty() }.forEachIndexed { i, part ->
            val joined = if (line.isNotEmpty()) "$line${if (i == 0) " " else ""}$part" else part
            if (width(joined) <= maxWidth) { line = joined; return@forEachIndexed }
            flush()
            var rest = part
            while (width(rest) > maxWidth) {
                var cut = rest.length - 1
                while (cut > 1 && width(rest.substring(0, cut)) > maxWidth) cut -= 1
                lines.add(rest.substring(0, cut))
                rest = rest.substring(cut)
            }
            line = rest
        }
    }
    flush()
    if (lines.size > maxLines) {
        val kept = lines.subList(0, maxLines).toMutableList()
        var last = kept[maxLines - 1].replace(Regex("[-._/]$"), "")
        while (last.length > 1 && width("$last…") > maxWidth) last = last.dropLast(1)
        kept[maxLines - 1] = "$last…"
        return kept
    }
    return lines.ifEmpty { listOf("") }
}

/** Line pitch of the bubble text: the 5-pixel font and a pixel between lines. */
private const val LINE = 6

/**
 * A speech bubble with text in capitals; the tail points down-left to (x, y). Text wider than
 * [maxWidth] pixels goes on more lines and the bubble grows upwards, the tail staying put.
 */
fun bubble(text: String, x: Int, y: Int, maxWidth: Int = Int.MAX_VALUE): Sprite {
    val lines = wrapText(text, maxWidth)
    val widths = lines.map { textPixels(it).width }
    val bw = widths.max() + 8
    val bh = lines.size * LINE + 5
    val bx = x + 2
    val by = y - 2 - bh
    val s = Sprite()
    s.mass(soft(bx, by, bw, bh, 3.0) + listOf(Pt(x + 3, y - 2), Pt(x + 4, y - 2), Pt(x + 2, y - 1), Pt(x + 3, y - 1), Pt(x + 1, y)), "pp")
    // A firm outline, so it reads over anything behind it.
    val outline = ArrayList<Pt>()
    s.forEachCell { px, py, tone -> if (tone == "pp0") outline.add(Pt(px, py)) }
    s.fill(outline, "ik2")
    lines.forEachIndexed { i, l ->
        val t = textPixels(l, 0, 0)
        val lx = bx + 4 + Math.floorDiv(bw - 8 - widths[i], 2)
        s.fill(t.points.map { Pt(it.x + lx, it.y + by + 3 + i * LINE) }, "ik2")
    }
    return s
}

/** A sign above the head: '!' or '?'. */
fun markSprite(kind: Char, x: Int, y: Int): Sprite {
    val rows = if (kind == '!') listOf("##", "##", "##", "##", "..", "##")
    else listOf(".###.", "##.##", "...##", "..##.", ".....", "..##.")
    return Sprite().mass(grid(rows, x, y), "gd")
}

/** The anger sign of comics (a throbbing vein) beside the head. */
fun angerSprite(x: Int, y: Int): Sprite =
    Sprite().mass(grid(listOf("..#.#..", ".##.##.", "##...##", ".......", "##...##", ".##.##.", "..#.#.."), x, y), "rd")

/** A puff of steam rising off the head: a bigger cloud and a small one above it. */
fun steamSprite(x: Int, y: Int): Sprite =
    Sprite().mass(disc(x + 2.5, y + 4.0, 2.6) + disc(x + 5.0, y + 3.2, 2.0) + disc(x + 1.2, y + 0.4, 1.3), "pp")

/** A drop of sweat beside the head. */
fun sweatSprite(x: Int, y: Int): Sprite = Sprite()
    .mass(listOf(Pt(x + 1, y), Pt(x, y + 1), Pt(x + 1, y + 1), Pt(x, y + 2), Pt(x + 1, y + 2), Pt(x + 2, y + 2), Pt(x + 1, y + 3)), "wt")
    .set(x, y + 1, "wt4")

/** "…" in a small thought bubble. */
fun thinkingSprite(x: Int, y: Int): Sprite {
    val s = Sprite().mass(soft(x, y, 13, 7, 2.5), "pp").mass(rounded(x - 2, y + 7, 2, 2, 0), "pp")
    s.fill(listOf(Pt(x + 3, y + 3), Pt(x + 6, y + 3), Pt(x + 9, y + 3)), "ik2")
    return s
}

// ── The mascot as it moves (the web's components/pixel/MascotFigure.tsx) ──────

/** How the whole figure moves: breathing on the spot, a hop per key, a jump, a shake, fuming, buzzing. */
enum class MascotMotion { Bob, Hop, Jump, Shake, Fume, Buzz }

data class MascotLook(
    val pose: Mascot.Pose,
    val blink: Boolean = false,
    val wave: Boolean = false,
    val mark: Char? = null,
    val sweat: Boolean = false,
    val think: Boolean = false,
    val anger: Boolean = false,
    val motion: MascotMotion = MascotMotion.Bob,
)

private val STEAM = steamSprite(1, -6)
private val ANGER = angerSprite(22, -9)
private val SWEAT = sweatSprite(31, 3)

/**
 * The mascot's layers at (0, 0): its shadow, the body with the frames of its animations
 * (breathing, blinking, waving, hopping, jumping, shaking, fuming), the signs beside it, and the
 * magnifier's glass. [motionKey] restarts the motion — a new key per keystroke makes it hop again.
 * [marksBeside] puts the signs beside the head (small drawings) instead of above it.
 */
fun mascotLayers(look: MascotLook, motionKey: Any, marksBeside: Boolean = true): List<PixelLayer> {
    val pose = look.pose
    val base = Mascot.sprite(pose)
    val wave2 = if (look.wave) Mascot.sprite(pose.copy(arms = Mascot.Arms.Wave2)) else null
    val wave3 = if (look.wave) Mascot.sprite(pose.copy(arms = Mascot.Arms.Wave3)) else null
    val blink = if (wave2 == null && look.blink && pose.eyes == Mascot.Eyes.Open) Mascot.sprite(pose.copy(eyes = Mascot.Eyes.Blink)) else null
    val (mx, my) = if (marksBeside) Mascot.W to 2 else 16 to -10
    val key = "motion:$motionKey"
    val motion = when (look.motion) {
        MascotMotion.Hop -> listOf(Px.msHop)
        MascotMotion.Jump -> listOf(Px.msJump)
        MascotMotion.Shake -> listOf(Px.msShake)
        MascotMotion.Fume -> listOf(Px.fume)
        MascotMotion.Buzz -> listOf(Px.buzz)
        MascotMotion.Bob -> emptyList()
    }
    val body = if (look.motion == MascotMotion.Bob) motion + Px.breathe else motion
    fun L(sprite: Sprite, extra: List<PxAnim> = emptyList(), anims: List<PxAnim> = body, start: Any = key) =
        PixelLayer(sprite, anims + extra, startKey = start)

    return buildList {
        add(PixelLayer(Mascot.SHADOW))
        add(L(base, if (wave2 != null) listOf(Px.frameA) else if (blink != null) listOf(Px.msOpen) else emptyList()))
        if (wave2 != null) add(L(wave2, listOf(Px.frameB)))
        if (wave3 != null) add(L(wave3, listOf(Px.frameC)))
        if (blink != null) add(L(blink, listOf(Px.msBlink)))
        if (pose.arms == Mascot.Arms.Lens) add(L(Mascot.LENS_GLASS))
        if (look.sweat) add(L(SWEAT, listOf(Px.drip)))
        // The signs pop up (each one restarting with the motion, as the web remounts them).
        look.mark?.let { add(L(markSprite(it, mx, my), listOf(Px.pop), anims = motion, start = "$key/mark$it")) }
        if (look.think) {
            add(L(thinkingSprite(if (marksBeside) Mascot.W - 2 else 24, if (marksBeside) 1 else -9), listOf(Px.pop), anims = motion, start = "$key/think"))
        }
        if (look.anger) {
            add(L(STEAM, listOf(Px.pop, Px.steam), anims = motion, start = "$key/anger"))
            add(L(ANGER, listOf(Px.pop, Px.throb), anims = motion, start = "$key/anger"))
        }
    }
}
