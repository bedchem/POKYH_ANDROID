package dev.plattnericus.pokyh.ui.login

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.i18n.LoginStrings
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.WordFilter
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
import dev.plattnericus.pokyh.ui.pixel.bubbleAbove
import dev.plattnericus.pokyh.ui.pixel.mascotLayers
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth

/**
 * The mascot of the sign-in screen — the web's `MascotBadge` (app/[lang]/login/Hallway.tsx), the
 * way phones show it: the mascot on its own above the form, its speech bubble beside it, without
 * the hallway and the door of room 2B. It answers the form: a wave and "Hallo, …!" while the name
 * is typed, both hands over its eyes for the password (peeking when the password is shown), wide
 * eyes and then a thumb up when the phone fills the form in, thinking while signing in, a wobble
 * and a drop of sweat at an error. A word it must not say ([WordFilter]) in the name makes it
 * angry: brows down, fists up, red in the face, fuming. Otherwise it blinks and breathes; each
 * key makes it hop. Decorative only.
 */

enum class MascotFocus { User, Password }

enum class MascotEventKind { Autofill, Error, Tap }

/** Something that happened, numbered so the same kind twice still counts twice. */
data class MascotEvent(val kind: MascotEventKind, val n: Long)

data class LoginMascotState(
    val focus: MascotFocus? = null,
    val username: String = "",
    val showPassword: Boolean = false,
    val loading: Boolean = false,
    /** Counts keystrokes: each one makes it hop. */
    val keys: Int = 0,
    val event: MascotEvent? = null,
    /** Back on the saved accounts (the Lock screen): it waves and says so. */
    val welcomeBack: Boolean = false,
)

private enum class Phase { Surprise, Thumb, Error, Mad }

private sealed interface Say {
    data object Hello : Say
    data class HelloName(val name: String) : Say
    data object Oops : Say
    data object Angry : Say
    data object Back : Say
    /** Tapped too often. */
    data object TooMuch : Say
}

private data class Look(val figure: MascotLook, val say: Say? = null)

/** The moments that play out on their own after an event. */
@Composable
private fun rememberPhase(event: MascotEvent?): Phase? {
    var phase by remember { mutableStateOf<Phase?>(null) }
    // When it was tapped lately: a tap does nothing, only many in a row make it mad.
    val taps = remember { ArrayDeque<Long>() }
    LaunchedEffect(event) {
        when (event?.kind) {
            MascotEventKind.Autofill -> phase = Phase.Surprise
            MascotEventKind.Error -> phase = Phase.Error
            MascotEventKind.Tap -> {
                val now = System.currentTimeMillis()
                taps.addLast(now)
                while (taps.isNotEmpty() && now - taps.first() > 3_000) taps.removeFirst()
                if (taps.size >= 6) {
                    taps.clear()
                    phase = Phase.Mad
                }
            }
            null -> Unit
        }
    }
    // Each look ends on its own. Keyed on the look, not the event: a new event (one more tap)
    // used to cancel the wait that ends it, and the mascot stayed angry for good.
    LaunchedEffect(phase) {
        when (phase) {
            Phase.Surprise -> { delay(950); phase = Phase.Thumb }
            Phase.Thumb -> { delay(1750); phase = null }
            Phase.Error -> { delay(2600); phase = null }
            Phase.Mad -> { delay(2200); phase = null }
            null -> Unit
        }
    }
    return phase
}

private fun pose(eyes: Eyes, look: Pair<Int, Int>, mouth: Mouth, arms: Arms) = Mascot.Pose(eyes, look, mouth, arms)

private fun lookFor(s: LoginMascotState, phase: Phase?): Look {
    val angry = Look(MascotLook(pose(Eyes.Angry, 0 to 0, Mouth.Frown, Arms.Fists), anger = true, motion = MascotMotion.Fume), Say.Angry)
    when (phase) {
        Phase.Error -> return Look(MascotLook(pose(Eyes.Open, 0 to 1, Mouth.Wobble, Arms.Down), sweat = true, motion = MascotMotion.Shake), Say.Oops)
        Phase.Surprise -> return Look(MascotLook(pose(Eyes.Wide, 0 to 0, Mouth.O, Arms.Down), mark = '!', motion = MascotMotion.Jump))
        Phase.Thumb -> return Look(MascotLook(pose(Eyes.Happy, 0 to 0, Mouth.Grin, Arms.Thumb)))
        // Tapped too often: mad, without a word.
        Phase.Mad -> return Look(MascotLook(pose(Eyes.Angry, 0 to 0, Mouth.Frown, Arms.Fists), anger = true, motion = MascotMotion.Fume), Say.TooMuch)
        null -> Unit
    }
    if (s.loading) return Look(MascotLook(pose(Eyes.Open, 1 to 0, Mouth.Smile, Arms.Down), think = true))
    val typing = if (s.keys > 0) MascotMotion.Hop else MascotMotion.Bob
    return when (s.focus) {
        MascotFocus.Password -> if (s.showPassword) {
            Look(MascotLook(pose(Eyes.Open, 1 to 1, Mouth.O, Arms.Peek), motion = typing))
        } else {
            Look(MascotLook(pose(Eyes.Closed, 0 to 0, Mouth.Flat, Arms.Cover), motion = typing))
        }
        MascotFocus.User -> {
            val name = s.username.trim()
            when {
                name.isNotEmpty() && WordFilter.isOffensive(name) -> angry
                name.isNotEmpty() -> Look(MascotLook(pose(Eyes.Happy, 0 to 0, Mouth.Grin, Arms.Wave), wave = true, motion = typing), Say.HelloName(name))
                else -> Look(MascotLook(pose(Eyes.Open, 1 to 0, Mouth.Smile, Arms.Wave), wave = true), Say.Hello)
            }
        }
        null -> {
            val name = s.username.trim()
            when {
                name.isNotEmpty() && WordFilter.isOffensive(name) -> angry
                s.welcomeBack -> Look(MascotLook(pose(Eyes.Happy, 0 to 0, Mouth.Smile, Arms.Wave), wave = true), Say.Back)
                else -> Look(MascotLook(pose(Eyes.Open, 1 to 0, Mouth.Smile, Arms.Down), blink = true))
            }
        }
    }
}

