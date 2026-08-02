package app.sillage.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SillageAuthHeader(
    appName: String,
    tagline: String,
    logoContainerColor: Color,
    languageIcon: ImageVector,
    languageContentDescription: String,
    languageEnabled: Boolean,
    onLanguageToggle: () -> Unit,
    logo: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAuthHeaderPresentation(
        appName = appName,
        tagline = tagline,
        languageContentDescription = languageContentDescription,
        languageEnabled = languageEnabled,
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = logoContainerColor,
        ) {
            logo()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                presentation.appName,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                presentation.tagline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onLanguageToggle,
            enabled = presentation.languageEnabled,
        ) {
            Icon(
                languageIcon,
                contentDescription = presentation.languageContentDescription,
            )
        }
    }
}

internal data class SillageAuthHeaderPresentation(
    val appName: String,
    val tagline: String,
    val languageContentDescription: String,
    val languageEnabled: Boolean,
)

internal fun sillageAuthHeaderPresentation(
    appName: String,
    tagline: String,
    languageContentDescription: String,
    languageEnabled: Boolean,
) = SillageAuthHeaderPresentation(
    appName = appName,
    tagline = tagline,
    languageContentDescription = languageContentDescription,
    languageEnabled = languageEnabled,
)
