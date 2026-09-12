package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.network.Config
import dev.plattnericus.pokyh.ui.theme.PokyhFontFamily
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.senderColor
import java.net.URI

/**
 * The initial-letter avatar: a flat disc in the name's own [senderColor] with its first letter.
 *
 * Flat, not a gradient. The color is already doing the identifying work, and a gradient on a
 * 34dp disc reads as noise next to the rest of the app's flat fills — it was also the only
 * gradient in the UI outside the Mensa image placeholder.
 */
@Composable
fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = senderColor(name),
) {
    Box(
        modifier = modifier.size(size).background(color, PokyhShapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            fontFamily = PokyhFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.40f).sp,
        )
    }
}

/**
 * [InitialAvatar] as the placeholder, crossfading to the network image once loaded (Coil handles
 * the memory/disk cache and crossfade). The initial stays behind the image, so a failed or slow
 * load degrades to a legible avatar rather than to an empty circle.
 *
 * For a **WebUntis** profile picture use [UntisAvatar] instead — that image needs URL resolution
 * and session headers, which a plain URL load can't supply.
 */
@Composable
fun PokyhAvatar(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = senderColor(name),
) {
    Box(modifier = modifier.size(size).clip(PokyhShapes.pill)) {
        InitialAvatar(name = name, size = size, color = color)
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(PokyhShapes.pill),
            )
        }
    }
}

/**
 * The credentials a WebUntis-hosted image request has to carry. Same pair every WebUntis call in
 * [dev.plattnericus.pokyh.data.untis.UntisClient] sends.
 */
data class UntisImageAuth(val sessionId: String, val bearerToken: String) {
    companion object {
        fun of(session: UserSession?): UntisImageAuth? = session?.let {
            if (it.sessionId.isBlank()) null else UntisImageAuth(it.sessionId, it.bearerToken)
        }
    }
}

/**
 * A WebUntis profile picture, falling back to [InitialAvatar].
 *
 * Two things make this different from a plain [PokyhAvatar], and both are why the picture never
 * appeared before:
 *
 *  1. **The URL out of app-data is often relative** (`/WebUntis/…`), and when it is absolute it
 *     usually points at a separate CDN host (`images.webuntis.com`). [resolveUntisImageUrl]
 *     resolves it against the configured WebUntis origin, matching what the web frontend's
 *     `/api/webuntis/profile-image` route does with `new URL(path, WEBUNTIS_BASE)`.
 *  2. **The image sits behind the WebUntis session** — it needs the `JSESSIONID`/`schoolname`
 *     cookie and the bearer token, exactly like every other WebUntis request. The web frontend
 *     can't attach those from the browser (the cookie is httpOnly), so it proxies the bytes
 *     server-side; on-device we can just put the headers on the image request.
 *
 * If the user has no picture, or the load fails, the [InitialAvatar] underneath is what shows —
 * so a missing picture is a silent, correct fallback rather than an empty circle.
 */
@Composable
fun UntisAvatar(
    rawImageUrl: String?,
    name: String,
    auth: UntisImageAuth?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = senderColor(name),
) {
    val context = LocalContext.current
    val resolved = remember(rawImageUrl) { resolveUntisImageUrl(rawImageUrl) }

    Box(modifier = modifier.size(size).clip(PokyhShapes.pill)) {
        InitialAvatar(name = name, size = size, color = color)
        if (resolved != null && auth != null) {
            val request = remember(resolved, auth) {
                ImageRequest.Builder(context)
                    .data(resolved)
                    .httpHeaders(
                        NetworkHeaders.Builder()
                            .set("Cookie", "JSESSIONID=${auth.sessionId}; schoolname=\"${Config.schoolCookie}\"")
                            .apply {
                                if (auth.bearerToken.isNotEmpty()) {
                                    set("Authorization", "Bearer ${auth.bearerToken}")
                                }
                            }
                            .build(),
                    )
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(PokyhShapes.pill),
            )
        }
    }
}

/**
 * Absolute URL for a WebUntis `imageUrl`, or null when there's nothing usable.
 *
 * Mirrors the web frontend's resolution: an absolute `http(s)` URL is used as-is, anything else
 * is treated as root-absolute against the WebUntis origin. Non-https absolute URLs are dropped
 * rather than upgraded — the same "only fetch from the WebUntis domain family" caution the web
 * route applies, since this value comes from a server response.
 */
internal fun resolveUntisImageUrl(raw: String?): String? {
    val url = raw?.trim().orEmpty()
    if (url.isEmpty()) return null

    if (url.startsWith("https://")) return url
    if (url.startsWith("http://")) return null

    val base = runCatching { URI(Config.untisBase) }.getOrNull() ?: return null
    val scheme = base.scheme ?: return null
    val authority = base.authority ?: return null
    val path = if (url.startsWith("/")) url else "/$url"
    return "$scheme://$authority$path"
}
