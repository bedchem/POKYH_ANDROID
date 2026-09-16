package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for a [androidx.compose.foundation.lazy.LazyColumn], keyed by item.
 *
 * The list stays the source of truth: this never holds a copy of the items, it just reports
 * "move the item at `from` to `to`" through [onMove] and lets the caller's state drive the
 * recomposition. That is what keeps a reorder and a data update from fighting each other, and
 * it means the caller can persist the new order at whatever moment suits it.
 *
 * How the dragged item is positioned is the part worth understanding. It would seem natural to
 * translate it by the accumulated drag distance, but every time a swap happens the item's own
 * laid-out position jumps to its new slot, and the translation would then be measured from the
 * wrong origin — the row visibly leaps out from under the finger. Instead the offset is
 * recomputed from the layout every frame:
 *
 *     translationY = (offset it had when the drag started + how far the finger has moved)
 *                    - (offset it has right now)
 *
 * so the row is pinned to the finger and the swap is absorbed silently. The *other* rows settle
 * through [LazyItemScope.animateItem]; the held row must not, or its placement animation and
 * this translation both try to move it and it wobbles (see [activeKey]).
 *
 * **Letting go is animated too.** The card is rarely exactly over its slot when the finger lifts,
 * and dropping translation, scale and shadow to zero in one frame is a visible snap. On release
 * the remaining offset and the lift spring down together, and the card stays on top of its
 * neighbours until it has landed.
 *
 * Long-press to start, rather than a plain drag, because these lists also scroll.
 */
class ReorderState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    /** Returns false to refuse the swap (e.g. a header row) — the drag then keeps its index. */
    private val onMove: (from: Int, to: Int) -> Boolean,
) {
    /** The key of the item currently being dragged, or null. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    /** The item that was just let go of and is still springing into its slot, or null. */
    var settlingKey: Any? by mutableStateOf(null)
        private set

    /**
     * The item that is in the hand *or* still landing. Callers use it to switch that one item's
     * placement animation off — the reorder already positions it — and to keep it above the rest.
     */
    val activeKey: Any? get() = draggingKey ?: settlingKey

    private var draggingIndex: Int? = null
    private var startOffset = 0
    private var startSize = 0
    private var dragged by mutableFloatStateOf(0f)
    private var autoScrollJob: Job? = null
    private var settleJob: Job? = null

    /** 0 = resting, 1 = fully lifted. Drives scale, shadow and how far the others dim. */
    internal val lift = Animatable(0f)

    /** The translation the released card still has to travel back to its slot. */
    internal val settleOffset = Animatable(0f)

    val isDragging: Boolean get() = draggingKey != null

    internal fun onDragStart(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        settleJob?.cancel()
        settlingKey = null
        draggingKey = key
        draggingIndex = item.index
        startOffset = item.offset
        startSize = item.size
        dragged = 0f
        scope.launch { lift.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
    }

    internal fun onDrag(delta: Float) {
        if (draggingKey == null) return
        dragged += delta

        val current = draggingIndex ?: return
        val top = startOffset + dragged
        val bottom = top + startSize
        val centre = (top + bottom) / 2f

        // Swap with whichever neighbour's own centre band the dragged row's centre has entered.
        // Comparing centres (rather than edges) is what makes a tall row and a short row behave
        // the same: the swap happens when you have actually moved past the other item, not when
        // the two merely start to overlap.
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != current &&
                item.key != draggingKey &&
                centre >= item.offset &&
                centre <= item.offset + item.size
        }
        // Only adopt the new index if the caller actually made the move. Taking it
        // unconditionally is how hovering over a refused slot (Home’s greeting) left every
        // later swap one position off, and the cards stopped moving at all.
        if (target != null && onMove(current, target.index)) {
            draggingIndex = target.index
        }

        autoScroll(top, bottom)
    }

    /**
     * Scrolls the list when the dragged row is held against either edge, so an item can be moved
     * further than one screenful. Without it a long list can only be reordered within the part
     * of it that happens to be visible.
     */
    private fun autoScroll(top: Float, bottom: Float) {
        val info = listState.layoutInfo
        val viewportTop = info.viewportStartOffset.toFloat()
        val viewportBottom = info.viewportEndOffset.toFloat()
        val margin = AutoScrollMarginPx

        val speed = when {
            bottom > viewportBottom - margin -> (bottom - (viewportBottom - margin)).coerceAtMost(margin)
            top < viewportTop + margin -> -((viewportTop + margin) - top).coerceAtMost(margin)
            else -> 0f
        }
        if (speed == 0f) {
            autoScrollJob?.cancel()
            autoScrollJob = null
            return
        }
        if (autoScrollJob?.isActive == true) return
        autoScrollJob = scope.launch {
            while (isDragging) {
                listState.scrollBy(speed * AutoScrollFactor)
                delay(16)
            }
        }
    }

    internal fun onDragEnd() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        val key = draggingKey ?: return
        // Read where the card is *now*, before the drag state is cleared — this is the distance
        // it still has to travel to its slot.
        val remaining = offsetFor(key)
        settlingKey = key
        draggingKey = null
        draggingIndex = null
        dragged = 0f
        settleJob?.cancel()
        settleJob = scope.launch {
            settleOffset.snapTo(remaining)
            coroutineScope {
                launch { settleOffset.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) }
                launch { lift.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) }
            }
            settlingKey = null
        }
    }

    /** The live translation for the dragged row — see the class doc for why it is computed this way. */
    internal fun offsetFor(key: Any): Float {
        if (key != draggingKey) return 0f
        val now = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return (startOffset + dragged) - now.offset
    }

    private companion object {
        /** How close to an edge (in px) the row has to be held before the list scrolls. */
        const val AutoScrollMarginPx = 120f

        /** Tames the raw overshoot distance into a per-frame scroll step. */
        const val AutoScrollFactor = 0.08f
    }
}

