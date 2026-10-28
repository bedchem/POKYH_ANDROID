package dev.plattnericus.pokyh.ui.recap

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.Lang
import dev.plattnericus.pokyh.core.i18n.recapText
import dev.plattnericus.pokyh.core.recap.RecapFigures
import dev.plattnericus.pokyh.core.recap.RecapStore
import dev.plattnericus.pokyh.core.recap.RecapStoryModel
import dev.plattnericus.pokyh.core.recap.RecapStoryModel.Mood
import dev.plattnericus.pokyh.core.recap.RecapStoryModel.Theme
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.pixel.Mascot
import dev.plattnericus.pokyh.ui.pixel.Mascot.Arms
import dev.plattnericus.pokyh.ui.pixel.Mascot.Eyes
import dev.plattnericus.pokyh.ui.pixel.Mascot.Mouth
import dev.plattnericus.pokyh.ui.pixel.MascotLook
import dev.plattnericus.pokyh.ui.pixel.MascotMotion
import dev.plattnericus.pokyh.ui.pixel.PixelArt
import dev.plattnericus.pokyh.ui.pixel.PixelLayer
import dev.plattnericus.pokyh.ui.pixel.PixelScene
import dev.plattnericus.pokyh.ui.pixel.Px
import dev.plattnericus.pokyh.ui.pixel.bubble
import dev.plattnericus.pokyh.ui.pixel.mascotLayers
import dev.plattnericus.pokyh.ui.pixel.rememberReducedMotion
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import java.text.NumberFormat
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

@HiltViewModel
class RecapViewModel @Inject constructor(val store: RecapStore, val appState: AppState) : ViewModel()

// ── The pages' colours (components/recap/themes.ts) ─────────────────────────

private class ThemeColors(val bg: Color, val dot: Color, val ground: Color, val edge: Color, val chip: Color, val soft: Color, val accent: Color, val accentInk: Color)

private fun c(hex: Long) = Color(0xFF000000 or hex)

private val THEMES = mapOf(
    Theme.Night to ThemeColors(c(0x1b1640), c(0x231d52), c(0x2c2566), c(0x3d3488), c(0x2f2870), c(0xd4cffb), c(0xffd166), c(0x1b1640)),
    Theme.Brand to ThemeColors(c(0x312a92), c(0x3a32a6), c(0x262079), c(0x4f46e5), c(0x3d35b0), c(0xe0e7ff), c(0x7ee8fa), c(0x1e1b4b)),
    Theme.Ocean to ThemeColors(c(0x0b3b66), c(0x0e4677), c(0x0e5289), c(0x1769aa), c(0x0f4a7d), c(0xd3e9ff), c(0xffd166), c(0x0b3b66)),
    Theme.Sky to ThemeColors(c(0x1d4f91), c(0x2259a1), c(0x173f74), c(0x3b82d6), c(0x255ea8), c(0xdbeafe), c(0xfde68a), c(0x16365f)),
    Theme.Teal to ThemeColors(c(0x0d4d4d), c(0x105858), c(0x116363), c(0x178080), c(0x125d5d), c(0xccfbf1), c(0xfde047), c(0x0b3b3b)),
    Theme.Mint to ThemeColors(c(0x0f5132), c(0x125c3a), c(0x146c43), c(0x1b8a56), c(0x156240), c(0xd1fae5), c(0xfef08a), c(0x0d3b25)),
    Theme.Forest to ThemeColors(c(0x1f3d1a), c(0x25481f), c(0x2d5a26), c(0x3b7532), c(0x2a5223), c(0xdcfce7), c(0xfdba74), c(0x1f3d1a)),
    Theme.Gold to ThemeColors(c(0x6b4300), c(0x7a4d00), c(0x8a5800), c(0xa86c00), c(0x7d4f00), c(0xfef3c7), c(0xffe066), c(0x3f2700)),
    Theme.Sunset to ThemeColors(c(0x7c2d12), c(0x883214), c(0x9a3412), c(0xc2410c), c(0x8f3313), c(0xffedd5), c(0xfde047), c(0x45180a)),
    Theme.Candy to ThemeColors(c(0x831843), c(0x8f1b4a), c(0x9d174d), c(0xbe185d), c(0x921a4b), c(0xfce7f3), c(0xfde68a), c(0x4a0d26)),
    Theme.Rose to ThemeColors(c(0x881337), c(0x94163d), c(0x9f1239), c(0xbe123c), c(0x97163e), c(0xffe4e6), c(0xfed7aa), c(0x4c0a1f)),
    Theme.Grape to ThemeColors(c(0x4c1d95), c(0x5521a6), c(0x5b21b6), c(0x6d28d9), c(0x5622ab), c(0xede9fe), c(0xf9a8d4), c(0x2e1065)),
    Theme.Dusk to ThemeColors(c(0x2a2770), c(0x302c7e), c(0x3730a3), c(0x4338ca), c(0x332f8c), c(0xe0e7ff), c(0xf0abfc), c(0x1e1b4b)),
    Theme.Ember to ThemeColors(c(0x8a3a0c), c(0x97400e), c(0xa54a0f), c(0xc2570c), c(0x9a440e), c(0xffedd5), c(0xfcd34d), c(0x451a03)),
    Theme.Alarm to ThemeColors(c(0x7f1d1d), c(0x8c2020), c(0x991b1b), c(0xb91c1c), c(0x922020), c(0xfee2e2), c(0xfde047), c(0x450a0a)),
)

