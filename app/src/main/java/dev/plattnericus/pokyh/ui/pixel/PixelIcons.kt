package dev.plattnericus.pokyh.ui.pixel

/**
 * Small pixel icons, 16 × 16, drawn like the scenes in [PixelSprite] (outlines, light from the
 * top left) and shown at three times their size, so every pixel stays square — the web's
 * components/pixel/icons.ts, pixel for pixel. The settings use them beside the account's e-mail,
 * password, two-factor sign-in and devices; [PixelIconName.Qr] is the app's own, for signing in
 * on a computer with the phone.
 */
enum class PixelIconName { Mail, Key, ShieldOn, ShieldOff, Fingerprint, Devices, Qr }

const val PIXEL_ICON_SIZE = 16

private fun p(x: Int, y: Int) = Pt(x, y)

// mail: an envelope seen from the back, its flap closed with a red seal.
private fun mail(): Sprite {
    val s = Sprite().mass(rect(1, 3, 14, 10), "pp", deep = true)
    val flap = listOf(p(1, 3), p(2, 4), p(3, 5), p(4, 6), p(5, 7), p(6, 8), p(14, 3), p(13, 4), p(12, 5), p(11, 6), p(10, 7), p(9, 8))
    s.fill(flap, "pp0")
    s.fill(listOf(p(1, 12), p(2, 11), p(3, 10), p(14, 12), p(13, 11), p(12, 10)), "pp1")
    s.mass(rect(7, 8, 2, 2), "rd", outline = false)
    return s.set(7, 8, "rd3")
}

// key: a gold key, its bow a ring, two teeth.
private fun key(): Sprite {
    val bow = minus(disc(4.5, 8.0, 3.7), disc(4.5, 8.0, 1.6))
    return Sprite().mass(bow + rect(8, 7, 6, 2) + rect(11, 9, 1, 2) + rect(13, 9, 1, 3), "gd", deep = true)
        .fill(listOf(p(3, 5), p(2, 6)), "gd4")
}

private val SHIELD = grid(
    listOf(
        "############",
        "############",
        "############",
        "############",
        "############",
        "############",
        ".##########.",
        ".##########.",
        "..########..",
        "..########..",
        "...######...",
        "....####....",
        ".....##.....",
    ),
    2, 1,
)

// shield-on: a green shield with a tick (two-factor sign-in on).
private fun shieldOn(): Sprite {
    val tick = listOf(p(4, 6), p(5, 7), p(6, 8), p(7, 7), p(8, 6), p(9, 5), p(10, 4), p(11, 3))
    return Sprite().mass(SHIELD, "gn", deep = true).fill(tick + tick.map { p(it.x, it.y + 1) }, "pp3")
}

// shield-off: a gold shield with an exclamation mark (two-factor sign-in off).
private fun shieldOff(): Sprite =
    Sprite().mass(SHIELD, "gd", deep = true).fill(rect(7, 3, 2, 5) + rect(7, 9, 2, 2), "gd0")

private val RIDGES = grid(
    listOf(
        "..######..",
        ".#......#.",
        "#..####..#",
        "#.#....#.#",
        "#.#.##.#.#",
        "#.#.#..#.#",
        "#.#.#.#..#",
        "..#.#.#.#.",
        "#...#.#.#.",
        "....#...#.",
    ),
    3, 3,
)

// fingerprint: light ridges on an indigo tile (signing in with the device's lock).
private fun fingerprint(): Sprite = Sprite().mass(rounded(1, 1, 14, 14, 3), "ac", deep = true).fill(RIDGES, "pp3")

// devices: a screen on its stand, a phone in front of it.
private fun devices(): Sprite {
    val s = Sprite().mass(rect(1, 2, 11, 8) + rect(5, 10, 3, 1) + rect(3, 11, 7, 1), "st")
    s.fill(rect(2, 3, 9, 6), "wt2").fill(listOf(p(3, 4), p(4, 4), p(3, 5)), "wt4")
    s.mass(rounded(10, 6, 5, 9, 1), "ac", deep = true)
    return s.fill(rect(11, 7, 3, 6), "wt2").set(11, 7, "wt4").set(12, 13, "ac3")
}

private val QR = grid(
    listOf(
        "####..#.####",
        "#..#.#..#..#",
        "#..#..#.#..#",
        "####.#..####",
        "............",
        "..#.##.#.#..",
        "#.#..#..##.#",
        "............",
        "####.#.#.#..",
        "#..#..##..#.",
        "#..#.#..##.#",
        "####..#.#.##",
    ),
    2, 2,
)

// qr: a code on a white card, its three corner squares (signing in on a computer with the phone).
private fun qr(): Sprite = Sprite().mass(rounded(1, 1, 14, 14, 1), "pp", deep = true).fill(QR, "ik2")

private val built = HashMap<PixelIconName, PixelScene>()

/** The icon as a 16 × 16 drawing, built once. */
fun iconScene(name: PixelIconName): PixelScene = built.getOrPut(name) {
    val sprite = when (name) {
        PixelIconName.Mail -> mail()
        PixelIconName.Key -> key()
        PixelIconName.ShieldOn -> shieldOn()
        PixelIconName.ShieldOff -> shieldOff()
        PixelIconName.Fingerprint -> fingerprint()
        PixelIconName.Devices -> devices()
        PixelIconName.Qr -> qr()
    }
    PixelScene(PIXEL_ICON_SIZE, PIXEL_ICON_SIZE, listOf(PixelLayer(sprite)))
}
