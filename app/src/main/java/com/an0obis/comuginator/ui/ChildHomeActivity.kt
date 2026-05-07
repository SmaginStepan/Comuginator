package com.an0obis.comuginator.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.ChildHomeNodeDto
import com.an0obis.comuginator.api.CreateChildHomeNodeRequest
import com.an0obis.comuginator.api.UpdateChildHomeNodeRequest
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChildHomeActivity : BaseActivity() {

    companion object {
        const val EXTRA_EDITOR_MODE = "editor_mode"

        private const val STATE_PATH_IDS = "child_home_path_ids"
        private const val STATE_PATH_TITLES = "child_home_path_titles"
        private const val STATE_PENDING_EDIT_NODE_ID = "pending_edit_node_id"
        private const val STATE_PENDING_EDIT_NODE_TYPE = "pending_edit_node_type"
    }

    private data class PathEntry(
        val parentId: String?,
        val title: String
    )

    private val isEditorMode: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_EDITOR_MODE, false)
    }

    private lateinit var sessionStore: SessionStore
    private lateinit var adapter: ChildHomeAdapter

    private lateinit var tvTitle: TextView
    private lateinit var tvBreadcrumbs: TextView
    private lateinit var btnBack: Button
    private lateinit var btnAdd: Button
    private lateinit var btnHideInvisible: Button
    private lateinit var btnShowHidden: Button
    private lateinit var progress: ProgressBar
    private lateinit var rv: RecyclerView
    private lateinit var btnPreview: Button

    private var lastLoadedNodesSize: Int? = null
    private var previewMode: Boolean = false
    private var blinkingNodeId: String? = null
    private val path = mutableListOf<PathEntry>()

    private var currentParentId: String?
        get() = path.lastOrNull()?.parentId
        set(value) {
            if (path.isEmpty()) {
                path.add(PathEntry(value, getString(R.string.home)))
            } else {
                val current = path.last()
                path[path.lastIndex] = current.copy(parentId = value)
            }
        }

    private var authToken: String = ""

    private var pendingEditNodeId: String? = null
    private var pendingEditNodeType: String? = null

    private val pickItemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val itemId =
                LibraryItemPickerActivity.parseResultItemId(result.data)
                    ?: return@registerForActivityResult

            val editingNodeId = pendingEditNodeId
            val editingNodeType = pendingEditNodeType

            pendingEditNodeId = null
            pendingEditNodeType = null

            if (editingNodeId == null || editingNodeType == null) {
                showCreateNodeDialog(itemId)
            } else {
                showEditNodeDialog(
                    nodeId = editingNodeId,
                    currentType = editingNodeType,
                    newItemId = itemId
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreState(savedInstanceState)
        ensureInitialized()
    }

    override fun onInitialized() {
        setContentView(R.layout.activity_child_home)

        sessionStore = SessionStore(this)
        authToken = sessionStore.token.orEmpty()

        tvTitle = findViewById(R.id.tvChildHomeTitle)
        tvBreadcrumbs = findViewById(R.id.tvChildHomeBreadcrumbs)
        btnBack = findViewById(R.id.btnChildHomeBack)
        btnAdd = findViewById(R.id.btnChildHomeAdd)
        btnHideInvisible = findViewById(R.id.btnHideInvisible)
        btnShowHidden = findViewById(R.id.btnShowHidden)
        btnPreview = findViewById(R.id.btnChildHomePreview)
        progress = findViewById(R.id.progressChildHome)
        rv = findViewById(R.id.rvChildHome)


        btnAdd.setOnClickListener {
            openAddNode()
        }

        btnBack.setOnClickListener {
            goBack()
        }
        btnHideInvisible.setOnClickListener {
            hideInvisible()
        }
        btnPreview.setOnClickListener {
            if (previewMode) {
                stopPreview()
            } else {
                startPreview()
            }
        }
        btnShowHidden.setOnClickListener {
            showHidden()
        }

        adapter = ChildHomeAdapter(
            authToken = authToken,
            isEditorMode = isEditorMode,
            onNodeClick = { node -> onNodeClicked(node) },
            onRenameClick = { node -> openRenameNode(node) },
            onEditClick = { node -> openEditNode(node) },
            onDeleteClick = { node -> confirmDeleteNode(node) },
            onToggleVisibilityClick = { node -> toggleNodeVisibility(node) }
        )

        rv.layoutManager = GridLayoutManager(
            this,
            resources.getInteger(R.integer.child_home_span_count)
        )
        rv.adapter = adapter
        rv.itemAnimator = null

        if (path.isEmpty()) {
            path.add(PathEntry(parentId = null, title = getString(R.string.home)))
        }

        updateUi()
        loadNodes(currentParentId, false)
    }

    private fun hideInvisible() {
        loadNodes(currentParentId, true)
    }
    private fun showHidden() {
        loadNodes(currentParentId, false)
    }

    private fun toggleNodeVisibility(node: ChildHomeNodeDto) {
        showLoading()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateChildHomeNode(
                        auth = sessionStore.authHeaderOrThrow(),
                        nodeId = node.id,
                        body = UpdateChildHomeNodeRequest(
                            isVisible = !node.isVisible
                        )
                    )
                }

                adapter.updateNodeVisibility(
                    node.id,
                    !node.isVisible
                )

                updateUi()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChildHomeActivity,
                    getString(R.string.child_home_update_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                hideLoading()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putStringArrayList(
            STATE_PATH_IDS,
            ArrayList(path.map { it.parentId.orEmpty() })
        )

        outState.putStringArrayList(
            STATE_PATH_TITLES,
            ArrayList(path.map { it.title })
        )

        outState.putString(STATE_PENDING_EDIT_NODE_ID, pendingEditNodeId)
        outState.putString(STATE_PENDING_EDIT_NODE_TYPE, pendingEditNodeType)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return

        val ids = savedInstanceState.getStringArrayList(STATE_PATH_IDS).orEmpty()
        val titles = savedInstanceState.getStringArrayList(STATE_PATH_TITLES).orEmpty()

        path.clear()

        val count = minOf(ids.size, titles.size)
        for (i in 0 until count) {
            path.add(
                PathEntry(
                    parentId = ids[i].ifBlank { null },
                    title = titles[i]
                )
            )
        }

        pendingEditNodeId = savedInstanceState.getString(STATE_PENDING_EDIT_NODE_ID)
        pendingEditNodeType = savedInstanceState.getString(STATE_PENDING_EDIT_NODE_TYPE)
    }

    private fun openAddNode() {
        pendingEditNodeId = null
        pendingEditNodeType = null

        pickItemLauncher.launch(
            LibraryItemPickerActivity.createIntent(
                this,
                LibraryItemPickerActivity.TargetMode.USER_AVATAR
            )
        )
    }

    private fun openEditNode(node: ChildHomeNodeDto) {
        pendingEditNodeId = node.id
        pendingEditNodeType = node.type

        pickItemLauncher.launch(
            LibraryItemPickerActivity.createIntent(
                this,
                LibraryItemPickerActivity.TargetMode.USER_AVATAR
            )
        )
    }

    private fun openRenameNode(node: ChildHomeNodeDto) {
        val input = EditText(this)
        input.setText(node.labelOverride ?: node.item?.label.orEmpty())
        input.setSelection(input.text.length)

        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty()) {
                    renameNode(node, newName)
                }
            }
            .show()
    }

    private fun renameNode(node: ChildHomeNodeDto, newName: String) {
        showLoading()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateChildHomeNode(
                        auth = sessionStore.authHeaderOrThrow(),
                        nodeId = node.id,
                        body = UpdateChildHomeNodeRequest(
                            labelOverride = newName
                        )
                    )
                }

                renameCurrentPathEntryIfNeeded(node.id, newName)
                loadNodes(currentParentId, false)
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChildHomeActivity,
                    getString(R.string.child_home_update_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun renameCurrentPathEntryIfNeeded(nodeId: String, newName: String) {
        val index = path.indexOfFirst { it.parentId == nodeId }
        if (index >= 0) {
            path[index] = path[index].copy(title = newName)
            updateUi()
        }
    }

    private fun confirmDeleteNode(node: ChildHomeNodeDto) {
        AlertDialog.Builder(this)
            .setTitle(R.string.child_home_delete_node_title)
            .setMessage(node.labelOverride ?: node.item?.label ?: node.id)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteNode(node)
            }
            .show()
    }

    private fun showCreateNodeDialog(itemId: String) {
        val labels = arrayOf(
            getString(R.string.element_menu),
            getString(R.string.element_action)
        )

        val values = arrayOf(
            "MENU",
            "ACTION"
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.child_home_node_type_title)
            .setSingleChoiceItems(labels, 0) { dialog, which ->
                dialog.dismiss()

                createNode(
                    itemId = itemId,
                    type = values[which]
                )
            }
            .show()
    }

    private fun showEditNodeDialog(
        nodeId: String,
        currentType: String,
        newItemId: String
    ) {
        val labels = arrayOf(
            getString(R.string.element_menu),
            getString(R.string.element_action)
        )

        val values = arrayOf(
            "MENU",
            "ACTION"
        )

        val checked = if (currentType == "ACTION") 1 else 0

        AlertDialog.Builder(this)
            .setTitle(R.string.child_home_node_type_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()

                updateNode(
                    nodeId = nodeId,
                    newItemId = newItemId,
                    newType = values[which]
                )
            }
            .show()
    }

    private fun createNode(itemId: String, type: String) {
        showLoading()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.createChildHomeNode(
                        auth = sessionStore.authHeaderOrThrow(),
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

                loadNodes(currentParentId, false)
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChildHomeActivity,
                    getString(R.string.child_home_create_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun updateNode(
        nodeId: String,
        newItemId: String,
        newType: String
    ) {
        showLoading()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateChildHomeNode(
                        auth = sessionStore.authHeaderOrThrow(),
                        nodeId = nodeId,
                        body = UpdateChildHomeNodeRequest(
                            itemId = newItemId,
                            type = newType
                        )
                    )
                }

                loadNodes(currentParentId, false)
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChildHomeActivity,
                    getString(R.string.child_home_update_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun deleteNode(node: ChildHomeNodeDto) {
        showLoading()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.deleteChildHomeNode(
                        auth = sessionStore.authHeaderOrThrow(),
                        nodeId = node.id
                    )
                }

                loadNodes(currentParentId, false)
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChildHomeActivity,
                    getString(R.string.child_home_delete_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun onNodeClicked(node: ChildHomeNodeDto) {
        when (node.type) {
            "MENU" -> openMenu(node)
            "ACTION" -> {
                when {
                    isEditorMode && !previewMode -> {
                        openEditNode(node)
                    }

                    previewMode -> {
                        previewAction(node)
                    }

                    else -> {
                        requestAction(node)
                    }
                }
            }
        }
    }

    private fun previewAction(node: ChildHomeNodeDto) {
        if (node.blinkEnabled) {
            blinkNode(node.id, node.blinkSeconds)
        }
    }

    private fun openMenu(node: ChildHomeNodeDto) {
        path.add(
            PathEntry(
                parentId = node.id,
                title = node.displayLabel()
            )
        )

        updateUi()
        loadNodes(currentParentId, false)
    }

    private fun goBack() {
        if (path.size > 1) {
            path.removeAt(path.lastIndex)
            updateUi()
            loadNodes(currentParentId, false)
            return
        }

        if (previewMode) {
            stopPreview()
            return
        }

        if (isEditorMode) {
            finish()
        }
    }

    private fun showLoading() {
        progress.clearAnimation()
        progress.alpha = 0f
        progress.visibility = View.VISIBLE
        progress.animate()
            .alpha(1f)
            .setDuration(180)
            .start()
    }

    private fun hideLoading() {
        progress.clearAnimation()
        progress.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                progress.visibility = View.GONE
            }
            .start()
    }
    private fun loadNodes(parentId: String?, hideInvisible: Boolean) {
        showLoading()
        updateUi()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getChildHomeNodes(
                        auth = sessionStore.authHeaderOrThrow(),
                        parentId = parentId
                    )
                }

                val effectiveEditorMode = isEditorMode && !hideInvisible && !previewMode

                Log.d("ChildHomeActivity", "$effectiveEditorMode response: $response ")

                lastLoadedNodesSize = response.items.size

                val visibleItems = if (effectiveEditorMode) {
                    response.items
                } else {
                    response.items.filter { it.isVisible }
                }

                adapter.submitItems(visibleItems)
                updateUi()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChildHomeActivity,
                    getString(R.string.child_home_load_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun requestAction(node: ChildHomeNodeDto) {
        showLoading()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.requestChildHomeAction(
                        auth = sessionStore.authHeaderOrThrow(),
                        nodeId = node.id
                    )
                }

                if (response.blinkEnabled) {
                    blinkNode(node.id, response.blinkSeconds)
                }

                Toast.makeText(
                    this@ChildHomeActivity,
                    getString(R.string.sent),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChildHomeActivity,
                    getString(R.string.child_home_send_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun updateUi() {
        val effectiveEditorMode = isEditorMode && !previewMode
        val items = adapter.readItems()
        val hasHidden = (lastLoadedNodesSize ?: 0) > items.size
        val hasInvisibleNodeInVisible = items.any { !it.isVisible }

        if (effectiveEditorMode) {
            btnAdd.visibility = View.VISIBLE
            if (hasHidden) {
                if (hasInvisibleNodeInVisible) {
                    btnHideInvisible.visibility = View.VISIBLE
                    btnShowHidden.visibility = View.GONE
                } else {
                    btnHideInvisible.visibility = View.GONE
                    btnShowHidden.visibility = View.VISIBLE
                }
            } else {
                btnShowHidden.visibility = View.GONE
                btnHideInvisible.visibility = View.VISIBLE
                btnHideInvisible.isEnabled = hasInvisibleNodeInVisible
            }
        } else {
            btnHideInvisible.visibility = View.GONE
            btnAdd.visibility = View.GONE
            btnShowHidden.visibility = View.GONE
        }

        btnPreview.visibility = if (isEditorMode) View.VISIBLE else View.GONE
        btnPreview.text = getString(
            if (previewMode) R.string.stop_preview else R.string.start_preview
        )

        btnBack.visibility =
            if (effectiveEditorMode || path.size > 1 || previewMode) View.VISIBLE else View.GONE

        tvTitle.visibility = View.VISIBLE
        tvBreadcrumbs.visibility = if (previewMode) View.GONE else View.VISIBLE

        tvBreadcrumbs.text = path.joinToString(" > ") { it.title }

        adapter.setEditorMode(effectiveEditorMode)
    }

    private fun startPreview() {
        previewMode = true
        blinkingNodeId = null
        adapter.showOnlyNode(null)
        loadNodes(currentParentId, false)
        updateUi()
    }

    private fun stopPreview() {
        previewMode = false
        blinkingNodeId = null
        rv.clearAnimation()
        adapter.showOnlyNode(null)
        loadNodes(currentParentId, false)
        updateUi()
    }

    private fun ChildHomeNodeDto.displayLabel(): String {
        return labelOverride ?: item?.label ?: type
    }

    private fun blinkNode(nodeId: String, seconds: Int) {
        val durationMs = seconds.coerceAtLeast(1) * 1000L

        blinkingNodeId = nodeId
        adapter.showOnlyNode(nodeId)
        btnBack.visibility = View.INVISIBLE

        val animation = AlphaAnimation(1.0f, 0.25f).apply {
            duration = 400
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        rv.startAnimation(animation)

        rv.postDelayed({
            if (blinkingNodeId == nodeId) {
                blinkingNodeId = null
                rv.clearAnimation()
                adapter.showOnlyNode(null)
                updateUi()
            }
        }, durationMs)
    }

    override fun onResume() {
        super.onResume()

        if (!redirectedByRoleGuard) {
            loadNodes(currentParentId, false)
        }
    }
}