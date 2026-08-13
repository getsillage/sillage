package app.sillage.ui.application

import app.sillage.core.application.ask.AskAnswerStreamEvent
import app.sillage.core.application.ask.AskAnswerStreamer
import app.sillage.core.application.ask.AskClient
import app.sillage.core.application.ask.AskClientFactory
import app.sillage.core.application.ask.AskRepository
import app.sillage.core.application.ask.StreamAskAnswerCommand
import app.sillage.core.application.auth.BootstrapInfo
import app.sillage.core.application.auth.AuthSession
import app.sillage.core.application.auth.AuthenticationFailureException
import app.sillage.core.application.auth.AuthenticationFailureReason
import app.sillage.core.application.auth.CapturedSignOutSession
import app.sillage.core.application.auth.ChangePasswordCommand
import app.sillage.core.application.auth.InitializeAccountCommand
import app.sillage.core.application.auth.InstanceAuthenticationRepository
import app.sillage.core.application.auth.InstanceAuthenticationRepositoryFactory
import app.sillage.core.application.auth.InstanceBootstrapRepository
import app.sillage.core.application.auth.SignInCommand
import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.preferences.ClientPreferencesRepository
import app.sillage.core.application.records.RecordDraft
import app.sillage.core.application.records.MAX_RECORD_CONTENT_UTF8_BYTES
import app.sillage.core.application.records.RecordDraftValidationError
import app.sillage.core.application.records.RecordLifecycleRepository
import app.sillage.core.application.records.RecordWriteRepository
import app.sillage.core.application.records.RecordsRepository
import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.ask.AskSourceRef
import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.auth.Account
import app.sillage.core.sync.AppliedMemoSync
import app.sillage.core.sync.ConflictMemoSync
import app.sillage.core.sync.MemoSyncGateway
import app.sillage.core.sync.MemoSyncGatewayFactory
import app.sillage.core.sync.MemoSyncWorkspace
import app.sillage.core.sync.MemoSyncWorkspaceFactory
import app.sillage.core.sync.PendingMemoSync
import app.sillage.core.sync.SyncPushSummary
import app.sillage.features.auth.InstanceBootstrapContext
import app.sillage.features.auth.InstanceAuthenticationFailure
import app.sillage.features.records.MemoListFilter
import app.sillage.ui.appshell.AppDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class SillageNativeControllerTest {
    @Test
    fun checksServerAndPersistsNormalizedAddress() = runTest {
        val repository = FakeRecordsRepository().apply {
            preferences = ClientPreferences(serverBaseUrl = "https://old.example")
        }
        val bootstrap = BootstrapInfo(
            initialized = true,
            serverVersion = "0.3.1",
            serverRevision = "abc",
            apiVersion = "v1",
            minimumAndroidVersionCode = 1,
        )
        val remote = FakeBootstrapRepository(bootstrap)
        val controller = controller(repository, remote)

        assertEquals("https://old.example", controller.state.serverConnection.baseUrl)
        controller.updateServerBaseUrl("new.example/")
        controller.checkServerConnection()

        assertEquals("https://new.example", remote.requestedBaseUrl)
        assertEquals(bootstrap, controller.state.serverConnection.bootstrap)
        assertEquals("https://new.example", repository.preferences.serverBaseUrl)
        assertFalse(controller.state.serverConnection.failed)

        controller.setDarkTheme(true)
        assertEquals("https://new.example", repository.preferences.serverBaseUrl)
        assertEquals(ClientPreferenceValues.THEME_DARK, repository.preferences.themeMode)
    }

    @Test
    fun failedServerCheckKeepsLastPersistedAddress() = runTest {
        val repository = FakeRecordsRepository().apply {
            preferences = ClientPreferences(serverBaseUrl = "https://working.example")
        }
        val controller = controller(
            repository,
            FakeBootstrapRepository(error = IllegalStateException("offline")),
        )

        controller.updateServerBaseUrl("unavailable.example")
        controller.checkServerConnection()

        assertTrue(controller.state.serverConnection.failed)
        assertEquals("https://unavailable.example", controller.state.serverConnection.checkedBaseUrl)
        assertEquals("https://working.example", repository.preferences.serverBaseUrl)
        assertTrue(controller.state.storageAvailable)
    }

    @Test
    fun cancelledServerCheckUnlocksRetryAndPropagatesCancellation() = runTest {
        val controller = controller(
            FakeRecordsRepository(),
            FakeBootstrapRepository(error = CancellationException("left settings")),
        )
        controller.updateServerBaseUrl("example.test")

        assertFailsWith<CancellationException> {
            controller.checkServerConnection()
        }

        assertFalse(controller.state.serverConnection.checking)
        assertFalse(controller.state.serverConnection.failed)
        assertTrue(
            controller.state.serverConnection.nextRequest(
                InstanceBootstrapContext(controller.state.clientContext.generation),
            ) != null,
        )
    }

    @Test
    fun savedServerCheckRestoresDeviceCredentialWithoutFeedback() = runTest {
        val local = FakeRecordsRepository().apply {
            preferences = ClientPreferences(serverBaseUrl = "https://example.test")
        }
        val remote = FakeAuthenticationRepository().apply {
            restoredSession = session
        }
        val bootstrap = FakeBootstrapRepository(
            result = BootstrapInfo(true, "0.3.1", "abc", "v1", 1),
        )
        val controller = controller(
            repository = local,
            bootstrapRepository = bootstrap,
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )

        controller.resumeSavedAuthentication()

        assertEquals("https://example.test", bootstrap.requestedBaseUrl)
        assertEquals(1, remote.restoreCalls)
        assertEquals("account-1", controller.state.authentication.account?.id)
        assertNull(controller.state.feedback)
    }

    @Test
    fun automaticSyncPullsRecordsWithoutReplacingAuthenticationFeedback() = runTest {
        val local = FakeRecordsRepository()
        val serverMemo = memo("server-record", "from server")
        val workspace = FakeMemoSyncWorkspace(local, pending = emptyList())
        val gateway = FakeMemoSyncGateway(pulledMemos = listOf(serverMemo))
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            memoSyncWorkspaceFactory = FakeMemoSyncWorkspaceFactory(workspace),
            memoSyncGatewayFactory = FakeMemoSyncGatewayFactory(gateway),
        )

        signIn(controller, dismissFeedback = false)
        assertEquals(SillageNativeFeedback.SignedIn, controller.state.feedback)

        controller.syncMemosAutomatically()

        assertEquals(1, gateway.pullCalls)
        assertEquals(listOf(serverMemo), local.records)
        assertEquals(listOf(serverMemo), controller.state.workspace.records.records)
        assertEquals(SillageNativeFeedback.SignedIn, controller.state.feedback)
        assertFalse(controller.state.busy)
    }

    @Test
    fun automaticSyncStillSurfacesFailures() = runTest {
        val local = FakeRecordsRepository()
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            memoSyncWorkspaceFactory = FakeMemoSyncWorkspaceFactory(
                FakeMemoSyncWorkspace(local, pending = emptyList()),
            ),
            memoSyncGatewayFactory = FakeMemoSyncGatewayFactory(
                FakeMemoSyncGateway(pullError = IllegalStateException("offline")),
            ),
        )

        signIn(controller)
        controller.syncMemosAutomatically()

        assertEquals(SillageNativeFeedback.MemoSyncFailed, controller.state.feedback)
        assertFalse(controller.state.busy)
    }

    @Test
    fun automaticSyncDoesNotRepublishConsumedFeedback() = runTest {
        val localMemo = memo("memo-1", "local")
        val local = FakeRecordsRepository(mutableListOf(localMemo))
        val pushStarted = CompletableDeferred<Unit>()
        val releasePush = CompletableDeferred<Unit>()
        val gateway = FakeMemoSyncGateway(
            beforeResult = {
                pushStarted.complete(Unit)
                releasePush.await()
            },
        )
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            memoSyncWorkspaceFactory = FakeMemoSyncWorkspaceFactory(
                FakeMemoSyncWorkspace(
                    local,
                    pending = listOf(PendingMemoSync(localMemo, null, "create-1", "create")),
                ),
            ),
            memoSyncGatewayFactory = FakeMemoSyncGatewayFactory(gateway),
        )

        signIn(controller, dismissFeedback = false)
        val sync = launch { controller.syncMemosAutomatically() }
        pushStarted.await()

        controller.dismissFeedback()
        releasePush.complete(Unit)
        sync.join()

        assertNull(controller.state.feedback)
        assertFalse(controller.state.busy)
    }

    @Test
    fun secureRestoreFailureKeepsLoginAvailableWithStableReason() = runTest {
        val local = FakeRecordsRepository().apply {
            preferences = ClientPreferences(serverBaseUrl = "https://example.test")
        }
        val remote = FakeAuthenticationRepository().apply {
            restoreError = AuthenticationFailureException(
                AuthenticationFailureReason.SecureStorageUnavailable,
            )
        }
        val controller = controller(
            repository = local,
            bootstrapRepository = FakeBootstrapRepository(
                result = BootstrapInfo(true, "0.3.1", "abc", "v1", 1),
            ),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )

        controller.resumeSavedAuthentication()

        assertEquals(
            InstanceAuthenticationFailure.SecureStorageUnavailable,
            controller.state.authentication.failure,
        )
        assertFalse(controller.state.authentication.loading)
        assertNull(controller.state.authentication.account)
    }

    @Test
    fun cancelledRestoreCleanupFailureUnlocksLoginWithStableReason() = runTest {
        val local = FakeRecordsRepository().apply {
            preferences = ClientPreferences(serverBaseUrl = "https://example.test")
        }
        val cleanupError = AuthenticationFailureException(
            AuthenticationFailureReason.SecureStorageUnavailable,
        )
        val remote = FakeAuthenticationRepository().apply {
            restoreError = CancellationException("left settings")
            localSessionClearError = cleanupError
        }
        val controller = controller(
            repository = local,
            bootstrapRepository = FakeBootstrapRepository(
                result = BootstrapInfo(true, "0.3.1", "abc", "v1", 1),
            ),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )

        val error = assertFailsWith<AuthenticationFailureException> {
            controller.resumeSavedAuthentication()
        }

        assertTrue(error === cleanupError)
        assertFalse(controller.state.authentication.loading)
        assertEquals(
            InstanceAuthenticationFailure.SecureStorageUnavailable,
            controller.state.authentication.failure,
        )
        assertFalse(remote.localSessionCleared)
    }

    @Test
    fun initializesAccountAgainstCheckedServerAndKeepsSessionOnlyInController() = runTest {
        val local = FakeRecordsRepository()
        val remote = FakeAuthenticationRepository()
        val controller = controller(
            repository = local,
            bootstrapRepository = FakeBootstrapRepository(
                result = BootstrapInfo(false, "0.3.1", "abc", "v1", 1),
            ),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )
        controller.updateServerBaseUrl("example.test")
        controller.checkServerConnection()
        controller.updateAuthenticationUsername("felix")
        controller.updateAuthenticationDisplayName("Felix")
        controller.updateAuthenticationPassword("correct horse battery staple")

        controller.authenticate()

        assertEquals("https://example.test", remote.baseUrl)
        assertEquals("account-1", controller.state.authentication.account?.id)
        assertEquals("", controller.state.authentication.form.password)
        assertEquals(true, controller.state.serverConnection.bootstrap?.initialized)
        assertEquals(SillageNativeFeedback.AccountInitialized, controller.state.feedback)
        assertEquals("https://example.test", local.preferences.serverBaseUrl)
    }

    @Test
    fun authenticationFailureKeepsCredentialsAndMapsStableReason() = runTest {
        val remote = FakeAuthenticationRepository().apply {
            authenticationError = AuthenticationFailureException(
                AuthenticationFailureReason.InvalidCredentials,
            )
        }
        val controller = controller(
            repository = FakeRecordsRepository(),
            bootstrapRepository = FakeBootstrapRepository(
                result = BootstrapInfo(true, "0.3.1", "abc", "v1", 1),
            ),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )
        controller.updateServerBaseUrl("https://example.test")
        controller.checkServerConnection()
        controller.updateAuthenticationUsername("felix")
        controller.updateAuthenticationPassword("wrong password")

        controller.authenticate()

        assertEquals(
            InstanceAuthenticationFailure.InvalidCredentials,
            controller.state.authentication.failure,
        )
        assertEquals("felix", controller.state.authentication.form.username)
        assertEquals("wrong password", controller.state.authentication.form.password)
        assertFalse(controller.state.authentication.loading)
        assertNull(controller.state.authentication.account)
    }

    @Test
    fun passwordChangeValidationDoesNotCallRepository() = runTest {
        val remote = FakeAuthenticationRepository()
        val controller = controller(
            repository = FakeRecordsRepository(),
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )
        signIn(controller)

        controller.updateAuthenticationNewPassword("new password")
        controller.updateAuthenticationConfirmPassword("different password")
        controller.changePassword()

        assertEquals(0, remote.changePasswordCalls)
        assertEquals(
            InstanceAuthenticationFailure.RequiredFields,
            controller.state.authentication.failure,
        )

        controller.updateAuthenticationCurrentPassword("current password")
        controller.changePassword()
        assertEquals(0, remote.changePasswordCalls)
        assertEquals(
            InstanceAuthenticationFailure.PasswordConfirmationMismatch,
            controller.state.authentication.failure,
        )
    }

    @Test
    fun passwordChangeRotatesSessionClearsSecretsAndRefreshesAccount() = runTest {
        val remote = FakeAuthenticationRepository().apply {
            changePasswordSession = session.copy(
                account = session.account.copy(displayName = "Updated Felix"),
                accessToken = "rotated-access-token",
            )
        }
        val controller = controller(
            repository = FakeRecordsRepository(),
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )
        signIn(controller)
        controller.updateAuthenticationCurrentPassword("current password")
        controller.updateAuthenticationNewPassword("new password")
        controller.updateAuthenticationConfirmPassword("new password")

        controller.changePassword()

        assertEquals(1, remote.changePasswordCalls)
        assertEquals("current password", remote.lastChangePasswordCommand?.currentPassword)
        assertEquals("new password", remote.lastChangePasswordCommand?.newPassword)
        assertEquals("Updated Felix", controller.state.authentication.account?.displayName)
        assertEquals("", controller.state.authentication.form.currentPassword)
        assertEquals("", controller.state.authentication.form.newPassword)
        assertEquals("", controller.state.authentication.form.confirmPassword)
        assertEquals(SillageNativeFeedback.PasswordChanged, controller.state.feedback)
        assertFalse(controller.state.busy)
    }

    @Test
    fun failedPasswordChangeKeepsDraftAndMapsCurrentPasswordFailure() = runTest {
        val remote = FakeAuthenticationRepository().apply {
            changePasswordError = AuthenticationFailureException(
                AuthenticationFailureReason.InvalidCredentials,
            )
        }
        val controller = controller(
            repository = FakeRecordsRepository(),
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )
        signIn(controller)
        controller.updateAuthenticationCurrentPassword("wrong password")
        controller.updateAuthenticationNewPassword("new password")
        controller.updateAuthenticationConfirmPassword("new password")

        controller.changePassword()

        assertEquals("wrong password", controller.state.authentication.form.currentPassword)
        assertEquals("new password", controller.state.authentication.form.newPassword)
        assertEquals("new password", controller.state.authentication.form.confirmPassword)
        assertEquals(
            InstanceAuthenticationFailure.InvalidCredentials,
            controller.state.authentication.failure,
        )
        assertEquals("account-1", controller.state.authentication.account?.id)
        assertFalse(controller.state.authentication.form.passwordChanging)
        assertFalse(controller.state.busy)
    }

    @Test
    fun passwordChangeLocksRecordWritesAndSignOutUntilCompletion() = runTest {
        val repository = FakeRecordsRepository()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val remote = FakeAuthenticationRepository().apply {
            beforeChangePassword = {
                entered.complete(Unit)
                release.await()
            }
        }
        val controller = controller(
            repository = repository,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )
        signIn(controller)
        controller.updateAuthenticationCurrentPassword("current password")
        controller.updateAuthenticationNewPassword("new password")
        controller.updateAuthenticationConfirmPassword("new password")

        val job = launch { controller.changePassword() }
        entered.await()
        assertTrue(controller.state.busy)
        assertTrue(controller.state.authentication.form.passwordChanging)

        controller.startNewRecord()
        controller.updateEditorContent("must wait")
        controller.saveEditor()
        controller.signOut()
        assertTrue(repository.records.isEmpty())
        assertEquals("account-1", controller.state.authentication.account?.id)

        release.complete(Unit)
        job.join()
        assertEquals(SillageNativeFeedback.PasswordChanged, controller.state.feedback)
        assertFalse(controller.state.busy)
    }

    @Test
    fun secureStorageFailureAfterPasswordChangeEndsLocalSession() = runTest {
        val remote = FakeAuthenticationRepository().apply {
            changePasswordError = AuthenticationFailureException(
                AuthenticationFailureReason.SecureStorageUnavailable,
            )
        }
        val controller = controller(
            repository = FakeRecordsRepository(),
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )
        signIn(controller)
        controller.updateAuthenticationCurrentPassword("current password")
        controller.updateAuthenticationNewPassword("new password")
        controller.updateAuthenticationConfirmPassword("new password")

        controller.changePassword()

        assertNull(controller.state.authentication.account)
        assertEquals(
            InstanceAuthenticationFailure.SecureStorageUnavailable,
            controller.state.authentication.failure,
        )
        assertEquals("", controller.state.authentication.form.currentPassword)
        assertFalse(controller.state.busy)
    }

    @Test
    fun failedRemoteSignOutStillClearsMemorySession() = runTest {
        val remote = FakeAuthenticationRepository()
        val controller = controller(
            repository = FakeRecordsRepository(),
            bootstrapRepository = FakeBootstrapRepository(
                result = BootstrapInfo(true, "0.3.1", "abc", "v1", 1),
            ),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(remote),
        )
        controller.updateServerBaseUrl("https://example.test")
        controller.checkServerConnection()
        controller.updateAuthenticationUsername("felix")
        controller.updateAuthenticationPassword("password")
        controller.authenticate()
        remote.signOutError = IllegalStateException("server unavailable")

        controller.signOut()

        assertTrue(remote.localSessionCleared)
        assertNull(controller.state.authentication.account)
        assertEquals(SillageNativeFeedback.SignedOutLocally, controller.state.feedback)
    }

    @Test
    fun reportsUnsavedEditorChangesForHostLifecycleGuards() {
        val controller = controller(FakeRecordsRepository())

        assertFalse(controller.hasUnsavedEditorChanges)
        controller.startNewRecord()
        assertFalse(controller.hasUnsavedEditorChanges)
        controller.updateEditorContent("unsaved")
        assertTrue(controller.hasUnsavedEditorChanges)
        controller.closeEditor()
        assertFalse(controller.hasUnsavedEditorChanges)
    }

    @Test
    fun createsEditsAndDeletesRecordThroughSharedApplicationFlow() = runTest {
        val repository = FakeRecordsRepository()
        val controller = controller(repository)

        controller.startNewRecord()
        controller.updateEditorContent("A portable record")
        controller.saveEditor()

        assertEquals(AppDestination.MemoDetail, controller.state.clientContext.screen)
        assertEquals("A portable record", controller.state.workspace.records.selection.selectedMemo?.content)
        assertEquals(SillageNativeFeedback.RecordSaved, controller.state.feedback)

        controller.editSelectedRecord()
        controller.updateEditorContent("Edited on desktop or iOS")
        controller.saveEditor()
        assertEquals(2L, controller.state.workspace.records.selection.selectedMemo?.version)

        controller.deleteSelectedRecord()
        assertEquals(AppDestination.Memos, controller.state.clientContext.screen)
        assertTrue(controller.state.workspace.records.records.isEmpty())

        controller.selectFilter(MemoListFilter.Deleted)
        assertEquals(1, controller.state.workspace.records.records.size)
    }

    @Test
    fun rejectsImpossibleCalendarDateBeforeStorageMutation() = runTest {
        val repository = FakeRecordsRepository()
        val controller = controller(repository)
        controller.startNewRecord()
        controller.updateEditorContent("body")
        controller.updateEditorEntryDate("2026-02-30")

        controller.saveEditor()

        assertEquals(
            RecordDraftValidationError.InvalidEntryDate,
            controller.state.editorValidationError,
        )
        assertTrue(repository.records.isEmpty())
        assertFalse(controller.state.busy)
    }

    @Test
    fun rejectsEmptyAndOversizedContentBeforeStorageMutation() = runTest {
        val repository = FakeRecordsRepository()
        val controller = controller(repository)
        controller.startNewRecord()

        controller.saveEditor()
        assertEquals(
            RecordDraftValidationError.EmptyContent,
            controller.state.editorValidationError,
        )

        controller.updateEditorContent("a".repeat(MAX_RECORD_CONTENT_UTF8_BYTES + 1))
        assertEquals(null, controller.state.editorValidationError)
        controller.saveEditor()
        assertEquals(
            RecordDraftValidationError.ContentTooLarge,
            controller.state.editorValidationError,
        )
        assertTrue(repository.records.isEmpty())
        assertFalse(controller.state.busy)
    }

    @Test
    fun searchPublishesOnlyMatchingFilteredRecords() {
        val repository = FakeRecordsRepository(
            mutableListOf(
                memo("one", "quiet morning"),
                memo("two", "release notes", archivedAt = "2026-08-03T10:00:00Z"),
            ),
        )
        val controller = controller(repository)

        controller.updateSearchQuery("morning")
        controller.searchRecords()

        assertEquals(listOf("one"), controller.state.workspace.records.search.currentResults()?.map(Memo::id))
        controller.selectFilter(MemoListFilter.Archived)
        assertTrue(controller.state.workspace.records.search.query.isBlank())
        assertEquals(listOf("two"), controller.state.workspace.records.records.map(Memo::id))
    }

    @Test
    fun unreadableStorageDisablesMutationsWithoutPretendingDataIsEmpty() {
        val repository = FakeRecordsRepository(failReads = true)
        val controller = controller(repository)

        assertFalse(controller.state.storageAvailable)
        assertEquals(SillageNativeFeedback.StorageUnavailable, controller.state.feedback)
        controller.startNewRecord()
        assertEquals(AppDestination.Editor, controller.state.clientContext.screen)
    }

    @Test
    fun restoreCanRecoverStorageThatFailedInitialHydration() = runTest {
        val repository = FakeRecordsRepository(failReads = true)
        val controller = controller(repository)
        var exportInvoked = false

        controller.exportBackup {
            exportInvoked = true
            true
        }
        assertFalse(exportInvoked)

        controller.restoreBackup { false }
        assertFalse(controller.state.storageAvailable)
        assertEquals(SillageNativeFeedback.StorageUnavailable, controller.state.feedback)

        repository.records += memo("recovered", "from backup")
        repository.preferences = ClientPreferences(
            themeMode = ClientPreferenceValues.THEME_DARK,
            languageMode = ClientPreferenceValues.LANGUAGE_EN,
        )
        controller.restoreBackup {
            repository.failReads = false
            true
        }

        assertTrue(controller.state.storageAvailable)
        assertEquals(listOf("recovered"), controller.state.workspace.records.records.map(Memo::id))
        assertEquals(ClientPreferenceValues.THEME_DARK, controller.state.appearance.themeMode)
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, controller.state.appearance.languageMode)
        assertEquals(SillageNativeFeedback.BackupRestored, controller.state.feedback)
        assertFalse(controller.state.busy)
    }

    @Test
    fun reportsBackupTransferResultsAndReloadsRestoredData() = runTest {
        val repository = FakeRecordsRepository(mutableListOf(memo("before", "before")))
        val controller = controller(repository)

        controller.exportBackup { true }
        assertEquals(SillageNativeFeedback.BackupExported, controller.state.feedback)

        repository.records.clear()
        repository.records += memo("restored", "from backup")
        repository.preferences = ClientPreferences(
            themeMode = ClientPreferenceValues.THEME_DARK,
            languageMode = ClientPreferenceValues.LANGUAGE_EN,
        )
        controller.navigateToSettings()
        controller.restoreBackup { true }

        assertEquals(AppDestination.Memos, controller.state.clientContext.screen)
        assertEquals(listOf("restored"), controller.state.workspace.records.records.map(Memo::id))
        assertEquals(ClientPreferenceValues.THEME_DARK, controller.state.appearance.themeMode)
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, controller.state.appearance.languageMode)
        assertEquals(SillageNativeFeedback.BackupRestored, controller.state.feedback)
        assertFalse(controller.state.busy)
    }

    @Test
    fun backupFailureDoesNotDisableReadableLocalStorage() = runTest {
        val controller = controller(FakeRecordsRepository())

        controller.exportBackup { error("destination unavailable") }

        assertEquals(SillageNativeFeedback.DataTransferFailed, controller.state.feedback)
        assertTrue(controller.state.storageAvailable)
        assertFalse(controller.state.busy)
    }

    @Test
    fun manualPushRequiresAuthenticationAndPresentsConflicts() = runTest {
        val localMemo = memo("memo-1", "local version").copy(version = 2)
        val local = FakeRecordsRepository(mutableListOf(localMemo))
        val pending = PendingMemoSync(
            memo = localMemo,
            baseVersion = 1,
            mutationId = "mutation-1",
            action = "update",
        )
        val serverMemo = localMemo.copy(
            content = "server version",
            version = 3,
            updatedAt = "2026-08-03T12:00:00Z",
        )
        val conflict = ConflictMemoSync(
            mutationId = pending.mutationId,
            resourceId = localMemo.id,
            clientVersion = localMemo.version,
            serverVersion = serverMemo.version,
            serverMemo = serverMemo,
        )
        val workspace = FakeMemoSyncWorkspace(local, listOf(pending))
        val gateway = FakeMemoSyncGateway(
            result = SyncPushSummary(
                applied = 0,
                conflict = 1,
                rejected = 0,
                conflictMemoSyncs = listOf(conflict),
            ),
        )
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            memoSyncWorkspaceFactory = FakeMemoSyncWorkspaceFactory(workspace),
            memoSyncGatewayFactory = FakeMemoSyncGatewayFactory(gateway),
        )

        controller.syncMemos()
        assertEquals(0, gateway.calls)

        signIn(controller)
        controller.syncMemos()

        assertEquals(1, gateway.calls)
        assertEquals(listOf(pending), gateway.lastPending)
        assertEquals(SillageNativeFeedback.MemoSyncNeedsReview, controller.state.feedback)
        assertEquals(localMemo, controller.state.sync.items.single().localMemo)
        assertEquals(conflict.resourceId, controller.state.sync.items.single().conflict.resourceId)
        assertFalse(controller.state.busy)

        controller.keepLocalSyncConflict(localMemo.id)
        assertEquals(listOf(conflict), workspace.keptConflicts)
        assertTrue(controller.state.sync.items.isEmpty())
        assertEquals(SillageNativeFeedback.MemoSyncConflictResolved, controller.state.feedback)
    }

    @Test
    fun manualPushLocksRecordWritesUntilCompletion() = runTest {
        val localMemo = memo("memo-1", "pending")
        val local = FakeRecordsRepository(mutableListOf(localMemo))
        val pending = PendingMemoSync(localMemo, null, "create-1", "create")
        val applied = AppliedMemoSync(pending.mutationId, localMemo)
        val pushStarted = CompletableDeferred<Unit>()
        val releasePush = CompletableDeferred<Unit>()
        val workspace = FakeMemoSyncWorkspace(local, listOf(pending))
        val gateway = FakeMemoSyncGateway(
            result = SyncPushSummary(
                applied = 1,
                conflict = 0,
                rejected = 0,
                appliedMemoSyncs = listOf(applied),
            ),
            beforeResult = {
                pushStarted.complete(Unit)
                releasePush.await()
            },
        )
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            memoSyncWorkspaceFactory = FakeMemoSyncWorkspaceFactory(workspace),
            memoSyncGatewayFactory = FakeMemoSyncGatewayFactory(gateway),
        )
        signIn(controller)
        controller.startNewRecord()
        controller.updateEditorContent("new local record")

        val push = launch { controller.syncMemos() }
        pushStarted.await()

        assertTrue(controller.state.busy)
        controller.saveEditor()
        assertEquals(listOf(localMemo), local.records)

        releasePush.complete(Unit)
        push.join()
        assertFalse(controller.state.busy)

        controller.saveEditor()
        assertEquals(listOf("pending", "new local record"), local.records.map(Memo::content))
    }

    @Test
    fun appliedManualPushRefreshesCanonicalRecordPresentation() = runTest {
        val localMemo = memo("memo-1", "local")
        val canonical = localMemo.copy(
            content = "canonical",
            updatedAt = "2026-08-03T12:00:00Z",
        )
        val local = FakeRecordsRepository(mutableListOf(localMemo))
        val pending = PendingMemoSync(localMemo, null, "create-1", "create")
        val applied = AppliedMemoSync(pending.mutationId, canonical)
        val workspace = FakeMemoSyncWorkspace(local, listOf(pending))
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            memoSyncWorkspaceFactory = FakeMemoSyncWorkspaceFactory(workspace),
            memoSyncGatewayFactory = FakeMemoSyncGatewayFactory(
                FakeMemoSyncGateway(
                    SyncPushSummary(
                        applied = 1,
                        conflict = 0,
                        rejected = 0,
                        appliedMemoSyncs = listOf(applied),
                    ),
                ),
            ),
        )
        signIn(controller)

        controller.syncMemos()

        assertEquals(listOf(applied), workspace.appliedMemos)
        assertEquals(canonical, local.records.single())
        assertEquals(canonical, controller.state.workspace.records.records.single())
        assertEquals(SillageNativeFeedback.MemoSyncCompleted, controller.state.feedback)
    }

    @Test
    fun failedPullStillPresentsTheCompletedPushPhase() = runTest {
        val localMemo = memo("memo-1", "local")
        val canonical = localMemo.copy(
            content = "canonical",
            updatedAt = "2026-08-03T12:00:00Z",
        )
        val local = FakeRecordsRepository(mutableListOf(localMemo))
        val pending = PendingMemoSync(localMemo, null, "create-1", "create")
        val applied = AppliedMemoSync(pending.mutationId, canonical)
        val workspace = FakeMemoSyncWorkspace(local, listOf(pending))
        val gateway = FakeMemoSyncGateway(
            result = SyncPushSummary(
                applied = 1,
                conflict = 0,
                rejected = 0,
                appliedMemoSyncs = listOf(applied),
            ),
            pullError = IllegalStateException("pull unavailable"),
        )
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            memoSyncWorkspaceFactory = FakeMemoSyncWorkspaceFactory(workspace),
            memoSyncGatewayFactory = FakeMemoSyncGatewayFactory(gateway),
        )
        signIn(controller)

        controller.syncMemos()

        assertEquals(listOf(applied), workspace.appliedMemos)
        assertEquals(canonical, local.records.single())
        assertEquals(canonical, controller.state.workspace.records.records.single())
        assertEquals(SillageNativeFeedback.MemoSyncFailed, controller.state.feedback)
        assertFalse(controller.state.busy)
    }

    @Test
    fun manualSyncPullsServerOnlyRecordsWhenOutboxIsEmpty() = runTest {
        val pulled = memo("server-record", "from server")
        val local = FakeRecordsRepository()
        val workspace = FakeMemoSyncWorkspace(local, emptyList())
        val gateway = FakeMemoSyncGateway(pulledMemos = listOf(pulled))
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            memoSyncWorkspaceFactory = FakeMemoSyncWorkspaceFactory(workspace),
            memoSyncGatewayFactory = FakeMemoSyncGatewayFactory(gateway),
        )
        signIn(controller)

        controller.syncMemos()

        assertEquals(0, gateway.calls)
        assertEquals(1, gateway.pullCalls)
        assertEquals(listOf(pulled), local.records)
        assertEquals(listOf(pulled), controller.state.workspace.records.records)
        assertEquals(SillageNativeFeedback.MemoSyncCompleted, controller.state.feedback)
    }

    @Test
    fun askRequiresAuthenticationAndFiltersInactiveConversations() = runTest {
        val local = FakeRecordsRepository()
        val ask = FakeAskService().apply {
            conversations += askConversation("active")
            conversations += askConversation("archived", archivedAt = "2026-08-03T12:00:00Z")
        }
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            askClientFactory = ask,
        )

        assertTrue(controller.state.askSupported)
        controller.navigateToAsk()
        assertEquals(AppDestination.Memos, controller.state.clientContext.screen)
        assertEquals(
            SillageNativeAskFailure.AuthenticationRequired,
            controller.state.askFailure,
        )

        signIn(controller)
        controller.navigateToAsk()
        controller.loadAskConversations()

        assertEquals(AppDestination.Ask, controller.state.clientContext.screen)
        assertEquals("https://example.test", ask.requestedBaseUrl)
        assertEquals(listOf("active"), controller.state.workspace.ask.conversations.map { it.id })
        assertNull(controller.state.askFailure)
    }

    @Test
    fun lateAskConversationLoadCannotApplyAfterLeavingAsk() = runTest {
        val local = FakeRecordsRepository()
        val conversation = askConversation("conversation-1", headMessageId = "answer-1")
        val answer = askMessage(
            id = "answer-1",
            conversationId = conversation.id,
            role = "assistant",
            content = "late answer",
        )
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        val ask = FakeAskService().apply {
            conversations += conversation
            messages[conversation.id] = mutableListOf(answer)
        }
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            askClientFactory = ask,
        )
        signIn(controller)
        controller.navigateToAsk()
        controller.loadAskConversations()
        ask.beforeListMessages = {
            loadStarted.complete(Unit)
            releaseLoad.await()
        }

        val load = launch { controller.selectAskConversation(conversation.id) }
        loadStarted.await()
        assertTrue(controller.state.workspace.ask.loading)

        controller.navigateToRecords()
        releaseLoad.complete(Unit)
        load.join()

        assertEquals(AppDestination.Memos, controller.state.clientContext.screen)
        assertFalse(controller.state.workspace.ask.loading)
        assertTrue(controller.state.workspace.ask.messages.isEmpty())
    }

    @Test
    fun successfulAskStreamCreatesConversationAndAppliesCanonicalSnapshot() = runTest {
        val local = FakeRecordsRepository()
        val ask = FakeAskService()
        ask.onStream = { command, onEvent ->
            val user = askMessage(
                id = "question-1",
                conversationId = command.conversationId,
                role = "user",
                content = command.content,
            )
            val answer = askMessage(
                id = "answer-1",
                conversationId = command.conversationId,
                role = "assistant",
                content = "Canonical answer",
                parentId = user.id,
            )
            onEvent(AskAnswerStreamEvent.Started(user, regenerating = false))
            onEvent(AskAnswerStreamEvent.Delta("Canonical "))
            onEvent(AskAnswerStreamEvent.Delta("answer"))
            ask.messages[command.conversationId] = mutableListOf(user, answer)
            ask.replaceHead(command.conversationId, answer.id)
        }
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            askClientFactory = ask,
        )
        signIn(controller)
        controller.navigateToAsk()
        controller.updateAskQuestion("What changed?")

        controller.sendAskQuestion()

        assertEquals("What changed?", ask.lastStreamCommand?.content)
        assertEquals("recent_30_days", ask.lastStreamCommand?.contextScope)
        assertEquals("records", ask.lastStreamCommand?.sourceKind)
        assertEquals("", controller.state.workspace.ask.question)
        assertEquals(
            listOf("question-1", "answer-1"),
            controller.state.workspace.ask.messages.map(AskMessage::id),
        )
        assertEquals("answer-1", controller.state.workspace.ask.headMessageId)
        assertEquals(1, controller.state.workspace.ask.stream.completionEventId)
        assertFalse(controller.state.workspace.ask.sending)
        assertNull(controller.state.askFailure)
    }

    @Test
    fun failedAskStreamRetainsQuestionAndUnlocksComposer() = runTest {
        val ask = FakeAskService().apply {
            streamError = IllegalStateException("offline")
        }
        val controller = controller(
            repository = FakeRecordsRepository(),
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            askClientFactory = ask,
        )
        signIn(controller)
        controller.navigateToAsk()
        controller.updateAskQuestion("Keep this draft")

        controller.sendAskQuestion()

        assertEquals("Keep this draft", controller.state.workspace.ask.question)
        assertEquals(SillageNativeAskFailure.SendFailed, controller.state.askFailure)
        assertFalse(controller.state.workspace.ask.sending)
        assertEquals(0, controller.state.workspace.ask.stream.completionEventId)
    }

    @Test
    fun cancellingAskStreamReconcilesStateWithoutDroppingQuestion() = runTest {
        val streamStarted = CompletableDeferred<Unit>()
        val releaseStream = CompletableDeferred<Unit>()
        val ask = FakeAskService().apply {
            onStream = { command, onEvent ->
                onEvent(
                    AskAnswerStreamEvent.Started(
                        askMessage(
                            id = "question-1",
                            conversationId = command.conversationId,
                            role = "user",
                            content = command.content,
                        ),
                        regenerating = false,
                    ),
                )
                onEvent(AskAnswerStreamEvent.Delta("partial"))
                streamStarted.complete(Unit)
                releaseStream.await()
            }
        }
        val controller = controller(
            repository = FakeRecordsRepository(),
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            askClientFactory = ask,
        )
        signIn(controller)
        controller.navigateToAsk()
        controller.updateAskQuestion("Do not lose this")

        val stream = launch { controller.sendAskQuestion() }
        streamStarted.await()
        controller.stopAskStreaming()
        stream.cancelAndJoin()

        assertEquals("Do not lose this", controller.state.workspace.ask.question)
        assertEquals(SillageNativeFeedback.AskGenerationStopped, controller.state.feedback)
        assertFalse(controller.state.workspace.ask.sending)
        assertNull(controller.state.askFailure)
    }

    @Test
    fun expiredAskSessionClearsAuthenticationAndAskWorkspace() = runTest {
        val ask = FakeAskService().apply {
            streamError = AuthenticationFailureException(AuthenticationFailureReason.SessionExpired)
        }
        val controller = controller(
            repository = FakeRecordsRepository(),
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            askClientFactory = ask,
        )
        signIn(controller)
        controller.navigateToAsk()
        controller.updateAskQuestion("Will expire")

        controller.sendAskQuestion()

        assertNull(controller.state.authentication.account)
        assertEquals(AppDestination.Memos, controller.state.clientContext.screen)
        assertEquals("", controller.state.workspace.ask.question)
        assertTrue(controller.state.workspace.ask.conversations.isEmpty())
        assertEquals(
            SillageNativeAskFailure.AuthenticationRequired,
            controller.state.askFailure,
        )
    }

    @Test
    fun failedAskVariantRollsBackHeadSelection() = runTest {
        val conversation = askConversation("conversation-1", headMessageId = "answer-1")
        val user = askMessage(
            id = "question-1",
            conversationId = conversation.id,
            role = "user",
            content = "Question",
        )
        val first = askMessage(
            id = "answer-1",
            conversationId = conversation.id,
            role = "assistant",
            content = "First",
            parentId = user.id,
        )
        val second = askMessage(
            id = "answer-2",
            conversationId = conversation.id,
            role = "assistant",
            content = "Second",
            parentId = user.id,
        )
        val ask = FakeAskService().apply {
            conversations += conversation
            messages[conversation.id] = mutableListOf(user, first, second)
            setHeadError = IllegalStateException("rejected")
        }
        val controller = controller(
            repository = FakeRecordsRepository(),
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            askClientFactory = ask,
        )
        signIn(controller)
        controller.navigateToAsk()
        controller.loadAskConversations()
        controller.selectAskConversation(conversation.id)

        controller.selectAskVariant(second.id)

        assertEquals(first.id, controller.state.workspace.ask.headMessageId)
        assertEquals(SillageNativeAskFailure.VariantFailed, controller.state.askFailure)
        assertFalse(controller.state.workspace.ask.variantLoading)
    }

    @Test
    fun askSourceAndSavedAnswerPreserveAskBackNavigation() = runTest {
        val source = memo("memo-source", "Source record")
        val local = FakeRecordsRepository(mutableListOf(source))
        val conversation = askConversation("conversation-1", headMessageId = "answer-1")
        val answer = askMessage(
            id = "answer-1",
            conversationId = conversation.id,
            role = "assistant",
            content = "Reusable answer",
        )
        val ask = FakeAskService().apply {
            conversations += conversation
            messages[conversation.id] = mutableListOf(answer)
        }
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            askClientFactory = ask,
        )
        signIn(controller)
        controller.navigateToAsk()
        controller.loadAskConversations()
        controller.selectAskConversation(conversation.id)

        controller.openAskSource(source.id)
        assertEquals(AppDestination.MemoDetail, controller.state.clientContext.screen)
        assertEquals(source.id, controller.state.workspace.records.selection.selectedMemo?.id)
        controller.navigateBackFromRecordDetail()
        assertEquals(AppDestination.Ask, controller.state.clientContext.screen)

        controller.saveAskAnswerAsRecord(answer)

        assertEquals(AppDestination.MemoDetail, controller.state.clientContext.screen)
        assertEquals("Reusable answer", local.records.last().content)
        assertEquals(
            "Reusable answer",
            controller.state.workspace.records.selection.selectedMemo?.content,
        )
        assertEquals(SillageNativeFeedback.AskAnswerSaved, controller.state.feedback)
        assertFalse(controller.state.busy)
        controller.navigateBackFromRecordDetail()
        assertEquals(AppDestination.Ask, controller.state.clientContext.screen)
    }

    @Test
    fun expiredSyncSessionReturnsControllerToSignedOutState() = runTest {
        val localMemo = memo("memo-1", "pending")
        val local = FakeRecordsRepository(mutableListOf(localMemo))
        val gateway = FakeMemoSyncGateway(
            error = AuthenticationFailureException(AuthenticationFailureReason.SessionExpired),
        )
        val controller = controller(
            repository = local,
            bootstrapRepository = initializedBootstrapRepository(),
            authenticationRepositoryFactory = FakeAuthenticationRepositoryFactory(),
            memoSyncWorkspaceFactory = FakeMemoSyncWorkspaceFactory(
                FakeMemoSyncWorkspace(
                    local,
                    listOf(PendingMemoSync(localMemo, null, "create-1", "create")),
                ),
            ),
            memoSyncGatewayFactory = FakeMemoSyncGatewayFactory(gateway),
        )
        signIn(controller)

        controller.syncMemos()

        assertNull(controller.state.authentication.account)
        assertEquals(SillageNativeFeedback.MemoSyncSessionExpired, controller.state.feedback)
        assertTrue(controller.state.sync.items.isEmpty())
        assertFalse(controller.state.busy)
    }

}

