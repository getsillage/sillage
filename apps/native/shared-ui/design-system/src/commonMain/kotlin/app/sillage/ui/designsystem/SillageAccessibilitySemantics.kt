package app.sillage.ui.designsystem

import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading

fun SemanticsPropertyReceiver.applySillageHeadingSemantics() {
    heading()
}

fun SemanticsPropertyReceiver.applySillageStatusSemantics(description: String) {
    contentDescription = description
}
