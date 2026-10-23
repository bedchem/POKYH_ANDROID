package dev.plattnericus.pokyh.ui.pixel

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Pixel sprites drawn in code — a port of the web frontend's `components/pixel/sprite.ts`, so the
 * drawings come out pixel for pixel the same: square pixels, a few flat tones per material, light
 * from the top left, an outline in the material's darkest tone and dithered shadows. A sprite is a
 * grid of tones; [PixelPalette] turns every tone into a colour for the current theme.
 *
 * A tone is a material and a step: 0 outline, 1 shade, 2 base, 3 light, 4 highlight, e.g. `ac2`
 * (the accent) or `pp3` (lit paper). A few tones are single colours without steps (`sh`, `gl`, …).
 */
data class Pt(val x: Int, val y: Int)

private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
private fun keyX(k: Long): Int = (k shr 32).toInt()
private fun keyY(k: Long): Int = k.toInt()

class Sprite {
    internal val cells = LinkedHashMap<Long, String>()

    fun set(x: Int, y: Int, tone: String?): Sprite {
        if (tone != null) cells[key(x, y)] = tone else cells.remove(key(x, y))
        return this
    }

    fun get(x: Int, y: Int): String? = cells[key(x, y)]

    fun fill(points: Iterable<Pt>, tone: String?): Sprite {
        for (p in points) set(p.x, p.y, tone)
        return this
    }

    /** Rows of characters at (ox, oy); `legend` names each character's tone, '.' and ' ' stay empty. */
    fun rows(rows: List<String>, ox: Int, oy: Int, legend: Map<Char, String?>): Sprite {
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c == '.' || c == ' ') return@forEachIndexed
                if (legend.containsKey(c)) set(ox + x, oy + y, legend[c])
            }
        }
        return this
    }

    /** Another sprite on top of this one. */
    fun paste(other: Sprite, dx: Int = 0, dy: Int = 0): Sprite {
        for ((k, tone) in other.cells) set(keyX(k) + dx, keyY(k) + dy, tone)
        return this
    }

    /**
     * A shape in a material: lit where it faces the top left, shaded where it faces away,
     * outlined in the material's darkest tone. With [round] the light follows the direction from
     * that centre (balls, rings); `outline = false` leaves the outline off (shapes inside others).
     */
    fun mass(mask: List<Pt>, mat: String, outline: Boolean = true, round: Pair<Double, Double>? = null, deep: Boolean = false): Sprite {
        val on = HashSet<Long>(mask.size * 2)
        for (p in mask) on.add(key(p.x, p.y))
        fun m(x: Int, y: Int) = key(x, y) in on
        if (outline) for (p in outlineOf(mask)) set(p.x, p.y, "${mat}0")
        for (p in mask) {
            val x = p.x
            val y = p.y
            var step = 2
            if (round != null) {
                val f = facing(round, x, y)
                val edge = !m(x - 1, y) || !m(x + 1, y) || !m(x, y - 1) || !m(x, y + 1) ||
                    !m(x - 2, y) || !m(x + 2, y) || !m(x, y - 2) || !m(x, y + 2)
                if (edge && f > 0.35) step = 3
                else if (f < -0.3 && (edge || deep)) step = 1
            } else if (!m(x, y - 1) || !m(x - 1, y)) {
                step = 3
            } else if (!m(x, y + 1) || !m(x + 1, y) || (deep && (!m(x + 1, y + 1) || !m(x, y + 2) || !m(x + 2, y)))) {
                step = 1
            }
            set(x, y, "$mat$step")
        }
        return this
    }

    /** Replaces one material by another, step for step (stripes, recolouring). */
    fun recolor(points: List<Pt>, from: String, to: String): Sprite {
        for (p in points) {
            val tone = get(p.x, p.y) ?: continue
            if (tone.startsWith(from) && tone.length == from.length + 1 && tone.last().isDigit()) {
                set(p.x, p.y, to + tone.last())
            }
        }
        return this
    }

    /** The same sprite moved by (dx, dy). */
    fun shifted(dx: Int, dy: Int): Sprite = Sprite().paste(this, dx, dy)

    /** Every cell as (x, y, tone). */
    fun forEachCell(action: (Int, Int, String) -> Unit) {
        for ((k, tone) in cells) action(keyX(k), keyY(k), tone)
    }

    val isEmpty: Boolean get() = cells.isEmpty()

    /** The cells' bounding box: [minX, minY, maxX, maxY], or null for an empty sprite. */
    fun bounds(): IntArray? {
        if (cells.isEmpty()) return null
        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = Int.MIN_VALUE
        var y1 = Int.MIN_VALUE
        for (k in cells.keys) {
            val x = keyX(k)
            val y = keyY(k)
            if (x < x0) x0 = x
            if (y < y0) y0 = y
            if (x > x1) x1 = x
            if (y > y1) y1 = y
        }
        return intArrayOf(x0, y0, x1, y1)
    }

    /** The topmost row the sprite reaches (for room above it). */
    fun top(): Int = bounds()?.get(1) ?: 0
}

