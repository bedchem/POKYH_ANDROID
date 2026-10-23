package dev.plattnericus.pokyh.ui.pixel

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The small scenes for empty and failed states — the web frontend's `components/pixel/states.ts`,
 * 40 × 25 pixels: what stands on the ground stands on row 20 and casts a dithered shadow on rows
 * 21–22.
 *
 *   Unplugged  a plug that keeps reaching for its socket — connection, loading
 *   Search     a magnifier going over a page, finding nothing — something isn't there
 *   Lock       a padlock whose shackle won't stay shut — the session ended
 *   Empty      a tumbleweed rolling past a cactus — nothing here yet
 *   Holiday    sun, a beach umbrella and waves rolling in — a week without school
 *   Sun        the sun in sunglasses, a cloud drifting by — a day without school
 *   Winter / Spring / Autumn  the holidays through the year (see [holidaySprite])
 *   Cancelled  a calendar page with a blinking cross — every lesson cancelled
 *   Event      a flag in the wind — a day-long event
 *   Swap       two arrows trading places — every lesson substituted
 *   Chat       a speech bubble typing, an answer popping up — no comments yet
 *   Mail       a mailbox, its flag going up and down — no messages
 *   Excused    someone asleep in bed, z's rising — a day excused from school
 *   Bell       a bell ringing — no reminders, no notifications
 *   Coffee     a mug of coffee, steam rising — supporting POKYH
 */
enum class PixelSprite { Unplugged, Search, Lock, Empty, Holiday, Sun, Cancelled, Event, Swap, Chat, Mail, Excused, Bell, Winter, Spring, Autumn, Coffee }

/** The scene for days off in [month] (1–12): the beach in summer ([PixelSprite.Sun] for a single day). */
fun holidaySprite(month: Int, wholeWeek: Boolean = true): PixelSprite = when {
    month == 12 || month <= 2 -> PixelSprite.Winter
    month <= 5 -> PixelSprite.Spring
    month <= 8 -> if (wholeWeek) PixelSprite.Holiday else PixelSprite.Sun
    else -> PixelSprite.Autumn
}

private const val W = 40
private const val H = 25

private fun layer(sprite: Sprite, vararg anims: PxAnim) = PixelLayer(sprite, anims.toList())
private fun p(x: Int, y: Int) = Pt(x, y)
private fun d(v: Number) = v.toDouble()

/** A soft shadow on the ground: a dithered ellipse with a denser middle. */
private fun shadow(s: Sprite, cx: Double, cy: Double, rx: Double, ry: Double = 1.2): Sprite {
    s.fill(dither(ellipse(cx, cy, rx, ry)), "sh")
    s.fill(dither(ellipse(cx, cy, rx * 0.55, ry * 0.8)), "shd")
    return s
}

// ── unplugged: a plug reaching for its socket, sparks when it nearly gets there

private fun unplugged(): PixelScene {
    val wall = Sprite()
    wall.fill(rect(37, 1, 1, 20), "fn")
    wall.fill(dither(rect(0, 21, 40, 1), 1), "fn")
    wall.mass(rounded(29, 6, 8, 13, 1), "pp")
    for (y in listOf(9, 10, 14, 15)) { wall.set(28, y, "ik0"); wall.set(29, y, "ik3") }
    wall.fill(rect(31, 8, 1, 9), "pp1")
    wall.set(30, 7, "pp4")

    val plug = Sprite()
    shadow(plug, 15.0, 21.5, 6.0)
    plug.mass(rect(-8, 19, 14, 2) + rect(4, 13, 2, 6) + rect(5, 11, 6, 2) + p(4, 12), "ik")
    plug.mass(rounded(10, 7, 9, 11, 2) + rect(15, 7, 4, 11), "ac", deep = true)
    for (y in 9..15) { plug.set(12, y, "ac1"); plug.set(13, y, "ac3"); plug.set(15, y, "ac1"); plug.set(16, y, "ac3") }
    plug.set(11, 8, "ac4")
    plug.mass(rect(19, 9, 4, 2), "st")
    plug.mass(rect(19, 14, 4, 2), "st")

    fun spark(cx: Int, cy: Int) = Sprite()
        .fill(listOf(p(cx, cy - 1), p(cx - 1, cy), p(cx + 1, cy), p(cx, cy + 1)), "gd2")
        .fill(listOf(p(cx, cy - 2), p(cx - 2, cy), p(cx + 2, cy), p(cx, cy + 2)), "gd3")
        .set(cx, cy, "gd4")
    return PixelScene(W, H, listOf(
        layer(wall),
        layer(plug, Px.plug),
        layer(spark(26, 7), Px.spark),
        layer(spark(27, 17), Px.sparkLate),
    ))
}

