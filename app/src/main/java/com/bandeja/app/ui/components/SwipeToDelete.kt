package com.bandeja.app.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bandeja.app.R
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ACTION_WIDTH = 64.dp
private val ACTION_GAP = Spacing.md
private const val FLING_VELOCITY = 800f

/**
 * Swipe a row to the left to reveal a delete button; the row is only deleted when that button is pressed.
 * Swiping back, or tapping the row, closes it again. TalkBack users get the same action from the actions menu,
 * since a drag gesture is not accessible by itself.
 */
@Composable
fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val deleteLabel = stringResource(R.string.action_delete)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val scope = rememberCoroutineScope()
    val revealPx = with(LocalDensity.current) { (ACTION_WIDTH + ACTION_GAP).toPx() }

    // 0 = closed, -revealPx = fully open. It follows the finger and then settles to one of the two.
    var offsetPx by remember { mutableFloatStateOf(0f) }
    val isRevealed = offsetPx < 0f

    fun settle(open: Boolean) {
        scope.launch {
            animate(offsetPx, if (open) -revealPx else 0f, animationSpec = tween(durationMillis = 180)) { value, _ ->
                offsetPx = value
            }
        }
    }

    val dragState = rememberDraggableState { delta -> offsetPx = (offsetPx + delta).coerceIn(-revealPx, 0f) }

    Box(
        modifier
            .semantics { customActions = listOf(CustomAccessibilityAction(deleteLabel) { currentOnDelete(); true }) }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity -> settle(open = offsetPx < -revealPx / 2 || velocity < -FLING_VELOCITY) },
            ),
    ) {
        if (isRevealed) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(end = Spacing.screen),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(ACTION_WIDTH)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable(role = Role.Button, onClickLabel = deleteLabel) { currentOnDelete() }
                        .semantics { contentDescription = deleteLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        Box(Modifier.offset { IntOffset(offsetPx.roundToInt(), 0) }) {
            content()
            if (isRevealed) {
                // While open, a tap on the row closes it instead of opening the item.
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { settle(open = false) },
                )
            }
        }
    }
}

/**
 * Returns the function to call when the user deletes an item: it deletes right away and offers "Deshacer"
 * for a few seconds; tapping it calls [onUndo] to put the item back.
 */
@Composable
fun rememberUndoableDelete(
    snackbar: SnackbarHostState,
    onDelete: (ContentItem) -> Unit,
    onUndo: (ContentItem) -> Unit,
): (ContentItem) -> Unit {
    val scope = rememberCoroutineScope()
    val message = stringResource(R.string.content_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val currentDelete by rememberUpdatedState(onDelete)
    val currentUndo by rememberUpdatedState(onUndo)
    return remember(snackbar, message, undoLabel) {
        { item ->
            currentDelete(item)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) currentUndo(item)
            }
            Unit
        }
    }
}