private fun initializedBootstrapRepository() = FakeBootstrapRepository(
    result = BootstrapInfo(true, "0.3.1", "abc", "v1", 1),
)

private suspend fun signIn(
    controller: SillageNativeController,
    dismissFeedback: Boolean = true,
) {
    controller.updateServerBaseUrl("example.test")
    controller.checkServerConnection()
    controller.updateAuthenticationUsername("felix")
    controller.updateAuthenticationPassword("correct horse battery staple")
    controller.authenticate()
    if (dismissFeedback) {
        controller.dismissFeedback()
    }
}

private fun controller(
    repository: FakeRecordsRepository,
    bootstrapRepository: InstanceBootstrapRepository = FakeBootstrapRepository(),
    authenticationRepositoryFactory: InstanceAuthenticationRepositoryFactory =
        FakeAuthenticationRepositoryFactory(),
    memoSyncWorkspaceFactory: MemoSyncWorkspaceFactory? = null,
    memoSyncGatewayFactory: MemoSyncGatewayFactory? = null,
    askClientFactory: AskClientFactory? = null,
) = SillageNativeController(
    recordsRepository = repository,
    recordWriteRepository = repository,
    recordLifecycleRepository = repository,
    preferencesRepository = repository,
    bootstrapRepository = bootstrapRepository,
    authenticationRepositoryFactory = authenticationRepositoryFactory,
    todayProvider = { "2026-08-03" },
    memoSyncWorkspaceFactory = memoSyncWorkspaceFactory,
    memoSyncGatewayFactory = memoSyncGatewayFactory,
    askClientFactory = askClientFactory,
)

