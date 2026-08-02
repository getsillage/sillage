package app.sillage.ui.records

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.MemoViewMode
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsRefreshStatus
import app.sillage.ui.designsystem.applySillageHeadingSemantics

data class SillageRecordsScreenChromeStrings(
    val recordsTitle: String,
    val calendarTitle: String,
    val refreshContentDescription: String,
    val newRecordContentDescription: String,
)

data class SillageRecordsScreenChromeIcons(
    val refresh: ImageVector,
    val newRecord: ImageVector,
)

@Composable
fun SillageRecordsTopBarTitle(
    state: RecordsFeatureStateHolder,
    subtitle: String,
    strings: SillageRecordsScreenChromeStrings,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageRecordsScreenChromePresentation(
        state = state,
        hostActionsEnabled = true,
    )
    Column(modifier = modifier) {
        Text(
            if (presentation.calendarMode) strings.calendarTitle else strings.recordsTitle,
            modifier = Modifier.semantics { applySillageHeadingSemantics() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SillageRecordsRefreshAction(
    state: RecordsFeatureStateHolder,
    hostActionsEnabled: Boolean,
    strings: SillageRecordsScreenChromeStrings,
    icon: ImageVector,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageRecordsScreenChromePresentation(
        state = state,
        hostActionsEnabled = hostActionsEnabled,
    )
    IconButton(
        onClick = onRefresh,
        enabled = presentation.refreshEnabled,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = strings.refreshContentDescription)
    }
}

@Composable
fun SillageRecordsNewRecordAction(
    state: RecordsFeatureStateHolder,
    strings: SillageRecordsScreenChromeStrings,
    icon: ImageVector,
    onNewRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageRecordsScreenChromePresentation(
        state = state,
        hostActionsEnabled = true,
    )
    if (!presentation.showNewRecord) return

    FloatingActionButton(
        onClick = onNewRecord,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = strings.newRecordContentDescription)
    }
}

internal data class SillageRecordsScreenChromePresentation(
    val calendarMode: Boolean,
    val refreshEnabled: Boolean,
    val showNewRecord: Boolean,
)

internal fun sillageRecordsScreenChromePresentation(
    state: RecordsFeatureStateHolder,
    hostActionsEnabled: Boolean,
): SillageRecordsScreenChromePresentation = SillageRecordsScreenChromePresentation(
    calendarMode = state.viewMode == MemoViewMode.Calendar,
    refreshEnabled = hostActionsEnabled && state.refreshStatus != RecordsRefreshStatus.Loading,
    showNewRecord = state.filter != MemoListFilter.Deleted,
)
