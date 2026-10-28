package dev.plattnericus.pokyh.ui.recap

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import dev.plattnericus.pokyh.core.i18n.Lang
import dev.plattnericus.pokyh.core.i18n.recapText
import dev.plattnericus.pokyh.ui.pixel.Mascot
import dev.plattnericus.pokyh.ui.pixel.PixelPalette
import dev.plattnericus.pokyh.ui.pixel.textPixels
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import java.io.File

// The recap's last card, to keep and to share (web components/recap/ShareCard.tsx): a poster in
// pixel art — the title, the first figure huge, the others each on a row of its own, the mascot
// peeking in at the bottom in the pose picked, on a bold colour. Drawn at 180 × 320 card pixels;
// the picture to share is exactly this drawing at six times the size.

private const val CARD_W = 180
private const val CARD_H = 320
private const val GLYPH_H = 5
private const val LINE = GLYPH_H + 3
private const val CARD_SLOTS = 5

private val POSTER_COLORS = listOf(0xFFFF5D3A, 0xFF2F6BFF, 0xFF0F9D63, 0xFFFFBF1F, 0xFF8B3DFF, 0xFFFF4F9A, 0xFF1B1640).map { it.toInt() }

/** The web's poses, as this app's mascot draws them. */
private enum class CardPose(val key: String, val pose: Mascot.Pose) {
    Cool("poseCool", Mascot.Pose(Mascot.Eyes.Open, 0 to 0, Mascot.Mouth.Smile, Mascot.Arms.Thumb)),
    Legend("poseLegend", Mascot.Pose(Mascot.Eyes.Happy, 0 to 0, Mascot.Mouth.Grin, Mascot.Arms.Cheer)),
    Wave("poseWave", Mascot.Pose(Mascot.Eyes.Open, 0 to 0, Mascot.Mouth.Smile, Mascot.Arms.Wave)),
    Proud("poseProud", Mascot.Pose(Mascot.Eyes.Happy, 0 to 0, Mascot.Mouth.Smile, Mascot.Arms.Fists)),
    Party("poseParty", Mascot.Pose(Mascot.Eyes.Wide, 0 to 0, Mascot.Mouth.Grin, Mascot.Arms.Cheer)),
    Love("poseLove", Mascot.Pose(Mascot.Eyes.Happy, 0 to 0, Mascot.Mouth.Smile, Mascot.Arms.Down)),
    Cheer("poseCheer", Mascot.Pose(Mascot.Eyes.Happy, 0 to 0, Mascot.Mouth.O, Mascot.Arms.Cheer)),
    Zap("poseZap", Mascot.Pose(Mascot.Eyes.Wide, 0 to 0, Mascot.Mouth.O, Mascot.Arms.Fists)),
    Sleepy("poseSleepy", Mascot.Pose(Mascot.Eyes.Closed, 0 to 0, Mascot.Mouth.Flat, Mascot.Arms.Down)),
    Angel("poseAngel", Mascot.Pose(Mascot.Eyes.Closed, 0 to 0, Mascot.Mouth.Smile, Mascot.Arms.Peek)),
}

private class PosterData(val title: String, val name: String, val items: List<Pair<String, String>>, val pose: CardPose, val color: Int)

/** One poster pixel array: fills, the pixel font at a size, and sprites. */
private class Canvas180(val color: Int) {
    val px = IntArray(CARD_W * CARD_H) { color }

    fun rect(x: Int, y: Int, w: Int, h: Int, c: Int) {
        for (yy in maxOf(0, y) until minOf(CARD_H, y + h)) for (xx in maxOf(0, x) until minOf(CARD_W, x + w)) px[yy * CARD_W + xx] = c
    }

    fun width(text: String) = textPixels(text).width

    /** Text at (x, y) in the pixel font at [size], shrunk until it fits [max]. */
    fun text(text: String, x: Int, y: Int, size: Int, c: Int, max: Int, align: Int = 0) {
        var s = size
        while (s > 1 && width(text) * s > max) s--
        var shown = text
        while (shown.isNotEmpty() && width(shown) * s > max) shown = shown.dropLast(1)
        val w = width(shown) * s
        val left = when (align) { 1 -> x - w / 2; 2 -> x - w; else -> x }
        val top = y + (size - s) * GLYPH_H
        for (p in textPixels(shown).points) rect(left + p.x * s, top + p.y * s, s, s, c)
    }