private class FakeAskService : AskRepository, AskAnswerStreamer, AskClientFactory {
    val conversations = mutableListOf<AskConversation>()
    val messages = mutableMapOf<String, MutableList<AskMessage>>()
    var requestedBaseUrl: String? = null
    var beforeListMessages: suspend () -> Unit = {}
    var streamError: Throwable? = null
    var setHeadError: Throwable? = null
    var lastStreamCommand: StreamAskAnswerCommand? = null
    var onStream: suspend (
        command: StreamAskAnswerCommand,
        onEvent: (AskAnswerStreamEvent) -> Unit,
    ) -> Unit = { _, _ -> }
    private var nextConversationId = 0

    override fun createAskClient(baseUrl: String): AskClient {
        requestedBaseUrl = baseUrl
        return AskClient(repository = this, answerStreamer = this)
    }

    override suspend fun listConversations(): List<AskConversation> = conversations.toList()

    override suspend fun listMessages(conversationId: String): List<AskMessage> {
        beforeListMessages()
        return messages[conversationId].orEmpty().toList()
    }

    override suspend fun createConversation(contextScope: String): AskConversation {
        val created = askConversation(
            id = "created-${++nextConversationId}",
            contextScope = contextScope,
        )
        conversations.add(0, created)
        return created
    }

