package com.miofelix.sillage.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miofelix.sillage.data.Account
import com.miofelix.sillage.data.Memo
import com.miofelix.sillage.data.SessionStore
import com.miofelix.sillage.data.SillageApi
import com.miofelix.sillage.data.activeMemos
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SillageViewModel(context: Context) : ViewModel() {
    private val sessionStore = SessionStore(context.applicationContext)
    private val api = SillageApi(sessionStore)
    private val _state = MutableStateFlow(
        SillageUiState(
            screen = Screen.Loading,
            baseUrl = sessionStore.baseUrl(),
            account = sessionStore.account(),
        ),
    )

    val state: StateFlow<SillageUiState> = _state.asStateFlow()

    init {
        connect()
    }

    fun updateBaseUrl(value: String) {
        _state.update { it.copy(baseUrl = value) }
    }

    fun saveServer() {
        sessionStore.saveBaseUrl(state.value.baseUrl)
        _state.update {
            it.copy(
                baseUrl = sessionStore.baseUrl(),
                account = null,
                memos = emptyList(),
                searchQuery = "",
                searchResults = null,
                searching = false,
                error = null,
                notice = null,
            )
        }
        connect()
    }

    fun openServerSettings() {
        _state.update { it.copy(screen = Screen.Server, error = null, notice = null) }
    }

    fun connect() {
        launchBusy {
            val initialized = api.bootstrap(state.value.baseUrl)
            val token = sessionStore.accessToken()
            val account = sessionStore.account()
            if (!initialized) {
                _state.update { it.copy(screen = Screen.Initialize, initialized = false, account = null) }
                return@launchBusy
            }
            if (token.isNullOrBlank() || account == null) {
                _state.update { it.copy(screen = Screen.Login, initialized = true, account = null) }
                return@launchBusy
            }
            val verified = api.me()
            _state.update { it.copy(screen = Screen.Memos, initialized = true, account = verified) }
            refreshMemos()
        }
    }

    fun updateUsername(value: String) = _state.update { it.copy(username = value) }

    fun updateDisplayName(value: String) = _state.update { it.copy(displayName = value) }

    fun updatePassword(value: String) = _state.update { it.copy(password = value) }

    fun initialize() {
        val current = state.value
        launchBusy {
            val session = api.initialize(current.username, current.displayName, current.password)
            _state.update {
                it.copy(
                    account = session.account,
                    username = "",
                    displayName = "",
                    password = "",
                    screen = Screen.Memos,
                    initialized = true,
                )
            }
            refreshMemos()
        }
    }

    fun signIn() {
        val current = state.value
        launchBusy {
            val session = api.signIn(current.username, current.password)
            _state.update {
                it.copy(
                    account = session.account,
                    username = "",
                    password = "",
                    screen = Screen.Memos,
                    initialized = true,
                )
            }
            refreshMemos()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { api.signOut() }
            sessionStore.clearSession()
            _state.update {
                it.copy(
                    account = null,
                    memos = emptyList(),
                    selectedMemo = null,
                    searchQuery = "",
                    searchResults = null,
                    searching = false,
                    screen = Screen.Login,
                    notice = "已退出登录",
                    error = null,
                )
            }
        }
    }

    fun refreshMemos() {
        viewModelScope.launch {
            runCatching { api.listMemos() }
                .onSuccess { memos ->
                    _state.update {
                        it.copy(
                            memos = activeMemos(memos),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
        }
    }

    fun startNewMemo() {
        _state.update {
            it.copy(
                screen = Screen.Editor,
                selectedMemo = null,
                draftContent = "",
                draftEntryDate = LocalDate.now().toString(),
                error = null,
                notice = null,
            )
        }
    }

    fun editMemo(memo: Memo) {
        _state.update {
            it.copy(
                screen = Screen.Editor,
                selectedMemo = memo,
                draftContent = memo.content,
                draftEntryDate = memo.entryDate,
                error = null,
                notice = null,
            )
        }
    }

    fun updateDraftContent(value: String) = _state.update { it.copy(draftContent = value) }

    fun updateDraftEntryDate(value: String) = _state.update { it.copy(draftEntryDate = value) }

    fun updateSearchQuery(value: String) {
        _state.update {
            it.copy(
                searchQuery = value,
                searchResults = if (value.isBlank()) null else it.searchResults,
                searching = if (value.isBlank()) false else it.searching,
            )
        }
    }

    fun searchMemos() {
        val query = state.value.searchQuery.trim()
        if (query.isBlank()) {
            clearSearch()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(searching = true, error = null, notice = null) }
            runCatching { api.searchMemos(query) }
                .onSuccess { memos ->
                    _state.update {
                        it.copy(
                            searchResults = activeMemos(memos),
                            searching = false,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            searchResults = emptyList(),
                            searching = false,
                            error = error.readableMessage(),
                        )
                    }
                }
        }
    }

    fun clearSearch() {
        _state.update {
            it.copy(
                searchQuery = "",
                searchResults = null,
                searching = false,
                error = null,
            )
        }
    }

    fun saveMemo() {
        val current = state.value
        if (current.draftContent.isBlank()) {
            _state.update { it.copy(error = "记录内容不能为空") }
            return
        }
        launchBusy {
            if (current.selectedMemo == null) {
                api.createMemo(current.draftContent.trim(), current.draftEntryDate)
            } else {
                api.updateMemo(current.selectedMemo, current.draftContent.trim(), current.draftEntryDate)
            }
            _state.update {
                it.copy(
                    screen = Screen.Memos,
                    selectedMemo = null,
                    draftContent = "",
                    searchQuery = "",
                    searchResults = null,
                    notice = "已保存",
                )
            }
            refreshMemos()
        }
    }

    fun deleteSelectedMemo() {
        val memo = state.value.selectedMemo ?: return
        launchBusy {
            api.deleteMemo(memo)
            _state.update {
                it.copy(
                    screen = Screen.Memos,
                    selectedMemo = null,
                    draftContent = "",
                    searchQuery = "",
                    searchResults = null,
                    notice = "已删除",
                )
            }
            refreshMemos()
        }
    }

    fun toggleSelectedMemoPinned() {
        val memo = state.value.selectedMemo ?: return
        launchBusy {
            val updated = api.setMemoPinned(memo, memo.pinnedAt == null)
            applyMemo(updated)
            _state.update {
                it.copy(notice = if (updated.pinnedAt == null) "已取消置顶" else "已置顶")
            }
        }
    }

    fun toggleSelectedMemoArchived() {
        val memo = state.value.selectedMemo ?: return
        launchBusy {
            val updated = api.setMemoArchived(memo, memo.archivedAt == null)
            applyMemo(updated)
            _state.update {
                it.copy(notice = if (updated.archivedAt == null) "已取消归档" else "已归档")
            }
        }
    }

    fun closeEditor() {
        _state.update {
            it.copy(
                screen = Screen.Memos,
                selectedMemo = null,
                draftContent = "",
                error = null,
            )
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            runCatching { block() }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            screen = if (it.screen == Screen.Loading) Screen.Server else it.screen,
                            error = error.readableMessage(),
                        )
                    }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    private fun applyMemo(memo: Memo) {
        _state.update { current ->
            val cached = activeMemos(current.memos.filter { it.id != memo.id } + memo)
            val searched = current.searchResults?.let { results ->
                activeMemos(results.filter { it.id != memo.id } + memo)
            }
            current.copy(
                memos = cached,
                searchResults = searched,
                selectedMemo = if (current.selectedMemo?.id == memo.id) memo else current.selectedMemo,
            )
        }
    }

    private fun Throwable.readableMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: "操作失败"
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SillageViewModel(context) as T
        }
    }
}

data class SillageUiState(
    val screen: Screen,
    val baseUrl: String,
    val initialized: Boolean? = null,
    val account: Account? = null,
    val memos: List<Memo> = emptyList(),
    val selectedMemo: Memo? = null,
    val draftContent: String = "",
    val draftEntryDate: String = LocalDate.now().toString(),
    val searchQuery: String = "",
    val searchResults: List<Memo>? = null,
    val searching: Boolean = false,
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

enum class Screen {
    Loading,
    Server,
    Initialize,
    Login,
    Memos,
    Editor,
}
