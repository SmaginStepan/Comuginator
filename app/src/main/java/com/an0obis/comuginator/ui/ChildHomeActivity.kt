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

class ChildHomeActivity : BaseActivity() {

    private lateinit var sessionStore: SessionStore
    private lateinit var adapter: ChildHomeAdapter

    private lateinit var tvTitle: TextView
    private lateinit var btnBack: Button
    private lateinit var progress: ProgressBar
    private lateinit var rv: RecyclerView

    private val parentStack = mutableListOf<String?>()
    private var currentParentId: String? = null
    private var authToken: String = ""

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

        adapter = ChildHomeAdapter(authToken) { node ->
            onNodeClicked(node)
        }

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

    private fun onNodeClicked(node: ChildHomeNodeDto) {
        when (node.type) {
            "MENU" -> {
                parentStack.add(currentParentId)
                currentParentId = node.id
                tvTitle.text = node.item?.label ?: "Menu"
                loadNodes(node.id)
            }

            "ACTION" -> {
                requestAction(node)
            }
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