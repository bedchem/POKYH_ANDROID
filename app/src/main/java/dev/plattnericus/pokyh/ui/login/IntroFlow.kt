package dev.plattnericus.pokyh.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.WordFilter
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.School
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.pixel.rememberReducedMotion
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.centeredForm
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The first run, one question per screen, the mascot keeping you company.
 *
 * The screen never rearranges itself: the bar stays at the top, the mascot in its place under it,
 * the key at the bottom. Only the question between them changes, and it does so in one calm
 * motion — the old one fades a little upwards, the new one fades in rising from just below, its
 * headline word by word. While a question is answered the mascot does what the web's sign-in
 * mascot does (Hallway.tsx): the magnifier for the school ("!" with results), a wave and
 * "Hallo, …!" for the name, both hands over its eyes for the password, a hop on every key.
 *
 * The password is checked right there: "Anmelden" signs in, a wrong name or password is said
 * under the field with the way back to the name, and only a sign-in that worked gets the
 * "Sieht gut aus!" with confetti ([IntroSuccess], over the app as it opens).
 *
 * Welcome → school → (Microsoft or password, where the school offers Microsoft) → username →
 * password. The whole form at once ([LoginScreen]'s classic one) stays one tap away on the welcome.
 */
private enum class IntroStep { Welcome, School, Method, User, Password }

/** The web's `expo.out`: fast off the mark, settling slowly. */
private val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** No bounce, just a slow settle: for things that move rather than pop. */
private fun <T> settle() = spring<T>(dampingRatio = 1f, stiffness = Spring.StiffnessLow)

@Composable
private fun accentInk(): Color = if (PokyhTheme.colors.isDark) Color(0xFFA9A1FF) else Color(0xFF534AB7)

/** Set when the intro signs in, so the app greets that sign-in with [IntroSuccess]. */
object IntroCelebration {
    val pending = MutableStateFlow(false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntroFlow(viewModel: LoginViewModel, onClassic: () -> Unit) {
    val t = strings().login
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf(IntroStep.Welcome) }
    var forward by remember { mutableStateOf(true) }
    var checkJob by remember { mutableStateOf<Job?>(null) }
    var school by remember { mutableStateOf<School?>(null) }
    var options by remember { mutableStateOf<BackendClient.LoginOptions?>(null) }
    var checking by remember { mutableStateOf(false) }
    // The mascot's answer to the question just answered: centre stage, big, then the next one.
    var react by remember { mutableStateOf(false) }
    var schoolQuery by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var save by rememberSaveable { mutableStateOf(true) }
    var officeOpen by remember { mutableStateOf(false) }
    var keys by remember { mutableIntStateOf(0) }
    var event by remember { mutableStateOf<MascotEvent?>(null) }
    var taps by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) { viewModel.clearError() }
    // Brought back without its school (the app was closed in between): ask for it again.
    LaunchedEffect(Unit) { if (school == null && step.ordinal > IntroStep.School.ordinal) step = IntroStep.School }

    fun go(to: IntroStep, ahead: Boolean = true) {
        forward = ahead
        react = false
        step = to
    }

    fun back() {
        if (ui.busy || react) return
        focusManager.clearFocus()
        checkJob?.cancel()
        checking = false
        viewModel.clearError()
        when (step) {
            IntroStep.Welcome -> Unit
            IntroStep.School -> go(IntroStep.Welcome, false)
            IntroStep.Method -> go(IntroStep.School, false)
            IntroStep.User -> go(if (options?.office365 == true) IntroStep.Method else IntroStep.School, false)
            IntroStep.Password -> go(IntroStep.User, false)
        }
    }
    BackHandler(enabled = step != IntroStep.Welcome && !ui.busy && !react) { back() }

    fun pick(picked: School) {
        school = picked
        viewModel.pickSchool(picked)
        focusManager.clearFocus()
        checking = true
        forward = true
        react = true
        val done = CompletableDeferred<Unit>()
        viewModel.checkSchool(picked) { o -> options = o; done.complete(Unit) }
        checkJob?.cancel()
        checkJob = scope.launch {
            // "Ah, LBS Brixen! Die kenn ich." gets its moment, however fast the answer comes.
            delay(REACT_MS)
            done.await()
            checking = false
            schoolQuery = ""
            go(if (options?.office365 == true) IntroStep.Method else IntroStep.User)
        }
    }

    fun submitUser() {
        if (username.isBlank()) return
        focusManager.clearFocus()
        forward = true
        react = true
        checkJob?.cancel()
        checkJob = scope.launch {
            delay(REACT_MS)
            // A name it must not say: angry, then back to the name to change it, never onwards.
            if (WordFilter.isOffensive(username.trim())) go(IntroStep.User, false) else go(IntroStep.Password)
        }
    }

    /** Signs in for real: a wrong name or password is answered on this screen. */
    fun signIn() {
        val s = school ?: return
        if (username.isBlank() || password.isBlank() || ui.busy) return
        focusManager.clearFocus()
        IntroCelebration.pending.value = true
        viewModel.submit(username.trim(), password, save, s)
    }

    LaunchedEffect(ui.error) {
        if (ui.error == null) return@LaunchedEffect
        IntroCelebration.pending.value = false
        event = MascotEvent(MascotEventKind.Error, System.currentTimeMillis())
    }

    // The steps the bar counts: not the welcome; the choice only where it exists.
    val counted = buildList {
        add(IntroStep.School)
        if (options?.office365 == true) add(IntroStep.Method)
        if (options?.password != false) {
            add(IntroStep.User); add(IntroStep.Password)
        }
    }
    val at = counted.indexOf(step).coerceAtLeast(0)

    val angryName = WordFilter.isOffensive(username.trim())
    val mascot = if (react) {
        LoginMascotState(
            event = event,
            scene = when {
                step == IntroStep.School -> IntroScene.ReactSchool
                angryName -> IntroScene.Angry
                else -> IntroScene.ReactUser
            },
        )
    } else when (step) {
        IntroStep.Welcome -> LoginMascotState(event = event, scene = IntroScene.Welcome)
        IntroStep.School -> {
            val answer = (search as? LoginViewModel.Search.Result)?.answer
            LoginMascotState(
                event = event,
                keys = keys,
                scene = when {
                    checking -> IntroScene.Picked
                    schoolQuery.isBlank() -> IntroScene.Search
                    search is LoginViewModel.Search.Searching -> IntroScene.SearchTyping
                    answer is BackendClient.SchoolSearch.Done && answer.schools.isNotEmpty() -> IntroScene.SearchFound
                    answer is BackendClient.SchoolSearch.Done || answer == BackendClient.SchoolSearch.Failed -> IntroScene.SearchNone
                    else -> IntroScene.SearchTyping
                },
            )
        }
        IntroStep.Method -> LoginMascotState(event = event)
        IntroStep.User -> LoginMascotState(focus = MascotFocus.User, username = username, keys = keys, event = event)
        IntroStep.Password -> LoginMascotState(focus = MascotFocus.Password, showPassword = showPassword, keys = keys, loading = ui.busy, event = event)
    }

    val key: IntroKey? = if (react) null else when (step) {
        IntroStep.Welcome -> IntroKey(t.introStart, ready = true) { IntroPrefs.markSeen(context); go(IntroStep.School) }
        IntroStep.User -> IntroKey(t.introNext, ready = username.isNotBlank()) { submitUser() }
        IntroStep.Password -> IntroKey(
            if (ui.busy) ui.statusText.ifEmpty { t.signingIn } else t.signIn,
            ready = password.isNotBlank(),
            busy = ui.busy,
        ) { signIn() }
        else -> null
    }

    IntroScaffold(
        scene = step to react,
        forward = forward,
        showBar = step != IntroStep.Welcome,
        segCount = counted.size,
        segCurrent = at,
        segLabel = t.introStep(at + 1, counted.size),
        backEnabled = step != IntroStep.Welcome && !ui.busy && !react,
        onBack = { back() },
        mascot = mascot,
        mascotHeight = when {
            react -> 270.dp
            step == IntroStep.Welcome -> 290.dp
            else -> 150.dp
        },
        onMascotTap = { taps++; event = MascotEvent(MascotEventKind.Tap, taps) },
        key = key,
    ) { (s, isReact) ->
        if (isReact) {
            val name = school?.name?.ifEmpty { school?.loginName.orEmpty() }.orEmpty()
            when {
                s == IntroStep.School -> ReactWords(t.reactSchool(name), t.reactSchoolSub)
                angryName -> ReactWords(t.mascotAngry, "")
                else -> ReactWords(t.reactUser(username.trim()), t.reactUserSub)
            }
        } else when (s) {
            IntroStep.Welcome -> Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))
                RiseHeadline(t.introChooseTitle, big = true, delayMs = 120)
                Spacer(Modifier.height(14.dp))
                RiseLine(t.introWelcomeSub, delayMs = 380)
                Spacer(Modifier.height(6.dp))
                Text(
                    t.introSkip,
                    style = PokyhType.footnote.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = accentInk(),
                    modifier = Modifier
                        .rise(520)
                        .padding(top = 6.dp)
                        .clip(PokyhShapes.pill)
                        .clickable { IntroPrefs.markSeen(context); onClassic() }
                        .padding(vertical = 8.dp),
                )
                FinePrint(start = true)
            }
            IntroStep.School -> Question(t.introSchoolTitle, t.introSchoolSub) {
                SchoolStep(
                    query = schoolQuery,
                    onQuery = { keys++; schoolQuery = it; viewModel.searchSchools(it) },
                    search = search,
                    last = viewModel.rememberedSchool,
                    onPick = { pick(it) },
                )
            }
            IntroStep.Method -> Question(t.introMethodTitle, t.introMethodSub) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MethodCard(t.introMethodOffice, t.introMethodOfficeSub, icon = { MicrosoftMark(size = 22) }, onClick = { officeOpen = true })
                    if (options?.password != false) {
                        MethodCard(
                            t.introMethodPassword,
                            t.introMethodPasswordSub,
                            icon = { Icon(PokyhIcons.locked, contentDescription = null, tint = accentInk(), modifier = Modifier.size(22.dp)) },
                            onClick = { go(IntroStep.User) },
                        )
                    }
                }
            }
            IntroStep.User -> Question(t.introUserTitle, t.introUserSub) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(450); runCatching { focus.requestFocus() } }
                IntroInput(
                    value = username,
                    onValue = { keys++; username = it },
                    placeholder = t.username,
                    focusRequester = focus,
                    imeAction = ImeAction.Next,
                    onIme = { submitUser() },
                )
            }
            IntroStep.Password -> Question(t.introPasswordTitle, t.introPasswordSub) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(450); runCatching { focus.requestFocus() } }
                IntroInput(
                    value = password,
                    onValue = {
                        keys++
                        password = it
                        if (ui.error != null) viewModel.clearError()
                    },
                    placeholder = t.password,
                    focusRequester = focus,
                    password = true,
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword },
                    error = ui.error,
                    enabled = !ui.busy,
                    imeAction = ImeAction.Go,
                    onIme = { signIn() },
                )
                // Wrong name or password: the way back to the name, right under the message.
                AnimatedVisibility(
                    visible = ui.error != null && !ui.busy,
                    enter = fadeIn(tween(260, delayMillis = 80)) + expandVertically(tween(320, easing = ExpoOut)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(260, easing = ExpoOut)),
                ) {
                    Text(
                        "← " + t.introChangeUser,
                        style = PokyhType.callout.copy(fontWeight = FontWeight.Bold),
                        color = accentInk(),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(PokyhShapes.pill)
                            .clickable { back() }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
                if (viewModel.biometricAvailable) {
                    Row(
                        Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(PokyhTheme.colors.card)
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(PokyhIcons.biometrics, contentDescription = null, tint = accentInk(), modifier = Modifier.size(20.dp))
                        Text(t.saveBiometric, style = PokyhType.callout, color = PokyhTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                        Switch(
                            checked = save,
                            onCheckedChange = { save = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Brand.accent, checkedThumbColor = Brand.onAccent),
                        )
                    }
                }
                FinePrint(start = true)
            }
        }
    }

    val officeSchool = school
    if (officeOpen && officeSchool != null) {
        OfficeSignInSheet(
            school = officeSchool,
            initialUser = username,
            busy = ui.busy,
            error = ui.error,
            onSubmit = { u, k ->
                username = u
                IntroCelebration.pending.value = true
                viewModel.submit(u, dev.plattnericus.pokyh.data.untis.AppKey.PREFIX + k, save, officeSchool)
            },
            onDismiss = { officeOpen = false },
        )
    }
}

