package com.bandeja.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import com.bandeja.app.ui.components.ConfirmDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandeja.app.R
import com.bandeja.app.domain.repository.CategoryOutcome
import com.bandeja.app.domain.repository.CategoryRepository
import com.bandeja.app.ui.appViewModel
import com.bandeja.app.ui.components.AccentPicker
import com.bandeja.app.ui.components.BandejaTopBar
import com.bandeja.app.ui.components.CenteredBox
import com.bandeja.app.ui.components.EmojiPicker
import com.bandeja.app.ui.theme.CategoryAccent
import com.bandeja.app.ui.theme.Spacing
import com.bandeja.app.ui.theme.container
import com.bandeja.app.ui.theme.onContainer

/** Create (`categoryId == null`) and edit are the same screen: one design, one set of rules. */
@Composable
fun CategoryEditorScreen(
    categoryId: Long?,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryEditorViewModel = appViewModel(key = "editor-${categoryId ?: "new"}") {
        CategoryEditorViewModel(categoryId, it.categoryRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = CategoryAccent.fromKey(state.color)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BandejaTopBar(
                title = stringResource(if (categoryId == null) R.string.editor_create_title else R.string.editor_edit_title),
                onBack = onBack,
                actions = {
                    // Always in sight: the button at the end of this long screen is easy to miss.
                    if (categoryId != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.category_delete_button),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = { viewModel.save(onSaved = onBack) },
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                        .height(56.dp),
                ) { Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        if (confirmDelete) {
            ConfirmDialog(
                title = stringResource(R.string.category_delete_title, state.name),
                message = if (state.itemCount == 0) {
                    stringResource(R.string.category_delete_empty)
                } else {
                    pluralStringResource(R.plurals.category_delete_with_items, state.itemCount, state.itemCount)
                },
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onDeleted)
                },
                onDismiss = { confirmDelete = false },
            )
        }
        if (!state.isLoaded) {
            CenteredBox(Modifier.padding(padding)) {}
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            LivePreview(name = state.name, icon = state.icon, accent = accent)

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.editor_name_label)) },
                singleLine = true,
                isError = state.error != null,
                supportingText = state.error?.let { { Text(it.message()) } },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            Field(stringResource(R.string.editor_icon_label)) {
                EmojiPicker(selected = state.icon, onSelect = viewModel::onIconChange)
            }
            Field(stringResource(R.string.editor_color_label)) {
                AccentPicker(selectedKey = state.color, onSelect = { viewModel.onColorChange(it.key) })
            }
            if (categoryId != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.category_delete_button))
                }
            }
        }
    }
}

/** What the category will look like, updated as the user types and picks. */
@Composable
private fun LivePreview(name: String, icon: String, accent: CategoryAccent) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = accent.container(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(icon, fontSize = 32.sp) }
            Text(
                name.ifBlank { stringResource(R.string.editor_preview_placeholder) },
                style = MaterialTheme.typography.titleLarge,
                color = accent.onContainer().copy(alpha = if (name.isBlank()) 0.7f else 1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun CategoryOutcome.message(): String = when (this) {
    CategoryOutcome.NameBlank -> stringResource(R.string.error_name_blank)
    CategoryOutcome.NameTaken -> stringResource(R.string.error_name_taken)
    CategoryOutcome.NameTooLong -> stringResource(R.string.error_name_long, CategoryRepository.MAX_NAME_LENGTH)
    is CategoryOutcome.Saved -> ""
}
