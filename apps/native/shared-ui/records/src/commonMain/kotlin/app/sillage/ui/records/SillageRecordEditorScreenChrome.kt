package app.sillage.ui.records

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.sillage.features.records.RecordsEditorActionContext
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.canRunEditorAction
import app.sillage.ui.designsystem.applySillageHeadingSemantics

data class SillageRecordEditorScreenChromeStrings(
    val newRecordTitle: String,
    val editRecordTitle: String,
    val backContentDescription: String,
)

@Composable
fun SillageRecordEditorTopBarTitle(
    state: RecordsFeatureStateHolder,
    context: RecordsEditorActionContext,
    strings: SillageRecordEditorScreenChromeStrings,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageRecordEditorScreenChromePresentation(
        state = state,
        context = context,
        strings = strings,
    )
    Text(
        presentation.title,
        modifier = modifier.semantics { applySillageHeadingSemantics() },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SillageRecordEditorBackAction(
    state: RecordsFeatureStateHolder,
    context: RecordsEditorActionContext,
    strings: SillageRecordEditorScreenChromeStrings,
    icon: ImageVector,
    onBack: () -> Unit,
) {
    val presentation = sillageRecordEditorScreenChromePresentation(
        state = state,
        context = context,
        strings = strings,
    )
    IconButton(
        onClick = onBack,
        enabled = presentation.backEnabled,
    ) {
        Icon(icon, contentDescription = strings.backContentDescription)
    }
}

internal data class SillageRecordEditorScreenChromePresentation(
    val title: String,
    val backEnabled: Boolean,
)

internal fun sillageRecordEditorScreenChromePresentation(
    state: RecordsFeatureStateHolder,
    context: RecordsEditorActionContext,
    strings: SillageRecordEditorScreenChromeStrings,
): SillageRecordEditorScreenChromePresentation = SillageRecordEditorScreenChromePresentation(
    title = if (state.selection.selectedMemo == null) {
        strings.newRecordTitle
    } else {
        strings.editRecordTitle
    },
    backEnabled = state.canRunEditorAction(context),
)
