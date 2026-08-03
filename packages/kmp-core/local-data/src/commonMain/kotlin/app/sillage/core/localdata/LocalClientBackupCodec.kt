package app.sillage.core.localdata

import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.preferences.normalizeLanguageMode
import app.sillage.core.application.preferences.normalizeThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

internal object LocalClientBackupCodec {
    private const val CurrentFormatVersion = 1

    fun decode(
        value: String,
        fallbackPreferences: ClientPreferences,
    ): LocalClientSnapshot {
        val persisted = try {
            LocalClientSnapshotCodec.json.decodeFromString<PersistedClientBackup>(value)
        } catch (error: SerializationException) {
            throw InvalidClientSnapshotException("The Sillage backup is not valid JSON.", error)
        } catch (error: IllegalArgumentException) {
            throw InvalidClientSnapshotException("The Sillage backup is invalid.", error)
        }
        if (persisted.formatVersion != CurrentFormatVersion) {
            throw InvalidClientSnapshotException(
                "Unsupported Sillage backup format ${persisted.formatVersion}.",
            )
        }
        if (persisted.exportedAt.isBlank()) {
            throw InvalidClientSnapshotException("The Sillage backup export timestamp is missing.")
        }
        return LocalClientSnapshot(
            preferences = ClientPreferences(
                themeMode = persisted.themeMode?.let(::normalizeThemeMode)
                    ?: fallbackPreferences.themeMode,
                languageMode = persisted.languageMode?.let(::normalizeLanguageMode)
                    ?: fallbackPreferences.languageMode,
            ),
            records = validateLocalRecords(persisted.memos.map(PersistedMemo::toDomain)),
        )
    }

    fun encode(snapshot: LocalClientSnapshot, exportedAt: String): String {
        val persisted = PersistedClientBackup(
            formatVersion = CurrentFormatVersion,
            exportedAt = exportedAt,
            themeMode = normalizeThemeMode(snapshot.preferences.themeMode),
            languageMode = normalizeLanguageMode(snapshot.preferences.languageMode),
            memos = snapshot.records.map(PersistedMemo::fromDomain),
        )
        return LocalClientSnapshotCodec.json.encodeToString(
            PersistedClientBackup.serializer(),
            persisted,
        )
    }
}

@Serializable
private data class PersistedClientBackup(
    val formatVersion: Int,
    val exportedAt: String,
    val themeMode: String? = null,
    val languageMode: String? = null,
    val memos: List<PersistedMemo>,
)