// ── How the mascot takes a figure (components/recap/reactions.ts) ───────────

private enum class Particles { None, Confetti, Stars, Rain, Alarm }

private class Reaction(val look: MascotLook, val field: Particles = Particles.None)

private fun pose(eyes: Eyes, look: Pair<Int, Int>, mouth: Mouth, arms: Arms) = Mascot.Pose(eyes, look, mouth, arms)
private val O = 0 to 0

private fun reactionOf(mood: Mood): Reaction = when (mood) {
    Mood.Wave -> Reaction(MascotLook(pose(Eyes.Happy, O, Mouth.Grin, Arms.Wave), wave = true, motion = MascotMotion.Hop), Particles.Stars)
    Mood.Legend -> Reaction(MascotLook(pose(Eyes.Happy, O, Mouth.Grin, Arms.Cheer), motion = MascotMotion.Jump), Particles.Confetti)
    Mood.Great -> Reaction(MascotLook(pose(Eyes.Happy, O, Mouth.Grin, Arms.Cheer), motion = MascotMotion.Jump), Particles.Stars)
    Mood.Good -> Reaction(MascotLook(pose(Eyes.Happy, O, Mouth.Grin, Arms.Thumb), motion = MascotMotion.Hop))
    Mood.Ok -> Reaction(MascotLook(pose(Eyes.Open, 1 to 0, Mouth.Smile, Arms.Wave), wave = true, blink = true))
    Mood.Meh -> Reaction(MascotLook(pose(Eyes.Open, -1 to 0, Mouth.Wobble, Arms.Down), sweat = true, blink = true))
    Mood.Worried -> Reaction(MascotLook(pose(Eyes.Wide, O, Mouth.O, Arms.Peek), mark = '!', sweat = true, motion = MascotMotion.Shake), Particles.Rain)
    Mood.Alarm -> Reaction(MascotLook(pose(Eyes.Closed, O, Mouth.Frown, Arms.Cover), mark = '!', sweat = true, motion = MascotMotion.Shake), Particles.Alarm)
    Mood.Proud -> Reaction(MascotLook(pose(Eyes.Happy, O, Mouth.Grin, Arms.Thumb), motion = MascotMotion.Hop))
    Mood.Love -> Reaction(MascotLook(pose(Eyes.Happy, O, Mouth.Grin, Arms.Wave), wave = true, motion = MascotMotion.Hop))
    Mood.Focused -> Reaction(MascotLook(pose(Eyes.Open, 1 to 0, Mouth.Flat, Arms.Lens), blink = true))
    Mood.Sleepy -> Reaction(MascotLook(pose(Eyes.Closed, O, Mouth.O, Arms.Down), think = true))
    Mood.Party -> Reaction(MascotLook(pose(Eyes.Happy, O, Mouth.Grin, Arms.Cheer), motion = MascotMotion.Jump), Particles.Confetti)
    Mood.Cool -> Reaction(MascotLook(pose(Eyes.Open, 1 to 0, Mouth.Smile, Arms.Thumb), motion = MascotMotion.Hop))
    Mood.Curious -> Reaction(MascotLook(pose(Eyes.Open, 1 to -1, Mouth.O, Arms.Lens), mark = '?'))
    Mood.Peek -> Reaction(MascotLook(pose(Eyes.Open, 1 to 1, Mouth.O, Arms.Peek), blink = true))
    Mood.Angry -> Reaction(MascotLook(pose(Eyes.Angry, O, Mouth.Frown, Arms.Fists), anger = true, motion = MascotMotion.Fume))
    Mood.Angel -> Reaction(MascotLook(pose(Eyes.Closed, O, Mouth.Smile, Arms.Down), motion = MascotMotion.Hop), Particles.Stars)
    Mood.Zap -> Reaction(MascotLook(pose(Eyes.Wide, O, Mouth.Grin, Arms.Cheer), motion = MascotMotion.Buzz))
    Mood.Night -> Reaction(MascotLook(pose(Eyes.Closed, O, Mouth.Smile, Arms.Down)), Particles.Stars)
    Mood.Early -> Reaction(MascotLook(pose(Eyes.Wide, O, Mouth.Grin, Arms.Wave), wave = true, motion = MascotMotion.Jump))
    Mood.Cheer -> Reaction(MascotLook(pose(Eyes.Happy, O, Mouth.Grin, Arms.Cheer), motion = MascotMotion.Hop), Particles.Confetti)
}