    override suspend fun setHead(conversationId: String, messageId: String) {
        setHeadError?.let { throw it }
        replaceHead(conversationId, messageId)
    }

    override suspend fun stream(
        command: StreamAskAnswerCommand,
        onEvent: (AskAnswerStreamEvent) -> Unit,
    ) {
        lastStreamCommand = command
        streamError?.let { throw it }
        onStream(command, onEvent)
    }

    fun replaceHead(conversationId: String, messageId: String?) {
        val index = conversations.indexOfFirst { it.id == conversationId }
        if (index >= 0) {
            conversations[index] = conversations[index].copy(headMessageId = messageId)
        }
    }
}

private class FakeBootstrapRepository(
    private val result: BootstrapInfo = BootstrapInfo(
        initialized = false,
        serverVersion = "",
        serverRevision = "",
        apiVersion = "",
        minimumAndroidVersionCode = 0,
    ),
    private val error: Throwable? = null,
) : InstanceBootstrapRepository {
    var requestedBaseUrl: String? = null

    override suspend fun load(baseUrl: String): BootstrapInfo {
        requestedBaseUrl = baseUrl
        error?.let { throw it }
        return result
    }
}

private class FakeAuthenticationRepositoryFactory(
    private val repository: FakeAuthenticationRepository = FakeAuthenticationRepository(),
) : InstanceAuthenticationRepositoryFactory {
    override fun create(baseUrl: String): InstanceAuthenticationRepository {
        repository.baseUrl = baseUrl
        return repository
    }
}

