package dev.plattnericus.pokyh.ui.mensa

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.ui.pixel.rememberReducedMotion
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.graphics.toArgb

/**
 * A dish served as pixel art — the web's `components/mensa/PixelPlate.tsx`: a glazed plate, lit
 * from the top left, with its shadow dithered onto the table. The dish photo lies sharp in the
 * well, cut to the plate's pixel edge; without a photo a small pixel meal in the dish's style
 * stands in. Two sizes: [PlateSize.Table] for the next dish (between fork and knife, steam rising
 * in steps, the glaze glinting now and then) and [PlateSize.Plate] for the plan's rows.
 */
enum class PlateSize { Table, Plate }

/** Which pixel meal stands in for a dish without a photo. */
enum class FoodStyle { Pasta, Salad, Meat, Fish }

/** The web's `foodStyleOf(dishTone(dish))`. */
fun foodStyleOf(dish: Dish): FoodStyle {
    val c = "${dish.category} ${dish.tags.joinToString(" ")}".lowercase()
    return when {
        c.contains("salat") || c.contains("vegan") || c.contains("vegetar") -> FoodStyle.Salad
        c.contains("suppe") || c.contains("pasta") || c.contains("nudel") -> FoodStyle.Pasta
        c.contains("fisch") -> FoodStyle.Fish
        else -> FoodStyle.Meat
    }
}

private class Geometry(
    val w: Int, val h: Int, val cx: Double, val cy: Double,
    val rOut: Double, val rRim: Double, val rFood: Double,
    val shadow: Pair<Int, Int>, val shadowSoft: Pair<Int, Int>,
) {
    // The food's square on the scene, in whole pixels, centred on the plate.
    val boxX = floor(cx - rFood).toInt()
    val boxY = floor(cy - rFood).toInt()
    val boxN = ceil(cx + rFood).toInt() - boxX
}

private val TABLE = Geometry(112, 64, 56.0, 31.0, 28.5, 23.0, 19.5, 2 to 3, 3 to 5)
private val PLATE = Geometry(36, 36, 17.0, 17.0, 15.5, 12.5, 10.5, 1 to 2, 2 to 3)

private fun geometry(size: PlateSize) = if (size == PlateSize.Table) TABLE else PLATE

// Light from the top left.
private const val LX = -0.61
private const val LY = -0.79

private fun dist(x: Int, y: Int, cx: Double, cy: Double) = hypot(x + 0.5 - cx, y + 0.5 - cy)

private fun facing(g: Geometry, x: Int, y: Int): Double {
    val dx = x + 0.5 - g.cx
    val dy = y + 0.5 - g.cy
    val d = hypot(dx, dy).takeIf { it > 0 } ?: 1.0
    return dx / d * LX + dy / d * LY
}

private enum class Tone { ShadowSoft, Shadow, Edge, Rim, RimHi, RimLo, Well, WellLo, Steel, SteelHi, SteelLo }

/** The scene's pixels per tone, back to front (as x, y pairs). */
private class Scene(val layers: List<Pair<Tone, IntArray>>)

private class LayerBuilder {
    val map = LinkedHashMap<Tone, ArrayList<Int>>()
    fun add(t: Tone, x: Int, y: Int) {
        map.getOrPut(t) { ArrayList() }.apply { add(x); add(y) }
    }
}

private fun span(rows: MutableList<IntArray>, y0: Int, y1: Int, x0: Int, x1: Int) {
    for (y in y0..y1) rows.add(intArrayOf(y, x0, x1))
}

