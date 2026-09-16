package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.status.PokyhService
import dev.plattnericus.pokyh.core.status.ServiceState
import dev.plattnericus.pokyh.core.status.unreachable
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.floatingSurface
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/**
 * The banner that drops in from the top when the app is running on cached data — and again when
 * it stops having to.
 *
 * **It names the server, and it says how old the data is.** "Keine Verbindung" leaves the reader
 * guessing between three different problems — their phone, the school's WebUntis, POKYH's own
 * backend — and only one of them is theirs to fix. Naming it also explains why half the app still
 * works: WebUntis down means no timetable and no grades while todos, reminders and the menu keep
 * loading, and the other way round.
 *
 * **It stays until it is dismissed.** Three earlier versions folded themselves away on a timer,
 * and every time the thing being explained was gone before it had been read. Nothing here times
 * out: the banner holds its place until the X is pressed, and the X dismisses *that situation*,
 * not the banner forever — if the connection drops again, it comes back.
 *
 * **Recovery is news too.** Being told the server is unreachable and then never being told it
 * came back leaves the reader distrusting everything on screen; the green notice closes the loop
 * and waits to be dismissed just like the amber one.
 *
 * It is not an error state: nothing is blocked, the cached data is on screen underneath, and the
 * banner's whole job is to explain why it might be out of date.
 */
@Composable
fun OfflineNotice(
    states: Map<PokyhService, ServiceState>,
    isOffline: Boolean,
    /**
     * Whether the phone has a network at all.
     *
     * Separate from [states], and it has to be: a server can only be reported unreachable after a
     * request to it has failed, so on a cold start in flight mode nothing was known and nothing
     * was shown — the exact case the banner exists for. This is known instantly, and it is also
     * the difference between “keine Internetverbindung” and “WebUntis ist nicht erreichbar”,
     * which are two different problems with two different owners.
     */
    deviceOnline: Boolean,
    /** False while the success check is on screen. */
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    val down = states.unreachable()
    val downKey = down.joinToString(",") { it.name }
    val names = down.joinToString(" & ") { it.label }

    // **"Kein Internet" and "Server antwortet nicht" are different problems and get different
    // words.** One is the phone's and the reader can go fix it; the other is the school's or
    // ours and waiting is the only move. Collapsing both into "keine Verbindung" — which is
    // what an app usually does — is what sends people to re-check a Wi-Fi that was never the
    // problem.
    val cause: String? = when {
        !deviceOnline -> "Keine Internetverbindung"
        down.isNotEmpty() -> names + " antwortet nicht"
        else -> null
    }

    // **Which server is down decides what is stale, and the two barely overlap.** WebUntis
    // carries the timetable and the grades; the POKYH backend carries the menu, todos and
    // reminders. Naming the server without naming what it holds leaves the reader to work out
    // which half of the app to distrust — and either half still working looks like proof that
    // the warning was wrong.
    val affected: String = when {
        down.size > 1 -> "Alle Daten stammen aus dem Zwischenspeicher"
        down.singleOrNull() == PokyhService.UNTIS -> "Stundenplan und Noten evtl. nicht aktuell"
        down.singleOrNull() == PokyhService.BACKEND -> "Mensa, Todos und Erinnerungen evtl. nicht aktuell"
        else -> "Gespeicherte Daten werden angezeigt"
    }

    // The situation, as one comparable value. Everything downstream keys off this: a new key is a
    // new thing to say, and an old dismissal does not silence it.
    val problem: NoticeState? = when {
        // Being signed in on stored data leads, because that is the state the reader is *in*;
        // whether the phone or the server is at fault is the reason for it, and goes below.
        isOffline -> NoticeState(
            key = "session:" + (if (deviceOnline) "net" else "nonet") + ":" + downKey,
            headline = "Offline angemeldet",
            detail = if (cause != null) "$cause \u00b7 $affected" else affected,
            services = down,
        )
        !deviceOnline -> NoticeState(
            key = "device",
            headline = "Keine Internetverbindung",
            detail = "Dein Gerät ist offline · " + affected,
            services = down,
        )
        down.isNotEmpty() -> NoticeState(
            key = "down:" + downKey,
            headline = names + " nicht erreichbar",
            detail = "Dein Internet funktioniert · " + affected,
            services = down,
        )
        else -> null
    }

    var shown by remember { mutableStateOf<NoticeState?>(null) }
    var dismissedKey by remember { mutableStateOf<String?>(null) }
    // Only a problem the user actually saw earns a recovery notice. Without this, connectivity
    // settling during launch would announce “wieder online” for an outage never on screen.
    var unresolved by remember { mutableStateOf<NoticeState?>(null) }

    LaunchedEffect(problem?.key, ready) {
        when {
            problem != null -> {
                shown = problem
                if (ready) unresolved = problem
            }
            unresolved != null -> {
                val was = unresolved!!
                shown = NoticeState(
                    key = "back:" + was.key,
                    headline = if (was.services.isEmpty()) {
                        "Wieder online"
                    } else {
                        was.services.joinToString(" & ") { it.label } + " wieder erreichbar"
                    },
                    detail = "Verbindung steht — Daten sind wieder aktuell",
                    services = was.services,
                    recovered = true,
                )
                unresolved = null
            }
            else -> shown = null
        }
    }

    val current = shown
    val visible = ready && current != null && current.key != dismissedKey

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(280)) { -it } + fadeIn(tween(280)),
        exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val colors = PokyhTheme.colors
        // Held through the exit animation so the card does not blank out as it slides away.
        val card = remember(current?.key) { current } ?: return@AnimatedVisibility
        val tone = if (card.recovered) Brand.success else Brand.warning
        val lastOkAt = card.services
            .mapNotNull { states[it]?.lastOkAt?.takeIf { at -> at > 0L } }
            .maxOrNull() ?: 0L
        val detail = if (!card.recovered && lastOkAt > 0L) {
            card.detail + " · Stand " + asOfText(lastOkAt)
        } else {
            card.detail
        }

        Row(
            modifier = Modifier
                .padding(horizontal = PokyhSpacing.screenH)
                .fillMaxWidth()
                .floatingSurface(shape = PokyhShapes.lg)
                .padding(
                    start = PokyhSpacing.lg,
                    top = PokyhSpacing.lg,
                    bottom = PokyhSpacing.lg,
                    end = PokyhSpacing.xs,
                ),
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = if (card.recovered) PokyhIcons.ok else PokyhIcons.offline,
                color = tone,
                size = 46.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = card.headline,
                    style = PokyhType.headline,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style = PokyhType.footnote,
                    color = colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PokyhIconButton(
                icon = PokyhIcons.close,
                contentDescription = "Hinweis schließen",
                onClick = { dismissedKey = card.key },
                iconSize = 16.dp,
            )
        }
    }
}

