package app.sillage.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.sillage.R
import app.sillage.ui.ask.AskScreen
import app.sillage.ui.auth.InitializeScreen
import app.sillage.ui.auth.LoginScreen
import app.sillage.ui.auth.ModeSelectionScreen
import app.sillage.ui.auth.ServerScreen
import app.sillage.ui.memos.MemoDetailScreen
import app.sillage.ui.memos.MemoEditorScreen
import app.sillage.ui.memos.MemoListScreen
import app.sillage.ui.settings.AISettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

@Composable
internal fun SillageApp(viewModel: SillageViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var activeToastType by remember { mutableStateOf(UiToastType.SUCCESS) }
    val attachmentChooserTitle = stringResource(R.string.attachment_open_chooser)
    val attachmentNoApp = stringResource(R.string.error_attachment_no_app)
    val attachmentShareDenied = stringResource(R.string.error_attachment_share_denied)
    val attachmentPrepareFailed = stringResource(R.string.error_attachment_prepare)
    val attachmentOpenFailed = stringResource(R.string.error_attachment_open)
    LaunchedEffect(
        viewModel,
        context,
        attachmentChooserTitle,
        attachmentNoApp,
        attachmentShareDenied,
        attachmentPrepareFailed,
        attachmentOpenFailed,
    ) {
        viewModel.attachmentOpenEvents.collect { event ->
            var handedOff = false
            try {
                if (!viewModel.state.value.canHandleAttachmentOpen(event.requestId)) {
                    return@collect
                }
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    event.file,
                )
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, event.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri(event.displayName, contentUri)
                }
                val chooser = Intent.createChooser(viewIntent, attachmentChooserTitle).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = viewIntent.clipData
                }
                context.startActivity(chooser)
                handedOff = true
                viewModel.onAttachmentOpenHandled(event.requestId)
            } catch (_: ActivityNotFoundException) {
                viewModel.onAttachmentOpenFailed(event.requestId, attachmentNoApp)
            } catch (_: SecurityException) {
                viewModel.onAttachmentOpenFailed(event.requestId, attachmentShareDenied)
            } catch (_: IllegalArgumentException) {
                viewModel.onAttachmentOpenFailed(event.requestId, attachmentPrepareFailed)
            } catch (_: RuntimeException) {
                viewModel.onAttachmentOpenFailed(event.requestId, attachmentOpenFailed)
            } finally {
                if (!handedOff) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        event.file.parentFile?.deleteRecursively()
                    }
                }
            }
        }
    }
    LaunchedEffect(viewModel, snackbarHostState, state.languageMode) {
        snackbarHostState.currentSnackbarData?.dismiss()
        viewModel.toastEvents.collectLatest { event ->
            if (!event.matchesLanguage(state.languageMode)) {
                return@collectLatest
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            activeToastType = event.type
            snackbarHostState.showSnackbar(
                message = event.message,
                withDismissAction = true,
                duration = if (event.type == UiToastType.SUCCESS) {
                    SnackbarDuration.Short
                } else {
                    SnackbarDuration.Long
                },
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (state.screen) {
                Screen.Loading -> LoadingScreen()
                Screen.ModeSelection -> ModeSelectionScreen(state, viewModel)
                Screen.Server -> ServerScreen(state, viewModel)
                Screen.Initialize -> InitializeScreen(state, viewModel)
                Screen.Login -> LoginScreen(state, viewModel)
                Screen.Memos -> MemoListScreen(state, viewModel)
                Screen.MemoDetail -> MemoDetailScreen(state, viewModel)
                Screen.Editor -> MemoEditorScreen(state, viewModel)
                Screen.AISettings -> AISettingsScreen(state, viewModel)
                Screen.Ask -> AskScreen(state, viewModel)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 68.dp, bottom = 12.dp),
        ) { data ->
            val isError = activeToastType == UiToastType.ERROR
            val isWarning = activeToastType == UiToastType.WARNING
            Snackbar(
                snackbarData = data,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .then(
                        if (isError) {
                            Modifier.semantics {
                                liveRegion = LiveRegionMode.Assertive
                                error(data.visuals.message)
                            }
                        } else {
                            Modifier
                        },
                    ),
                containerColor = when {
                    isError -> MaterialTheme.colorScheme.errorContainer
                    isWarning -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.inverseSurface
                },
                contentColor = when {
                    isError -> MaterialTheme.colorScheme.onErrorContainer
                    isWarning -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.inverseOnSurface
                },
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(8.dp),
                color = colorResource(R.color.ic_launcher_background),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                )
            }
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}