private fun buildScene(g: Geometry, cutlery: Boolean): Scene {
    val plate = LayerBuilder()
    val k = g.rOut / 28.5
    for (y in 0 until g.h) for (x in 0 until g.w) {
        val d = dist(x, y, g.cx, g.cy)
        if (d <= g.rOut) {
            val f = facing(g, x, y)
            when {
                d > g.rRim -> when {
                    d > g.rOut - 1 -> plate.add(if (f > 0.3) Tone.RimLo else Tone.Edge, x, y)
                    f > 0.42 && d > g.rOut - 3.6 * k -> plate.add(Tone.RimHi, x, y)
                    f < -0.38 -> plate.add(Tone.RimLo, x, y)
                    else -> plate.add(Tone.Rim, x, y)
                }
                d > g.rRim - 1 -> plate.add(if (f > 0) Tone.RimLo else Tone.RimHi, x, y)
                d <= g.rFood -> plate.add(Tone.Well, x, y)
                d <= g.rFood + 1.5 * k && f < -0.2 -> plate.add(Tone.WellLo, x, y)
                f > 0.3 && d > g.rRim - 3 * k -> plate.add(Tone.WellLo, x, y)
                else -> plate.add(Tone.Well, x, y)
            }
        } else if (dist(x, y, g.cx + g.shadow.first, g.cy + g.shadow.second) <= g.rOut) {
            plate.add(Tone.Shadow, x, y)
        } else if (dist(x, y, g.cx + g.shadowSoft.first, g.cy + g.shadowSoft.second) <= g.rOut + 0.5 && (x + y) % 2 == 0) {
            plate.add(Tone.ShadowSoft, x, y)
        }
    }
    val order = listOf(Tone.ShadowSoft, Tone.Shadow, Tone.Edge, Tone.Rim, Tone.RimHi, Tone.RimLo, Tone.Well, Tone.WellLo)
    val layers = order.mapNotNull { t -> plate.map[t]?.let { t to it.toIntArray() } }.toMutableList()
    if (!cutlery) return Scene(layers)

    fun piece(rows: List<IntArray>, gaps: Set<Long>, extraHi: List<Pair<Int, Int>>): List<Pair<Tone, IntArray>> {
        fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
        val filled = LinkedHashSet<Long>()
        for (r in rows) for (x in r[1]..r[2]) if (key(x, r[0]) !in gaps) filled.add(key(x, r[0]))
        val b = LayerBuilder()
        for (k2 in filled) {
            val x = (k2 shr 32).toInt()
            val y = k2.toInt()
            when {
                key(x - 1, y) !in filled -> b.add(Tone.SteelHi, x, y)
                key(x + 1, y) !in filled -> b.add(Tone.SteelLo, x, y)
                else -> b.add(Tone.Steel, x, y)
            }
            if (key(x + 1, y + 1) !in filled) b.add(Tone.Shadow, x + 1, y + 1)
        }
        for ((x, y) in extraHi) b.add(Tone.SteelHi, x, y)
        return listOf(Tone.Shadow, Tone.Steel, Tone.SteelHi, Tone.SteelLo).mapNotNull { t -> b.map[t]?.let { t to it.toIntArray() } }
    }

    // Four tines with three gaps, rounded tips.
    val fork = mutableListOf(intArrayOf(4, 8, 8), intArrayOf(4, 10, 10), intArrayOf(4, 12, 12), intArrayOf(4, 14, 14))
    span(fork, 5, 15, 8, 14)
    val gaps = HashSet<Long>()
    for (y in 5..15) for (x in listOf(9, 11, 13)) gaps.add((x.toLong() shl 32) or y.toLong())
    span(fork, 16, 18, 8, 14); span(fork, 19, 20, 9, 13); span(fork, 21, 23, 10, 12)
    span(fork, 24, 36, 10, 12); span(fork, 37, 38, 9, 13); span(fork, 39, 57, 9, 13)
    fork.add(intArrayOf(58, 10, 12))
    // A table knife from above: a curved edge on the left, a straight back.
    val knife = mutableListOf(intArrayOf(4, 101, 102), intArrayOf(5, 100, 102), intArrayOf(6, 99, 102))
    span(knife, 7, 30, 98, 102); span(knife, 31, 32, 97, 103); span(knife, 33, 57, 98, 102)
    knife.add(intArrayOf(58, 99, 101))
    // A long light along the blade and one down the fork's handle.
    layers += piece(fork, gaps, (41..54).map { 11 to it })
    layers += piece(knife, emptySet(), (9..27).map { 100 to it })
    return Scene(layers)
}

