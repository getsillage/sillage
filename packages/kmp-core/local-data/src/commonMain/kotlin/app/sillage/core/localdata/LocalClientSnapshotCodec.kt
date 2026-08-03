package app.sillage.core.localdata

import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.preferences.normalizeBaseUrl
import app.sillage.core.application.preferences.normalizeLanguageMode
import app.sillage.core.application.preferences.normalizeThemeMode
import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.PendingMemoMutation
import app.sillage.core.sync.SupportedMemoSyncActions
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
    val memoSync: LocalMemoSyncState = LocalMemoSyncState(),
)

internal data class LocalMemoSyncState(
    val serverBaseUrl: String = "",
    val cloudVersions: Map<String, Long> = emptyMap(),
    val pendingMutations: Map<String, PendingMemoMutation> = emptyMap(),
)

internal object LocalClientSnapshotCodec {
    private const val CurrentSchemaVersion = 2
    private const val OldestSupportedSchemaVersion = 1

    internal val json = Json {
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
        if (persisted.schemaVersion !in OldestSupportedSchemaVersion..CurrentSchemaVersion) {
            throw InvalidClientSnapshotException(
                "Unsupported local client snapshot schema ${persisted.schemaVersion}.",
            )
        }

        val records = validateLocalRecords(persisted.records.map(PersistedMemo::toDomain))
        val memoSync = if (persisted.schemaVersion == 1) {
            LocalMemoSyncState()
        } else {
            validateMemoSyncState(
                state = LocalMemoSyncState(
                    serverBaseUrl = normalizeBaseUrl(persisted.memoSyncServerBaseUrl),
                    cloudVersions = persisted.memoCloudVersions,
                    pendingMutations = persisted.pendingMemoMutations.mapValues { (_, mutation) ->
                        mutation.toDomain()
                    },
                ),
                records = records,
            )
        }

        return LocalClientSnapshot(
            preferences = ClientPreferences(
                themeMode = normalizeThemeMode(persisted.themeMode),
                languageMode = normalizeLanguageMode(persisted.languageMode),
                serverBaseUrl = normalizeBaseUrl(persisted.serverBaseUrl),
            ),
            records = records,
            memoSync = memoSync,
        )
    }

    fun encode(snapshot: LocalClientSnapshot): String {
        val records = validateLocalRecords(snapshot.records)
        val memoSync = validateMemoSyncState(snapshot.memoSync, records)
        val persisted = PersistedClientSnapshot(
            schemaVersion = CurrentSchemaVersion,
            themeMode = normalizeThemeMode(snapshot.preferences.themeMode),
            languageMode = normalizeLanguageMode(snapshot.preferences.languageMode),
            serverBaseUrl = normalizeBaseUrl(snapshot.preferences.serverBaseUrl),
            records = records.map(PersistedMemo::fromDomain),
            memoSyncServerBaseUrl = memoSync.serverBaseUrl,
            memoCloudVersions = memoSync.cloudVersions,
            pendingMemoMutations = memoSync.pendingMutations.mapValues { (_, mutation) ->
                PersistedPendingMemoMutation.fromDomain(mutation)
            },
        )
        return json.encodeToString(PersistedClientSnapshot.serializer(), persisted)
    }
}

internal fun validateLocalRecords(records: List<Memo>): List<Memo> {
    if (records.any { it.id.isBlank() }) {
        throw InvalidClientSnapshotException("Local records must have non-empty identifiers.")
    }
    if (records.any { it.version < 1L }) {
        throw InvalidClientSnapshotException("Local record versions must be positive.")
    }
    if (records.map(Memo::id).distinct().size != records.size) {
        throw InvalidClientSnapshotException("Local record identifiers must be unique.")
    }
    return records
}

private fun validateMemoSyncState(
    state: LocalMemoSyncState,
    records: List<Memo>,
): LocalMemoSyncState {
    val normalizedServerBaseUrl = normalizeBaseUrl(state.serverBaseUrl)
    val recordIds = records.mapTo(mutableSetOf(), Memo::id)
    if (state.cloudVersions.keys.any(String::isBlank) ||
        state.cloudVersions.values.any { it < 1L }
    ) {
        throw InvalidClientSnapshotException("Memo cloud versions must use valid records and positive versions.")
    }
    if (!recordIds.containsAll(state.cloudVersions.keys)) {
        throw InvalidClientSnapshotException("Memo cloud versions must reference local records.")
    }
    if (state.cloudVersions.isNotEmpty() && normalizedServerBaseUrl.isBlank()) {
        throw InvalidClientSnapshotException("Memo cloud versions require a bound server address.")
    }
    if (!recordIds.containsAll(state.pendingMutations.keys)) {
        throw InvalidClientSnapshotException("Pending memo mutations must reference local records.")
    }
    val mutations = state.pendingMutations.values
    if (mutations.any {
            it.mutationId.isBlank() ||
                it.memoVersion < 1L ||
                it.memoUpdatedAt.isBlank() ||
                (it.action.isNotBlank() && it.action !in SupportedMemoSyncActions)
        }
    ) {
        throw InvalidClientSnapshotException("Pending memo mutations are invalid.")
    }
    if (mutations.map(PendingMemoMutation::mutationId).distinct().size != mutations.size) {
        throw InvalidClientSnapshotException("Pending memo mutation identifiers must be unique.")
    }
    return state.copy(serverBaseUrl = normalizedServerBaseUrl)
}

@Serializable
private data class PersistedClientSnapshot(
    val schemaVersion: Int = 1,
    val themeMode: String = ClientPreferenceValues.THEME_LIGHT,
    val languageMode: String = ClientPreferenceValues.LANGUAGE_ZH_CN,
    val serverBaseUrl: String = "",
    val records: List<PersistedMemo> = emptyList(),
    val memoSyncServerBaseUrl: String = "",
    val memoCloudVersions: Map<String, Long> = emptyMap(),
    val pendingMemoMutations: Map<String, PersistedPendingMemoMutation> = emptyMap(),
)

@Serializable
private data class PersistedPendingMemoMutation(
    val mutationId: String,
    val memoVersion: Long,
    val memoUpdatedAt: String,
    val action: String = "",
) {
    fun toDomain() = PendingMemoMutation(
        mutationId = mutationId,
        memoVersion = memoVersion,
        memoUpdatedAt = memoUpdatedAt,
        action = action,
    )

    companion object {
        fun fromDomain(mutation: PendingMemoMutation) = PersistedPendingMemoMutation(
            mutationId = mutation.mutationId,
            memoVersion = mutation.memoVersion,
            memoUpdatedAt = mutation.memoUpdatedAt,
            action = mutation.action,
        )
    }
}

@Serializable
internal data class PersistedMemo(
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