// ── search: a magnifier going over a page and finding nothing

private fun search(): PixelScene {
    val page = Sprite()
    val sheet = minus(rect(5, 3, 27, 18), listOf(p(31, 3), p(30, 3), p(29, 3), p(31, 4), p(30, 4), p(31, 5)))
    page.fill(dither(shift(sheet, 1, 1)), "sh")
    page.mass(sheet, "pp")
    page.fill(listOf(p(29, 4), p(30, 5), p(29, 5), p(28, 4)), "pp1").fill(listOf(p(28, 3), p(31, 6)), "pp0")
        .fill(listOf(p(29, 3), p(30, 4), p(31, 5)), "pp0")
    page.fill(rect(8, 6, 11, 2), "ac3")
    for ((y, runs) in listOf(10 to listOf(8 to 9, 19 to 7), 13 to listOf(8 to 14, 24 to 5), 16 to listOf(8 to 6, 16 to 11))) {
        for ((x, n) in runs) page.fill(rect(x, y, n, 1), "st1")
    }

    val lens = Sprite()
    lens.fill(dither(disc(7.5, 8.5, 5.5)), "sh")
    val ring = minus(disc(5.5, 5.5, 5.5), disc(5.5, 5.5, 3.6))
    lens.fill(disc(5.5, 5.5, 3.6), "gl")
    lens.mass(ring, "ac", round = 5.5 to 5.5)
    lens.fill(listOf(p(3, 3), p(4, 3), p(3, 4)), "pp4")
    lens.mass(line(9, 9, 12, 12) + line(10, 9, 13, 12) + line(9, 10, 12, 13), "wd")

    val mark = Sprite().mass(grid(listOf(".####.", "##..##", "....##", "...##.", "..##..", "..##..", "......", "..##.."), 32, 1), "gd")
    return PixelScene(W, H, listOf(layer(page), layer(lens, Px.lens), layer(mark, Px.question)))
}

// ── lock: a padlock whose shackle won't stay shut

private fun lock(): PixelScene {
    val base = shadow(Sprite(), 20.0, 21.5, 9.0)
    val shackle = Sprite().mass(grid(listOf(
        "...######...",
        "..########..",
        ".###....###.",
        ".##......##.",
        ".##......##.",
        ".##......##.",
        ".##......##.",
        ".##......##.",
    ), 14, 2), "st", deep = true)
    shackle.fill(listOf(p(18, 2), p(19, 2)), "st4")
    val body = Sprite().mass(rounded(12, 9, 16, 12, 2), "ac", deep = true)
    body.fill(rect(13, 11, 14, 1), "ac3")
    body.fill(disc(20.0, 14.0, 1.6) + rect(19, 15, 2, 3), "ik2").set(19, 13, "ik3")
    val glint = Sprite().fill(listOf(p(24, 3), p(23, 4), p(25, 4), p(24, 5)), "gd3").set(24, 4, "pp4")
    return PixelScene(W, H, listOf(layer(base), layer(shackle, Px.shackle), layer(body), layer(glint, Px.spark)))
}

// ── empty: a tumbleweed rolling past a cactus