private val scenes = HashMap<PlateSize, Scene>()
private fun sceneFor(size: PlateSize) = scenes.getOrPut(size) { buildScene(geometry(size), size == PlateSize.Table) }

/**
 * The plate and table as one bitmap, per size and theme: drawn once, then scaled with no
 * smoothing. Drawing every pixel as its own rectangle on every frame (the steam animates) made
 * the Mensa crawl with a plate per dish.
 */
private val sceneImages = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()
private val mealImages = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

private fun sceneImage(size: PlateSize, dark: Boolean, g: Geometry, scene: Scene, tones: PlateTones): ImageBitmap =
    sceneImages.getOrPut("$size-$dark") {
        val px = IntArray(g.w * g.h)
        for ((tone, pts) in scene.layers) {
            val c = tones.map.getValue(tone).toArgb()
            var i = 0
            while (i < pts.size) {
                val x = pts[i]
                val y = pts[i + 1]
                if (x in 0 until g.w && y in 0 until g.h) px[y * g.w + x] = c
                i += 2
            }
        }
        Bitmap.createBitmap(px, g.w, g.h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

private fun inFood(g: Geometry, x: Int, y: Int) = dist(x, y, g.cx - g.boxX, g.cy - g.boxY) <= g.rFood

/** The food's outline in whole pixels, so the photo keeps the plate's edge (the web's clip-path). */
private class FoodShape(private val g: Geometry) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val n = g.boxN
        val s = size.width / n
        val path = Path()
        for (y in 0 until n) {
            var x0 = -1
            var x1 = -1
            for (x in 0 until n) if (inFood(g, x, y)) { if (x0 < 0) x0 = x; x1 = x }
            if (x0 < 0) continue
            path.addRect(androidx.compose.ui.geometry.Rect(x0 * s, y * s, (x1 + 1) * s, (y + 1) * s))
        }
        return Outline.Generic(path)
    }
}

private val FOOD_COLORS = mapOf(
    FoodStyle.Pasta to (0xFFE9B54A to listOf(0xFFF6D27A, 0xFFC98F2A, 0xFFC2412D, 0xFF3F8F3A, 0xFFF6D27A)),
    FoodStyle.Salad to (0xFF6FAE45 to listOf(0xFF3E7F2C, 0xFF9CCB62, 0xFFD9472F, 0xFFF2C744, 0xFF3E7F2C)),
    FoodStyle.Meat to (0xFF9A5530 to listOf(0xFF7A3A1E, 0xFFB8703F, 0xFFE8C25A, 0xFF4F8F3A, 0xFF5E2614)),
    FoodStyle.Fish to (0xFFEADBC2 to listOf(0xFFD6BF9B, 0xFFF4D43A, 0xFFF7F3EA, 0xFF4F8F3A, 0xFFC9A97E)),
)

/** The web's seeded random (FNV-1a seed, mulberry32 steps), so a dish always looks the same. */
private class SeededRandom(seed: String) {
    private var h: Int = 0x811C9DC5.toInt()
    init {
        for (ch in seed) h = (h xor ch.code) * 16777619
    }
    fun next(): Double {
        h += 0x6d2b79f5
        var t = h
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }
}

private val meals = object : android.util.LruCache<String, ImageBitmap>(64) {}

