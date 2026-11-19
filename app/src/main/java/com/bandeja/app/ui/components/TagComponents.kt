package com.bandeja.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bandeja.app.R
import com.bandeja.app.domain.usecase.TextRules
import com.bandeja.app.ui.theme.Spacing

/** Read-only tag. */
@Composable
fun TagChip(label: String, modifier: Modifier = Modifier) {
    SuggestionChip(
        onClick = {},
        label = { Text("#$label") },
        modifier = modifier,
        enabled = true,
    )
}

/** Adds tags by Enter, comma or the + button; each tag can be removed. Cleaning rules come from [TextRules]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(tags: List<String>, onTagsChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    var draft by rememberSaveable { mutableStateOf("") }

    fun commit(text: String) {
        if (text.isBlank()) return
        onTagsChange(TextRules.tags(tags + text))
        draft = ""
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { value ->
                if (value.endsWith(",")) commit(value.trimEnd(',')) else draft = value
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.save_tags_label)) },
            placeholder = { Text(stringResource(R.string.save_tags_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(draft) }),
            trailingIcon = {
                if (draft.isNotBlank()) {
                    IconButton(onClick = { commit(draft) }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.tag_add))
                    }
                }
            },
            shape = MaterialTheme.shapes.medium,
        )
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onTagsChange(tags - tag) },
                        label = { Text("#$tag") },
                        trailingIcon = {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.tag_remove, tag),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}