private fun empty(): PixelScene {
    val land = Sprite()
    val dune = ellipse(21.0, 23.6, 19.0, 3.0).filter { it.y >= 21 }
    land.mass(dune, "sd", outline = false)
    land.fill(dither(dune.filter { it.y >= 23 }), "sd1")
    land.fill(listOf(p(6, 22), p(14, 23), p(33, 22), p(36, 23)), "sd0")
    land.fill(dither(rect(29, 20, 7, 2)), "sh")
    val cactus = rounded(24, 4, 5, 17, 2) + rounded(19, 8, 4, 8, 1) + rect(21, 13, 4, 3) + rounded(30, 6, 4, 7, 1) + rect(28, 10, 4, 3)
    land.mass(cactus, "gn", deep = true)
    for (y in 6..19) land.set(26, y, "gn1")
    for (q in listOf(p(25, 7), p(27, 10), p(25, 13), p(27, 16), p(20, 10), p(31, 8))) land.set(q.x, q.y, "pp4")
    land.fill(listOf(p(25, 3), p(27, 3), p(26, 2)), "pk2").set(26, 3, "gd2")

    val weed = Sprite()
    val ball = minus(disc(4.0, 4.0, 3.9), listOf(p(2, 3), p(3, 5), p(5, 2), p(5, 5), p(4, 3), p(2, 5)))
    weed.mass(ball, "wd", round = 4.0 to 4.0)
    weed.fill(listOf(p(3, 3), p(4, 4), p(5, 3), p(2, 4)), "wd1")
    val dust = Sprite().fill(listOf(p(-3, 7), p(-5, 6), p(-6, 7)), "fn")
    return PixelScene(W, H, listOf(
        layer(land),
        layer(dust, Px.tumble, Px.blink),
        layer(weed.shifted(0, 12), Px.tumble, Px.hop, Px.turn),
    ))
}

// ── holiday: sun, a beach umbrella, waves rolling in

private fun sunBall(s: Sprite, cx: Double, cy: Double, r: Double): Sprite = s.mass(disc(cx, cy, r), "gd", round = cx to cy)

private fun rays(cx: Double, cy: Double, r: Double, long: Boolean): Sprite {
    val s = Sprite()
    for (i in 0 until 8) {
        val a = (i / 8.0) * PI * 2
        val x = jsRound(cx - 0.5 + cos(a) * (r + if (long) 3 else 2))
        val y = jsRound(cy - 0.5 + sin(a) * (r + if (long) 3 else 2))
        s.set(x, y, if (long) "gd3" else "gd2")
    }
    return s
}

/** JavaScript's Math.round: halves go up (towards +∞), unlike Kotlin's roundToInt for negatives. */
internal fun jsRound(v: Double): Int = floor(v + 0.5).toInt()

private fun holiday(): PixelScene {
    val sky = Sprite()
    sunBall(sky, 8.0, 7.0, 4.2)
    sky.fill(listOf(p(6, 5), p(7, 5), p(6, 6)), "gd4")

    val beach = Sprite()
    val sand = ArrayList<Pt>()
    for (y in 18 until H) {
        var x = 40 - (y - 17) * 4 - 10
        while (x < W) { sand.add(p(max(x, 0), y)); x++ }
    }
    beach.mass(sand.distinct(), "sd", outline = false)
    beach.fill(dither(sand.filter { it.y > 20 }, 1), "sd1")
    beach.fill(dither(rect(24, 20, 9, 1)), "sh")
    beach.mass(rect(24, 6, 2, 15), "st")
    val canopy = ellipse(24.5, 10.5, 11.0, 7.0).filter { it.y <= 10 }.filter { it.y < 10 || it.x % 3 != 0 }
    beach.mass(canopy, "rd", round = 24.5 to 10.5)
    // JavaScript's remainder keeps the sign: a stripe index of -1 is not an odd stripe.
    beach.recolor(canopy.filter { floor((it.x - 13.5) / 4.5).toInt() % 2 == 1 }, "rd", "pp")
    beach.mass(rect(24, 2, 2, 2), "gd")

    val water = Sprite()
    val sea = ArrayList<Pt>()
    for (y in 20 until H) {
        var x = -6
        while (x < 46 - (y - 17) * 4 - 10) { sea.add(p(x, y)); x++ }
    }
    water.mass(sea, "wt", outline = false, deep = true)
    var x = -6
    while (x < 30) {
        water.fill(listOf(p(x, 20), p(x + 1, 20), p(x + 2, 21), p(x + 4, 22)), "pp4")
        x += 6
    }
    return PixelScene(W, H, listOf(
        layer(sky),
        layer(rays(8.0, 7.0, 4.2, false)),
        layer(rays(8.0, 7.0, 4.2, true), Px.ray),
        layer(beach),
        layer(water, Px.waves),
    ))
}