/** The meal at one pixel per pixel: a heap of the base colour, spots, a white light, round. */
private fun mealBitmap(g: Geometry, style: FoodStyle, seed: String): ImageBitmap {
    val cacheKey = "${g.w}:$style:$seed"
    meals.get(cacheKey)?.let { return it }
    val rnd = SeededRandom(seed)
    val (baseHex, spots) = FOOD_COLORS.getValue(style)
    val n = g.boxN
    val c = n / 2.0
    val k = g.rFood / 19.5
    val px = IntArray(n * n)
    fun fill(x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in maxOf(0, y) until minOf(n, y + h)) for (xx in maxOf(0, x) until minOf(n, x + w)) px[yy * n + xx] = color
    }
    val base = baseHex.toInt()
    for (y in 0 until n) for (x in 0 until n) {
        val r = hypot(x + 0.5 - c, y + 0.5 - c)
        if (r <= g.rFood - (3 + rnd.next() * 2.5) * k) fill(x, y, 1, 1, base)
    }
    val count = ((if (style == FoodStyle.Pasta) 90 else 70) * k * k).roundToInt()
    repeat(count) {
        val a = rnd.next() * PI * 2
        val r = sqrt(rnd.next()) * (g.rFood - 6 * k)
        val x = Math.round(c + cos(a) * r).toInt()
        val y = Math.round(c + sin(a) * r).toInt()
        val color = spots[floor(rnd.next() * spots.size).toInt()].toInt()
        if (style == FoodStyle.Pasta) {
            val dir = if (rnd.next() > 0.5) 1 else -1
            for (s in 0 until 3) fill(x + s, y + s * dir, 1, 1, color)
        } else {
            val w = if (rnd.next() > 0.5) 2 else 1
            fill(x, y, w, if (rnd.next() > 0.6) 2 else 1, color)
        }
    }
    val light = 0.22
    for (y in 0 until n) for (x in 0 until n) {
        val i = y * n + x
        if (!inFood(g, x, y)) { px[i] = 0; continue }
        val dx = x + 0.5 - c
        val dy = y + 0.5 - c
        val r = hypot(dx, dy)
        if (r < g.rFood - 6 * k && r > g.rFood - 9 * k && (dx * LX + dy * LY) / (if (r == 0.0) 1.0 else r) > 0.5) {
            val p = px[i]
            val da = ((p ushr 24) and 0xff) / 255.0
            val a = light + da * (1 - light)
            fun ch(shift: Int) = ((255 * light + ((p ushr shift) and 0xff) * da * (1 - light)) / a).roundToInt().coerceIn(0, 255)
            px[i] = ((a * 255).roundToInt() shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
    val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    meals.put(cacheKey, bmp)
    return bmp
}

private class PlateTones(val map: Map<Tone, Color>, val steam: Color, val spark: Color)

private val LightTones = PlateTones(
    mapOf(
        Tone.Rim to Color(0xFFF4F1EB), Tone.RimHi to Color(0xFFFFFFFF), Tone.RimLo to Color(0xFFDDD6CB), Tone.Edge to Color(0xFFC2B8A9),
        Tone.Well to Color(0xFFECE7DF), Tone.WellLo to Color(0xFFD6CEC1), Tone.Shadow to Color(60, 40, 20, (0.17 * 255).toInt()),
        Tone.ShadowSoft to Color(60, 40, 20, (0.1 * 255).toInt()), Tone.Steel to Color(0xFFC9CCD3), Tone.SteelHi to Color(0xFFF6F7F9),
        Tone.SteelLo to Color(0xFF8E939D),
    ),
    steam = Color(140, 132, 122, (0.5 * 255).toInt()),
    spark = Color(0xFFFFD86B),
)

private val DarkTones = PlateTones(
    mapOf(
        Tone.Rim to Color(0xFF3E3E46), Tone.RimHi to Color(0xFF5B5B65), Tone.RimLo to Color(0xFF2D2D34), Tone.Edge to Color(0xFF1C1C21),
        Tone.Well to Color(0xFF36363D), Tone.WellLo to Color(0xFF28282E), Tone.Shadow to Color(0, 0, 0, (0.45 * 255).toInt()),
        Tone.ShadowSoft to Color(0, 0, 0, (0.28 * 255).toInt()), Tone.Steel to Color(0xFFA9AEB7), Tone.SteelHi to Color(0xFFE6E8ED),
        Tone.SteelLo to Color(0xFF686E78),
    ),
    steam = Color(255, 255, 255, (0.34 * 255).toInt()),
    spark = Color(0xFFFFFFFF),
)

// The steam's three wisps and the glaze's sparkles (the web's SVG paths), as pixels.
private val WISPS = listOf(
    listOf(50 to 13, 51 to 11, 51 to 9, 50 to 7),
    listOf(56 to 12, 55 to 10, 56 to 8, 57 to 6, 56 to 4),
    listOf(62 to 13, 61 to 11, 61 to 9, 62 to 7),
)
private val SPARK = listOf(35 to 9, 34 to 10, 35 to 10, 36 to 10, 35 to 11)
private val SPARK_LATE = listOf(76 to 52)

/**
 * A dish on its pixel plate, [size] wide as the space allows (the web's aspect ratio). [photo]
 * (when there is one) lies in the well; until it has loaded, and without one, the pixel meal
 * of [style] does. [empty]: the plate without a meal, the table laid while the menu loads.
 */
@Composable
fun PixelPlate(
    style: FoodStyle,
    seed: String,
    modifier: Modifier = Modifier,
    size: PlateSize = PlateSize.Table,
    photo: String? = null,
    empty: Boolean = false,
) {
    val g = geometry(size)
    val scene = sceneFor(size)
    val tones = if (PokyhTheme.colors.isDark) DarkTones else LightTones
    // Kept for the app's life: a list scrolled back and forth doesn't draw every meal again.
    val meal = remember(size, style, seed) { mealImages.getOrPut("$size-$style-$seed") { mealBitmap(g, style, seed) } }
    val reduced = rememberReducedMotion()
    val table = size == PlateSize.Table
    // The steam and the glints run on their own clock, in steps as on the web (a tick every
    // 100 ms is all they move in — not every frame).
    var clock by remember { mutableLongStateOf(0L) }
    if (table && !reduced) {
        LaunchedEffect(Unit) {
            val start = System.nanoTime()
            while (true) {
                clock = (System.nanoTime() - start) / 1_000_000
                kotlinx.coroutines.delay(100)
            }
        }
    }
    val base = sceneImage(size, PokyhTheme.colors.isDark, g, scene, tones)
    var photoShown by remember(photo) { mutableStateOf(false) }

    BoxWithConstraints(modifier.aspectRatio(g.w.toFloat() / g.h)) {
        val unit = maxWidth / g.w
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.width / g.w
            fun px(x: Int, y: Int, color: Color, dy: Float = 0f) =
                drawRect(color, topLeft = Offset(x * s, (y + dy) * s), size = Size(s + 0.5f, s + 0.5f))
            drawImage(
                base,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(g.w, g.h),
                dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                filterQuality = FilterQuality.None,
            )
            if (table) {
                val t = clock
                // Wisps: 2.8 s each, in 7 steps, rising from 2 px below to 5 px above, fading.
                WISPS.forEachIndexed { w, pts ->
                    val phase = ((t - w * 900L).mod(2800L)) / 2800.0
                    val step = floor(phase * 7) / 7
                    val alpha = when {
                        reduced -> 0.0
                        step < 0.2 -> step / 0.2
                        step < 0.7 -> 1 - (step - 0.2) / 0.5 * 0.2
                        else -> 0.8 * (1 - (step - 0.7) / 0.3)
                    }
                    val dy = (2 - 7 * step).toFloat()
                    if (alpha > 0.01) for ((x, y) in pts) px(x, y, tones.steam.copy(alpha = tones.steam.alpha * alpha.toFloat()), dy)
                }
                // The glaze catches the light: on for a moment every 4 s.
                fun sparkOn(delay: Long) = !reduced && ((t - delay).mod(4000L)) in 0L..260L && t >= delay
                if (sparkOn(800)) for ((x, y) in SPARK) px(x, y, tones.spark)
                if (sparkOn(2600)) for ((x, y) in SPARK_LATE) px(x, y, tones.spark)
            }
        }
        if (!empty) {
            Box(
                Modifier
                    .offset(x = unit * g.boxX, y = unit * g.boxY)
                    .size(unit * g.boxN)
                    .clip(FoodShape(g)),
            ) {
                if (!photoShown) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(
                            meal,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(g.boxN, g.boxN),
                            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
                if (!photo.isNullOrBlank()) {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { photoShown = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