// ── Formatting in the reader's language (the web's useFmt) ──────────────────

private fun fmtFor(lang: Lang): RecapStoryModel.Fmt = object : RecapStoryModel.Fmt {
    private val c = dev.plattnericus.pokyh.core.i18n.stringsFor(lang).common
    override fun date(n: Int): String {
        val d = RecapFigures.dateOfNum(n)
        return "${c.weekdays[d.dayOfWeek.ordinal]}, ${c.dayMonth(d.dayOfMonth, d.monthValue)}"
    }
    override fun day(iso: String): String {
        val d = java.time.LocalDate.parse(iso)
        return c.dateLong(d.dayOfMonth, d.monthValue, d.year)
    }
    override fun weekday(i: Int): String = c.weekdays[i.coerceIn(0, 6)]
    override fun time(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
    override fun num(n: Double, digits: Int): String = NumberFormat.getInstance(lang.locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(n)
    override fun dish(name: BackendClient.RecapDishName): String = when (lang) {
        Lang.It -> name.it.ifEmpty { name.de }
        Lang.En -> name.en.ifEmpty { name.de }
        Lang.De -> name.de
    }
}

// ── The host: on screen while a recap is open ───────────────────────────────

/**
 * Recaps in the app frame (the web's `RecapHost`): starts the store, loads the WebUntis figures of
 * a recap still waiting a moment after it is there, and shows the story while one is open.
 */
@Composable
fun RecapHost(viewModel: RecapViewModel = hiltViewModel()) {
    val store = viewModel.store
    LaunchedEffect(Unit) { store.start() }
    val state by store.state.collectAsStateWithLifecycle()
    val session by viewModel.appState.session.collectAsStateWithLifecycle()
    // The figures of the newest recap not watched to its end, ahead of time.
    val waiting = state.recaps?.firstOrNull { it.finishedAt == null }
    LaunchedEffect(waiting?.id, session?.studentId) {
        val w = waiting ?: return@LaunchedEffect
        if ((session?.studentId ?: 0) <= 0 || w.sections.none { it != "pokyh" }) return@LaunchedEffect
        delay(3000)
        runCatching { store.loadUntis(w) }
    }
    val current = state.open?.let { id -> state.recaps?.firstOrNull { it.id == id } } ?: return
    RecapStoryOverlay(recap = current, store = store, appState = viewModel.appState, onClose = store::close)
}

/** The recap in a tab's header (the web's `RecapBanner` on phones): a round button, its ring turning while it is new. */
@Composable
fun RecapHeaderButton(viewModel: RecapViewModel = hiltViewModel()) {
    val state by viewModel.store.state.collectAsStateWithLifecycle()
    val recap = state.recaps?.firstOrNull() ?: return
    val isNew = recap.finishedAt == null
    val lang by I18n.lang.collectAsStateWithLifecycle()
    val label = "${recapText(lang, if (isNew) "bannerNew" else "bannerSeen")}: ${recap.title}"
    val colors = PokyhTheme.colors
    val accent = dev.plattnericus.pokyh.ui.theme.Brand.accent
    val spin = rememberInfiniteTransition(label = "recapRing")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "recapRingAngle")
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.cardAlt)
            .clickable(onClickLabel = label) { viewModel.store.open(recap.id) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            rotate(if (isNew) angle else 0f) {
                drawCircle(
                    color = accent,
                    radius = size.minDimension / 2 - stroke / 2,
                    style = Stroke(
                        width = stroke,
                        pathEffect = if (isNew) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null,
                    ),
                )
            }
        }
        PixelArt(
            scene = PixelScene(Mascot.W + 4, Mascot.H + 6, mascotLayers(MascotLook(pose(Eyes.Happy, O, Mouth.Grin, Arms.Wave), wave = true), "banner", marksBeside = true), x0 = -2, y0 = -6),
            modifier = Modifier.width(26.dp),
            clip = false,
        )
    }
}

