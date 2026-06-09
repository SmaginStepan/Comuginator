package com.an0obis.comuginator.ui.childhome

import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ChildHomeNodeDto
import com.an0obis.comuginator.ui.base.BaseActivity
import com.an0obis.comuginator.ui.library.LibraryItemPickerActivity
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.an0obis.comuginator.service.ACTION_CHILD_HOME_SCHEDULE_APPLIED

class ChildHomeActivity : BaseActivity() {

    companion object {
        const val EXTRA_EDITOR_MODE = "editor_mode"
        private const val STATE_PATH_IDS = "child_home_path_ids"
        private const val STATE_PATH_TITLES = "child_home_path_titles"
        private const val STATE_PENDING_EDIT_NODE_ID = "pending_edit_node_id"
        private const val STATE_PENDING_EDIT_NODE_TYPE = "pending_edit_node_type"
    }

    private val viewModel: ChildHomeViewModel by viewModels()

    private val scheduleAppliedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.loadNodes()
        }
    }
    private lateinit var adapter: ChildHomeAdapter
    private lateinit var tvTitle: TextView
    private lateinit var tvBreadcrumbs: TextView
    private lateinit var tvCounter: TextView
    private lateinit var btnBack: Button
    private lateinit var btnAdd: Button
    private lateinit var btnHideInvisible: Button
    private lateinit var btnShowHidden: Button
    private lateinit var btnPreview: Button
    private lateinit var progress: ProgressBar
    private lateinit var rv: RecyclerView

    private var blinkingNodeId: String? = null

    private val pickItemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val itemId = LibraryItemPickerActivity.parseResultItemId(result.data)
                ?: return@registerForActivityResult

            val editingNodeId = viewModel.pendingEditNodeId
            val editingNodeType = viewModel.pendingEditNodeType
            viewModel.pendingEditNodeId = null
            viewModel.pendingEditNodeType = null

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

    override fun onStart() {
        super.onStart()

        ContextCompat.registerReceiver(
            this,
            scheduleAppliedReceiver,
            IntentFilter(ACTION_CHILD_HOME_SCHEDULE_APPLIED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(scheduleAppliedReceiver)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.setup(
            isEditorMode = intent.getBooleanExtra(EXTRA_EDITOR_MODE, false),
            savedPathIds = savedInstanceState?.getStringArrayList(STATE_PATH_IDS).orEmpty(),
            savedPathTitles = savedInstanceState?.getStringArrayList(STATE_PATH_TITLES).orEmpty(),
            savedPendingEditNodeId = savedInstanceState?.getString(STATE_PENDING_EDIT_NODE_ID),
            savedPendingEditNodeType = savedInstanceState?.getString(STATE_PENDING_EDIT_NODE_TYPE)
        )

        ensureInitialized()
    }

    override fun onInitialized() {
        setContentView(R.layout.activity_child_home)

        tvTitle = findViewById(R.id.tvChildHomeTitle)
        tvBreadcrumbs = findViewById(R.id.tvChildHomeBreadcrumbs)
        btnBack = findViewById(R.id.btnChildHomeBack)
        btnAdd = findViewById(R.id.btnChildHomeAdd)
        tvCounter = findViewById(R.id.tvChildHomeCounter)
        btnHideInvisible = findViewById(R.id.btnHideInvisible)
        btnShowHidden = findViewById(R.id.btnShowHidden)
        btnPreview = findViewById(R.id.btnChildHomePreview)
        progress = findViewById(R.id.progressChildHome)
        rv = findViewById(R.id.rvChildHome)

        adapter = ChildHomeAdapter(
            authToken = viewModel.store.token.orEmpty(),
            isEditorMode = viewModel.isEditorMode,
            onNodeClick = ::onNodeClicked,
            onRenameClick = ::openRenameNode,
            onEditClick = ::openEditNode,
            onDeleteClick = ::confirmDeleteNode,
            onToggleVisibilityClick = viewModel::toggleNodeVisibility
        )

        rv.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.child_home_span_count))
        rv.adapter = adapter
        rv.itemAnimator = null

        setupDragAndDrop()

        btnAdd.setOnClickListener { openAddNode() }
        btnBack.setOnClickListener { goBack() }
        btnHideInvisible.setOnClickListener { viewModel.loadNodes(hideInvisible = true) }
        btnShowHidden.setOnClickListener { viewModel.loadNodes(hideInvisible = false) }
        btnPreview.setOnClickListener {
            if (viewModel.previewMode.value) stopPreview() else startPreview()
        }

        viewModel.ensureRootPath(getString(R.string.home))
        bindState()
        viewModel.loadNodes()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentPath = viewModel.path.value
        outState.putStringArrayList(STATE_PATH_IDS, ArrayList(currentPath.map { it.parentId.orEmpty() }))
        outState.putStringArrayList(STATE_PATH_TITLES, ArrayList(currentPath.map { it.title }))
        outState.putString(STATE_PENDING_EDIT_NODE_ID, viewModel.pendingEditNodeId)
        outState.putString(STATE_PENDING_EDIT_NODE_TYPE, viewModel.pendingEditNodeType)
    }

    override fun onResume() {
        super.onResume()
        if (!redirectedByRoleGuard) {
            viewModel.loadNodes()
        }
    }

    // ── State binding ─────────────────────────────────────────────────────────

    private fun bindState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.nodes.collect { nodes ->
                        adapter.submitItems(nodes)
                        tvCounter.text = resources.getQuantityString(
                            R.plurals.items_count, nodes.size, nodes.size
                        )
                        updateUi()
                    }
                }
                launch {
                    viewModel.path.collect { updateUi() }
                }
                launch {
                    viewModel.previewMode.collect { updateUi() }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        if (loading) showLoading() else hideLoading()
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ChildHomeViewModel.Event.ShowToast ->
                                Toast.makeText(this@ChildHomeActivity, event.message, Toast.LENGTH_LONG).show()
                            is ChildHomeViewModel.Event.BlinkNode ->
                                blinkNode(event.nodeId, event.seconds)
                            is ChildHomeViewModel.Event.NodeVisibilityUpdated -> {
                                adapter.updateNodeVisibility(event.nodeId, event.isVisible)
                                updateUi()
                            }
                        }
                    }
                }
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun updateUi() {
        val effectiveEditorMode = viewModel.isEditorMode && !viewModel.previewMode.value
        val items = adapter.readItems()
        val hasHidden = (viewModel.lastLoadedNodesSize ?: 0) > items.size
        val hasInvisibleInList = items.any { !it.isVisible }

        if (effectiveEditorMode) {
            btnAdd.visibility = View.VISIBLE
            if (hasHidden) {
                btnHideInvisible.visibility = if (hasInvisibleInList) View.VISIBLE else View.GONE
                btnHideInvisible.isEnabled = true
                btnShowHidden.visibility    = if (hasInvisibleInList) View.GONE   else View.VISIBLE
            } else {
                btnShowHidden.visibility = View.GONE
                btnHideInvisible.visibility = View.VISIBLE
                btnHideInvisible.isEnabled = hasInvisibleInList
            }
        } else {
            btnAdd.visibility = View.GONE
            btnHideInvisible.visibility = View.GONE
            btnShowHidden.visibility = View.GONE
        }

        val previewMode = viewModel.previewMode.value
        btnPreview.visibility = if (viewModel.isEditorMode) View.VISIBLE else View.GONE
        btnPreview.text = getString(if (previewMode) R.string.stop_preview else R.string.start_preview)

        btnBack.visibility =
            if (effectiveEditorMode || viewModel.path.value.size > 1 || previewMode) View.VISIBLE else View.GONE

        tvTitle.visibility = View.VISIBLE
        tvBreadcrumbs.visibility = if (previewMode) View.GONE else View.VISIBLE
        val pathSegments = viewModel.path.value.joinToString(" > ") { it.title }
        tvBreadcrumbs.text = if (pathSegments.isNotEmpty())
            getString(R.string.breadcrumb_child_home, pathSegments)
        else
            getString(R.string.breadcrumb_child_home_root)

        adapter.setEditorMode(effectiveEditorMode)
    }

    private fun showLoading() {
        progress.clearAnimation()
        progress.alpha = 0f
        progress.visibility = View.VISIBLE
        progress.animate().alpha(1f).setDuration(180).start()
    }

    private fun hideLoading() {
        progress.clearAnimation()
        progress.animate().alpha(0f).setDuration(180).withEndAction {
            progress.visibility = View.GONE
        }.start()
    }

    // ── Preview & animation ───────────────────────────────────────────────────

    private fun startPreview() {
        blinkingNodeId = null
        adapter.showOnlyNode(null)
        viewModel.setPreviewMode(true)
        viewModel.loadNodes()
    }

    private fun stopPreview() {
        blinkingNodeId = null
        rv.clearAnimation()
        adapter.showOnlyNode(null)
        viewModel.setPreviewMode(false)
        viewModel.loadNodes()
    }

    private fun blinkNode(nodeId: String, seconds: Int) {
        val durationMs = seconds.coerceAtLeast(1) * 1000L
        blinkingNodeId = nodeId
        adapter.showOnlyNode(nodeId)
        btnBack.visibility = View.INVISIBLE

        rv.startAnimation(AlphaAnimation(1.0f, 0.25f).apply {
            duration = 400
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        })

        rv.postDelayed({
            if (blinkingNodeId == nodeId) {
                blinkingNodeId = null
                rv.clearAnimation()
                adapter.showOnlyNode(null)
                updateUi()
            }
        }, durationMs)
    }

    // ── Drag & drop ───────────────────────────────────────────────────────────

    private fun setupDragAndDrop() {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun isLongPressDragEnabled() =
                viewModel.isEditorMode && !viewModel.previewMode.value

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (!viewModel.isEditorMode || viewModel.previewMode.value) return false
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || from == to) return false
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (viewModel.isEditorMode && !viewModel.previewMode.value) {
                    viewModel.persistCurrentNodeOrder(adapter.readItems())
                }
            }
        }).attachToRecyclerView(rv)
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun goBack() {
        if (viewModel.popPath()) {
            viewModel.loadNodes()
            return
        }
        if (viewModel.previewMode.value) {
            stopPreview()
            return
        }
        if (viewModel.isEditorMode) finish()
    }

    private fun openAddNode() {
        viewModel.pendingEditNodeId = null
        viewModel.pendingEditNodeType = null
        pickItemLauncher.launch(
            LibraryItemPickerActivity.createIntent(this, LibraryItemPickerActivity.TargetMode.USER_AVATAR)
        )
    }

    private fun openEditNode(node: ChildHomeNodeDto) {
        viewModel.pendingEditNodeId = node.id
        viewModel.pendingEditNodeType = node.type
        pickItemLauncher.launch(
            LibraryItemPickerActivity.createIntent(this, LibraryItemPickerActivity.TargetMode.USER_AVATAR)
        )
    }

    private fun onNodeClicked(node: ChildHomeNodeDto) {
        when (node.type) {
            "MENU" -> {
                viewModel.pushPath(PathEntry(parentId = node.id, title = node.displayLabel()))
                viewModel.loadNodes()
            }
            "ACTION" -> when {
                viewModel.isEditorMode && !viewModel.previewMode.value -> openEditNode(node)
                viewModel.previewMode.value -> {
                    if (node.blinkEnabled) blinkNode(node.id, node.blinkSeconds)
                }
                else -> viewModel.requestAction(node)
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun openRenameNode(node: ChildHomeNodeDto) {
        val input = EditText(this).apply {
            setText(node.labelOverride ?: node.item?.label.orEmpty())
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty()) viewModel.renameNode(node, newName)
            }
            .show()
    }

    private fun showCreateNodeDialog(itemId: String) {
        val labels = arrayOf(getString(R.string.element_menu), getString(R.string.element_action))
        val values = arrayOf("MENU", "ACTION")
        AlertDialog.Builder(this)
            .setTitle(R.string.child_home_node_type_title)
            .setSingleChoiceItems(labels, 0) { dialog, which ->
                dialog.dismiss()
                viewModel.createNode(itemId = itemId, type = values[which])
            }
            .show()
    }

    private fun showEditNodeDialog(nodeId: String, currentType: String, newItemId: String) {
        val labels = arrayOf(getString(R.string.element_menu), getString(R.string.element_action))
        val values = arrayOf("MENU", "ACTION")
        val checked = if (currentType == "ACTION") 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.child_home_node_type_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                viewModel.updateNode(nodeId = nodeId, newItemId = newItemId, newType = values[which])
            }
            .show()
    }

    private fun confirmDeleteNode(node: ChildHomeNodeDto) {
        AlertDialog.Builder(this)
            .setTitle(R.string.child_home_delete_node_title)
            .setMessage(node.labelOverride ?: node.item?.label ?: node.id)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteNode(node) }
            .show()
    }
}
