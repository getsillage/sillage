package app.sillage.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStoreUrlTest {
    @Test
    fun missingSchemeDefaultsToHttps() {
        assertEquals("https://sillage.example", SessionStore.normalizeBaseUrl("sillage.example/"))
    }

    @Test
    fun explicitDebugHttpIsPreserved() {
        assertEquals("http://10.0.2.2:5231", SessionStore.normalizeBaseUrl("http://10.0.2.2:5231/"))
    }
}