// ── The story ───────────────────────────────────────────────────────────────

private sealed interface Load {
    data object Waiting : Load
    data class Ready(val slides: List<RecapStoryModel.Slide>, val summary: List<RecapStoryModel.SummaryItem>) : Load
    data class Failed(val message: String) : Load
}

/**
 * A recap as a story, page after page (the web's `RecapStory`): a figure, what it means and the
 * mascot's take on it. Each page is a little scene — a sky in the page's colour, the ground the
 * mascot stands on. A tap on the right or "Weiter" goes on, on the left back; a swipe down or
 * back closes. Nothing moves on by itself. The last page sums it up.
 */
@Composable
private fun RecapStoryOverlay(recap: BackendClient.Recap, store: RecapStore, appState: AppState, onClose: () -> Unit) {
    var posterOpen by remember { mutableStateOf(false) }
    val lang by I18n.lang.collectAsStateWithLifecycle()
    val session by appState.session.collectAsStateWithLifecycle()
    val progress by store.progress.collectAsStateWithLifecycle()
    val fmt = remember(lang) { fmtFor(lang) }
    var load by remember(recap.id) { mutableStateOf<Load>(Load.Waiting) }
    var index by remember(recap.id) { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    fun t(text: RecapStoryModel.Text?) = text?.let { recapText(lang, it.key, it.vars) }

    BackHandler(onBack = onClose)
    LaunchedEffect(recap.id) { store.markView(recap.id, finished = false) }
    LaunchedEffect(recap.id, attempt, lang) {
        load = Load.Waiting
        val s = session
        val firstName = s?.personName?.trim()?.split(" ")?.firstOrNull { it.isNotEmpty() }.orEmpty()
        val detail = runCatching { store.detail(recap.id) }.getOrNull()
        val untis = if (s != null && s.hasUntis && recap.sections.any { it != "pokyh" }) {
            runCatching { store.loadUntis(recap).await() }.getOrNull()
        } else null
        if (detail == null && untis == null) {
            load = Load.Failed(recapText(lang, "errorAll"))
            return@LaunchedEffect
        }
        val input = RecapStoryModel.Input(recap.title, firstName, recap.periodFrom, recap.periodTo, recap.sections, untis, detail?.pokyh)
        load = Load.Ready(RecapStoryModel.build(input, fmt), RecapStoryModel.summaryItems(input, fmt))
    }

    val ready = load as? Load.Ready
    // The first page is there at once; its button shows how far the figures are.
    val introOnly = RecapStoryModel.Slide(
        "intro", Theme.Night, Mood.Wave, RecapStoryModel.Layout.Intro, RecapStoryModel.Place.Bottom,
        lead = (session?.personName?.trim()?.split(" ")?.firstOrNull { it.isNotEmpty() })?.let { RecapStoryModel.Text("introHello", mapOf("name" to it)) } ?: RecapStoryModel.Text("introHelloAnon"),
        value = RecapStoryModel.Value.Words(recap.title),
        body = listOf(RecapStoryModel.Text("introLead", mapOf("from" to fmt.day(recap.periodFrom), "to" to fmt.day(recap.periodTo)))),
        quip = RecapStoryModel.Text("introQuip"),
    )
    val slides = ready?.slides ?: listOf(introOnly)
    val slide = slides[index.coerceIn(0, slides.lastIndex)]
    LaunchedEffect(slide.id) { if (slide.layout == RecapStoryModel.Layout.Summary) store.markView(recap.id, finished = true) }

    fun next() {
        if (ready == null) return
        if (index < slides.lastIndex) index++ else onClose()
    }
    fun back() { if (index > 0) index-- }

    val theme = THEMES.getValue(slide.theme)
    val key = "${session?.studentId}:${recap.periodFrom}:${recap.periodTo}:${recap.sections.joinToString(",")}"
    val p = progress[key]
    val primaryLabel = when {
        slide.layout == RecapStoryModel.Layout.Summary -> recapText(lang, "done")
        ready == null && load is Load.Failed -> recapText(lang, "retry")
        ready == null -> if (p != null && p.total > 0) recapText(lang, "loading", mapOf("done" to p.done, "total" to p.total)) else recapText(lang, "loadingShort")
        index == 0 -> recapText(lang, "start")
        else -> recapText(lang, "next")
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.bg)
            .pointerInput(ready, index) {
                detectTapGestures { pos -> if (pos.x < size.width / 3f) back() else next() }
            }
            .pointerInput(Unit) {
                var drag = 0f
                detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if (drag > 160f) onClose() }) { _, dy -> drag += dy }
            },
    ) {
        AnimatedContent(targetState = slide, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) }, label = "recapPage") { page ->
            val th = THEMES.getValue(page.theme)
            Box(Modifier.fillMaxSize()) {
                SkyAndGround(th)
                ParticleField(reactionOf(page.mood).field, page.id)
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 56.dp, start = 24.dp, end = 24.dp),
                ) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        if (page.layout == RecapStoryModel.Layout.Summary) {
                            SummaryPage(page, ready?.summary.orEmpty(), th, lang)
                        } else {
                            PageText(page, th, lang, fmt)
                        }
                    }
                    StoryMascot(page, th, lang)
                    Spacer(Modifier.height(GroundHeight - 8.dp))
                }
            }
        }

        // The progress through the pages and the way out.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            slides.forEachIndexed { i, _ ->
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= index) theme.accent else theme.chip))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(theme.chip).clickable(onClickLabel = recapText(lang, "close"), onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PokyhIcons.close, contentDescription = recapText(lang, "close"), tint = theme.soft, modifier = Modifier.size(18.dp))
            }
        }

        // The buttons on the ground.
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (index > 0) {
                PixelButton(recapText(lang, "back"), fill = theme.chip, ink = theme.soft) { back() }
            }
            if (slide.layout == RecapStoryModel.Layout.Summary) {
                PixelButton(recapText(lang, "replay"), fill = theme.chip, ink = theme.soft) { index = 0 }
                // The poster to keep and share, as on the web.
                PixelButton(recapText(lang, "share"), fill = theme.chip, ink = theme.soft) { posterOpen = true }
            }
            Spacer(Modifier.weight(1f))
            PixelButton(primaryLabel, fill = theme.accent, ink = theme.accentInk, wide = ready == null) {
                when {
                    load is Load.Failed -> attempt++
                    else -> next()
                }
            }
        }
    }
    if (posterOpen) {
        RecapPosterDialog(
            title = recap.title,
            firstName = session?.personName?.trim()?.split(" ")?.firstOrNull { it.isNotEmpty() },
            items = ready?.summary.orEmpty().map { Triple(it.id, it.label, it.value) },
            lang = lang,
            onDismiss = { posterOpen = false },
        )
    }
}

