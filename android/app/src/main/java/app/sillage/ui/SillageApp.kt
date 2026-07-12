package app.sillage.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.withContext

@Composable
internal fun SillageApp(viewModel: SillageViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
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
                val chooser = Intent.createChooser(viewIntent, "打开附件").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = viewIntent.clipData
                }
                context.startActivity(chooser)
                handedOff = true
                viewModel.onAttachmentOpenHandled(event.requestId)
            } catch (_: ActivityNotFoundException) {
                viewModel.onAttachmentOpenFailed(event.requestId, "没有可打开此附件的应用")
            } catch (_: SecurityException) {
                viewModel.onAttachmentOpenFailed(event.requestId, "系统拒绝共享此附件")
            } catch (_: IllegalArgumentException) {
                viewModel.onAttachmentOpenFailed(event.requestId, "无法准备此附件")
            } catch (_: RuntimeException) {
                viewModel.onAttachmentOpenFailed(event.requestId, "无法打开此附件")
            } finally {
                if (!handedOff) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        event.file.parentFile?.deleteRecursively()
                    }
                }
            }
        }
    }
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
            Text("Sillage", style = MaterialTheme.typography.titleMedium)
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}
