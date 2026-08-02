package app.sillage.ui.ask

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.ui.designsystem.applySillageHeadingSemantics

data class SillageAskOptionsStrings(
    val title: String,
    val timeRangeLabel: String,
    val recentSevenDaysAction: String,
    val recentThirtyDaysAction: String,
    val allTimeAction: String,
    val sourceLabel: String,
    val recordsSourceAction: String,
    val summariesSourceAction: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SillageAskOptionsSheet(
    state: AskFeatureStateHolder,
    enabled: Boolean,
    strings: SillageAskOptionsStrings,
    onDismiss: () -> Unit,
    onContextScopeChange: (String) -> Unit,
    onSourceKindChange: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                strings.title,
                modifier = Modifier.semantics { applySillageHeadingSemantics() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SillageAskOptions(
                state = state,
                enabled = enabled,
                strings = strings,
                onContextScopeChange = onContextScopeChange,
                onSourceKindChange = onSourceKindChange,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun SillageAskOptions(
    state: AskFeatureStateHolder,
    enabled: Boolean,
    strings: SillageAskOptionsStrings,
    onContextScopeChange: (String) -> Unit,
    onSourceKindChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAskOptionsPresentation(state)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            strings.timeRangeLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AskOptionButton(
                label = strings.recentSevenDaysAction,
                selected = presentation.recentSevenDaysSelected,
                enabled = enabled,
                onClick = { onContextScopeChange(ASK_CONTEXT_SCOPE_RECENT_7_DAYS) },
            )
            AskOptionButton(
                label = strings.recentThirtyDaysAction,
                selected = presentation.recentThirtyDaysSelected,
                enabled = enabled,
                onClick = { onContextScopeChange(ASK_CONTEXT_SCOPE_RECENT_30_DAYS) },
            )
            AskOptionButton(
                label = strings.allTimeAction,
                selected = presentation.allTimeSelected,
                enabled = enabled,
                onClick = { onContextScopeChange(ASK_CONTEXT_SCOPE_ALL) },
            )
        }
        Text(
            strings.sourceLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AskOptionButton(
                label = strings.recordsSourceAction,
                selected = presentation.recordsSelected,
                enabled = enabled,
                onClick = { onSourceKindChange(ASK_SOURCE_KIND_RECORDS) },
            )
            AskOptionButton(
                label = strings.summariesSourceAction,
                selected = presentation.summariesSelected,
                enabled = enabled,
                onClick = { onSourceKindChange(ASK_SOURCE_KIND_SUMMARIES) },
            )
        }
    }
}

@Composable
private fun AskOptionButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
    )
}

internal data class SillageAskOptionsPresentation(
    val recentSevenDaysSelected: Boolean,
    val recentThirtyDaysSelected: Boolean,
    val allTimeSelected: Boolean,
    val recordsSelected: Boolean,
    val summariesSelected: Boolean,
)

internal fun sillageAskOptionsPresentation(
    state: AskFeatureStateHolder,
): SillageAskOptionsPresentation = SillageAskOptionsPresentation(
    recentSevenDaysSelected = state.contextScope == ASK_CONTEXT_SCOPE_RECENT_7_DAYS,
    recentThirtyDaysSelected = state.contextScope == ASK_CONTEXT_SCOPE_RECENT_30_DAYS,
    allTimeSelected = state.contextScope == ASK_CONTEXT_SCOPE_ALL,
    recordsSelected = state.sourceKind == ASK_SOURCE_KIND_RECORDS,
    summariesSelected = state.sourceKind == ASK_SOURCE_KIND_SUMMARIES,
)

private const val ASK_CONTEXT_SCOPE_RECENT_7_DAYS = "recent_7_days"
private const val ASK_CONTEXT_SCOPE_RECENT_30_DAYS = "recent_30_days"
private const val ASK_CONTEXT_SCOPE_ALL = "all"
private const val ASK_SOURCE_KIND_RECORDS = "records"
private const val ASK_SOURCE_KIND_SUMMARIES = "summaries"