private class FakeAuthenticationRepository(
    val session: AuthSession = AuthSession(
        account = Account("account-1", "felix", "Felix"),
        accessToken = "access-token",
        expiresAt = "2026-08-03T12:00:00Z",
    ),
) : InstanceAuthenticationRepository {
    var baseUrl: String = ""
    var signedOut: Boolean = false
    var localSessionCleared: Boolean = false
        var authenticationError: Throwable? = null
        var changePasswordError: Throwable? = null
        var signOutError: Throwable? = null
    var localSessionClearError: Throwable? = null
    var restoredSession: AuthSession? = null
    var restoreError: Throwable? = null
        var restoreCalls: Int = 0
        var changePasswordCalls: Int = 0
        var lastChangePasswordCommand: ChangePasswordCommand? = null
        var changePasswordSession: AuthSession = session
        var beforeChangePassword: suspend () -> Unit = {}

    override suspend fun restore(): AuthSession? {
        restoreCalls += 1
        restoreError?.let { throw it }
        return restoredSession
    }

    override suspend fun initialize(command: InitializeAccountCommand): AuthSession {
        authenticationError?.let { throw it }
        return session
    }

    override suspend fun signIn(command: SignInCommand): AuthSession {
        authenticationError?.let { throw it }
        return session
    }

    override suspend fun currentAccount(): Account = session.account

        override suspend fun changePassword(command: ChangePasswordCommand): AuthSession {
            changePasswordCalls += 1
            lastChangePasswordCommand = command
            beforeChangePassword()
            changePasswordError?.let { throw it }
            return changePasswordSession
        }

    override fun captureSession(): CapturedSignOutSession {
        return object : CapturedSignOutSession {
            override suspend fun signOutRemote() {
                signOutError?.let { throw it }
                signedOut = true
                localSessionCleared = true
            }

            override fun clearLocalSession(): Boolean {
                localSessionClearError?.let { throw it }
                signedOut = true
                localSessionCleared = true
                return true
            }
        }
    }
}

