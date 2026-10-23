package dev.plattnericus.pokyh.ui.pixel

import kotlin.math.cbrt
import kotlin.math.pow

/**
 * The sprites' palette — the web frontend's `components/pixel/palette.ts`: five tones per
 * material, worked out from the app's own colours. Shades lean towards a cool violet and lights
 * towards a warm white, as hand-drawn sprites do, instead of plain black and white. Where the web
 * uses `light-dark()` a material has a second set for the dark theme.
 *
 * Tones resolve to ARGB ints (what the sprite bitmaps are filled with), once per theme.
 */
object PixelPalette {

    /** A colour that may differ between the themes. */
    private class Tone(val light: Int, val dark: Int = light) {
        fun of(dark: Boolean) = if (dark) this.dark else light
    }

    private fun hex(s: String): Int = (0xFF000000L or s.removePrefix("#").toLong(16)).toInt()
    private fun t(light: String, dark: String = light) = Tone(hex(light), hex(dark))

    private class Material(
        val base: Tone,
        val shade: Int = SHADE,
        val out: Int = OUT,
        val light: Int = LIGHT,
        val fixed: Map<Int, Tone> = emptyMap(),
    )

    private val SHADE = hex("#2a1f55")
    private val OUT = hex("#140d2b")
    private val LIGHT = hex("#fff8e6")

    private val MATERIALS: Map<String, Material> = mapOf(
        // The accent: indigo.
        "ac" to Material(t("#6366F1")),
        // Gold: the sun, sparks, keys.
        "gd" to Material(t("#F5B301"), shade = hex("#a2400f"), out = hex("#4a1c06")),
        // Red: crosses, stripes, pins.
        "rd" to Material(t("#EF4444"), shade = hex("#5a1236"), out = hex("#2e0718")),
        // Green: plants.
        "gn" to Material(t("#10B981"), shade = hex("#0b3b4a"), out = hex("#05202a")),
        // Steel: prongs, poles, hinges.
        "st" to Material(t("#bcc1cc"), shade = hex("#3b4160"), out = hex("#262a3d"), light = hex("#ffffff")),
        // Paper: soft outlines, as the Mensa plate's glaze.
        "pp" to Material(
            t("#f4f1eb", "#dcd8d0"),
            fixed = mapOf(0 to t("#bdb3a4", "#2a2830"), 1 to t("#ddd6cb", "#b4afa6"), 3 to t("#ffffff", "#ece9e3"), 4 to t("#ffffff")),
        ),
        // The 404's ghost: white, a firm dark outline, cool blue shade.
        "gh" to Material(t("#eef2fb"), fixed = mapOf(0 to t("#28234d"), 1 to t("#a9b5db"), 3 to t("#ffffff"), 4 to t("#ffffff"))),
        // Orange: pumpkins, autumn leaves, a carrot nose.
        "or" to Material(t("#f0892b"), shade = hex("#7a2a10"), out = hex("#3d1406")),
        // Wood and dry grass.
        "wd" to Material(t("#a8693a"), shade = hex("#3d1d2a"), out = hex("#24100f")),
        // Sand.
        "sd" to Material(t("#efd790"), shade = hex("#9a5a2a"), out = hex("#5a3416")),
        // Water and sky.
        "wt" to Material(t("#5aa9e6"), shade = hex("#1d2f73"), out = hex("#0f1a45")),
        // Cheeks.
        "pk" to Material(t("#f59ab0"), shade = hex("#b0446a")),
        // The sky in the windows: by day and by night.
        "sk" to Material(t("#8fcdf3"), shade = hex("#1d2f73"), out = hex("#0f1a45")),
        "nt" to Material(t("#1d2652"), shade = hex("#05081f"), out = hex("#03040f"), light = hex("#6d7fd0")),
        // Eyes and dark details.
        "ik" to Material(t("#1d1730"), fixed = mapOf(3 to t("#3d3460"), 4 to t("#ffffff"))),
        // The mascot's pupils: indigo, from deep at the top to lighter low down, a white catch-light.
        "pu" to Material(t("#3b3494"), fixed = mapOf(0 to t("#1f1a52"), 1 to t("#2c2672"), 3 to t("#5a52c4"), 4 to t("#ffffff"))),
    )

    /** Shadows on the ground, and faint lines (not materials: one tone each), as ARGB. */
    private val EXTRA: Map<String, Tone> = mapOf(
        "sh" to Tone(rgba(48, 30, 70, 0.16), rgba(0, 0, 0, 0.38)),
        "shd" to Tone(rgba(48, 30, 70, 0.26), rgba(0, 0, 0, 0.55)),
        // color-mix(in srgb, var(--ink) 16%, transparent)
        "fn" to Tone(rgba(0x1C, 0x1A, 0x17, 0.16), rgba(0xF2, 0xF2, 0xF4, 0.16)),
        // Glass: what lies behind it shows through.
        "gl" to Tone(rgba(140, 190, 250, 0.38), rgba(140, 190, 250, 0.22)),
        // Warm light falling out of an open door, off a lamp or a screen.
        "lit" to Tone(rgba(255, 226, 150, 0.42), rgba(255, 205, 120, 0.3)),
        "glow" to Tone(rgba(255, 214, 140, 0.22)),
    )

    private fun rgba(r: Int, g: Int, b: Int, a: Double): Int =
        ((a * 255 + 0.5).toInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b

    private fun recipe(m: Material, step: Int): Pair<Int, Double>? = when (step) {
        0 -> m.out to 0.62
        1 -> m.shade to 0.32
        3 -> m.light to 0.38
        4 -> m.light to 0.76
        else -> null
    }

    private fun resolve(m: Material, step: Int, dark: Boolean): Int {
        m.fixed[step]?.let { return it.of(dark) }
        val base = m.base.of(dark)
        val r = recipe(m, step) ?: return base
        return mixOklab(base, r.first, r.second)
    }

    private val tables = arrayOfNulls<Map<String, Int>>(2)

    /** Every tone's colour in one theme. */
    fun tones(dark: Boolean): Map<String, Int> {
        val i = if (dark) 1 else 0
        tables[i]?.let { return it }
        val out = HashMap<String, Int>()
        for ((name, m) in MATERIALS) for (step in 0..4) out["$name$step"] = resolve(m, step, dark)
        for ((name, e) in EXTRA) out[name] = e.of(dark)
        tables[i] = out
        return out
    }

    // ── color-mix(in oklab, a, b p%) ─────────────────────────────────────────

    private fun toLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun toSrgb(c: Double): Double = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

    private fun oklab(argb: Int): DoubleArray {
        val r = toLinear(((argb shr 16) and 255) / 255.0)
        val g = toLinear(((argb shr 8) and 255) / 255.0)
        val b = toLinear((argb and 255) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun fromOklab(lab: DoubleArray): Int {
        val l = lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2]
        val m = lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2]
        val s = lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2]
        val l3 = l * l * l
        val m3 = m * m * m
        val s3 = s * s * s
        val r = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
        val g = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
        val b = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
        fun ch(v: Double) = (toSrgb(v.coerceIn(0.0, 1.0)) * 255 + 0.5).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** [a] mixed with [p] of [b], in oklab — CSS `color-mix(in oklab, a, b p%)`. */
    fun mixOklab(a: Int, b: Int, p: Double): Int {
        val la = oklab(a)
        val lb = oklab(b)
        return fromOklab(DoubleArray(3) { la[it] * (1 - p) + lb[it] * p })
    }
}
