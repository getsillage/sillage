package app.sillage.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.sillage.ui.designsystem.SillageErrorCard

const val SETTINGS_LIST_TEST_TAG = "settings-list"

@Composable
fun SillageSettingsList(
    loading: Boolean,
    errorMessage: String?,
    retryLabel: String,
    retryIcon: ImageVector,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val presentation = sillageSettingsListPresentation(
        loading = loading,
        errorMessage = errorMessage,
    )

    if (presentation.loading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag(SETTINGS_LIST_TEST_TAG),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            presentation.errorMessage?.let { message ->
                item(key = "settings-load-error") {
                    SillageErrorCard(
                        message = message,
                        actionLabel = retryLabel,
                        actionIcon = retryIcon,
                        onAction = onRetry,
                    )
                }
            }
            content()
        }
    }
}

internal data class SillageSettingsListPresentation(
    val loading: Boolean,
    val errorMessage: String?,
)

internal fun sillageSettingsListPresentation(
    loading: Boolean,
    errorMessage: String?,
) = SillageSettingsListPresentation(
    loading = loading,
    errorMessage = errorMessage.takeUnless { loading },
)