// ── The frame every screen shares ────────────────────────────────────────────

/** The key at the bottom: its words, whether it is ready (dimmed if not), busy, what it does. */
private class IntroKey(val label: String, val ready: Boolean, val busy: Boolean = false, val onClick: () -> Unit)

/**
 * The bar, the mascot and the key, each in a place that never moves, and the screen's own part
 * between them. A new [scene] fades in rising from just below (from above going back) while the
 * old one fades a little the other way, with nothing resizing in between; the mascot's place
 * grows or shrinks with a slow settle only between the welcome and the questions.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> IntroScaffold(
    scene: T,
    forward: Boolean,
    showBar: Boolean,
    segCount: Int,
    segCurrent: Int,
    segLabel: String,
    backEnabled: Boolean,
    onBack: () -> Unit,
    mascot: LoginMascotState,
    mascotHeight: Dp,
    onMascotTap: () -> Unit,
    key: IntroKey?,
    content: @Composable (T) -> Unit,
) {
    val t = strings().login
    Box(Modifier.fillMaxSize().background(PokyhTheme.colors.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .centeredForm(maxWidth = 480.dp)
                .padding(horizontal = 24.dp),
        ) {
            // ── The bar: always there, faded out on the welcome ──
            val barAlpha by animateFloatAsState(if (showBar) 1f else 0f, tween(400), label = "barAlpha")
            val backAlpha by animateFloatAsState(if (backEnabled) 1f else 0f, tween(300), label = "backAlpha")
            Row(Modifier.fillMaxWidth().height(56.dp).alpha(barAlpha), verticalAlignment = Alignment.CenterVertically) {
                PokyhIconButton(
                    icon = PokyhIcons.back,
                    contentDescription = t.introBack,
                    onClick = onBack,
                    enabled = backEnabled,
                    tint = PokyhTheme.colors.textPrimary,
                    size = 40.dp,
                    iconSize = 20.dp,
                    modifier = Modifier.alpha(backAlpha),
                )
                Spacer(Modifier.width(12.dp))
                Segments(count = segCount, current = segCurrent, label = segLabel, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(52.dp))
            }

            // ── The mascot, in its own place — stepping away while the keyboard is up ──
            // While typing the question has the screen to itself: the mascot fades first, then
            // its place closes with a slow settle, so the question glides up with the keyboard
            // instead of jumping (and the other way round when the keyboard goes).
            //
            // Smooth, not stepped: the pixel art is drawn once at the size it is heading for
            // (crisp, whole pixels, as everywhere) and the change of size is a scale of that
            // drawing, read in the draw phase; the room it takes is read in the layout phase.
            // Animating its real size instead re-snapped the pixels every frame (5 → 4 → 3
            // screen pixels per pixel) and rebuilt it each time, which is what stuttered.
            val typing = WindowInsets.isImeVisible
            val size = animateDpAsState(mascotHeight, settle(), label = "mascotSize")
            val slot = animateDpAsState(if (typing) 0.dp else mascotHeight + 16.dp, settle(), label = "mascotSlot")
            val mascotShown = animateFloatAsState(
                if (typing) 0f else 1f,
                if (typing) tween(140) else tween(360, delayMillis = 200),
                label = "mascotAlpha",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val h = slot.value.roundToPx().coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                        layout(constraints.maxWidth, h) { placeable.placeRelative((constraints.maxWidth - placeable.width) / 2, 0) }
                    },
                // The box is the full width; the mascot sits in its middle.
                contentAlignment = Alignment.TopCenter,
            ) {
                MascotPop {
                    LoginMascotBadge(
                        state = mascot,
                        centered = true,
                        maxWidth = 420.dp,
                        modifier = Modifier
                            .requiredHeight(mascotHeight)
                            .requiredWidth(mascotHeight * CENTERED_MASCOT_ASPECT)
                            .graphicsLayer {
                                val a = mascotShown.value
                                val s = (size.value / mascotHeight) * (0.9f + 0.1f * a)
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                                scaleX = s
                                scaleY = s
                                alpha = a
                            },
                        onTap = onMascotTap,
                    )
                }
            }

            // ── The screen's own part ──
            AnimatedContent(
                targetState = scene,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (fadeIn(tween(460, delayMillis = 140)) + slideInVertically(tween(700, delayMillis = 140, easing = ExpoOut)) { h -> dir * h / 14 }) togetherWith
                        (fadeOut(tween(180)) + slideOutVertically(tween(240)) { h -> -dir * h / 28 }) using
                        SizeTransform(clip = false) { _, _ -> snap() }
                },
                label = "introScene",
            ) { s -> content(s) }

            // ── The key: always the same place, faded out where there is nothing to press ──
            val keyAlpha by animateFloatAsState(
                when {
                    key == null -> 0f
                    key.ready -> 1f
                    else -> 0.45f
                },
                tween(260),
                label = "keyAlpha",
            )
            // The last key stays drawn while it fades out (a plain holder, not state: writing
            // state here on every pass would recompose forever).
            val lastKey = remember { arrayOfNulls<IntroKey>(1) }
            if (key != null) lastKey[0] = key
            val shown = lastKey[0]
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).alpha(keyAlpha)) {
                if (shown != null) {
                    AnimatedContent(
                        targetState = shown.label,
                        transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) { _, _ -> snap() } },
                        label = "keyLabel",
                    ) { label ->
                        LoginKey(
                            text = label,
                            onClick = { if (key != null && key.ready) key.onClick() },
                            busy = shown.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** How long the mascot's answer stays before the next question. */
