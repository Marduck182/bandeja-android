package com.bandeja.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandeja.app.R
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.ui.appViewModel
import com.bandeja.app.ui.components.BandejaTopBar
import com.bandeja.app.ui.components.CategoryPill
import com.bandeja.app.ui.components.CategorySelector
import com.bandeja.app.ui.components.ConfirmDialog
import com.bandeja.app.ui.components.ErrorState
import com.bandeja.app.ui.components.FavoriteButton
import com.bandeja.app.ui.components.TagChip
import com.bandeja.app.ui.components.TagEditor
import com.bandeja.app.ui.components.ThumbnailImage
import com.bandeja.app.ui.components.labelRes
import com.bandeja.app.ui.theme.Spacing
import com.bandeja.app.ui.util.Actions
import com.bandeja.app.ui.util.rememberFormattedDate
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun DetailScreen(
    contentId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = appViewModel(key = "detail-$contentId") {
        DetailViewModel(contentId, it.contentRepository, it.categoryRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var moving by rememberSaveable { mutableStateOf(false) }

    val item = state.item
    val copiedMessage = stringResource(R.string.detail_link_copied)
    val openFailedMessage = stringResource(R.string.detail_open_failed)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            BandejaTopBar(
                title = "",
                onBack = onBack,
                actions = {
                    if (item != null) {
                        FavoriteButton(item.content.isFavorite, viewModel::toggleFavorite)
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        DetailMenu(
                            onMove = { moving = true },
                            onDelete = { confirmDelete = true },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (item != null) {
                ActionBar(
                    onOpen = {
                        if (!Actions.openUrl(context, item.content.url)) scope.launch { snackbar.showSnackbar(openFailedMessage) }
                    },
                    onCopy = {
                        if (Actions.copyToClipboard(context, item.content.title, item.content.url)) {
                            scope.launch { snackbar.showSnackbar(copiedMessage, duration = SnackbarDuration.Short) }
                        }
                    },
                    onShare = { Actions.shareText(context, item.content.title, item.content.url) },
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize())
            item == null -> ErrorState(
                title = stringResource(R.string.detail_not_found_title),
                message = stringResource(R.string.detail_not_found_body),
                modifier = Modifier.padding(padding),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
            )
            else -> DetailBody(item, Modifier.padding(padding))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.detail_delete_title),
            message = stringResource(R.string.detail_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (editing && item != null) {
        EditContentSheet(
            item = item,
            categories = state.categories,
            onSave = { title, notes, categoryId, tags ->
                viewModel.update(title, notes, categoryId, tags)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
    if (moving && item != null) {
        MoveSheet(
            item = item,
            categories = state.categories,
            onSelect = { categoryId ->
                viewModel.moveToCategory(categoryId)
                moving = false
            },
            onDismiss = { moving = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(item: ContentItem, modifier: Modifier = Modifier) {
    val content = item.content
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ThumbnailImage(
            url = content.thumbnailUrl,
            type = content.contentType,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large),
        )
        Text(content.title, style = MaterialTheme.typography.headlineSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${content.source} · ${stringResource(content.contentType.labelRes())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            CategoryPill(item.category)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Section(stringResource(R.string.detail_notes)) {
            Text(
                content.description.ifBlank { stringResource(R.string.detail_no_notes) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (content.description.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (item.tags.isNotEmpty()) {
            Section(stringResource(R.string.detail_tags)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    item.tags.forEach { TagChip(it) }
                }
            }
        }

        val saved = rememberFormattedDate(content.createdAt)
        Text(
            stringResource(R.string.detail_saved_on, saved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content.publishedAt?.let {
            Text(
                stringResource(R.string.detail_published_on, rememberFormattedDate(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Primary action at thumb height: open the original; copy and share next to it. */
@Composable
private fun ActionBar(onOpen: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onOpen,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.action_open_original))
            }
            FilledTonalIconButton(onClick = onCopy, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy_link))
            }
            FilledTonalIconButton(onClick = onShare, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
            }
        }
    }
}

@Composable
private fun DetailMenu(onMove: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_move)) },
                leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                onClick = { expanded = false; onMove() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(
    item: ContentItem,
    categories: List<com.bandeja.app.domain.model.Category>,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.detail_move), style = MaterialTheme.typography.titleLarge)
            CategorySelector(
                categories = categories,
                selectedId = item.content.categoryId,
                onSelect = onSelect,
                wrap = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditContentSheet(
    item: ContentItem,
    categories: List<com.bandeja.app.domain.model.Category>,
    onSave: (title: String, notes: String, categoryId: Long?, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(item.content.title) }
    var notes by rememberSaveable { mutableStateOf(item.content.description) }
    var categoryId by rememberSaveable { mutableStateOf(item.content.categoryId) }
    var tags by rememberSaveable { mutableStateOf(item.tags) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(stringResource(R.string.edit_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.save_title_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
                shape = MaterialTheme.shapes.medium,
            )
            CategorySelector(categories, categoryId, onSelect = { categoryId = it })
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.save_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
            )
            TagEditor(tags = tags, onTagsChange = { tags = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onDismiss, modifier = Modifier.height(56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { onSave(title, notes, categoryId, tags) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
