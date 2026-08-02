package app.sillage.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SillageCalendarGrid(
    weekdayLabels: List<String>,
    weeks: List<List<String?>>,
    counts: Map<String, Int>,
    today: String,
    selectedDate: String?,
    dayDescription: @Composable (date: String, count: Int, isToday: Boolean) -> String,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekdayLabels.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(modifier = Modifier.weight(1f).height(44.dp))
                    } else {
                        val count = counts[date] ?: 0
                        val isToday = date == today
                        SillageCalendarDayCell(
                            date = date,
                            count = count,
                            isToday = isToday,
                            selected = date == selectedDate,
                            description = dayDescription(date, count, isToday),
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SillageCalendarDayCell(
    date: String,
    count: Int,
    isToday: Boolean,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageCalendarDayPresentation(
        date = date,
        count = count,
        isToday = isToday,
        selected = selected,
    )
    val color = when {
        presentation.selected -> MaterialTheme.colorScheme.surfaceContainerHighest
        presentation.count > 0 -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> Color.Transparent
    }
    val border = when {
        presentation.selected -> BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)
        presentation.isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        else -> null
    }

    Surface(
        selected = presentation.selected,
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                applySillageCalendarDaySemantics(
                    description = description,
                    isSelected = presentation.selected,
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = color,
        border = border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = presentation.dayNumber,
                fontWeight = if (presentation.isToday || presentation.selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = presentation.countLabel,
                color = if (presentation.count > 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color.Transparent
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

internal data class SillageCalendarDayPresentation(
    val dayNumber: String,
    val count: Int,
    val countLabel: String,
    val isToday: Boolean,
    val selected: Boolean,
)

internal fun sillageCalendarDayPresentation(
    date: String,
    count: Int,
    isToday: Boolean,
    selected: Boolean,
): SillageCalendarDayPresentation = SillageCalendarDayPresentation(
    dayNumber = date.takeLast(2).toInt().toString(),
    count = count,
    countLabel = if (count > 0) count.toString() else " ",
    isToday = isToday,
    selected = selected,
)

internal fun SemanticsPropertyReceiver.applySillageCalendarDaySemantics(
    description: String,
    isSelected: Boolean,
) {
    contentDescription = description
    selected = isSelected
}
