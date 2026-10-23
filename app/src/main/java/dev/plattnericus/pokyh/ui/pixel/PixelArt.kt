package dev.plattnericus.pokyh.ui.pixel

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import java.util.WeakHashMap
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A layer of a drawing: a sprite and the animations on it, outermost first (they add up, as the
 * web's nested `<g class>` groups do). Layers with the same [startKey] start their animations
 * together, the moment the key first appears — the web restarts an animation by giving its
 * group a new `key`; without one a layer runs from the moment the drawing is first shown.
 */
class PixelLayer(
    val sprite: Sprite,
    val anims: List<PxAnim> = emptyList(),
    val startKey: Any? = null,
    val dx: Int = 0,
    val dy: Int = 0,
)

/** A drawing: its own size in pixels (the web's viewBox, from [x0], [y0]) and its layers, back to front. */
class PixelScene(val w: Int, val h: Int, val layers: List<PixelLayer>, val x0: Int = 0, val y0: Int = 0)

/** A sprite as a bitmap at one pixel per drawing pixel, and where its top left lies. */
private class SpriteImage(val image: ImageBitmap, val x: Int, val y: Int, val w: Int, val h: Int)

private object SpriteImages {
    private val cache = arrayOf(WeakHashMap<Sprite, SpriteImage?>(), WeakHashMap<Sprite, SpriteImage?>())

    /** Sprites never change once drawn, so each is turned into a bitmap once per theme. */
    fun of(sprite: Sprite, dark: Boolean): SpriteImage? {
        val map = cache[if (dark) 1 else 0]
        if (map.containsKey(sprite)) return map[sprite]
        val made = build(sprite, dark)
        map[sprite] = made
        return made
    }

    private fun build(sprite: Sprite, dark: Boolean): SpriteImage? {
        val b = sprite.bounds() ?: return null
        val w = b[2] - b[0] + 1
        val h = b[3] - b[1] + 1
        val tones = PixelPalette.tones(dark)
        val pixels = IntArray(w * h)
        sprite.forEachCell { x, y, tone ->
            pixels[(y - b[1]) * w + (x - b[0])] = tones[tone] ?: 0
        }
        val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return SpriteImage(bitmap.asImageBitmap(), b[0], b[1], w, h)
    }
}

/** Whether the system asks for less motion (animator duration scale off): the drawings stand still. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/**
 * Draws a [PixelScene] as crisp square pixels, scaled to the space given (a whole number of
 * screen pixels per drawing pixel where there is room for it, centred), animated in the web's
 * sprite steps. The width decides the size; the height follows the drawing's aspect ratio unless
 * [keepAspect] is off.
 */
@Composable
fun PixelArt(
    scene: PixelScene,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    clip: Boolean = true,
    keepAspect: Boolean = true,
    animate: Boolean = true,
) {
    val dark = PokyhTheme.colors.isDark
    val reduced = rememberReducedMotion()
    val running = animate && !reduced
    val clock = remember { mutableLongStateOf(System.nanoTime()) }
    val shownAt = remember { System.nanoTime() }
    // When each start key first appeared: its layers' animations run from then.
    val starts = remember { HashMap<Any, Long>() }
    val now = System.nanoTime()
    val keys = scene.layers.mapNotNullTo(HashSet()) { it.startKey }
    starts.keys.retainAll(keys)
    for (k in keys) starts.getOrPut(k) { now }

    if (running) {
        LaunchedEffect(Unit) {
            while (true) withFrameNanos { clock.longValue = System.nanoTime() }
        }
    }

    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    val sized = if (keepAspect) modifier.aspectRatio(scene.w.toFloat() / scene.h) else modifier
    Canvas(sized.then(semantics)) {
        val t = if (running) clock.longValue else shownAt
        drawPixelScene(scene, dark, reduced, clip) { layer ->
            val start = layer.startKey?.let { starts[it] } ?: shownAt
            (t - start) / 1_000_000
        }
    }
}

private val frame = Frame()

/** Draws [scene] into this canvas: [elapsed] gives each layer's animation time in ms. */
private fun DrawScope.drawPixelScene(
    scene: PixelScene,
    dark: Boolean,
    reduced: Boolean,
    clip: Boolean,
    elapsed: (PixelLayer) -> Long,
) {
    val fit = minOf(size.width / scene.w, size.height / scene.h)
    if (fit <= 0f) return
    // Whole screen pixels per drawing pixel once there is room for it, so every pixel is the same size.
    val s = if (fit >= 2f) floor(fit) else fit
    val ox = (size.width - scene.w * s) / 2f - scene.x0 * s
    val oy = (size.height - scene.h * s) / 2f - scene.y0 * s

    fun drawLayers() {
        for (layer in scene.layers) {
            val img = SpriteImages.of(layer.sprite, dark) ?: continue
            frame.reset()
            val ms = elapsed(layer)
            for (a in layer.anims) a.apply(ms, reduced, frame)
            if (frame.alpha <= 0.001) continue
            val x = ox + (img.x + layer.dx + frame.tx.toFloat()) * s
            val y = oy + (img.y + layer.dy + frame.ty.toFloat()) * s
            val dst = IntOffset(x.roundToInt(), y.roundToInt())
            val dstSize = IntSize((img.w * s).roundToInt(), (img.h * s).roundToInt())
            val draw = {
                drawImage(
                    image = img.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.w, img.h),
                    dstOffset = dst,
                    dstSize = dstSize,
                    alpha = frame.alpha.toFloat().coerceIn(0f, 1f),
                    filterQuality = FilterQuality.None,
                )
            }
            if (frame.rotate % 360.0 != 0.0) {
                rotate(frame.rotate.toFloat(), pivot = Offset(dst.x + dstSize.width / 2f, dst.y + dstSize.height / 2f)) { draw() }
            } else {
                draw()
            }
        }
    }

    if (clip) {
        clipRect(ox + scene.x0 * s, oy + scene.y0 * s, ox + (scene.x0 + scene.w) * s, oy + (scene.y0 + scene.h) * s) { drawLayers() }
    } else {
        drawLayers()
    }
}