private const val REACT_MS = 1600L

/** The mascot's answer under it, centred: "Ah, LBS Brixen!" and "Die kenn ich.", rising in. */
@Composable
private fun ReactWords(title: String, sub: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        RiseHeadline(title, centered = true, size = if (title.length > 26) 30.sp else 38.sp, delayMs = 160)
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RiseLine(sub, centered = true, delayMs = 420)
        }
    }
}

/** A question: the headline, its line, then whatever answers it — all rising in, in order. */
@Composable
private fun Question(title: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RiseHeadline(title, delayMs = 160)
        Spacer(Modifier.height(10.dp))
        RiseLine(sub, delayMs = 340)
        Spacer(Modifier.height(26.dp))
        Column(Modifier.fillMaxWidth().rise(440), content = content)
        Spacer(Modifier.height(12.dp))
    }
}

// ── The pieces ───────────────────────────────────────────────────────────────

/** Fades and grows the mascot in once, when the screen first appears. */
@Composable
private fun MascotPop(content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    val p = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        delay(120)
        p.animateTo(1f, tween(800, easing = ExpoOut))
    }
    Box(
        Modifier.graphicsLayer {
            val v = p.value
            alpha = v
            scaleX = 0.85f + 0.15f * v
            scaleY = 0.85f + 0.15f * v
            translationY = (1f - v) * 24.dp.toPx()
        },
    ) { content() }
}

