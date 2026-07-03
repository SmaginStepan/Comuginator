package com.an0obis.comuginator.ui.messaging

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacMessageListItemDto
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.storage.OfflineCache
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore
import com.an0obis.comuginator.ui.base.BaseActivity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserMessageHistoryActivity : BaseActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rvHistory: RecyclerView
    private lateinit var ivTargetAvatar: ImageView
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
        ivTargetAvatar = findViewById(R.id.ivTargetAvatar)

        targetUserId = intent.getStringExtra("targetUserId").orEmpty()
        targetUserName = intent.getStringExtra("targetUserName").orEmpty()

        if (targetUserId.isBlank()) {
            Toast.makeText(this, getString(R.string.no_target_user), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvTitle.text = getString(
            R.string.history_with,
            targetUserName.ifBlank { targetUserId }
        )

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
            putExtra("mode", item.mode)
            putExtra(
                ComposeMessageActivity.EXTRA_INITIAL_REPLY_CARDS,
                Gson().toJson(item.suggestedReplies.map { it.toCardDto() })
            )
            putExtra(ComposeMessageActivity.EXTRA_INITIAL_REQUIRED_REPLY_COUNT, item.requiredReplyCount)
        }
        startActivity(intent)
    }

    private fun loadHistory() {
        progressBar.visibility = View.VISIBLE
        tvStatus.text = getString(R.string.loading_history)

        lifecycleScope.launch {
            val cache = OfflineCache(this@UserMessageHistoryActivity)
            val familyId = store.familyId

            if (SettingsStore(this@UserMessageHistoryActivity).offlineMode) {
                showHistory(cache.loadMessages(familyId) ?: emptyList(), offline = true)
                progressBar.visibility = View.GONE
                return@launch
            }

            try {
                val family = withContext(Dispatchers.IO) {
                    ApiClient.api.getMyFamily(store.authHeaderOrThrow())
                }

                val targetUser = family.users.firstOrNull { it.id == targetUserId }

                withContext(Dispatchers.Main) {
                    if (targetUser != null) {
                        tvTitle.text = getString(
                            R.string.history_with,
                            targetUser.name ?: targetUserName.ifBlank { targetUserId }
                        )

                        loadProtectedImage(targetUser.avatarImageUrl, ivTargetAvatar)
                    }
                }

                val messagesResponse = withContext(Dispatchers.IO) {
                    ApiClient.api.getAacMessages(
                        auth = store.authHeaderOrThrow(),
                        scope = "all"
                    )
                }

                cache.saveMessages(familyId, messagesResponse.items)
                showHistory(messagesResponse.items, offline = false)
            } catch (e: Exception) {
                val cached = cache.loadMessages(familyId)
                if (cached != null) {
                    showHistory(cached, offline = true)
                } else {
                    tvStatus.text = getString(R.string.failed_load_history)
                    Toast.makeText(
                        this@UserMessageHistoryActivity,
                        getString(R.string.failed_load_history_message, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun showHistory(messages: List<AacMessageListItemDto>, offline: Boolean) {
        val myUserId = store.userId

        val filtered = messages.filter { msg ->
            (msg.fromUserId == myUserId && msg.toUserId == targetUserId) ||
                    (msg.fromUserId == targetUserId && msg.toUserId == myUserId)
        }

        historyAdapter.submitItems(filtered)
        val baseText = if (filtered.isEmpty()) getString(R.string.no_messages_yet)
        else resources.getQuantityString(R.plurals.loaded_messages, filtered.size, filtered.size)
        tvStatus.text = if (offline) {
            getString(R.string.status_with_offline, baseText, getString(R.string.offline))
        } else {
            baseText
        }
    }

}