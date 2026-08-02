package app.sillage.core.localdata

import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.preferences.normalizeLanguageMode
import app.sillage.core.application.preferences.normalizeThemeMode
import app.sillage.core.domain.records.Memo
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class InvalidClientSnapshotException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal data class LocalClientSnapshot(
    val preferences: ClientPreferences = ClientPreferences(),
    val records: List<Memo> = emptyList(),
)

internal object LocalClientSnapshotCodec {
    private const val CurrentSchemaVersion = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun decode(value: String?): LocalClientSnapshot {
        if (value == null) {
            return LocalClientSnapshot()
        }
        val persisted = try {
            json.decodeFromString<PersistedClientSnapshot>(value)
        } catch (error: SerializationException) {
            throw InvalidClientSnapshotException("The local client snapshot is not valid JSON.", error)
        } catch (error: IllegalArgumentException) {
            throw InvalidClientSnapshotException("The local client snapshot is invalid.", error)
        }
        if (persisted.schemaVersion != CurrentSchemaVersion) {
            throw InvalidClientSnapshotException(
                "Unsupported local client snapshot schema ${persisted.schemaVersion}.",
            )
        }

        val records = persisted.records.map(PersistedMemo::toDomain)
        if (records.any { it.id.isBlank() }) {
            throw InvalidClientSnapshotException("Local records must have non-empty identifiers.")
        }
        if (records.any { it.version < 1L }) {
            throw InvalidClientSnapshotException("Local record versions must be positive.")
        }
        if (records.map(Memo::id).distinct().size != records.size) {
            throw InvalidClientSnapshotException("Local record identifiers must be unique.")
        }

        return LocalClientSnapshot(
            preferences = ClientPreferences(
                themeMode = normalizeThemeMode(persisted.themeMode),
                languageMode = normalizeLanguageMode(persisted.languageMode),
            ),
            records = records,
        )
    }

    fun encode(snapshot: LocalClientSnapshot): String {
        val persisted = PersistedClientSnapshot(
            schemaVersion = CurrentSchemaVersion,
            themeMode = normalizeThemeMode(snapshot.preferences.themeMode),
            languageMode = normalizeLanguageMode(snapshot.preferences.languageMode),
            records = snapshot.records.map(PersistedMemo::fromDomain),
        )
        return json.encodeToString(PersistedClientSnapshot.serializer(), persisted)
    }
}

@Serializable
private data class PersistedClientSnapshot(
    val schemaVersion: Int = 1,
    val themeMode: String = ClientPreferenceValues.THEME_LIGHT,
    val languageMode: String = ClientPreferenceValues.LANGUAGE_ZH_CN,
    val records: List<PersistedMemo> = emptyList(),
)

@Serializable
private data class PersistedMemo(
    val id: String,
    val content: String,
    val entryDate: String,
    val version: Long,
    val createdAt: String,
    val updatedAt: String,
    val favoritedAt: String? = null,
    val archivedAt: String? = null,
    val deletedAt: String? = null,
    val purgedAt: String? = null,
) {
    fun toDomain() = Memo(
        id = id,
        content = content,
        entryDate = entryDate,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        favoritedAt = favoritedAt,
        archivedAt = archivedAt,
        deletedAt = deletedAt,
        purgedAt = purgedAt,
    )

    companion object {
        fun fromDomain(memo: Memo) = PersistedMemo(
            id = memo.id,
            content = memo.content,
            entryDate = memo.entryDate,
            version = memo.version,
            createdAt = memo.createdAt,
            updatedAt = memo.updatedAt,
            favoritedAt = memo.favoritedAt,
            archivedAt = memo.archivedAt,
            deletedAt = memo.deletedAt,
            purgedAt = memo.purgedAt,
        )
    }
}