/**
 * Big type, word by word: each word rises out of its own line (a mask, as the web's SplitText),
 * a little after the one before. Words between `*` take the accent ink.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RiseHeadline(text: String, big: Boolean = false, centered: Boolean = false, size: TextUnit? = null, delayMs: Int = 0) {
    val fontSize = size ?: if (big) 46.sp else 36.sp
    val style = PokyhType.display.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.06f,
        letterSpacing = (-0.045).em,
        fontWeight = FontWeight.ExtraBold,
    )
    val words = remember(text) { markedWords(text) }
    val accent = accentInk()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
    ) {
        words.forEachIndexed { i, (word, marked) ->
            WordRise(
                word = if (i == words.lastIndex) word else "$word ",
                style = style,
                color = if (marked) accent else PokyhTheme.colors.textPrimary,
                delayMs = delayMs + i * 50,
            )
        }
    }
}

/** The words of [text], and whether each sits between `*` marks. */
private fun markedWords(text: String): List<Pair<String, Boolean>> {
    var inside = false
    return text.split(' ').filter { it.isNotEmpty() }.map { raw ->
        var w = raw
        if (w.startsWith("*")) { inside = true; w = w.drop(1) }
        val closes = w.contains("*")
        val marked = inside
        if (closes) { inside = false; w = w.replace("*", "") }
        w to marked
    }
}