private class FakeMemoSyncWorkspaceFactory(
    private val workspace: FakeMemoSyncWorkspace,
) : MemoSyncWorkspaceFactory {
    override fun createMemoSyncWorkspace(baseUrl: String): MemoSyncWorkspace = workspace
}

private class FakeMemoSyncGatewayFactory(
    private val gateway: FakeMemoSyncGateway,
) : MemoSyncGatewayFactory {
    override fun createMemoSyncGateway(baseUrl: String): MemoSyncGateway = gateway
}

private class FakeMemoSyncWorkspace(
    private val recordsRepository: FakeRecordsRepository,
    private val pending: List<PendingMemoSync>,
) : MemoSyncWorkspace {
    var appliedMemos: List<AppliedMemoSync> = emptyList()
    val keptConflicts = mutableListOf<ConflictMemoSync>()
    val takenConflicts = mutableListOf<ConflictMemoSync>()

    override suspend fun pendingMemos(): List<PendingMemoSync> = pending

        override suspend fun applySyncedMemos(applied: List<AppliedMemoSync>) {
            appliedMemos = applied
            applied.forEach { item ->
            val index = recordsRepository.records.indexOfFirst { it.id == item.memo.id }
            if (index >= 0) {
                recordsRepository.records[index] = item.memo
            } else {
                recordsRepository.records += item.memo
                }
            }
        }

        override suspend fun mergePulledMemos(memos: List<Memo>): Int {
            var changed = 0
            memos.forEach { memo ->
                val index = recordsRepository.records.indexOfFirst { it.id == memo.id }
                if (index < 0) {
                    recordsRepository.records += memo
                    changed += 1
                } else if (recordsRepository.records[index] != memo) {
                    recordsRepository.records[index] = memo
                    changed += 1
                }
            }
            return changed
        }

    override suspend fun keepLocal(conflict: ConflictMemoSync) {
        keptConflicts += conflict
    }

    override suspend fun takeServer(conflict: ConflictMemoSync) {
        takenConflicts += conflict
        val serverMemo = conflict.serverMemo ?: return
        val index = recordsRepository.records.indexOfFirst { it.id == serverMemo.id }
        if (index >= 0) {
            recordsRepository.records[index] = serverMemo
        } else {
            recordsRepository.records += serverMemo
        }
    }

    override fun localMemo(resourceId: String): Memo? {
        return recordsRepository.records.firstOrNull { it.id == resourceId }
    }
}

    private class FakeMemoSyncGateway(
        private val result: SyncPushSummary = SyncPushSummary(0, 0, 0),
        private val error: Throwable? = null,
        private val beforeResult: suspend () -> Unit = {},
        private val pulledMemos: List<Memo> = emptyList(),
        private val pullError: Throwable? = null,
    ) : MemoSyncGateway {
        var calls: Int = 0
        var pullCalls: Int = 0
        var lastPending: List<PendingMemoSync> = emptyList()

    override suspend fun pushMemos(pending: List<PendingMemoSync>): SyncPushSummary {
        calls += 1
        lastPending = pending
        beforeResult()
            error?.let { throw it }
            return result
        }

        override suspend fun pullMemos(): List<Memo> {
            pullCalls += 1
            pullError?.let { throw it }
            error?.let { throw it }
            return pulledMemos
        }
    }

