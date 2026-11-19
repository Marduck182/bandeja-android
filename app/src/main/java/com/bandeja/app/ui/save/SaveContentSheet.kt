package com.bandeja.app.ui.save

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandeja.app.R
import com.bandeja.app.domain.repository.CategoryOutcome
import com.bandeja.app.domain.repository.CategoryRepository
import com.bandeja.app.domain.url.InvalidUrlReason
import com.bandeja.app.ui.components.CategoryPill
import com.bandeja.app.ui.components.CategorySelector
import com.bandeja.app.ui.components.EmojiPicker
import com.bandeja.app.ui.components.ErrorState
import com.bandeja.app.ui.components.ShimmerBox
import com.bandeja.app.ui.components.TagEditor
import com.bandeja.app.ui.components.ThumbnailImage
import com.bandeja.app.ui.components.labelRes
import com.bandeja.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The save sheet: shown over the app the user came from (Share) or over Home (manual save).
 * Fast path: category chip + Save. Everything else (notes, tags) is collapsed until asked for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveContentSheet(
    viewModel: SaveViewModel,
    onDismiss: () -> Unit,
    onOpenExisting: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Hides with the sheet's own animation first, then tells the host: no visual jump when closing.
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (state.step) {
            SaveStep.INVALID -> InvalidContent(state.invalidReason, onClose = ::close)
            SaveStep.SAVED -> SavedContent(state, onDone = ::close)
            SaveStep.EDITING -> EditingContent(
                state = state,
                viewModel = viewModel,
                onOpenExisting = onOpenExisting,
            )
        }
    }
}

@Composable
internal fun EditingContent(state: SaveUiState, viewModel: SaveViewModel, onOpenExisting: (Long) -> Unit) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showNewCategory by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(stringResource(R.string.save_title), style = MaterialTheme.typography.titleLarge)

        if (state.urlEditable) UrlField(state, viewModel)
        if (state.isUrlValid) PreviewCard(state)

        val duplicate = state.duplicate
        AnimatedVisibility(
            visible = duplicate != null && !state.allowDuplicate,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            if (duplicate != null) {
                DuplicateBanner(
                    categoryName = duplicate.category?.name,
                    onOpen = { onOpenExisting(duplicate.content.id) },
                    onSaveAgain = viewModel::allowDuplicate,
                )
            }
        }

        if (state.isUrlValid) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.save_title_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                shape = MaterialTheme.shapes.medium,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(R.string.save_category),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CategorySelector(
                categories = state.categories,
                selectedId = state.categoryId,
                onSelect = viewModel::onCategorySelected,
                onCreate = { showNewCategory = true },
            )
        }

        MoreSection(state, viewModel, expanded = showMore, onToggle = { showMore = !showMore })

        // Primary actions at the bottom of the sheet, within thumb reach.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconToggleButton(
                checked = state.isFavorite,
                onCheckedChange = viewModel::onFavoriteChange,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    if (state.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = stringResource(R.string.save_favorite),
                )
            }
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.save_button), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showNewCategory) {
        QuickCategoryDialog(
            onCreate = viewModel::createCategory,
            onDismiss = { showNewCategory = false },
        )
    }
}

@Composable
private fun UrlField(state: SaveUiState, viewModel: SaveViewModel) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (state.url.isBlank()) focus.requestFocus() }

    OutlinedTextField(
        value = state.url,
        onValueChange = viewModel::onUrlChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
        label = { Text(stringResource(R.string.save_url_label)) },
        placeholder = { Text(stringResource(R.string.save_url_hint)) },
        singleLine = true,
        isError = state.urlError != null,
        supportingText = state.urlError?.let { reason -> { Text(stringResource(reason.messageRes())) } },
        trailingIcon = {
            IconButton(onClick = { readClipboard(context)?.let(viewModel::onUrlChange) }) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.save_url_hint))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.medium,
    )
}

/** Reads the clipboard only when the user taps "paste": never in the background. */
private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