/** Flakes or leaves scattered over the picture, one tone each. */
private fun scatter(points: List<Pt>, tone: String): Sprite {
    val s = Sprite()
    for ((x, y) in points) s.fill(listOf(p(x, y - 1), p(x - 1, y), p(x, y), p(x + 1, y), p(x, y + 1)), "${tone}2").set(x, y, "${tone}4")
    return s
}

// winter: a snowman in a drift, a fir with snow on its branches, snow falling

private fun winter(): PixelScene {
    val land = Sprite()
    val drift = ellipse(20.0, 24.5, 22.0, 4.6).filter { it.y >= 20 }
    land.mass(drift, "pp")
    land.fill(dither(drift.filter { it.y >= 23 }, 1), "pp1")
    land.mass(rect(32, 17, 2, 4), "wd")
    fun tier(top: Int, half: Int, rows: Int): List<Pt> {
        val out = ArrayList<Pt>()
        for (j in 0 until rows) {
            val w = jsRound(1 + (half * (j + 1)).toDouble() / rows)
            for (xx in (33 - w)..(32 + w)) out.add(p(xx, top + j))
        }
        return out
    }
    val tiers = listOf(tier(3, 3, 5), tier(7, 4, 5), tier(11, 6, 6))
    for (t in tiers) land.mass(t, "gn", deep = true)
    for (t in tiers) {
        val top = t.minOf { it.y }
        land.fill(t.filter { it.y == top + 1 || it.y == top + 2 }.filterIndexed { i, _ -> i % 3 != 1 }, "pp3")
    }
    val man = shadow(Sprite(), 15.0, 20.8, 6.5, 0.9)
    man.mass(line(10, 13, 5, 9) + p(5, 8) + p(4, 9), "wd")
    man.mass(line(20, 13, 25, 10) + p(25, 9) + p(26, 10), "wd")
    man.mass(disc(15.0, 15.5, 5.2), "pp", round = 15.0 to 15.5)
    man.mass(disc(15.0, 8.3, 3.6), "pp", round = 15.0 to 8.3)
    man.fill(listOf(p(13, 7), p(16, 7), p(15, 14), p(15, 17)), "ik2")
    man.mass(listOf(p(15, 9), p(16, 9), p(17, 9), p(18, 10)), "or", outline = false)
    man.mass(rect(11, 11, 9, 2) + rect(17, 13, 2, 3), "rd")
    man.mass(rect(11, 4, 9, 1) + rect(12, 1, 7, 3), "ik")
    man.fill(rect(12, 3, 7, 1), "rd2")
    val flakes = scatter(listOf(p(3, 2), p(24, 1), p(36, 6), p(8, 13), p(28, 15)), "wt")
    val flakes2 = scatter(listOf(p(18, 3), p(2, 9), p(38, 12), p(26, 5), p(9, 3)), "wt")
    return PixelScene(W, H, listOf(
        layer(land),
        layer(man),
        layer(flakes, Px.snow),
        layer(flakes2, Px.snow.delayed(-1200)),
    ))
}

// spring: tulips on a meadow, a butterfly fluttering over them

