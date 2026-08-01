package app.sillage.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.RecordsFeatureStateHolder

data class SillageRecordFilterStrings(
    val unarchived: String,
    val archived: String,
    val favorited: String,
    val deleted: String,
)

@Composable
fun SillageRecordFilterTabs(
    state: RecordsFeatureStateHolder,
    strings: SillageRecordFilterStrings,
    onSelect: (MemoListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageRecordFilterPresentation(state = state, strings = strings)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    ) {
        Row(modifier = Modifier.selectableGroup()) {
            presentation.options.forEach { option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .selectable(
                            selected = option.selected,
                            onClick = { onSelect(option.filter) },
                            role = Role.Tab,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 3.dp)
                            .height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (option.selected) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                option.label,
                                color = if (option.selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class SillageRecordFilterOptionPresentation(
    val filter: MemoListFilter,
    val label: String,
    val selected: Boolean,
)

internal data class SillageRecordFilterPresentation(
    val options: List<SillageRecordFilterOptionPresentation>,
)

internal fun sillageRecordFilterPresentation(
    state: RecordsFeatureStateHolder,
    strings: SillageRecordFilterStrings,
) = SillageRecordFilterPresentation(
    options = MemoListFilter.entries.map { filter ->
        SillageRecordFilterOptionPresentation(
            filter = filter,
            label = when (filter) {
                MemoListFilter.Unarchived -> strings.unarchived
                MemoListFilter.Archived -> strings.archived
                MemoListFilter.Favorited -> strings.favorited
                MemoListFilter.Deleted -> strings.deleted
            },
            selected = state.filter == filter,
        )
    },
)
