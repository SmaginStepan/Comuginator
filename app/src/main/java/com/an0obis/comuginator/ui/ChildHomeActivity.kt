package com.an0obis.comuginator.ui

import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.ChildHomeNodeDto
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.an0obis.comuginator.api.CreateChildHomeNodeRequest
import com.an0obis.comuginator.api.UpdateChildHomeNodeRequest

class ChildHomeActivity : BaseActivity() {
    companion object {
        const val EXTRA_EDITOR_MODE = "editor_mode"
    }
    private val isEditorMode: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_EDITOR_MODE, false)
    }
    private lateinit var sessionStore: SessionStore
    private lateinit var adapter: ChildHomeAdapter
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: Button
    private lateinit var btnAdd: Button
    private lateinit var progress: ProgressBar
    private lateinit var rv: RecyclerView

    private val parentStack = mutableListOf<String?>()
    private var currentParentId: String? = null
    private var authToken: String = ""
    private var pendingEditNode: ChildHomeNodeDto? = null

    private val pickItemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val itemId = LibraryItemPickerActivity.parseResultItemId(result.data) ?: return@registerForActivityResult
            val editing = pendingEditNode
            pendingEditNode = null

            if (editing == null) {
                showCreateNodeDialog(itemId)
            } else {
                showEditNodeDialog(editing, itemId)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureInitialized()
    }

    override fun onInitialized() {
        setContentView(R.layout.activity_child_home)

        sessionStore = SessionStore(this)
        authToken = sessionStore.token.orEmpty()

        tvTitle = findViewById(R.id.tvChildHomeTitle)
        btnBack = findViewById(R.id.btnChildHomeBack)
        progress = findViewById(R.id.progressChildHome)
        rv = findViewById(R.id.rvChildHome)
        btnAdd = findViewById(R.id.btnChildHomeAdd)
        btnAdd.visibility = if (isEditorMode) View.VISIBLE else View.GONE

        btnAdd.setOnClickListener {
            openAddNode()
        }

        adapter = ChildHomeAdapter(
            authToken = authToken,
            isEditorMode = isEditorMode,
            onNodeClick = { node -> onNodeClicked(node) },
            onEditClick = { node -> openEditNode(node) },
            onDeleteClick = { node -> confirmDeleteNode(node) }
        )

        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = adapter

        btnBack.setOnClickListener {
            if (parentStack.isNotEmpty()) {
                currentParentId = parentStack.removeAt(parentStack.lastIndex)
                loadNodes(currentParentId)
            }
        }

        loadNodes(null)
    }

    private fun openAddNode() {
        pendingEditNode = null
        pickItemLauncher.launch(
            LibraryItemPickerActivity.createIntent(
                this,
                LibraryItemPickerActivity.TargetMode.USER_AVATAR
            )
        )
    }

    private fun openEditNode(node: ChildHomeNodeDto) {
        pendingEditNode = node
        pickItemLauncher.launch(
            LibraryItemPickerActivity.createIntent(
                this,
                LibraryItemPickerActivity.TargetMode.USER_AVATAR
            )
        )
    }

    private fun confirmDeleteNode(node: ChildHomeNodeDto) {
        AlertDialog.Builder(this)
            .setTitle("Delete node?")
            .setMessage(node.item?.label ?: node.id)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteNode(node)
            }
            .show()
    }

    private fun showCreateNodeDialog(itemId: String) {
        val types = arrayOf("MENU", "ACTION")

        AlertDialog.Builder(this)
            .setTitle("Node type")
            .setItems(types) { _, which ->
                val type = types[which]
                createNode(itemId, type)
            }
            .show()
    }

    private fun showEditNodeDialog(node: ChildHomeNodeDto, newItemId: String) {
        val types = arrayOf("MENU", "ACTION")
        val checked = if (node.type == "ACTION") 1 else 0

        AlertDialog.Builder(this)
            .setTitle("Node type")
            .setSingleChoiceItems(types, checked) { dialog, which ->
                dialog.dismiss()
                updateNode(
                    node = node,
                    newItemId = newItemId,
                    newType = types[which]
                )
            }
            .show()
    }

    private fun createNode(itemId: String, type: String) {
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.createChildHomeNode(
                        auth = "Bearer $authToken",
                        body = CreateChildHomeNodeRequest(
                            itemId = itemId,
                            parentId = currentParentId,
                            type = type,
                            targetMode = "ALL_PARENTS",
                            blinkEnabled = true,
                            blinkSeconds = 60
                        )
                    )
                }

                loadNodes(currentParentId)
            } catch (e: Exception) {
                Toast.makeText(this@ChildHomeActivity, "Create failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun updateNode(
        node: ChildHomeNodeDto,
        newItemId: String,
        newType: String
    ) {
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateChildHomeNode(
                        auth = "Bearer $authToken",
                        nodeId = node.id,
                        body = UpdateChildHomeNodeRequest(
                            itemId = newItemId,
                            type = newType
                        )
                    )
                }

                loadNodes(currentParentId)
            } catch (e: Exception) {
                Toast.makeText(this@ChildHomeActivity, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun deleteNode(node: ChildHomeNodeDto) {
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.deleteChildHomeNode(
                        auth = "Bearer $authToken",
                        nodeId = node.id
                    )
                }

                loadNodes(currentParentId)
            } catch (e: Exception) {
                Toast.makeText(this@ChildHomeActivity, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun onNodeClicked(node: ChildHomeNodeDto) {
        if (isEditorMode) {
            if (node.type == "MENU") {
                parentStack.add(currentParentId)
                currentParentId = node.id
                loadNodes(currentParentId)
            } else {
                openEditNode(node)
            }
            return
        }

        when (node.type) {
            "MENU" -> {
                parentStack.add(currentParentId)
                currentParentId = node.id
                loadNodes(currentParentId)
            }
            "ACTION" -> requestAction(node)
        }
    }

    private fun loadNodes(parentId: String?) {
        progress.visibility = View.VISIBLE
        btnBack.visibility = if (parentStack.isEmpty()) View.GONE else View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getChildHomeNodes(
                        auth = "Bearer $authToken",
                        parentId = parentId
                    )
                }

                adapter.submitItems(response.items)
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChildHomeActivity,
                    "Failed to load child home: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun requestAction(node: ChildHomeNodeDto) {
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.requestChildHomeAction(
                        auth = "Bearer $authToken",
                        nodeId = node.id
                    )
                }

                if (response.blinkEnabled) {
                    blinkRecycler(response.blinkSeconds)
                }

                Toast.makeText(
                    this@ChildHomeActivity,
                    "Sent",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChildHomeActivity,
                    "Failed to send request: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun blinkRecycler(seconds: Int) {
        val durationMs = seconds.coerceAtLeast(1) * 1000L

        val animation = AlphaAnimation(1.0f, 0.25f).apply {
            duration = 400
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        rv.startAnimation(animation)

        rv.postDelayed({
            rv.clearAnimation()
        }, durationMs)
    }
}