/** One page of the story on its own, for the debug preview gallery (no store, no backend). */
@Composable
internal fun RecapSlidePreview(page: RecapStoryModel.Slide, summary: List<RecapStoryModel.SummaryItem> = emptyList(), modifier: Modifier = Modifier) {
    val lang by I18n.lang.collectAsStateWithLifecycle()
    val fmt = remember(lang) { fmtFor(lang) }
    val th = THEMES.getValue(page.theme)
    Box(modifier.background(th.bg)) {
        SkyAndGround(th)
        ParticleField(reactionOf(page.mood).field, page.id)
        Column(Modifier.fillMaxSize().padding(top = 32.dp, start = 24.dp, end = 24.dp)) {
            Column(Modifier.weight(1f)) {
                if (page.layout == RecapStoryModel.Layout.Summary) SummaryPage(page, summary, th, lang) else PageText(page, th, lang, fmt)
            }
            StoryMascot(page, th, lang)
            Spacer(Modifier.height(GroundHeight - 8.dp))
        }
    }
}

private val GroundHeight = 120.dp

/** The sky's dotted pattern and the ground's checker of tiles, with its lit top edge. */
@Composable
private fun SkyAndGround(th: ThemeColors) {
    Canvas(Modifier.fillMaxSize()) {
        val step = 12.dp.toPx()
        val dot = 2.dp.toPx()
        val groundTop = size.height - GroundHeight.toPx()
        var y = 5.dp.toPx()
        while (y < groundTop) {
            var x = 5.dp.toPx()
            while (x < size.width) {
                drawRect(th.dot, Offset(x, y), Size(dot, dot))
                x += step
            }
            y += step
        }
        drawRect(th.ground, Offset(0f, groundTop), Size(size.width, size.height - groundTop))
        val tile = 8.dp.toPx()
        var ty = groundTop
        var row = 0
        while (ty < size.height) {
            var tx = if (row % 2 == 0) 0f else tile
            while (tx < size.width) {
                drawRect(th.chip, Offset(tx, ty), Size(tile, tile))
                tx += tile * 2
            }
            ty += tile
            row++
        }
        drawRect(th.edge, Offset(0f, groundTop), Size(size.width, 4.dp.toPx()))
    }
}