private fun tulip(s: Sprite, x: Int, top: Int, mat: String) {
    s.mass(rect(x, top + 4, 1, 20 - top - 4), "gn")
    s.mass(listOf(p(x - 1, 16), p(x - 2, 15), p(x - 2, 14), p(x - 1, 15), p(x - 3, 13)), "gn", outline = false)
    s.mass(grid(listOf("#.#.#", "#####", "#####", ".###."), x - 2, top), mat, deep = true)
}

private fun wings(open: Boolean): Sprite {
    val shape = if (open) grid(listOf("##...##", "###.###", ".##.##.", ".#...#."), 28, 3) else grid(listOf(".#.#.", ".###.", "..#.."), 29, 4)
    val s = Sprite().mass(shape, "pk", deep = true)
    return s.mass(rect(31, 3, 1, 4), "ik", outline = false)
}

private fun spring(): PixelScene {
    val meadow = Sprite()
    val mound = ellipse(20.0, 23.5, 19.0, 3.6).filter { it.y >= 20 }
    meadow.mass(mound, "gn", outline = false)
    meadow.fill(dither(mound.filter { it.y >= 22 }), "gn1")
    meadow.fill(listOf(p(6, 21), p(33, 21), p(16, 23)), "gn3")
    tulip(meadow, 12, 9, "rd")
    tulip(meadow, 19, 6, "gd")
    tulip(meadow, 26, 10, "ac")
    for ((x, y) in listOf(p(5, 20), p(31, 21), p(36, 20))) meadow.fill(listOf(p(x, y - 1), p(x - 1, y), p(x + 1, y)), "pp3").set(x, y, "gd2")
    return PixelScene(W, H, listOf(
        layer(meadow),
        layer(wings(true), Px.flutter, Px.flag),
        layer(wings(false), Px.flutter, Px.flagAlt),
    ))
}

// autumn: a tree in orange and red, leaves falling, a pumpkin under it

private fun autumn(): PixelScene {
    val ground = Sprite()
    val mound = ellipse(20.0, 23.5, 19.0, 3.6).filter { it.y >= 20 }
    ground.mass(mound, "gn", outline = false)
    ground.fill(dither(mound.filter { it.y >= 22 }), "gn1")
    ground.fill(listOf(p(5, 21), p(7, 22), p(19, 21), p(22, 22), p(34, 21)), "or2").fill(listOf(p(6, 21), p(20, 22), p(35, 22)), "rd2")
    ground.mass(rect(12, 10, 3, 11) + line(14, 12, 18, 9) + line(12, 13, 9, 10), "wd", deep = true)
    val crown = disc(13.5, 6.0, 5.4) + disc(9.0, 9.0, 3.8) + disc(18.5, 8.5, 3.9) + disc(13.5, 10.4, 3.4)
    ground.mass(crown, "or", round = 13.0 to 6.0)
    fun inside(pts: List<Pt>) = dither(pts).toHashSet()
    val red = inside(disc(9.0, 11.0, 2.6) + disc(18.0, 11.0, 2.8) + disc(16.0, 3.0, 1.8))
    val gold = inside(disc(10.5, 4.5, 2.4) + disc(6.5, 8.0, 1.6))
    ground.recolor(crown.filter { it in red }, "or", "rd")
    ground.recolor(crown.filter { it in gold }, "or", "gd")
    shadow(ground, 29.0, 20.8, 6.0, 0.9)
    ground.mass(ellipse(29.0, 17.6, 5.4, 3.2), "or", round = 28.0 to 16.5)
    ground.fill(listOf(p(27, 15), p(27, 16), p(27, 17), p(27, 18), p(31, 15), p(31, 16), p(31, 17), p(31, 18)), "or1")
    ground.mass(rect(29, 12, 1, 3) + p(30, 12), "gn")
    fun leaf(x: Int, y: Int, mat: String) = Sprite().mass(listOf(p(x, y), p(x + 1, y), p(x, y + 1), p(x + 1, y + 1), p(x + 2, y + 1)), mat, outline = false)
    return PixelScene(W, H, listOf(
        layer(ground),
        layer(leaf(5, 13, "or"), Px.fall),
        layer(leaf(20, 13, "rd"), Px.fall.delayed(-1100)),
        layer(leaf(14, 14, "gd"), Px.fall.delayed(-2200)),
    ))
}

