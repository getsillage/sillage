package app.sillage.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NavigationContentHeight = 60.dp
private const val NavigationDividerAlpha = 0.08f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SillageNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = sillageNavigationBarColors(MaterialTheme.colorScheme)

    Surface(
        modifier = modifier,
        color = colors.container,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = colors.divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                    .height(NavigationContentHeight)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

internal data class SillageNavigationBarColors(
    val container: Color,
    val divider: Color,
)

internal fun sillageNavigationBarColors(colorScheme: ColorScheme) = SillageNavigationBarColors(
    container = colorScheme.surfaceContainerLow,
    divider = colorScheme.onSurface.copy(alpha = NavigationDividerAlpha),
)