/** Confetti, stars, rain or an alarm light, seeded per page so they stay where they are. */
@Composable
private fun ParticleField(kind: Particles, seed: String) {
    if (kind == Particles.None || rememberReducedMotion()) return
    val pieces = remember(kind, seed) {
        val r = Random(seed.hashCode() + kind.ordinal)
        List(if (kind == Particles.Confetti) 28 else if (kind == Particles.Stars) 14 else if (kind == Particles.Rain) 30 else 0) {
            floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat())
        }
    }
    val confetti = remember { listOf(c(0xfde047), c(0xf472b6), c(0x60a5fa), c(0x34d399), c(0xfb923c), c(0xc4b5fd), c(0xf87171)) }
    val clock = rememberInfiniteTransition(label = "particles")
    val t by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "particlesT")
    Canvas(Modifier.fillMaxSize()) {
        when (kind) {
            Particles.Confetti -> pieces.forEachIndexed { i, p ->
                val s = (6 + (p[2] * 2).roundToInt() * 2).dp.toPx()
                val y = ((t * (1.2f + p[1]) + p[3]) % 1f) * size.height
                val x = p[0] * size.width + (p[2] - 0.5f) * 60.dp.toPx() * t
                drawRect(confetti[i % confetti.size], Offset(x, y), Size(s, s))
            }
            Particles.Stars -> pieces.forEach { p ->
                val on = ((t * (1.5f + p[2]) + p[3]) % 1f) < 0.5f
                if (on) {
                    val x = (0.04f + p[0] * 0.92f) * size.width
                    val y = (0.08f + p[1] * 0.58f) * size.height
                    val u = 3.dp.toPx()
                    drawRect(Color.White.copy(alpha = 0.8f), Offset(x, y - u), Size(u, u * 3))
                    drawRect(Color.White.copy(alpha = 0.8f), Offset(x - u, y), Size(u * 3, u))
                }
            }
            Particles.Rain -> pieces.forEach { p ->
                val y = ((t * (3f + p[1] * 2) + p[3]) % 1f) * size.height
                drawRect(Color.White.copy(alpha = 0.35f), Offset(p[0] * size.width, y), Size(2.dp.toPx(), 12.dp.toPx()))
            }
            Particles.Alarm -> if ((t * 5).toInt() % 2 == 0) drawRect(Color(0x33FF2020))
            Particles.None -> Unit
        }
    }
}