    /** Text over up to [lines] lines, as large as fits; returns its height. */
    fun lines(text: String, x: Int, y: Int, size: Int, c: Int, max: Int, lines: Int, center: Boolean = false): Int {
        var s = size
        var set = wrap(text, max / s)
        while (s > 1 && (set.size > lines || set.any { width(it) * s > max })) { s--; set = wrap(text, max / s) }
        set = set.take(lines)
        set.forEachIndexed { i, l -> text(l, if (center) x else x, y + i * LINE * s, s, c, max, if (center) 1 else 0) }
        return (set.size * LINE - 3) * s
    }

    private fun wrap(text: String, maxPx: Int): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (width(next) <= maxPx || line.isEmpty()) line = next else { out += line; line = word }
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    fun mascot(pose: Mascot.Pose, x: Int, y: Int, scale: Int) {
        val tones = PixelPalette.tones(false)
        Mascot.sprite(pose).forEachCell { cx, cy, tone ->
            val c = tones[tone] ?: return@forEachCell
            if ((c ushr 24) == 0) return@forEachCell
            rect(x + cx * scale, y + cy * scale, scale, scale, c)
        }
    }
}

/** Text on a poster colour (dark on the light ones), and its rows: the colour a step darker. */
private fun inks(color: Int): Triple<Int, Int, Int> {
    val r = (color shr 16) and 255
    val g = (color shr 8) and 255
    val b = color and 255
    val light = 0.299 * r + 0.587 * g + 0.114 * b > 150
    fun shade(v: Int) = (v * if (light) 0.86 else 0.78).toInt()
    fun mix(v: Int, to: Int) = (v + (to - v) * 0.7).toInt()
    val ink = if (light) 0xFF1A1405.toInt() else 0xFFFFFFFF.toInt()
    val soft = if (light) argb(mix(r, 26), mix(g, 20), mix(b, 5)) else argb(mix(r, 255), mix(g, 255), mix(b, 255))
    return Triple(ink, soft, argb(shade(r), shade(g), shade(b)))
}

private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

private fun drawPoster(d: PosterData): Bitmap {
    val c = Canvas180(d.color)
    val (ink, soft, box) = inks(d.color)
    val big = d.items.firstOrNull()
    val rest = d.items.drop(1).take(CARD_SLOTS - 1)
    var y = 12 + run { val h = Canvas180(d.color).lines(d.title, 12, 12, 2, ink, 156, 3); h } + 6
    // The mascot behind the rows, peeking in at the bottom.
    val rowsHeight = rest.sumOf { (l, v) -> (if (c.width(l) + 8 + c.width(v) * 2 <= 144) 20 else 30) + 3 }
    val bigBlock = if (big != null) 2 + 7 * 8 + 5 + 2 * GLYPH_H + 8 else 0
    val rowsEnd = y + (if (d.name.isNotEmpty()) LINE + 4 else 0) + bigBlock + rowsHeight
    c.mascot(d.pose.pose, 66, maxOf(232, minOf(rowsEnd + 4, 262)), 3)
    c.lines(d.title, 12, 12, 2, ink, 156, 3)
    if (d.name.isNotEmpty()) { c.text(d.name, 12, y, 1, soft, 156); y += LINE + 4 }
    if (big != null) {
        val bigTop = y + 2
        c.text(big.second, 90, bigTop, 8, ink, 164, align = 1)
        val labelY = bigTop + 7 * 8 + 5
        val h = c.lines(big.first, 90, labelY, 2, ink, 160, 1, center = true)
        y = labelY + h + 8
    }
    for ((label, value) in rest) {
        val oneLine = c.width(label) + 8 + c.width(value) * 2 <= 144
        val h = if (oneLine) 20 else 30
        c.rect(12, y, 156, h, box)
        if (oneLine) {
            c.text(label, 18, y + 10, 1, soft, 144)
            c.text(value, 162, y + 3, 2, ink, 144, align = 2)
        } else {
            c.text(label, 18, y + 4, 1, soft, 144)
            c.text(value, 18, y + 13, 2, ink, 144)
        }
        y += h + 3
    }
    c.text("POKYH.COM", 10, 306, 1, ink, 70)
    return Bitmap.createBitmap(c.px, CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
}

private fun bigPng(d: PosterData): Bitmap = Bitmap.createScaledBitmap(drawPoster(d), CARD_W * 6, CARD_H * 6, false)

private fun shareImage(context: Context, bitmap: Bitmap, text: String) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "pokyh-recap.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Into the gallery (Pictures/POKYH); true when it worked. */
private fun saveImage(context: Context, bitmap: Bitmap): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "pokyh-recap-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/POKYH")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
}.getOrDefault(false)

