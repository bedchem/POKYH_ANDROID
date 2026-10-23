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
import dev.plattnericus.pokyh.ui.pixel.mascotLayers
import kotlinx.coroutines.delay

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

enum class MascotEventKind { Autofill, Error }

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
)

private enum class Phase { Surprise, Thumb, Error }

private sealed interface Say {
    data object Hello : Say
    data class HelloName(val name: String) : Say
    data object Oops : Say
    data object Angry : Say
}

private data class Look(val figure: MascotLook, val say: Say? = null)

/** The moments that play out on their own after an event. */
@Composable
private fun rememberPhase(event: MascotEvent?): Phase? {
    var phase by remember { mutableStateOf<Phase?>(null) }
    LaunchedEffect(event) {
        when (event?.kind) {
            MascotEventKind.Autofill -> {
                phase = Phase.Surprise
                delay(950)
                phase = Phase.Thumb
                delay(1750)
                phase = null
            }
            MascotEventKind.Error -> {
                phase = Phase.Error
                delay(2600)
                phase = null
            }
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
            if (name.isNotEmpty() && WordFilter.isOffensive(name)) angry
            else Look(MascotLook(pose(Eyes.Open, 1 to 0, Mouth.Smile, Arms.Down), blink = true))
        }
    }
}

private fun text(t: LoginStrings, say: Say): String = when (say) {
    Say.Hello -> t.mascotHello
    // A name the mascot shouldn't say gets a plain hello instead.
    is Say.HelloName -> if (WordFilter.isOffensive(say.name)) t.mascotHello else t.mascotHelloName(say.name)
    Say.Oops -> t.mascotOops
    Say.Angry -> t.mascotAngry
}

private fun sayKey(say: Say): String = when (say) {
    Say.Hello, is Say.HelloName -> "hello"
    Say.Oops -> "oops"
    Say.Angry -> "angry"
}

/** The drawing's width: the mascot and room beside it for a bubble on up to three lines. */
private const val BADGE_W = 112

/** Room above the head, so the form below doesn't move while it talks. */
private const val TOP = -12

/** The small mascot above the form, with its speech bubble. */
@Composable
fun LoginMascotBadge(state: LoginMascotState, modifier: Modifier = Modifier) {
    val t = strings().login
    val phase = rememberPhase(state.event)
    val look = lookFor(state, phase)
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
    PixelArt(
        scene = PixelScene(BADGE_W, Mascot.H - top, layers, y0 = top),
        modifier = modifier.widthIn(max = 252.dp),
        clip = false,
    )
}
