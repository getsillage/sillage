package app.sillage.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper

/**
 * Transactional encrypted state storage for the offline client.
 *
 * Values are encrypted independently with Android Keystore AES-GCM before
 * entering SQLite. The first open migrates the legacy encrypted
 * SharedPreferences values without replacing any state already in SQLite.
 */
internal interface LocalStateStorage {
    fun readString(key: String): SecureReadResult

    fun contains(key: String): Boolean

    fun putString(key: String, value: String)

    fun putStrings(values: Map<String, String>)
}

internal class LocalStateStore(
    context: Context,
    private val cipher: ValueCipher = KeystoreCipher(),
) : LocalStateStorage {
    private val appContext = context.applicationContext
    private val database = LocalStateDatabase(appContext)
    private val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val legacySecurePrefs = SecurePreferences(legacyPrefs, cipher)

    init {
        migrateLegacyPreferences()
    }

    override fun readString(key: String): SecureReadResult = synchronized(STATE_LOCK) {
        val encrypted = readEncrypted(database.readableDatabase, key) ?: return@synchronized SecureReadResult.Missing
        runCatching { SecureReadResult.Value(cipher.decrypt(encrypted)) }
            .getOrElse { SecureReadResult.Unreadable(encrypted) }
    }

    override fun contains(key: String): Boolean = synchronized(STATE_LOCK) {
        readEncrypted(database.readableDatabase, key) != null
    }

    override fun putString(key: String, value: String) {
        putStrings(mapOf(key to value))
    }

    override fun putStrings(values: Map<String, String>) = synchronized(STATE_LOCK) {
        if (values.isEmpty()) return@synchronized
        val encrypted = values.mapValues { (_, value) -> cipher.encrypt(value) }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            encrypted.forEach { (key, value) -> writeEncrypted(db, key, value, SQLiteDatabase.CONFLICT_REPLACE) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    internal fun close() = synchronized(STATE_LOCK) {
        database.close()
    }

    private fun migrateLegacyPreferences() = synchronized(STATE_LOCK) {
        val legacyValues = buildMap {
            LEGACY_KEYS.forEach { key ->
                when (val value = legacySecurePrefs.readString(key)) {
                    is SecureReadResult.Missing -> Unit
                    is SecureReadResult.Value -> put(key, cipher.encrypt(value.value))
                    is SecureReadResult.Unreadable -> put(key, value.rawPayload)
                }
            }
        }
        if (legacyValues.isEmpty()) return@synchronized

        val db = database.writableDatabase
        db.beginTransaction()
        try {
            legacyValues.forEach { (key, encrypted) ->
                writeEncrypted(db, key, encrypted, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        var editor = legacyPrefs.edit()
        LEGACY_KEYS.forEach { key -> editor = legacySecurePrefs.remove(editor, key) }
        if (!editor.commit()) {
            throw SQLiteException("Could not remove migrated local preferences")
        }
    }

    private fun readEncrypted(db: SQLiteDatabase, key: String): String? {
        db.query(
            TABLE_STATE,
            arrayOf(COLUMN_VALUE),
            "$COLUMN_KEY = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun writeEncrypted(
        db: SQLiteDatabase,
        key: String,
        encrypted: String,
        conflictAlgorithm: Int,
    ) {
        val values = ContentValues().apply {
            put(COLUMN_KEY, key)
            put(COLUMN_VALUE, encrypted)
        }
        if (db.insertWithOnConflict(TABLE_STATE, null, values, conflictAlgorithm) == -1L) {
            throw SQLiteException("Could not persist local state")
        }
    }

    private class LocalStateDatabase(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ) {
        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.rawQuery("PRAGMA secure_delete=ON", null).use { cursor -> cursor.moveToFirst() }
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_STATE (
                    $COLUMN_KEY TEXT PRIMARY KEY NOT NULL,
                    $COLUMN_VALUE TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw SQLiteException("Unsupported local database upgrade $oldVersion -> $newVersion")
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw SQLiteException("Unsupported local database downgrade $oldVersion -> $newVersion")
        }
    }

    companion object {
        private val STATE_LOCK = Any()
        internal const val DATABASE_NAME = "sillage-local.db"
        internal const val TABLE_STATE = "state_values"
        internal const val COLUMN_KEY = "state_key"
        internal const val COLUMN_VALUE = "encrypted_value"
        private const val DATABASE_VERSION = 1
        private const val LEGACY_PREFS_NAME = "sillage.local_data"
        private val LEGACY_KEYS = listOf(
            "data",
            "cloud_memo_versions",
            "pending_memo_mutations",
            "pending_local_attachments",
            "data_corrupt_backup",
        )
    }
}
