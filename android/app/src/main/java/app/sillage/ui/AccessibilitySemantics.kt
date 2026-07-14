package app.sillage.ui

import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading

internal fun SemanticsPropertyReceiver.applyHeadingSemantics() {
    heading()
}

internal fun SemanticsPropertyReceiver.applyStatusSemantics(description: String) {
    contentDescription = description
}