@Composable
private fun WordRise(word: String, style: TextStyle, color: Color, delayMs: Int) {
    val reduced = rememberReducedMotion()
    val p = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        delay(delayMs.toLong())
        p.animateTo(1f, tween(900, easing = ExpoOut))
    }
    Box(Modifier.clipToBounds()) {
        Text(
            word,
            style = style,
            color = color,
            modifier = Modifier.graphicsLayer { translationY = (1f - p.value) * size.height },
        )
    }
}

/** The quieter line under a headline, rising in. */
@Composable
private fun RiseLine(text: String, centered: Boolean = false, delayMs: Int = 0) {
    Text(
        text,
        style = PokyhType.body.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        color = PokyhTheme.colors.textSecondary,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().rise(delayMs),
    )
}

/** Fades in and rises 14dp after [delayMs], with the web's expo ease; at once with reduced motion. */
@Composable
private fun Modifier.rise(delayMs: Int): Modifier {
    val reduced = rememberReducedMotion()
    val p = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        delay(delayMs.toLong())
        p.animateTo(1f, tween(800, easing = ExpoOut))
    }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 14.dp.toPx()
    }
}

/** The answer field: big type on the surface tone, the caret in the accent, no outline. */
@Composable
private fun IntroInput(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onIme: () -> Unit,
    password: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: () -> Unit = {},
    error: String? = null,
    enabled: Boolean = true,
) {
    val t = strings().login
    val colors = PokyhTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(
        when {
            error != null -> Brand.danger.copy(alpha = 0.12f)
            focused -> colors.card
            else -> colors.card.copy(alpha = 0.75f)
        },
        tween(300),
        label = "introFieldFill",
    )
    val style = PokyhType.headline.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(fill)
                .padding(start = 22.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                enabled = enabled,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                textStyle = style.copy(color = colors.textPrimary),
                singleLine = true,
                cursorBrush = SolidColor(accentInk()),
                interactionSource = interaction,
                visualTransformation = if (password && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }, onGo = { onIme() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, style = style, color = colors.textTertiary, maxLines = 1)
                        inner()
                    }
                },
            )
            if (password) {
                PokyhIconButton(
                    icon = if (showPassword) PokyhIcons.hidePassword else PokyhIcons.showPassword,
                    contentDescription = if (showPassword) t.hidePassword else t.showPassword,
                    onClick = onTogglePassword,
                    tint = colors.textTertiary,
                    size = 44.dp,
                    iconSize = 20.dp,
                )
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(tween(260, delayMillis = 60)) + expandVertically(tween(320, easing = ExpoOut)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(260, easing = ExpoOut)),
        ) {
            var shown by remember { mutableStateOf(error.orEmpty()) }
            if (error != null) shown = error
            Text(
                shown,
                style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold),
                color = Brand.danger,
                modifier = Modifier.padding(top = 12.dp, start = 6.dp),
            )
        }
    }
}

