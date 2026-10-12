package com.an0obis.comuginator.ui.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.ChildHomeNodeDto
import com.an0obis.comuginator.storage.OfflineCache
import com.an0obis.comuginator.storage.SECTION_CONVERSATION
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore
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

/** One tappable card: a word of the library, a folder, or an ARASAAC search result. */
data class ConvCell(
    val id: String,
    val label: String,
    val imageUrl: String?,
    val isFolder: Boolean = false,
    /** Fixed cell on the page (null = not placed yet), and the word-class colour key. */
    val row: Int? = null,
    val col: Int? = null,
    val color: String? = null
)

/**
 * Holds the state of the conversation screen across rotation: the folder the
 * user is in, the library cards shown, and the line of cards composed so far.
 *
 * The library comes from the "CONVERSATION" section of the child-home tree. It
 * is read through the offline cache, so the screen keeps working without a
 * connection (the snapshot is refreshed in the background).
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    sealed class Event {
        data class ShowToast(val message: String) : Event()
    }

    val store = SessionStore(application)

    private val _page = MutableStateFlow(BoardPage.EMPTY)
    /** The board page of the folder we are in. */
    val page: StateFlow<BoardPage> = _page.asStateFlow()

    /** Size of the home page; every page uses the card size derived from it. */
    var homeCols: Int = 0
        private set
    var homeRows: Int = 0
        private set

    private val _line = MutableStateFlow<List<ConvCell>>(emptyList())
    val line: StateFlow<List<ConvCell>> = _line.asStateFlow()

    private val _path = MutableStateFlow(listOf(PathEntry(parentId = null, title = "")))
    val path: StateFlow<List<PathEntry>> = _path.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** True once a level was shown (even an empty one), so "empty" isn't flashed while loading. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val currentParentId: String?
        get() = _path.value.lastOrNull()?.parentId

    // ── Library navigation ───────────────────────────────────────────────────

    fun openFolder(cell: ConvCell) {
        _path.value = _path.value + PathEntry(parentId = cell.id, title = cell.label)
        showLevelChange()
        loadNodes()
    }

    /** Goes one folder up; false when already at the top. */
    fun goUp(): Boolean {
        if (_path.value.size <= 1) return false
        _path.value = _path.value.dropLast(1)
        showLevelChange()
        loadNodes()
        return true
    }

    /**
     * Never leave the previous folder's cards on screen while the new level
     * loads: a tap would add the wrong word. The saved copy of the level (if
     * any) is shown at once by [loadNodes]; otherwise the grid stays empty
     * until the server answers.
     */
    private fun showLevelChange() {
        _page.value = BoardPage.EMPTY
        _loaded.value = false
    }

    private fun showCells(nodes: List<ChildHomeNodeDto>) {
        val page = BoardLayout.build(nodes.map { it.toCell() })
        if (_path.value.size == 1) {
            homeCols = page.cols
            homeRows = page.rows
        }
        _page.value = page
        _loaded.value = true
    }

    fun loadNodes() {
        val parentId = currentParentId
        val familyId = store.familyId
        _isLoading.value = true

        viewModelScope.launch {
            val cache = OfflineCache(getApplication())
            val forcedOffline = SettingsStore(getApplication<Application>()).offlineMode

            // Show the saved copy of this level right away; the server's
            // answer (below) replaces it if anything changed.
            if (!forcedOffline && !_loaded.value) {
                cache.loadChildHomeNodes(familyId, parentId, SECTION_CONVERSATION)?.let { showCells(it) }
            }

            val nodes: List<ChildHomeNodeDto>? =
                if (forcedOffline) {
                    cache.loadChildHomeNodes(familyId, parentId, SECTION_CONVERSATION)
                        ?: emptyList()
                } else {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            ApiClient.api.getChildHomeNodes(
                                auth = store.authHeaderOrThrow(),
                                parentId = parentId,
                                section = SECTION_CONVERSATION
                            )
                        }
                        cache.saveChildHomeNodes(
                            familyId, parentId, response.items, SECTION_CONVERSATION
                        )
                        response.items
                    } catch (e: Exception) {
                        // No connection (or a server problem): fall back to the
                        // snapshot instead of blocking the user's communication.
                        val cached = cache.loadChildHomeNodes(familyId, parentId, SECTION_CONVERSATION)
                        if (cached == null) {
                            _events.emit(
                                Event.ShowToast(
                                    getApplication<Application>().getString(
                                        R.string.conversation_load_failed
                                    )
                                )
                            )
                        }
                        cached
                    }
                }

            if (nodes != null) showCells(nodes)
            _isLoading.value = false
        }
    }

    private fun ChildHomeNodeDto.toCell() = ConvCell(
        id = id,
        label = displayLabel(),
        imageUrl = item?.imageUrl,
        isFolder = type == "MENU",
        row = gridRow,
        col = gridCol,
        color = color
    )

    /** Jumps back to the home page. */
    fun goHome() {
        if (_path.value.size <= 1) return
        _path.value = _path.value.take(1)
        showLevelChange()
        loadNodes()
    }

    // ── The line of composed cards ───────────────────────────────────────────

    fun addToLine(cell: ConvCell) {
        _line.value = _line.value + cell
    }

    fun removeFromLine(index: Int) {
        val current = _line.value
        if (index !in current.indices) return
        _line.value = current.filterIndexed { i, _ -> i != index }
    }

    fun backspace() {
        _line.value = _line.value.dropLast(1)
    }

    fun clearLine() {
        _line.value = emptyList()
    }

    /** What "speak all" says: the labels in order. */
    fun lineText(): String = _line.value.joinToString(" ") { it.label }
}
