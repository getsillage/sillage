package app.sillage.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class SillageCalendarHeaderStrings(
    val currentMonth: String,
    val browseByDate: String,
    val previousMonthDescription: String,
    val nextMonthDescription: String,
)

@Composable
fun SillageCalendarHeader(
    strings: SillageCalendarHeaderStrings,
    previousIcon: ImageVector,
    nextIcon: ImageVector,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageCalendarHeaderPresentation(strings)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = previousIcon,
                contentDescription = presentation.previousMonthDescription,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = presentation.currentMonth,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = presentation.browseByDate,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = nextIcon,
                contentDescription = presentation.nextMonthDescription,
            )
        }
    }
}

internal data class SillageCalendarHeaderPresentation(
    val currentMonth: String,
    val browseByDate: String,
    val previousMonthDescription: String,
    val nextMonthDescription: String,
)

internal fun sillageCalendarHeaderPresentation(
    strings: SillageCalendarHeaderStrings,
): SillageCalendarHeaderPresentation = SillageCalendarHeaderPresentation(
    currentMonth = strings.currentMonth,
    browseByDate = strings.browseByDate,
    previousMonthDescription = strings.previousMonthDescription,
    nextMonthDescription = strings.nextMonthDescription,
)