// ── coffee: a mug of coffee on a saucer, steam rising (supporting POKYH)

private fun coffee(): PixelScene {
    val table = shadow(Sprite(), 19.0, 21.4, 12.0, 1.0)
    table.mass(ellipse(19.0, 20.2, 11.0, 1.6), "pp")
    table.mass(rect(25, 10, 5, 2) + rect(28, 11, 2, 6) + rect(25, 16, 5, 2), "ac", deep = true)
    table.mass(rounded(10, 8, 17, 12, 3), "ac", deep = true)
    table.mass(ellipse(18.5, 10.2, 6.4, 1.1), "wd", outline = false)
    table.fill(listOf(p(14, 10), p(15, 10)), "wd3")
    table.fill(grid(listOf("##.##", "#####", ".###.", "..#.."), 16, 12), "pp3")
    fun wisp(x: Int, y: Int) = Sprite().fill(listOf(p(x, y), p(x + 1, y + 1), p(x + 1, y + 2), p(x, y + 3), p(x, y + 4), p(x + 1, y + 5)), "fn")
    return PixelScene(W, H, listOf(
        layer(table),
        layer(wisp(14, 1), Px.z),
        layer(wisp(20, 0), Px.z.delayed(1300)),
    ))
}

// ── sun: a day without school, the sun in sunglasses, a cloud drifting by

private fun sun(): PixelScene {
    val body = sunBall(Sprite(), 20.0, 11.5, 6.6)
    body.fill(rect(15, 9, 4, 3) + rect(21, 9, 4, 3) + rect(19, 9, 2, 1), "ik2")
    body.fill(listOf(p(15, 9), p(21, 9)), "ik3").set(16, 9, "ik4").set(22, 9, "ik4")
    body.fill(listOf(p(17, 14), p(18, 15), p(19, 15), p(20, 15), p(21, 15), p(22, 14)), "gd0")
    body.fill(listOf(p(16, 7), p(17, 6), p(16, 6)), "gd4")
    val cloud = Sprite().mass(disc(4.0, 4.5, 2.6) + disc(8.0, 3.5, 3.3) + disc(11.5, 5.0, 2.4) + rect(2, 5, 11, 2), "pp", round = 7.0 to 4.0)
    return PixelScene(W, H, listOf(
        layer(rays(20.0, 11.5, 6.6, false)),
        layer(rays(20.0, 11.5, 6.6, true), Px.ray),
        layer(body),
        layer(cloud.shifted(0, 16), Px.cloud),
    ))
}

// ── cancelled: a calendar page with a blinking red cross

private fun cancelled(): PixelScene {
    val cal = shadow(Sprite(), 20.0, 21.5, 10.0)
    cal.mass(rounded(11, 6, 18, 15, 1), "pp", deep = true)
    cal.mass(rect(11, 6, 18, 4), "ac")
    for (x in listOf(15, 24)) cal.mass(rect(x, 3, 2, 5), "st")
    for (row in 0 until 3) for (col in 0 until 4) cal.fill(rect(13 + col * 4, 11 + row * 3, 2, 2), "pp1")
    val cross = Sprite().mass(line(15, 11, 24, 20) + line(16, 11, 25, 20) + line(24, 11, 15, 20) + line(25, 11, 16, 20), "rd")
    return PixelScene(W, H, listOf(layer(cal), layer(cross, Px.blink)))
}

// ── event: a flag in the wind

private fun flagFrame(phase: Double): Sprite {
    val cloth = ArrayList<Pt>()
    val stripe = ArrayList<Pt>()
    for (i in 0 until 16) {
        val off = jsRound(sin(i / 4.0 + phase) * min(1.4, i / 5.0))
        for (j in 0 until 9) (if (j == 4) stripe else cloth).add(p(14 + i, 4 + off + j))
    }
    val s = Sprite().mass(cloth + stripe, "ac", deep = true)
    s.recolor(stripe, "ac", "pp")
    return s
}