/** One thing the banner has to say, and the key that decides whether it has already been said. */
private data class NoticeState(
    val key: String,
    val headline: String,
    val detail: String,
    val services: List<PokyhService>,
    val recovered: Boolean = false,
)
/**
 * "Stand …" for a cached screen, e.g. `Stand heute, 08:12` or `Stand 14.09., 16:40`.
 *
 * Today and yesterday are named rather than dated, because that is the question the label
 * answers — "is this current?" — and a date makes the reader do the arithmetic. Anything older
 * gets its date, where the arithmetic is the point.
 */
fun asOfText(epochMillis: Long): String {
    if (epochMillis <= 0L) return "unbekannt"
    val then = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val now = Calendar.getInstance()
    val time = String.format(
        Locale.GERMAN,
        "%02d:%02d",
        then.get(Calendar.HOUR_OF_DAY),
        then.get(Calendar.MINUTE),
    )
    val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "heute, $time"
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val wasYesterday = then.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    if (wasYesterday) return "gestern, $time"
    val date = String.format(
        Locale.GERMAN,
        "%02d.%02d.",
        then.get(Calendar.DAY_OF_MONTH),
        then.get(Calendar.MONTH) + 1,
    )
    return "$date, $time"
}

/**
 * The small "Stand …" caption a screen shows over restored data.
 *
 * Only over *restored* data: a screen that just loaded from the server has nothing to qualify,
 * and stamping every screen with a timestamp would turn a useful warning into furniture.
 */
@Composable
fun AsOfLabel(savedAt: Long, stale: Boolean, modifier: Modifier = Modifier) {
    if (!stale || savedAt <= 0L) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PokyhIcons.offline,
            contentDescription = null,
            tint = PokyhTheme.colors.textTertiary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = "Stand ${asOfText(savedAt)}",
            style = PokyhType.caption2,
            color = PokyhTheme.colors.textTertiary,
            maxLines = 1,
        )
    }
}