@Composable
fun rememberReorderState(listState: LazyListState, onMove: (from: Int, to: Int) -> Boolean): ReorderState {
    val scope = rememberCoroutineScope()
    // onMove is re-created on every recomposition by most callers, so the state holder reads it
    // through a ref rather than capturing the first one it ever saw.
    val moveRef = remember { mutableStateOf(onMove) }
    moveRef.value = onMove
    return remember(listState) {
        ReorderState(listState = listState, scope = scope, onMove = { from, to -> moveRef.value(from, to) })
    }
}

/**
 * Makes an item draggable and lifts it while it is being dragged.
 *
 * Apply to the row itself. The lift (scale + shadow) is deliberately small: it has to say "this
 * one is loose" without turning a reorder into a special effect.
 */
fun Modifier.reorderable(state: ReorderState, key: Any): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val liftShape = PokyhShapes.lg
    val active = state.activeKey == key

    this
        // Above its neighbours for the whole gesture *and* the landing, so it slides over them
        // rather than disappearing under the next card down as it springs home.
        .zIndex(if (active) 1f else 0f)
        // The whole block is read at *draw* time, not composition time. `offsetFor` reads the
        // list's layout info and the accumulated drag, both of which change every frame of a
        // drag — reading them during composition would recompose the row (and its text) sixty
        // times a second to move it a few pixels. In a graphicsLayer lambda the same reads only
        // invalidate the layer.
        .graphicsLayer {
            val dragging = state.draggingKey == key
            val settling = state.settlingKey == key
            translationY = when {
                dragging -> state.offsetFor(key)
                settling -> state.settleOffset.value
                else -> 0f
            }
            val lift = if (dragging || settling) state.lift.value else 0f
            scaleX = 1f + 0.03f * lift
            scaleY = 1f + 0.03f * lift
            // The shape has to match the row's own surface, otherwise the shadow is cast from a
            // rectangle and shows as four corners poking out from behind a rounded card.
            shadowElevation = 18.dp.toPx() * lift
            shape = liftShape
            // Everything not in the hand steps back — and steps forward again *with* the lift,
            // so the rest of the screen brightens as the card lands instead of in one frame.
            val other = state.activeKey
            alpha = if (other != null && other != key) 1f - 0.5f * state.lift.value else 1f
        }
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.onDragStart(key)
                },
                onDrag = { change, amount ->
                    change.consume()
                    state.onDrag(amount.y)
                },
                onDragEnd = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    state.onDragEnd()
                },
                onDragCancel = { state.onDragEnd() },
            )
        }
}