/** How far along, as story bars: done ones full, the current one half, the rest empty. */
@Composable
private fun Segments(count: Int, current: Int, label: String, modifier: Modifier = Modifier) {
    val accent = accentInk()
    val track = PokyhTheme.colors.textPrimary.copy(alpha = 0.1f)
    Row(
        modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val target = when {
                i < current -> 1f
                i == current -> 0.5f
                else -> 0f
            }
            val f by animateFloatAsState(target, tween(800, easing = ExpoOut), label = "segment$i")
            Box(Modifier.weight(1f).height(5.dp).clip(PokyhShapes.pill).background(track)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(PokyhShapes.pill).background(accent))
            }
        }
    }
}

/** A burst of confetti from the middle, falling away (skipped with reduced motion). */
@Composable
private fun Confetti(key: Any) {
    if (rememberReducedMotion()) return
    val dark = PokyhTheme.colors.isDark
    val palette = listOf(
        accentInk(), Brand.success, Brand.accent,
        PokyhDecorative.butter(dark).ink, PokyhDecorative.blush(dark).ink, PokyhDecorative.sage(dark).ink, PokyhDecorative.periwinkle(dark).ink,
    )
    val pieces = remember(key) {
        val rnd = kotlin.random.Random(42)
        List(56) {
            val angle = -PI / 2 + (rnd.nextDouble() - 0.5) * PI * 1.1
            val speed = 0.55 + rnd.nextDouble() * 0.75
            floatArrayOf(
                (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat(),
                rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 720f,
                6f + rnd.nextFloat() * 6f, rnd.nextInt(palette.size).toFloat(), rnd.nextFloat(),
            )
        }
    }
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(300)
        p.animateTo(1f, tween(2400, easing = LinearEasing))
    }
    if (p.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val time = p.value
        if (time <= 0f) return@Canvas
        val origin = Offset(size.width / 2f, size.height * 0.3f)
        val reach = size.width * 0.9f
        val gravity = size.height * 1.4f
        pieces.forEach { pc ->
            val x = origin.x + pc[0] * reach * time
            val y = origin.y + pc[1] * reach * time + gravity * time * time * 0.5f
            val s = pc[4] * density
            val alpha = 1f - ((time - 0.65f) / 0.35f).coerceIn(0f, 1f)
            rotate(pc[2] + pc[3] * time, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = palette[pc[5].toInt()].copy(alpha = alpha),
                    topLeft = Offset(x - s / 2, y - s * 0.3f),
                    size = Size(s, s * (if (pc[6] > 0.5f) 0.6f else 1f)),
                    cornerRadius = CornerRadius(s * 0.2f),
                )
            }
        }
    }
}

