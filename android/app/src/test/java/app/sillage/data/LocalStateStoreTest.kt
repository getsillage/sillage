package app.sillage.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalStateStoreTest {
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
    fun migratesLegacyPreferencesOnceAndRemovesTheOldCopy() {
        val legacy = context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE)
        legacy.edit()
            .putString("data", "legacy diary")
            .putString("cloud_memo_versions", "{\"memo-1\":2}")
            .commit()

        val store = store()

        assertEquals(SecureReadResult.Value("legacy diary"), store.readString("data"))
        assertEquals(
            SecureReadResult.Value("{\"memo-1\":2}"),
            store.readString("cloud_memo_versions"),
        )
        assertTrue(legacy.all.isEmpty())
        assertTrue(context.getDatabasePath(LocalStateStore.DATABASE_NAME).isFile)
    }

    @Test
    fun writesMultipleValuesTransactionallyAndReopensThem() {
        val first = store()
        first.putStrings(mapOf("data" to "one", "pending_memo_mutations" to "two"))
        first.close()
        activeStore = null

        val reopened = store()

        assertEquals(SecureReadResult.Value("one"), reopened.readString("data"))
        assertEquals(
            SecureReadResult.Value("two"),
            reopened.readString("pending_memo_mutations"),
        )
    }

    @Test
    fun reportsUnreadableCiphertextInsteadOfTreatingItAsMissing() {
        val first = store()
        first.putString("data", "private")
        first.close()
        activeStore = null

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(LocalStateStore.DATABASE_NAME).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            database.execSQL(
                "UPDATE ${LocalStateStore.TABLE_STATE} SET ${LocalStateStore.COLUMN_VALUE} = ? WHERE ${LocalStateStore.COLUMN_KEY} = ?",
                arrayOf("broken", "data"),
            )
        }

        val result = store().readString("data")

        assertTrue(result is SecureReadResult.Unreadable)
        assertFalse(result is SecureReadResult.Missing)
    }

    @Test
    fun preservesUnreadableLegacyCiphertextDuringMigration() {
        val legacy = context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE)
        legacy.edit().putString("secure.data", "broken-legacy-payload").commit()

        val first = store()

        assertEquals(
            SecureReadResult.Unreadable("broken-legacy-payload"),
            first.readString("data"),
        )
        assertTrue(legacy.all.isEmpty())
        first.close()
        activeStore = null

        assertEquals(
            SecureReadResult.Unreadable("broken-legacy-payload"),
            store().readString("data"),
        )
    }

    @Test
    fun leavesExistingValuesUntouchedWhenBatchEncryptionFails() {
        val initial = store()
        initial.putStrings(mapOf("data" to "old-data", "pending_memo_mutations" to "old-sync"))
        initial.close()
        activeStore = null
        val failing = LocalStateStore(context, FailingValueCipher).also { activeStore = it }

        assertThrows(IllegalStateException::class.java) {
            failing.putStrings(mapOf("data" to "new-data", "pending_memo_mutations" to "fail"))
        }

        assertEquals(SecureReadResult.Value("old-data"), failing.readString("data"))
        assertEquals(
            SecureReadResult.Value("old-sync"),
            failing.readString("pending_memo_mutations"),
        )
    }

    private fun store(): LocalStateStore {
        return LocalStateStore(context, TestValueCipher).also { activeStore = it }
    }

    private data object TestValueCipher : ValueCipher {
        override fun encrypt(value: String): String = "test:$value"

        override fun decrypt(payload: String): String {
            require(payload.startsWith("test:"))
            return payload.removePrefix("test:")
        }
    }

    private data object FailingValueCipher : ValueCipher {
        override fun encrypt(value: String): String {
            check(value != "fail")
            return TestValueCipher.encrypt(value)
        }

        override fun decrypt(payload: String): String = TestValueCipher.decrypt(payload)
    }
}
