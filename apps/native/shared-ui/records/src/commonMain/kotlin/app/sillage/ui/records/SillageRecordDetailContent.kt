package app.sillage.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.RecordsFeatureStateHolder

@Composable
fun SillageRecordDetailContent(
    state: RecordsFeatureStateHolder,
    missingRecord: String,
    recordContent: @Composable (memo: Memo, modifier: Modifier) -> Unit,
    summaryContent: @Composable (memo: Memo, modifier: Modifier) -> Unit,
    metadataContent: @Composable (memo: Memo, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sillageRecordDetailBody(state) == SillageRecordDetailBody.Missing) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                missingRecord,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val selectedMemo = requireNotNull(state.selection.selectedMemo)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = SillageRecordDetailBodyKey) {
            recordContent(selectedMemo, recordDetailItemModifier())
        }
        item(key = SillageRecordDetailSummaryKey) {
            summaryContent(selectedMemo, recordDetailItemModifier())
        }
        item(key = SillageRecordDetailMetadataKey) {
            metadataContent(selectedMemo, recordDetailItemModifier())
        }
    }
}

internal enum class SillageRecordDetailBody {
    Missing,
    Content,
}

internal fun sillageRecordDetailBody(state: RecordsFeatureStateHolder): SillageRecordDetailBody =
    if (state.selection.selectedMemo == null) {
        SillageRecordDetailBody.Missing
    } else {
        SillageRecordDetailBody.Content
    }

private const val SillageRecordDetailBodyKey = "record-detail-body"
private const val SillageRecordDetailSummaryKey = "record-detail-summary"
private const val SillageRecordDetailMetadataKey = "record-detail-metadata"

private fun recordDetailItemModifier(): Modifier = Modifier
    .widthIn(max = 720.dp)
    .fillMaxWidth()