/** A page's words: the lead, the figure (counting up), its unit, a meter or a list, the body. */
@Composable
private fun PageText(page: RecapStoryModel.Slide, th: ThemeColors, lang: Lang, fmt: RecapStoryModel.Fmt) {
    fun t(text: RecapStoryModel.Text?) = text?.let { recapText(lang, it.key, it.vars) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        t(page.lead)?.let { Text(it, style = PokyhType.title3, color = th.soft) }
        when (val v = page.value) {
            is RecapStoryModel.Value.Count -> CountUp(v, page.id, th, fmt)
            is RecapStoryModel.Value.Words -> Text(
                v.text,
                style = PokyhType.display.copy(fontSize = if (page.layout == RecapStoryModel.Layout.Intro) 40.sp else 34.sp, lineHeight = 42.sp),
                color = Color.White,
            )
            null -> Unit
        }
        t(page.unit)?.let { Text(it, style = PokyhType.headline, color = th.soft) }
        page.meter?.let { (value, limit) -> Meter(value, limit, th) }
        if (page.list.isNotEmpty()) {
            val max = page.list.maxOf { it.value }.takeIf { it > 0 } ?: 1.0
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                page.list.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(row.labelKey?.let { recapText(lang, it) } ?: row.label, style = PokyhType.callout, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(row.display, style = PokyhType.callout.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).background(th.chip)) {
                            Box(Modifier.fillMaxWidth((row.value / max).toFloat().coerceIn(0.04f, 1f)).fillMaxHeight().background(th.accent))
                        }
                    }
                }
            }
        }
        page.body.forEach { Text(recapText(lang, it.key, it.vars), style = PokyhType.body, color = th.soft) }
    }
}

/** The figure counts up (or, for a ranking, down) in steps, then stands. */
@Composable
private fun CountUp(v: RecapStoryModel.Value.Count, id: String, th: ThemeColors, fmt: RecapStoryModel.Fmt) {
    val reduced = rememberReducedMotion()
    val shown = remember(id) { Animatable(if (reduced) v.n.toFloat() else v.from.toFloat()) }
    LaunchedEffect(id, v.n) { if (!reduced) shown.animateTo(v.n.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    val n = if (v.decimals == 0) shown.value.roundToInt().toDouble() else shown.value.toDouble()
    Text(
        v.prefix + fmt.num(n, v.decimals) + v.suffix,
        style = PokyhType.display.copy(fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.ExtraBold),
        color = th.accent,
    )
}

/** The absence rate against the limit of 25 %. */
@Composable
private fun Meter(value: Double, limit: Double, th: ThemeColors) {
    val scale = maxOf(limit * 1.6, value)
    Box(Modifier.fillMaxWidth().height(14.dp).background(th.chip)) {
        Box(Modifier.fillMaxWidth((value / scale).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(if (value >= limit) Color(0xFFEF4444) else th.accent))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(Modifier.padding(start = maxWidth * (limit / scale).toFloat()).width(3.dp).fillMaxHeight().background(Color.White))
        }
    }
}

/** The mascot on the ground at the page's place, with what it says in a bubble. */
@Composable
private fun StoryMascot(page: RecapStoryModel.Slide, th: ThemeColors, lang: Lang) {
    val reaction = reactionOf(page.mood)
    val quip = page.quip?.let { recapText(lang, it.key, it.vars) }
    val speech = quip?.let { bubble(it, Mascot.W - 2, 17, 76) }
    val top = minOf(-12, speech?.let { it.top() - 1 } ?: 0)
    val layers = buildList {
        addAll(mascotLayers(reaction.look, "page:${page.id}", marksBeside = true))
        if (speech != null) add(PixelLayer(speech, listOf(Px.pop), startKey = "say:${page.id}"))
    }
    val align = when (page.place) {
        RecapStoryModel.Place.Left -> Alignment.CenterStart
        RecapStoryModel.Place.Right, RecapStoryModel.Place.Corner -> Alignment.CenterEnd
        RecapStoryModel.Place.Bottom -> Alignment.Center
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        PixelArt(
            scene = PixelScene(Mascot.W + 80, Mascot.H - top, layers, y0 = top),
            modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(0.82f),
            clip = false,
        )
    }
}

/** The last page: the period's figures at a glance. */
@Composable
private fun SummaryPage(page: RecapStoryModel.Slide, items: List<RecapStoryModel.SummaryItem>, th: ThemeColors, lang: Lang) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        (page.value as? RecapStoryModel.Value.Words)?.let { Text(it.text, style = PokyhType.display.copy(fontSize = 34.sp), color = Color.White) }
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    Column(Modifier.weight(1f).background(th.chip).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(recapText(lang, item.label), style = PokyhType.footnote, color = th.soft, maxLines = 2)
                        Text(item.value, style = PokyhType.title2, color = th.accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A square-cornered button in the scene's colours, as on the web's pixel story. */
@Composable
private fun PixelButton(text: String, fill: Color, ink: Color, wide: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .height(48.dp)
            .background(fill)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp)
            .then(if (wide) Modifier.widthIn(max = 260.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PokyhType.button, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