// ── Shapes (lists of pixels) ─────────────────────────────────────────────

/** The pixels marked with [ch] in rows of characters, placed at (ox, oy). */
fun grid(rows: List<String>, ox: Int = 0, oy: Int = 0, ch: Char = '#'): List<Pt> {
    val out = ArrayList<Pt>()
    rows.forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c == ch) out.add(Pt(ox + x, oy + y)) } }
    return out
}

fun rect(x: Int, y: Int, w: Int, h: Int): List<Pt> {
    val out = ArrayList<Pt>(max(0, w * h))
    for (j in 0 until h) for (i in 0 until w) out.add(Pt(x + i, y + j))
    return out
}

/** A rectangle with its corners cut by [r] steps. */
fun rounded(x: Int, y: Int, w: Int, h: Int, r: Int = 1): List<Pt> = rect(x, y, w, h).filter { (px, py) ->
    val dx = maxOf(x + r - px, px - (x + w - 1 - r), 0)
    val dy = maxOf(y + r - py, py - (y + h - 1 - r), 0)
    dx + dy <= r
}

/** A rectangle with round corners of radius [r] (soft shapes; [rounded] cuts them straight). */
fun soft(x: Int, y: Int, w: Int, h: Int, r: Double): List<Pt> = rect(x, y, w, h).filter { (px, py) ->
    val cx = min(max(px + 0.5, x + r), x + w - r)
    val cy = min(max(py + 0.5, y + r), y + h - r)
    hypot(px + 0.5 - cx, py + 0.5 - cy) <= r - 0.2
}

/** A thick stroke: discs of radius [r] along a line. */
fun limb(x0: Double, y0: Double, x1: Double, y1: Double, r: Double): List<Pt> {
    val out = LinkedHashMap<Long, Pt>()
    val steps = max(1, ceil(hypot(x1 - x0, y1 - y0) * 2).toInt())
    for (i in 0..steps) {
        val t = i.toDouble() / steps
        for (p in disc(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, r)) out[key(p.x, p.y)] = p
    }
    return out.values.toList()
}

/** Pixels whose centre lies within [r] of (cx, cy). */
fun disc(cx: Double, cy: Double, r: Double): List<Pt> = ellipse(cx, cy, r, r)

fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double): List<Pt> {
    val out = ArrayList<Pt>()
    var y = floor(cy - ry - 1).toInt()
    val yEnd = ceil(cy + ry + 1).toInt()
    while (y <= yEnd) {
        var x = floor(cx - rx - 1).toInt()
        val xEnd = ceil(cx + rx + 1).toInt()
        while (x <= xEnd) {
            val dx = (x + 0.5 - cx) / rx
            val dy = (y + 0.5 - cy) / ry
            if (dx * dx + dy * dy <= 1) out.add(Pt(x, y))
            x++
        }
        y++
    }
    return out
}

/** A one-pixel line (Bresenham). */
fun line(x0In: Int, y0In: Int, x1: Int, y1: Int): List<Pt> {
    var x0 = x0In
    var y0 = y0In
    val out = ArrayList<Pt>()
    val dx = abs(x1 - x0)
    val dy = -abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy
    while (true) {
        out.add(Pt(x0, y0))
        if (x0 == x1 && y0 == y1) return out
        val e2 = 2 * err
        if (e2 >= dy) { err += dy; x0 += sx }
        if (e2 <= dx) { err += dx; y0 += sy }
    }
}

fun shift(points: List<Pt>, dx: Int, dy: Int): List<Pt> = points.map { Pt(it.x + dx, it.y + dy) }

fun minus(points: List<Pt>, cut: List<Pt>): List<Pt> {
    val gone = cut.mapTo(HashSet()) { key(it.x, it.y) }
    return points.filter { key(it.x, it.y) !in gone }
}

/** The pixels right around a shape (above, below, left, right). */
fun outlineOf(shape: List<Pt>): List<Pt> {
    val on = shape.mapTo(HashSet()) { key(it.x, it.y) }
    val out = LinkedHashMap<Long, Pt>()
    for ((x, y) in shape) {
        for ((nx, ny) in listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
            val k = key(nx, ny)
            if (k !in on) out[k] = Pt(nx, ny)
        }
    }
    return out.values.toList()
}

/** Every other pixel of a shape, for dithered shadows and light. */
fun dither(points: List<Pt>, parity: Int = 0): List<Pt> = points.filter { Math.floorMod(it.x + it.y, 2) == parity }

/** How much a pixel faces the light from the top left, seen from [centre] (-1 … 1). */
fun facing(centre: Pair<Double, Double>, x: Int, y: Int): Double {
    val dx = x + 0.5 - centre.first
    val dy = y + 0.5 - centre.second
    val d = hypot(dx, dy).takeIf { it != 0.0 } ?: 1.0
    return (dx / d) * -0.61 + (dy / d) * -0.79
}

// ── A 3 × 5 pixel font (capitals, digits, a few signs) ───────────────────

