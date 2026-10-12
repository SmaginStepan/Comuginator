package com.an0obis.comuginator.ui.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.ChildHomeNodeDto
import com.an0obis.comuginator.api.CreateChildHomeNodeRequest
import com.an0obis.comuginator.api.InstallPresetRequest
import com.an0obis.comuginator.api.UpdateChildHomeNodeRequest
import com.an0obis.comuginator.storage.OfflineCache
import com.an0obis.comuginator.storage.SECTION_CONVERSATION
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.childhome.PathEntry
import com.an0obis.comuginator.ui.childhome.displayLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the user is in the middle of while the card picker screen is open. */
sealed class PendingEdit {
    /** Adding a new card at a cell. */
    data class NewCard(val row: Int, val col: Int) : PendingEdit()
    /** Replacing the picture of an existing card. */
    data class ChangePicture(val nodeId: String) : PendingEdit()
}

/**
 * State and server calls of the conversation library editor. Edits always go
 * to the server and the level is reloaded afterwards, so what is shown is what
 * the other devices will get.
 */
class ConversationEditorViewModel(application: Application) : AndroidViewModel(application) {

    sealed class Event {
        data class ShowToast(val message: String) : Event()
    }

    val store = SessionStore(application)

    private val _page = MutableStateFlow(BoardPage.EMPTY)
    val page: StateFlow<BoardPage> = _page.asStateFlow()

    private val _path = MutableStateFlow(listOf(PathEntry(parentId = null, title = "")))
    val path: StateFlow<List<PathEntry>> = _path.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    var homeCols: Int = 0
        private set
    var homeRows: Int = 0
        private set

    /** Survives rotation while the picker screen is open. */
    var pending: PendingEdit? = null

    /** Raw nodes of the level on screen (to find a card by id). */
    private var nodes: List<ChildHomeNodeDto> = emptyList()

    /** True when the home page has no cards at all (so the starter can be installed without replacing). */
    var libraryEmpty: Boolean = true
        private set

    private val currentParentId: String?
        get() = _path.value.lastOrNull()?.parentId

    private val auth: String get() = store.authHeaderOrThrow()

    fun nodeById(id: String): ChildHomeNodeDto? = nodes.firstOrNull { it.id == id }

    // ── Navigation ───────────────────────────────────────────────────────────

    fun openFolder(cell: ConvCell) {
        _path.value = _path.value + PathEntry(parentId = cell.id, title = cell.label)
        clearLevel()
        loadNodes()
    }

    fun goUp(): Boolean {
        if (_path.value.size <= 1) return false
        _path.value = _path.value.dropLast(1)
        clearLevel()
        loadNodes()
        return true
    }

    fun goHome() {
        if (_path.value.size <= 1) return
        _path.value = _path.value.take(1)
        clearLevel()
        loadNodes()
    }

    private fun clearLevel() {
        _page.value = BoardPage.EMPTY
        _loaded.value = false
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    fun loadNodes() {
        val parentId = currentParentId
        val familyId = store.familyId
        _isLoading.value = true
        viewModelScope.launch {
            val cache = OfflineCache(getApplication())
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getChildHomeNodes(auth = auth, parentId = parentId, section = SECTION_CONVERSATION)
                }
                cache.saveChildHomeNodes(familyId, parentId, response.items, SECTION_CONVERSATION)
                show(response.items)
            } catch (e: Exception) {
                cache.loadChildHomeNodes(familyId, parentId, SECTION_CONVERSATION)?.let { show(it) }
                toast(R.string.conversation_load_failed)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun show(items: List<ChildHomeNodeDto>) {
        nodes = items
        val page = BoardLayout.build(items.map {
            ConvCell(
                id = it.id, label = it.displayLabel(), imageUrl = it.item?.imageUrl,
                isFolder = it.type == "MENU", row = it.gridRow, col = it.gridCol, color = it.color
            )
        })
        if (_path.value.size == 1) {
            homeCols = page.cols
            homeRows = page.rows
            libraryEmpty = items.isEmpty()
        }
        _page.value = page
        _loaded.value = true
    }

    // ── Edits ────────────────────────────────────────────────────────────────

    fun createCard(itemId: String, type: String, color: String?, row: Int, col: Int) {
        runEdit {
            ApiClient.api.createChildHomeNode(
                auth = auth,
                body = CreateChildHomeNodeRequest(
                    itemId = itemId,
                    parentId = currentParentId,
                    type = type,
                    section = SECTION_CONVERSATION,
                    gridRow = row,
                    gridCol = col,
                    color = color
                )
            )
        }
    }

    fun update(nodeId: String, body: UpdateChildHomeNodeRequest) {
        runEdit { ApiClient.api.updateChildHomeNode(auth = auth, nodeId = nodeId, body = body) }
    }

    /** Drop of a card on a cell: the server moves it, or swaps it with the card that is there. */
    fun move(nodeId: String, row: Int, col: Int) {
        val node = nodeById(nodeId) ?: return
        val current = _page.value.placed.firstOrNull { it.cell.id == nodeId }
        if (current != null && current.row == row && current.col == col) return
        if (node.gridRow == row && node.gridCol == col) return
        update(nodeId, UpdateChildHomeNodeRequest(gridRow = row, gridCol = col))
    }

    /** Deletes a card; a folder is emptied first, level by level. */
    fun delete(nodeId: String) {
        runEdit { deleteTree(nodeId) }
    }

    private suspend fun deleteTree(nodeId: String) {
        val children = ApiClient.api.getChildHomeNodes(
            auth = auth, parentId = nodeId, section = SECTION_CONVERSATION
        ).items
        for (child in children) deleteTree(child.id)
        ApiClient.api.deleteChildHomeNode(auth = auth, nodeId = nodeId)
    }

    /** Installs the family's starter vocabulary; [replace] first removes the current cards. */
    fun installStarter(language: String, replace: Boolean, onDone: (nodesCreated: Int) -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val created = withContext(Dispatchers.IO) {
                    val presets = ApiClient.api.getConversationPresets(auth).items
                    val preset = presets.firstOrNull() ?: error("no preset")
                    val lang = if (language in preset.languages) language else preset.languages.firstOrNull() ?: "en"
                    ApiClient.api.installConversationPreset(
                        auth, preset.id,
                        InstallPresetRequest(language = lang, mode = if (replace) "REPLACE" else "EMPTY_ONLY")
                    ).nodesCreated
                }
                _path.value = _path.value.take(1)
                onDone(created)
                loadNodes()
            } catch (e: Exception) {
                _isLoading.value = false
                _events.emit(Event.ShowToast(str(R.string.starter_failed, e.message)))
            }
        }
    }

    private fun runEdit(block: suspend () -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                _events.emit(Event.ShowToast(str(R.string.conversation_edit_failed, e.message)))
            }
            loadNodes()
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private suspend fun toast(resId: Int) {
        _events.emit(Event.ShowToast(getApplication<Application>().getString(resId)))
    }

    private fun str(resId: Int, vararg args: Any?): String =
        getApplication<Application>().getString(resId, *args.map { it ?: "" }.toTypedArray())
}
