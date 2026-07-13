package app.sillage.ui.auth

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorSemanticsTest {
    @Test
    fun authenticationErrorIsAnAssertiveLiveRegion() {
        val message = "Sign-in failed"
        val semantics = SemanticsConfiguration()

        semantics.applyAuthErrorSemantics(message)

        assertEquals(LiveRegionMode.Assertive, semantics[SemanticsProperties.LiveRegion])
        assertEquals(message, semantics[SemanticsProperties.Error])
    }
}
