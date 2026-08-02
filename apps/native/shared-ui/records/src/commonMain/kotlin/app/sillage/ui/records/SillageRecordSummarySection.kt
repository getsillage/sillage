package app.sillage.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.MemoAI
import app.sillage.ui.designsystem.applySillageHeadingSemantics

data class SillageRecordSummaryStrings(
    val title: String,
    val readingAction: String,
    val generatingAction: String,
    val generateAction: String,
    val regenerateAction: String,
    val loadingBody: String,
    val emptyBody: String,
)

@Composable
fun SillageRecordSummarySection(
    summary: MemoAI?,
    loading: Boolean,
    strings: SillageRecordSummaryStrings,
    sourceRecordsLabel: String?,
    tokenCountLabel: String?,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    onGenerate: () -> Unit,
) {
    val presentation = remember(
        summary,
        loading,
        strings,
        sourceRecordsLabel,
        tokenCountLabel,
    ) {
        sillageRecordSummaryPresentation(
            summary = summary,
            loading = loading,
            strings = strings,
            sourceRecordsLabel = sourceRecordsLabel,
            tokenCountLabel = tokenCountLabel,
        )
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.title,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { applySillageHeadingSemantics() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onGenerate,
                    enabled = actionEnabled && !loading,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(presentation.actionLabel)
                }
            }

            Text(
                presentation.body,
                color = if (presentation.bodyMuted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            if (presentation.sourceRecordsLabel != null || presentation.technicalDetails != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    presentation.sourceRecordsLabel?.let { label ->
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    presentation.technicalDetails?.let { details ->
                        Text(
                            details,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

internal data class SillageRecordSummaryPresentation(
    val actionLabel: String,
    val body: String,
    val bodyMuted: Boolean,
    val sourceRecordsLabel: String?,
    val technicalDetails: String?,
)

internal fun sillageRecordSummaryPresentation(
    summary: MemoAI?,
    loading: Boolean,
    strings: SillageRecordSummaryStrings,
    sourceRecordsLabel: String?,
    tokenCountLabel: String?,
): SillageRecordSummaryPresentation {
    val summaryBody = summary?.summary?.takeIf { it.isNotBlank() }
    val actionLabel = when {
        loading && summary == null -> strings.readingAction
        loading -> strings.generatingAction
        summary == null -> strings.generateAction
        else -> strings.regenerateAction
    }
    val model = summary?.let { value ->
        listOf(value.provider, value.model)
            .filter { it.isNotBlank() }
            .joinToString(" / ")
    }.orEmpty()
    val technicalDetails = if (summaryBody == null) {
        null
    } else {
        buildList {
            if (model.isNotBlank()) {
                add(model)
            }
            tokenCountLabel?.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString(" · ").ifBlank { null }
    }

    return SillageRecordSummaryPresentation(
        actionLabel = actionLabel,
        body = summaryBody ?: if (loading) strings.loadingBody else strings.emptyBody,
        bodyMuted = summaryBody == null,
        sourceRecordsLabel = sourceRecordsLabel
            ?.takeIf { summaryBody != null && it.isNotBlank() },
        technicalDetails = technicalDetails,
    )
}