@Composable
private fun PreviewCard(state: SaveUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        // One step above the sheet itself (surfaceContainerLow), otherwise the card disappears into it.
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumbnail = state.metadata?.thumbnailUrl
            if (state.isLoadingMetadata && thumbnail == null) {
                ShimmerBox(Modifier.width(96.dp).aspectRatio(4f / 3f))
            } else {
                ThumbnailImage(
                    url = thumbnail,
                    type = state.contentType,
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(4f / 3f)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    state.source,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(state.contentType.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.isLoadingMetadata) {
                    Text(
                        stringResource(R.string.save_preview_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DuplicateBanner(categoryName: String?, onOpen: () -> Unit, onSaveAgain: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.save_duplicate_title), style = MaterialTheme.typography.titleSmall)
            Text(
                if (categoryName != null) {
                    stringResource(R.string.save_duplicate_in, categoryName)
                } else {
                    stringResource(R.string.save_duplicate_uncategorized)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.action_open)) }
                TextButton(onClick = onSaveAgain) { Text(stringResource(R.string.save_duplicate_again)) }
            }
        }
    }
}

@Composable
private fun MoreSection(state: SaveUiState, viewModel: SaveViewModel, expanded: Boolean, onToggle: () -> Unit) {
    Column {
        // Zero start padding so the label lines up with the fields above and below.
        TextButton(onClick = onToggle, contentPadding = PaddingValues(start = 0.dp, end = Spacing.md)) {
            Text(stringResource(R.string.save_more))
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text(stringResource(R.string.save_notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = MaterialTheme.shapes.medium,
                )
                TagEditor(tags = state.tags, onTagsChange = viewModel::onTagsChange)
            }
        }
    }
}

@Composable
internal fun InvalidContent(reason: InvalidUrlReason?, onClose: () -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.lg)) {
        ErrorState(
            title = stringResource(R.string.save_error_title),
            message = stringResource((reason ?: InvalidUrlReason.NO_URL).messageRes()),
            actionLabel = stringResource(R.string.action_close),
            onAction = onClose,
        )
    }
}

/** Confirmation: a check that pops in, then the sheet closes by itself. */
@Composable
internal fun SavedContent(state: SaveUiState, onDone: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(Unit) {
        delay(SAVED_VISIBLE_MS)
        onDone()
    }
    val category = state.savedCategory
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = Spacing.xxl, horizontal = Spacing.screen)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(72.dp)
                .scale(scale.value),
        )
        Text(stringResource(R.string.save_saved), style = MaterialTheme.typography.titleLarge)
        if (category != null) {
            Text(
                stringResource(R.string.save_saved_in, category.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CategoryPill(category)
        }
    }
}

/** Minimal category creation (name + emoji). The color is picked automatically; it can be changed later. */
@Composable
private fun QuickCategoryDialog(
    onCreate: suspend (name: String, icon: String) -> CategoryOutcome,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("📌") }
    var error by remember { mutableStateOf<CategoryOutcome?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_category_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.editor_name_label)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { outcome -> { Text(outcome.message()) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    shape = MaterialTheme.shapes.medium,
                )
                EmojiPicker(selected = icon, onSelect = { icon = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (val outcome = onCreate(name, icon)) {
                        is CategoryOutcome.Saved -> onDismiss()
                        else -> error = outcome
                    }
                }
            }) { Text(stringResource(R.string.quick_category_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CategoryOutcome.message(): String = when (this) {
    CategoryOutcome.NameBlank -> stringResource(R.string.error_name_blank)
    CategoryOutcome.NameTaken -> stringResource(R.string.error_name_taken)
    CategoryOutcome.NameTooLong -> stringResource(R.string.error_name_long, CategoryRepository.MAX_NAME_LENGTH)
    is CategoryOutcome.Saved -> ""
}

private fun InvalidUrlReason.messageRes(): Int = when (this) {
    InvalidUrlReason.EMPTY -> R.string.save_error_empty
    InvalidUrlReason.NO_URL -> R.string.save_error_no_url
    InvalidUrlReason.UNSUPPORTED_SCHEME -> R.string.save_error_unsupported
    InvalidUrlReason.TOO_LONG -> R.string.save_error_too_long
    InvalidUrlReason.MALFORMED -> R.string.save_error_malformed
}

private const val SAVED_VISIBLE_MS = 900L
