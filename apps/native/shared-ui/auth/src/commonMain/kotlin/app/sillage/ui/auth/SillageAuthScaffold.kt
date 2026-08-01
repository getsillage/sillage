package app.sillage.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sillage.ui.designsystem.SillageInlineError
import app.sillage.ui.designsystem.applySillageHeadingSemantics

@Composable
fun SillageAuthScaffold(
    title: String,
    supporting: String,
    errorMessage: String?,
    errorIcon: ImageVector,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val presentation = sillageAuthScaffoldPresentation(
        title = title,
        supporting = supporting,
        errorMessage = errorMessage,
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                header()
                Row(verticalAlignment = Alignment.Top) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            presentation.title,
                            modifier = Modifier.semantics { applySillageHeadingSemantics() },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            presentation.supporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (trailing != null) {
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            trailing()
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    presentation.errorMessage?.let { message ->
                        SillageInlineError(
                            message = message,
                            icon = errorIcon,
                        )
                    }
                    content()
                }
            }
        }
    }
}

internal data class SillageAuthScaffoldPresentation(
    val title: String,
    val supporting: String,
    val errorMessage: String?,
)

internal fun sillageAuthScaffoldPresentation(
    title: String,
    supporting: String,
    errorMessage: String?,
) = SillageAuthScaffoldPresentation(
    title = title,
    supporting = supporting,
    errorMessage = errorMessage,
)
