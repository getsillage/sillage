package app.sillage.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.ask.AskSourceRef

class SillageAskSourceReferenceStrings(
    val sourceCount: @Composable (count: Int) -> String,
    val sourceLabel: @Composable (source: AskSourceRef) -> String,
    val showSourcesContentDescription: String,
    val hideSourcesContentDescription: String,
)

class SillageAskSourceReferenceIcons(
    val expand: ImageVector,
    val collapse: ImageVector,
)

@Composable
fun SillageAskSourceReferences(
    sources: List<AskSourceRef>,
    enabled: Boolean,
    strings: SillageAskSourceReferenceStrings,
    icons: SillageAskSourceReferenceIcons,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sources.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val presentation = sillageAskSourceReferencesPresentation(
        sources = sources,
        enabled = enabled,
        expanded = expanded,
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.height(48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                strings.sourceCount(presentation.totalSources),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (expanded) icons.collapse else icons.expand,
                contentDescription = if (expanded) {
                    strings.hideSourcesContentDescription
                } else {
                    strings.showSourcesContentDescription
                },
                modifier = Modifier.size(16.dp),
            )
        }
        presentation.visibleSources.forEach { source ->
            TextButton(
                onClick = { onOpenSource(source.source.memoId) },
                enabled = source.enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text(
                    strings.sourceLabel(source.source),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal data class SillageAskSourceReferenceRow(
    val source: AskSourceRef,
    val enabled: Boolean,
)

internal data class SillageAskSourceReferencesPresentation(
    val totalSources: Int,
    val visibleSources: List<SillageAskSourceReferenceRow>,
)

internal fun sillageAskSourceReferencesPresentation(
    sources: List<AskSourceRef>,
    enabled: Boolean,
    expanded: Boolean,
): SillageAskSourceReferencesPresentation = SillageAskSourceReferencesPresentation(
    totalSources = sources.size,
    visibleSources = if (expanded) {
        sources.take(MAX_VISIBLE_ASK_SOURCES).map { source ->
            SillageAskSourceReferenceRow(
                source = source,
                enabled = enabled && source.memoId.isNotBlank(),
            )
        }
    } else {
        emptyList()
    },
)

private const val MAX_VISIBLE_ASK_SOURCES = 5
