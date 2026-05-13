package com.an0obis.comuginator.ui.childhome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.ChildHomeNodeDto
import com.an0obis.comuginator.api.CreateChildHomeNodeRequest
import com.an0obis.comuginator.api.UpdateChildHomeNodeRequest
import com.an0obis.comuginator.storage.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PathEntry(val parentId: String?, val title: String)

fun ChildHomeNodeDto.displayLabel(): String = labelOverride ?: item?.label ?: type

class ChildHomeViewModel(application: Application) : AndroidViewModel(application) {

    sealed class Event {
        data class ShowToast(val message: String) : Event()
        data class BlinkNode(val nodeId: String, val seconds: Int) : Event()
        data class NodeVisibilityUpdated(val nodeId: String, val isVisible: Boolean) : Event()
    }

    val store = SessionStore(application)

    private val _nodes = MutableStateFlow<List<ChildHomeNodeDto>>(emptyList())
    val nodes: StateFlow<List<ChildHomeNodeDto>> = _nodes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _path = MutableStateFlow<List<PathEntry>>(emptyList())
    val path: StateFlow<List<PathEntry>> = _path.asStateFlow()

    private val _previewMode = MutableStateFlow(false)
    val previewMode: StateFlow<Boolean> = _previewMode.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    var isEditorMode: Boolean = false
        private set

    var pendingEditNodeId: String? = null
    var pendingEditNodeType: String? = null

    var lastLoadedNodesSize: Int? = null
        private set

    private var setupDone = false

    val currentParentId: String?
        get() = _path.value.lastOrNull()?.parentId

    // ── Setup ─────────────────────────────────────────────────────────────────

    fun setup(
        isEditorMode: Boolean,
        savedPathIds: List<String>,
        savedPathTitles: List<String>,
        savedPendingEditNodeId: String?,
        savedPendingEditNodeType: String?
    ) {
        if (setupDone) return
        setupDone = true

        this.isEditorMode = isEditorMode
        pendingEditNodeId = savedPendingEditNodeId
        pendingEditNodeType = savedPendingEditNodeType

        val count = minOf(savedPathIds.size, savedPathTitles.size)
        if (count > 0) {
            _path.value = (0 until count).map { i ->
                PathEntry(
                    parentId = savedPathIds[i].ifBlank { null },
                    title = savedPathTitles[i]
                )
            }
        }
    }

    fun ensureRootPath(homeTitle: String) {
        if (_path.value.isEmpty()) {
            _path.value = listOf(PathEntry(parentId = null, title = homeTitle))
        }
    }

    // ── Path navigation ───────────────────────────────────────────────────────

    fun pushPath(entry: PathEntry) {
        _path.value = _path.value + entry
    }

    fun popPath(): Boolean {
        if (_path.value.size > 1) {
            _path.value = _path.value.dropLast(1)
            return true
        }
        return false
    }

    private fun renamePathEntryIfNeeded(nodeId: String, newName: String) {
        val current = _path.value.toMutableList()
        val index = current.indexOfFirst { it.parentId == nodeId }
        if (index >= 0) {
            current[index] = current[index].copy(title = newName)
            _path.value = current.toList()
        }
    }

    // ── Preview mode ──────────────────────────────────────────────────────────

    fun setPreviewMode(enabled: Boolean) {
        _previewMode.value = enabled
    }

    // ── Node loading ──────────────────────────────────────────────────────────

    fun loadNodes(hideInvisible: Boolean = false) {
        val parentId = currentParentId
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getChildHomeNodes(
                        auth = store.authHeaderOrThrow(),
                        parentId = parentId
                    )
                }
                val effectiveEditorMode = isEditorMode && !hideInvisible && !_previewMode.value
                lastLoadedNodesSize = response.items.size
                _nodes.value = if (effectiveEditorMode) response.items
                               else response.items.filter { it.isVisible }
            } catch (e: Exception) {
                _events.emit(Event.ShowToast(str(R.string.child_home_load_failed, e.message)))
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun persistCurrentNodeOrder(nodes: List<ChildHomeNodeDto>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    nodes.forEachIndexed { index, node ->
                        ApiClient.api.updateChildHomeNode(
                            auth = store.authHeaderOrThrow(),
                            nodeId = node.id,
                            body = UpdateChildHomeNodeRequest(sortOrder = index)
                        )
                    }
                }
            } catch (e: Exception) {
                _events.emit(Event.ShowToast(str(R.string.child_home_update_failed, e.message)))
                loadNodes()
            }
        }
    }

    fun toggleNodeVisibility(node: ChildHomeNodeDto) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateChildHomeNode(
                        auth = store.authHeaderOrThrow(),
                        nodeId = node.id,
                        body = UpdateChildHomeNodeRequest(isVisible = !node.isVisible)
                    )
                }
                _events.emit(Event.NodeVisibilityUpdated(node.id, !node.isVisible))
            } catch (e: Exception) {
                _events.emit(Event.ShowToast(str(R.string.child_home_update_failed, e.message)))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun renameNode(node: ChildHomeNodeDto, newName: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateChildHomeNode(
                        auth = store.authHeaderOrThrow(),
                        nodeId = node.id,
                        body = UpdateChildHomeNodeRequest(labelOverride = newName)
                    )
                }
                renamePathEntryIfNeeded(node.id, newName)
                loadNodes()
            } catch (e: Exception) {
                _events.emit(Event.ShowToast(str(R.string.child_home_update_failed, e.message)))
                _isLoading.value = false
            }
        }
    }

    fun createNode(itemId: String, type: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.createChildHomeNode(
                        auth = store.authHeaderOrThrow(),
                        body = CreateChildHomeNodeRequest(
                            itemId = itemId,
                            parentId = currentParentId,
                            type = type,
                            targetMode = "ALL_PARENTS",
                            blinkEnabled = true,
                            blinkSeconds = 10
                        )
                    )
                }
                loadNodes()
            } catch (e: Exception) {
                _events.emit(Event.ShowToast(str(R.string.child_home_create_failed, e.message)))
                _isLoading.value = false
            }
        }
    }

    fun updateNode(nodeId: String, newItemId: String, newType: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateChildHomeNode(
                        auth = store.authHeaderOrThrow(),
                        nodeId = nodeId,
                        body = UpdateChildHomeNodeRequest(itemId = newItemId, type = newType)
                    )
                }
                loadNodes()
            } catch (e: Exception) {
                _events.emit(Event.ShowToast(str(R.string.child_home_update_failed, e.message)))
                _isLoading.value = false
            }
        }
    }

    fun deleteNode(node: ChildHomeNodeDto) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.deleteChildHomeNode(
                        auth = store.authHeaderOrThrow(),
                        nodeId = node.id
                    )
                }
                loadNodes()
            } catch (e: Exception) {
                _events.emit(Event.ShowToast(str(R.string.child_home_delete_failed, e.message)))
                _isLoading.value = false
            }
        }
    }

    fun requestAction(node: ChildHomeNodeDto) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.requestChildHomeAction(
                        auth = store.authHeaderOrThrow(),
                        nodeId = node.id
                    )
                }
                if (response.blinkEnabled) {
                    _events.emit(Event.BlinkNode(node.id, response.blinkSeconds))
                }
                _events.emit(Event.ShowToast(str(R.string.sent)))
            } catch (e: Exception) {
                _events.emit(Event.ShowToast(str(R.string.child_home_send_failed, e.message)))
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun str(resId: Int) = getApplication<Application>().getString(resId)
    private fun str(resId: Int, vararg args: Any?): String =
        getApplication<Application>().getString(resId, *args.map { it ?: "" }.toTypedArray())
}
