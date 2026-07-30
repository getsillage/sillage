package app.sillage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStateStoreInstrumentedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private var activeStore: LocalStateStore? = null

    @Before
    fun resetStorage() {
        context.deleteDatabase(LocalStateStore.DATABASE_NAME)
        context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun closeStorage() {
        activeStore?.close()
        activeStore = null
    }

    @Test
    fun migratesAndEncryptsLocalStateOnARealAndroidRuntime() {
        val legacy = context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE)
        val privateText = "device-only diary payload"
        SecurePreferences(legacy).putString(legacy.edit(), "data", privateText).commit()

        val store = LocalStateStore(context).also { activeStore = it }
        assertEquals(SecureReadResult.Value(privateText), store.readString("data"))
        assertTrue(legacy.all.isEmpty())

        store.putStrings(
            mapOf(
                "cloud_memo_versions" to "{\"memo-device\":4}",
                "pending_memo_mutations" to "{\"memo-device\":{}}",
            ),
        )
        store.close()
        activeStore = null

        val reopened = LocalStateStore(context).also { activeStore = it }
        assertEquals(SecureReadResult.Value(privateText), reopened.readString("data"))
        assertEquals(
            SecureReadResult.Value("{\"memo-device\":4}"),
            reopened.readString("cloud_memo_versions"),
        )

        reopened.close()
        activeStore = null
        val databaseFiles = listOf(
            context.getDatabasePath(LocalStateStore.DATABASE_NAME),
            File(context.getDatabasePath(LocalStateStore.DATABASE_NAME).absolutePath + "-wal"),
        ).filter(File::isFile)
        assertTrue(databaseFiles.isNotEmpty())
        assertFalse(databaseFiles.any { it.readBytes().toString(Charsets.ISO_8859_1).contains(privateText) })
    }
}