private class FakeRecordsRepository(
    val records: MutableList<Memo> = mutableListOf(),
    var failReads: Boolean = false,
) : RecordsRepository,
    RecordWriteRepository,
    RecordLifecycleRepository,
    ClientPreferencesRepository {
    var preferences = ClientPreferences()
    private var nextId = records.size

    override fun listRecords(): List<Memo> {
        if (failReads) error("unreadable")
        return records.toList()
    }

    override fun loadPreferences(): ClientPreferences {
        if (failReads) error("unreadable")
        return preferences
    }

    override fun savePreferences(preferences: ClientPreferences) {
        this.preferences = preferences
    }

    override suspend fun createRecord(draft: RecordDraft): Memo {
        val created = memo(
            id = "created-${++nextId}",
            content = draft.content,
            entryDate = draft.entryDate,
        )
        records += created
        return created
    }

    override suspend fun updateRecord(memo: Memo, draft: RecordDraft): Memo = replace(
        memo.copy(
            content = draft.content,
            entryDate = draft.entryDate,
            version = memo.version + 1,
        ),
    )

    override suspend fun setRecordFavorited(memo: Memo, favorited: Boolean): Memo = replace(
        memo.copy(
            version = memo.version + 1,
            favoritedAt = "2026-08-03T11:00:00Z".takeIf { favorited },
        ),
    )

    override suspend fun setRecordArchived(memo: Memo, archived: Boolean): Memo = replace(
        memo.copy(
            version = memo.version + 1,
            archivedAt = "2026-08-03T11:00:00Z".takeIf { archived },
        ),
    )

    override suspend fun deleteRecord(memo: Memo): Memo = replace(
        memo.copy(version = memo.version + 1, deletedAt = "2026-08-03T11:00:00Z"),
    )

    override suspend fun restoreRecord(memo: Memo): Memo = replace(
        memo.copy(version = memo.version + 1, deletedAt = null),
    )

    override suspend fun purgeRecord(memo: Memo): Memo = replace(
        memo.copy(version = memo.version + 1, purgedAt = "2026-08-03T11:00:00Z"),
    )

    private fun replace(memo: Memo): Memo {
        val index = records.indexOfFirst { it.id == memo.id }
        records[index] = memo
        return memo
    }
}

