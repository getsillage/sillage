package app.sillage.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SillageSettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = sillageSettingsSectionColors(MaterialTheme.colorScheme)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .semantics { heading() },
            color = colors.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = colors.container,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Column(content = content)
        }
    }
}

internal data class SillageSettingsSectionColors(
    val title: Color,
    val container: Color,
    val border: Color,
)

internal fun sillageSettingsSectionColors(colorScheme: ColorScheme) =
    SillageSettingsSectionColors(
        title = colorScheme.onSurfaceVariant,
        container = colorScheme.surfaceContainerLow,
        border = colorScheme.outlineVariant,
    )