private fun text(t: LoginStrings, say: Say): String = when (say) {
    Say.Hello -> t.mascotHello
    // A name the mascot shouldn't say gets a plain hello instead.
    is Say.HelloName -> if (WordFilter.isOffensive(say.name)) t.mascotHello else t.mascotHelloName(say.name)
    Say.Oops -> t.mascotOops
    Say.Angry -> t.mascotAngry
    Say.Back -> t.mascotBack
    Say.TooMuch -> t.mascotTooMuch
}

private fun sayKey(say: Say): String = when (say) {
    Say.Hello, is Say.HelloName -> "hello"
    Say.Oops -> "oops"
    Say.Angry -> "angry"
    Say.Back -> "back"
    Say.TooMuch -> "toomuch"
}

/** The drawing's width: the mascot and room beside it for a bubble on up to three lines. */
private const val BADGE_W = 112

/** Room above the head, so the form below doesn't move while it talks. */
private const val TOP = -12

/**
 * The small mascot above the form, with its speech bubble. With [onTap] it can be tapped: it
 * jumps and says something back (the caller counts the taps into a [MascotEventKind.Tap] event).
 */
@Composable
fun LoginMascotBadge(
    state: LoginMascotState,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    maxWidth: androidx.compose.ui.unit.Dp = 252.dp,
    /** In the middle of the screen (the lock screen): the mascot centred, its words above it. */
    centered: Boolean = false,
) {
    val t = strings().login
    val phase = rememberPhase(state.event)
    val look = lookFor(state, phase)
    if (centered) {
        CenteredMascot(look, state, onTap, maxWidth, modifier)
        return
    }
    val motion = look.figure.motion
    val motionKey = when (motion) {
        MascotMotion.Hop -> "h${state.keys}"
        MascotMotion.Bob -> "bob"
        else -> "$motion${state.event?.n ?: 0}"
    }
    val speech = look.say?.let { bubble(text(t, it), Mascot.W - 2, 17, BADGE_W - Mascot.W - 4) }
    val top = minOf(TOP, speech?.let { it.top() - 1 } ?: 0)
    val layers = buildList {
        addAll(mascotLayers(look.figure, motionKey, marksBeside = true))
        if (speech != null) add(PixelLayer(speech, listOf(Px.pop), startKey = "say:${sayKey(look.say)}"))
    }
    val tap = if (onTap != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = t.mascotTapLabel,
            onClick = onTap,
        )
    } else {
        Modifier
    }
    PixelArt(
        scene = PixelScene(BADGE_W, Mascot.H - top, layers, y0 = top),
        modifier = modifier.widthIn(max = maxWidth).then(tap),
        clip = false,
    )
}

/** The centred drawing's width: the mascot in the middle, room either side for a bubble over its head. */
private const val CENTERED_W = 96

/** The widest a line of the bubble over the head gets before it wraps. */
private const val CENTERED_LINE = 80

/** The bubble's tail just over the head; above the signs when there are any (anger, "!", thinking). */
private const val TAIL_Y = -1
private const val TAIL_Y_SIGNS = -11

/** The top of the drawing: room for a bubble on two lines over the signs, so nothing below moves. */
private const val CENTERED_TOP = TAIL_Y_SIGNS - 2 - (2 * 6 + 5) - 1

/**
 * Width to height of the centred mascot's drawing (`centered = true`). The lock screen keeps its
 * room at this size while the unlock mark stands in for it, so nothing jumps between the two.
 */
const val CENTERED_MASCOT_ASPECT: Float = CENTERED_W.toFloat() / (Mascot.H - CENTERED_TOP)

/**
 * The mascot on its own in the middle, as on the lock screen: what it says in the web's pixel
 * speech bubble over its head (the tail pointing down at it; always the same room, so nothing
 * below moves), the drawing centred under it.
 */
@Composable
private fun CenteredMascot(look: Look, state: LoginMascotState, onTap: (() -> Unit)?, maxWidth: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val t = strings().login
    val motion = look.figure.motion
    val motionKey = when (motion) {
        MascotMotion.Hop -> "h${state.keys}"
        MascotMotion.Bob -> "bob"
        else -> "$motion${state.event?.n ?: 0}"
    }
    val tap = if (onTap != null) {
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = t.mascotTapLabel, onClick = onTap)
    } else {
        Modifier
    }
    val signs = look.figure.anger || look.figure.mark != null || look.figure.think
    val speech = look.say?.let { bubbleAbove(text(t, it), Mascot.W / 2, if (signs) TAIL_Y_SIGNS else TAIL_Y, CENTERED_LINE) }
    val layers = buildList {
        addAll(mascotLayers(look.figure, motionKey, marksBeside = false))
        if (speech != null) add(PixelLayer(speech, listOf(Px.pop), startKey = "say:${sayKey(look.say)}"))
    }
    PixelArt(
        scene = PixelScene(CENTERED_W, Mascot.H - CENTERED_TOP, layers, x0 = Mascot.W / 2 - CENTERED_W / 2, y0 = CENTERED_TOP),
        modifier = modifier.widthIn(max = maxWidth).then(tap),
        clip = false,
    )
}