private fun memo(
    id: String,
    content: String,
    entryDate: String = "2026-08-03",
    archivedAt: String? = null,
) = Memo(
    id = id,
    content = content,
    entryDate = entryDate,
    version = 1,
    createdAt = "2026-08-03T10:00:00Z",
    updatedAt = "2026-08-03T10:00:00Z",
    favoritedAt = null,
    archivedAt = archivedAt,
    deletedAt = null,
)

private fun askConversation(
    id: String,
    headMessageId: String? = null,
    contextScope: String = "recent_30_days",
    archivedAt: String? = null,
) = AskConversation(
    id = id,
    title = "Conversation $id",
    status = "active",
    contextScope = contextScope,
    headMessageId = headMessageId,
    pinnedAt = null,
    archivedAt = archivedAt,
    createdAt = "2026-08-03T10:00:00Z",
    updatedAt = "2026-08-03T10:00:00Z",
    deletedAt = null,
)

private fun askMessage(
    id: String,
    conversationId: String,
    role: String,
    content: String,
    parentId: String? = null,
    sourceRefs: List<AskSourceRef> = emptyList(),
) = AskMessage(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    parentId = parentId,
    forkOfId = null,
    status = "complete",
    sourceRefs = sourceRefs,
    model = "test-model",
    promptVersion = "test-v1",
    createdAt = "2026-08-03T10:00:00Z",
    updatedAt = "2026-08-03T10:00:00Z",
    deletedAt = null,
)