private fun event(): PixelScene {
    val ground = Sprite()
    val mound = ellipse(13.0, 22.5, 8.0, 2.6).filter { it.y >= 20 }
    ground.mass(mound, "gn", outline = false)
    ground.fill(dither(mound.filter { it.y >= 22 }), "gn1")
    ground.mass(rect(12, 3, 2, 18), "st")
    ground.mass(disc(13.0, 2.0, 1.8), "gd", round = 13.0 to 2.0)
    return PixelScene(W, H, listOf(layer(ground), layer(flagFrame(0.0), Px.flag), layer(flagFrame(PI), Px.flagAlt)))
}

// ── swap: two arrows trading places (every lesson substituted)

private fun arrow(right: Boolean, mat: String): Sprite {
    val rows = listOf(
        "..............##....",
        "..............####..",
        "####################",
        "####################",
        "####################",
        "..............####..",
        "..............##....",
    )
    val shape = grid(if (right) rows else rows.map { it.reversed() })
    return Sprite().mass(shape, mat, deep = true)
}

private fun swap(): PixelScene = PixelScene(W, H, listOf(
    layer(shadow(Sprite(), 20.0, 21.5, 11.0)),
    layer(arrow(true, "ac").shifted(8, 3), Px.arrowR),
    layer(arrow(false, "gd").shifted(12, 12), Px.arrowL),
))

// ── chat: a speech bubble, three dots typing in turn (no comments yet)

private fun chat(): PixelScene {
    val ground = shadow(Sprite(), 20.0, 21.5, 11.0)
    val bubble = Sprite()
    bubble.mass(rounded(8, 3, 25, 13, 3) + listOf(p(12, 16), p(13, 16), p(14, 16), p(12, 17), p(13, 17), p(12, 18)), "pp", deep = true)
    fun dot(x: Int) = Sprite().mass(rect(x, 9, 2, 2), "ac")
    val reply = Sprite().mass(rounded(27, 13, 11, 7, 2) + listOf(p(34, 20), p(35, 20), p(35, 21)), "ac", deep = true)
    reply.fill(listOf(p(30, 16), p(32, 16), p(34, 16)), "pp3")
    return PixelScene(W, H, listOf(
        layer(ground),
        layer(bubble),
        layer(dot(13), Px.type),
        layer(dot(18), Px.type.delayed(250)),
        layer(dot(23), Px.type.delayed(500)),
        layer(reply, Px.reply),
    ))
}

// ── mail: a mailbox on its post, the flag going up and down (no messages)

private fun mail(): PixelScene {
    val ground = Sprite()
    val grass = ellipse(20.0, 22.5, 12.0, 2.4).filter { it.y >= 20 }
    ground.mass(grass, "gn", outline = false)
    ground.fill(dither(grass.filter { it.y >= 22 }), "gn1")
    ground.mass(rect(19, 12, 3, 9), "wd")
    val box = Sprite().mass(rounded(10, 4, 20, 9, 1) + rect(10, 8, 20, 5), "ac", deep = true)
    box.mass(rect(11, 7, 4, 5), "ik", outline = false)
    box.fill(listOf(p(11, 7), p(12, 7)), "ik3")
    box.fill(rect(16, 6, 12, 1), "ac3")
    fun flag(up: Boolean) = Sprite().mass(if (up) rect(30, 2, 2, 8) + rect(32, 2, 4, 3) else rect(30, 8, 8, 2) + rect(36, 8, 2, 4), "rd")
    return PixelScene(W, H, listOf(layer(ground), layer(box), layer(flag(true), Px.flag), layer(flag(false), Px.flagAlt)))
}

// ── excused: a bed with someone asleep, z's rising (a day excused from school)