private val GLYPHS: Map<Char, List<String>> = mapOf(
    'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"), 'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
    'C' to listOf(".##", "#..", "#..", "#..", ".##"), 'D' to listOf("##.", "#.#", "#.#", "#.#", "##."),
    'E' to listOf("###", "#..", "##.", "#..", "###"), 'F' to listOf("###", "#..", "##.", "#..", "#.."),
    'G' to listOf(".##", "#..", "#.#", "#.#", ".##"), 'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"),
    'I' to listOf("###", ".#.", ".#.", ".#.", "###"), 'J' to listOf("..#", "..#", "..#", "#.#", ".#."),
    'K' to listOf("#.#", "#.#", "##.", "#.#", "#.#"), 'L' to listOf("#..", "#..", "#..", "#..", "###"),
    'M' to listOf("#.#", "###", "###", "#.#", "#.#"), 'N' to listOf("##.", "#.#", "#.#", "#.#", "#.#"),
    'O' to listOf(".#.", "#.#", "#.#", "#.#", ".#."), 'P' to listOf("##.", "#.#", "##.", "#..", "#.."),
    'Q' to listOf(".#.", "#.#", "#.#", "##.", ".##"), 'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"),
    'S' to listOf(".##", "#..", ".#.", "..#", "##."), 'T' to listOf("###", ".#.", ".#.", ".#.", ".#."),
    'U' to listOf("#.#", "#.#", "#.#", "#.#", "###"), 'V' to listOf("#.#", "#.#", "#.#", ".#.", ".#."),
    'W' to listOf("#.#", "#.#", "###", "###", "#.#"), 'X' to listOf("#.#", "#.#", ".#.", "#.#", "#.#"),
    'Y' to listOf("#.#", "#.#", ".#.", ".#.", ".#."), 'Z' to listOf("###", "..#", ".#.", "#..", "###"),
    '0' to listOf("###", "#.#", "#.#", "#.#", "###"), '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
    '2' to listOf("##.", "..#", ".#.", "#..", "###"), '3' to listOf("##.", "..#", ".#.", "..#", "##."),
    '4' to listOf("#.#", "#.#", "###", "..#", "..#"), '5' to listOf("###", "#..", "##.", "..#", "##."),
    '6' to listOf(".##", "#..", "###", "#.#", "###"), '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
    '8' to listOf("###", "#.#", "###", "#.#", "###"), '9' to listOf("###", "#.#", "###", "..#", "##."),
    '!' to listOf("#", "#", "#", ".", "#"), '?' to listOf("##.", "..#", ".#.", "...", ".#."),
    '.' to listOf(".", ".", ".", ".", "#"), ',' to listOf("..", "..", "..", ".#", "#."),
    ':' to listOf(".", "#", ".", "#", "."), '-' to listOf("...", "...", "###", "...", "..."),
    '_' to listOf("...", "...", "...", "...", "###"), '\'' to listOf("#", "#", ".", ".", "."),
    '’' to listOf("#", "#", ".", ".", "."),
    '/' to listOf("..#", "..#", ".#.", "#..", "#.."), '@' to listOf("###", "#.#", "#.#", "#..", ".##"),
    '+' to listOf("...", ".#.", "###", ".#.", "..."), '…' to listOf(".....", ".....", ".....", ".....", "#.#.#"),
    '(' to listOf(".#", "#.", "#.", "#.", ".#"), ')' to listOf("#.", ".#", ".#", ".#", "#."),
    '=' to listOf("...", "###", "...", "###", "..."), '<' to listOf("..#", ".#.", "#..", ".#.", "..#"),
    '>' to listOf("#..", ".#.", "..#", ".#.", "#.."), '%' to listOf("#.#", "..#", ".#.", "#..", "#.#"),
    '*' to listOf("...", "#.#", ".#.", "#.#", "..."), '×' to listOf("...", "#.#", ".#.", "#.#", "..."),
    '÷' to listOf(".#.", "...", "###", "...", ".#."), '♥' to listOf(".#.#.", "#####", "#####", ".###.", "..#.."),
    '★' to listOf("..#..", "#####", ".###.", ".#.#.", "#...#"), '#' to listOf("#.#", "###", "#.#", "###", "#.#"),
    ' ' to listOf("..", "..", "..", "..", ".."),
)

private val COMBINING = Regex("[\\u0300-\\u036f]")

/** The text as the font writes it: capitals, accents dropped, ß as SS. */
private fun plainText(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(COMBINING, "")
        .replace("ß", "SS")
        .uppercase()

class TextPixels(val points: List<Pt>, val width: Int)

/** Text in capitals; letters with accents lose them, other signs become '?'. */
fun textPixels(text: String, ox: Int = 0, oy: Int = 0): TextPixels {
    val points = ArrayList<Pt>()
    var x = ox
    for (c in plainText(text)) {
        val g = GLYPHS[c] ?: GLYPHS.getValue('?')
        points.addAll(grid(g, x, oy))
        x += g[0].length + 1
    }
    return TextPixels(points, max(0, x - ox - 1))
}
