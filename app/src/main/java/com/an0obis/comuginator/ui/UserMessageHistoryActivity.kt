package com.an0obis.comuginator.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacMessageListItemDto
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.storage.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson

class UserMessageHistoryActivity : AppCompatActivity() {

    private lateinit var store: SessionStore

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rvHistory: RecyclerView

    private lateinit var historyAdapter: MessageHistoryAdapter

    private var targetUserId: String = ""
    private var targetUserName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_message_history)

        store = SessionStore(this)

        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        rvHistory = findViewById(R.id.rvHistory)

        targetUserId = intent.getStringExtra("targetUserId").orEmpty()
        targetUserName = intent.getStringExtra("targetUserName").orEmpty()

        if (targetUserId.isBlank()) {
            Toast.makeText(this, "No target user", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvTitle.text = "History with ${if (targetUserName.isBlank()) targetUserId else targetUserName}"

        historyAdapter = MessageHistoryAdapter { item ->
            repeatMessage(item)
        }
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        loadHistory()
    }

    private fun repeatMessage(item: AacMessageListItemDto) {
        val intent = Intent(this, ComposeMessageActivity::class.java).apply {
            putExtra("targetUserId", targetUserId)
            putExtra("targetUserName", targetUserName)
            putExtra(
                ComposeMessageActivity.EXTRA_INITIAL_MESSAGE_CARDS,
                Gson().toJson(item.message)
            )
            putExtra(
                ComposeMessageActivity.EXTRA_INITIAL_REPLY_CARDS,
                Gson().toJson(item.suggestedReplies)
            )
        }
        startActivity(intent)
    }

    private fun authHeaderOrThrow(): String {
        val token = store.token ?: error("No token in SessionStore")
        return "Bearer $token"
    }

    private fun loadHistory() {
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Loading history..."

        lifecycleScope.launch {
            try {
                val family = withContext(Dispatchers.IO) {
                    ApiClient.api.getMyFamily(authHeaderOrThrow())
                }

                val myUserId = family.me.userId

                val messagesResponse = withContext(Dispatchers.IO) {
                    ApiClient.api.getAacMessages(
                        auth = authHeaderOrThrow(),
                        scope = "all"
                    )
                }

                val filtered = messagesResponse.items.filter { msg ->
                    (msg.fromUserId == myUserId && msg.toUserId == targetUserId) ||
                            (msg.fromUserId == targetUserId && msg.toUserId == myUserId)
                }

                historyAdapter.submitItems(filtered)
                tvStatus.text = if (filtered.isEmpty()) "No messages yet" else "Loaded ${filtered.size} messages"
            } catch (e: Exception) {
                tvStatus.text = "Failed to load history"
                Toast.makeText(
                    this@UserMessageHistoryActivity,
                    "Failed to load history: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}