private fun excused(): PixelScene {
    val room = shadow(Sprite(), 20.0, 21.5, 15.0)
    room.mass(rect(5, 7, 3, 14) + rect(33, 12, 3, 9), "wd", deep = true)
    room.mass(rect(8, 15, 25, 3), "wd")
    room.mass(rect(8, 13, 25, 2), "pp", outline = false)
    room.mass(rounded(8, 10, 7, 4, 1), "pp")
    room.mass(rounded(14, 10, 19, 6, 2) + rect(14, 13, 19, 3), "ac", deep = true)
    room.fill(dither(rect(16, 12, 15, 1), 1), "ac3")
    room.mass(disc(13.0, 10.0, 2.4), "pk", round = 13.0 to 10.0)
    room.fill(listOf(p(12, 10), p(14, 10)), "pk0")
    room.fill(listOf(p(11, 7), p(12, 7), p(13, 7), p(14, 7), p(10, 8), p(11, 8), p(12, 8), p(13, 8), p(15, 8), p(10, 9), p(11, 9)), "wd1")
    fun z(x: Int, y: Int) = Sprite().mass(grid(listOf("###", "..#", ".#.", "#..", "###"), x, y), "gd")
    return PixelScene(W, H, listOf(layer(room), layer(z(17, 3), Px.z), layer(z(22, 0), Px.z.delayed(1300))))
}

// ── bell: a bell ringing, swinging a pixel each way (no reminders yet)

private fun bell(): PixelScene {
    val ground = shadow(Sprite(), 20.0, 21.5, 9.0)
    val body = grid(listOf(
        "......######......",
        "....##########....",
        "...############...",
        "...############...",
        "...############...",
        "...############...",
        "..##############..",
        "..##############..",
        "..##############..",
        ".################.",
        "##################",
        "##################",
    ), 11, 4)
    val shell = Sprite().mass(body, "gd", deep = true)
    shell.mass(rect(19, 1, 2, 2), "gd")
    shell.fill(listOf(p(16, 6), p(15, 7), p(15, 8), p(15, 9), p(14, 10), p(14, 11)), "gd4")
    shell.fill(rect(13, 12, 14, 1), "gd1")
    val clapper = Sprite().mass(disc(20.0, 18.5, 1.6), "st", round = 20.0 to 18.5)
    fun arc(x: Int, y: Int, flip: Boolean, long: Boolean): List<Pt> {
        val rows = if (long) listOf(0, 1, 1, 1, 0) else listOf(0, 1, 1, 0)
        return rows.mapIndexed { i, dd -> p(if (flip) x + dd else x - dd, y + i) }
    }
    fun waves(right: Boolean) = Sprite().fill(
        if (right) arc(32, 7, true, false) + arc(35, 6, true, true) else arc(7, 7, false, false) + arc(4, 6, false, true),
        "gd2",
    )
    return PixelScene(W, H, listOf(
        layer(ground),
        layer(clapper, Px.clapper),
        layer(shell, Px.swing),
        layer(waves(false), Px.wave),
        layer(waves(true), Px.wave.delayed(400)),
    ))
}

private val built = HashMap<PixelSprite, PixelScene>()

/** The scene for [sprite], built once. */
fun stateScene(sprite: PixelSprite): PixelScene = built.getOrPut(sprite) {
    when (sprite) {
        PixelSprite.Unplugged -> unplugged()
        PixelSprite.Search -> search()
        PixelSprite.Lock -> lock()
        PixelSprite.Empty -> empty()
        PixelSprite.Holiday -> holiday()
        PixelSprite.Sun -> sun()
        PixelSprite.Cancelled -> cancelled()
        PixelSprite.Event -> event()
        PixelSprite.Swap -> swap()
        PixelSprite.Chat -> chat()
        PixelSprite.Mail -> mail()
        PixelSprite.Excused -> excused()
        PixelSprite.Bell -> bell()
        PixelSprite.Winter -> winter()
        PixelSprite.Spring -> spring()
        PixelSprite.Autumn -> autumn()
        PixelSprite.Coffee -> coffee()
    }
}
