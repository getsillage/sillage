package app.sillage.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sillage.features.records.CalendarMemoCoverage

data class SillageCalendarEmptySelectionStrings(
    val empty: String,
    val mayBeIncomplete: String,
)

@Composable
fun SillageCalendarEmptySelection(
    coverage: CalendarMemoCoverage,
    strings: SillageCalendarEmptySelectionStrings,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    ) {
        Text(
            text = sillageCalendarEmptySelectionMessage(
                coverage = coverage,
                strings = strings,
            ),
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal fun sillageCalendarEmptySelectionMessage(
    coverage: CalendarMemoCoverage,
    strings: SillageCalendarEmptySelectionStrings,
): String = if (coverage.currentMonthMayBeIncomplete) {
    strings.mayBeIncomplete
} else {
    strings.empty
}