/** The chosen school, small: badge and name. */
@Composable
private fun SchoolLine(school: School) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SchoolBadge(school)
        Column(Modifier.weight(1f)) {
            Text(
                school.name.ifEmpty { school.loginName },
                style = PokyhType.callout.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = PokyhTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (school.address.isNotBlank()) {
                Text(school.address, style = PokyhType.footnote, color = PokyhTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One way to sign in, as a big card: its mark, what it is, an arrow. */
@Composable
private fun MethodCard(title: String, sub: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PokyhTheme.colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(PokyhTheme.colors.cardAlt), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = PokyhType.headline.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = PokyhTheme.colors.textPrimary)
            Text(sub, style = PokyhType.footnote, color = PokyhTheme.colors.textTertiary)
        }
        Text("→", style = PokyhType.headline.copy(fontSize = 20.sp), color = accentInk())
    }
}

/** Age and the legal links, small, where the data is entered (Art. 13 DSGVO) — as on the form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinePrint(start: Boolean = false) {
    val t = strings().login
    val uri = LocalUriHandler.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp, if (start) Alignment.Start else Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    ) {
        Text(t.ageShort, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
        listOf(
            t.privacyLink to "https://pokyh.com/legal/datenschutz",
            t.imprint to "https://pokyh.com/legal/impressum",
            t.terms to "https://pokyh.com/legal/nutzungsbedingungen",
        ).forEach { (label, url) ->
            Text(
                label,
                style = PokyhType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = PokyhTheme.colors.textTertiary,
                modifier = Modifier.clickable { uri.openUri(url) },
            )
        }
    }
}

// ── After signing in through the intro ──────────────────────────────────────

/**
 * "Sieht gut aus!" over the app as it opens, once the intro's sign-in has worked: the mascot
 * cheering, the words rising in, confetti. [dev.plattnericus.pokyh.ui.PokyhApp] shows it for a
 * couple of seconds in place of the plain success tick and fades it away.
 */
@Composable
fun IntroSuccess() {
    val t = strings().login
    Box(Modifier.fillMaxSize().background(PokyhTheme.colors.bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MascotPop {
                LoginMascotBadge(
                    state = LoginMascotState(scene = IntroScene.Saved),
                    centered = true,
                    maxWidth = 420.dp,
                    modifier = Modifier.height(250.dp).width(250.dp * CENTERED_MASCOT_ASPECT),
                )
            }
            Spacer(Modifier.height(20.dp))
            RiseHeadline(t.introSavedTitle, big = true, centered = true, delayMs = 200)
            Spacer(Modifier.height(12.dp))
            RiseLine(t.introSavedSub, centered = true, delayMs = 480)
        }
        Confetti(key = "intro-success")
    }
}

// ── After the update, for whoever is already signed in ──────────────────────

/** Whether this device has had its welcome: the intro started, or the update greeting confirmed. */
object IntroPrefs {
    private const val FILE = "pokyh_intro"
    private const val KEY = "welcomed_v2"

    fun seen(context: android.content.Context): Boolean =
        context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markSeen(context: android.content.Context) {
        context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

/**
 * The one-time greeting for an account that was already signed in before this version: the
 * mascot waving and "Danke, dass du POKYH nutzt.", one key, and back into the app. **The sign-in
 * is not touched** — nothing is signed out, asked for again or deleted. Its school is not asked
 * about: it is the one the account signs in at, so there is nothing to confirm or change.
 */
@Composable
fun UpdateWelcome(school: School, onDone: () -> Unit) {
    val t = strings().login
    val context = androidx.compose.ui.platform.LocalContext.current
    var event by remember { mutableStateOf<MascotEvent?>(null) }
    var taps by remember { mutableLongStateOf(0L) }

    IntroScaffold(
        scene = Unit,
        forward = true,
        showBar = false,
        segCount = 0,
        segCurrent = 0,
        segLabel = "",
        backEnabled = false,
        onBack = {},
        mascot = LoginMascotState(event = event, scene = IntroScene.Welcome),
        mascotHeight = 290.dp,
        onMascotTap = { taps++; event = MascotEvent(MascotEventKind.Tap, taps) },
        key = IntroKey(t.introStart, ready = true) {
            dev.plattnericus.pokyh.data.storage.SchoolChoice.save(context, school)
            IntroPrefs.markSeen(context)
            onDone()
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            RiseHeadline(t.introWelcomeTitle, big = true, delayMs = 120)
            Spacer(Modifier.height(14.dp))
            RiseLine(t.updateWelcomeSub, delayMs = 380)
        }
    }
}
