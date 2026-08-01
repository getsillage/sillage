package app.sillage.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.sillage.features.records.RecordsFeatureStateHolder

data class SillageRecordSearchStrings(
    val label: String,
    val clearContentDescription: String,
    val searchContentDescription: String,
)

@Composable
fun SillageRecordSearchBar(
    state: RecordsFeatureStateHolder,
    strings: SillageRecordSearchStrings,
    searchIcon: ImageVector,
    clearIcon: ImageVector,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageRecordSearchPresentation(state)

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = presentation.query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(strings.label) },
            leadingIcon = {
                if (presentation.searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(searchIcon, contentDescription = null)
                }
            },
            trailingIcon = {
                if (presentation.showClear) {
                    IconButton(onClick = onClear) {
                        Icon(
                            clearIcon,
                            contentDescription = strings.clearContentDescription,
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        FilledIconButton(
            onClick = onSearch,
            enabled = presentation.searchEnabled,
        ) {
            Icon(
                searchIcon,
                contentDescription = strings.searchContentDescription,
            )
        }
    }
}

internal data class SillageRecordSearchPresentation(
    val query: String,
    val searching: Boolean,
    val showClear: Boolean,
    val searchEnabled: Boolean,
)

internal fun sillageRecordSearchPresentation(
    state: RecordsFeatureStateHolder,
) = SillageRecordSearchPresentation(
    query = state.search.query,
    searching = state.search.searching,
    showClear = state.search.query.isNotBlank() || state.search.results != null,
    searchEnabled = !state.search.searching && state.search.query.isNotBlank(),
)