/**
 * The poster to keep and share, as the web's recap summary: the pose (swipe or the arrows), the
 * colour, which figures (up to five) and whether the first name is on it; then share the picture,
 * save it as PNG, or copy the text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecapPosterDialog(
    title: String,
    firstName: String?,
    items: List<Triple<String, String, String>>, // id, label, value
    lang: Lang,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var pose by remember { mutableStateOf(CardPose.Cool) }
    var color by remember { mutableStateOf(POSTER_COLORS.first()) }
    var picked by remember { mutableStateOf(items.take(CARD_SLOTS).map { it.first }) }
    var showName by remember { mutableStateOf(firstName != null) }
    var note by remember { mutableStateOf<String?>(null) }
    val shown = picked.mapNotNull { id -> items.firstOrNull { it.first == id } }.map { recapText(lang, it.second) to it.third }
    val data = PosterData(title, if (showName) firstName.orEmpty() else "", shown, pose, color)
    val preview = remember(title, firstName, showName, picked, pose, color) { drawPoster(data) }
    val shareText = recapText(lang, "shareText", mapOf("title" to title, "url" to "pokyh.com"))
    fun step(d: Int) { pose = CardPose.entries[(pose.ordinal + d + CardPose.entries.size) % CardPose.entries.size] }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF16122E)).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(recapText(lang, "customize"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                PosterButton(recapText(lang, "close")) { onDismiss() }
            }
            Image(
                preview.asImageBitmap(),
                contentDescription = title,
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(CARD_W / CARD_H.toFloat()).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PosterButton("<") { step(-1) }
                Text(recapText(lang, pose.key), color = Color.White, fontWeight = FontWeight.Bold)
                PosterButton(">") { step(1) }
            }
            Label(recapText(lang, "customizeColor"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                POSTER_COLORS.forEach { c ->
                    Box(
                        Modifier.size(34.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(Color(c))
                            .then(if (c == color) Modifier.padding(3.dp).background(Color.White).padding(3.dp).background(Color(c)) else Modifier)
                            .clickable { color = c },
                    )
                }
            }
            Label(recapText(lang, "customizeFigures") + " · " + recapText(lang, "customizeRoom", mapOf("n" to CARD_SLOTS)))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { (id, label, value) ->
                    val on = id in picked
                    val full = !on && picked.size >= CARD_SLOTS
                    CheckRow(recapText(lang, label), value, on, enabled = !full) {
                        picked = if (on) picked - id else picked + id
                    }
                }
                if (firstName != null) CheckRow(recapText(lang, "customizeName"), firstName, showName, enabled = true) { showName = !showName }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterButton(recapText(lang, "share"), accent = true) { shareImage(context, bigPng(data), shareText) }
                PosterButton(recapText(lang, "downloadPngLong")) {
                    note = if (saveImage(context, bigPng(data))) recapText(lang, "shareSaved") else null
                }
                PosterButton(recapText(lang, "shareCopy")) {
                    clipboard.setText(AnnotatedString(shareText))
                    note = recapText(lang, "shareCopied")
                }
            }
            note?.let { Text(it, color = Color(0xFFB8F5C8), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun CheckRow(label: String, value: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.06f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(18.dp).background(if (on) Color(0xFF7C6BFF) else Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            if (on) Icon(PokyhIcons.check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PosterButton(text: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (accent) Color(0xFF16122E) else Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(if (accent) Color(0xFFFFD84A) else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
