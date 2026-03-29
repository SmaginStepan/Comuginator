package com.example.comuginator.ui

import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.comuginator.api.AacMessageDetailsDto
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.comuginator.R
import com.example.comuginator.api.AacCardDto
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.SendAacReplyRequest
import com.example.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class IncomingMessageActivity : BaseActivity() {

    companion object {
        const val EXTRA_MESSAGE_ID = "message_id"
    }
    private lateinit var tvFromUser: TextView
    private lateinit var tvCreatedAt: TextView
    private lateinit var tvCurrentReply: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rvMessageCards: RecyclerView
    private lateinit var rvSuggestedReplies: RecyclerView

    private lateinit var messageAdapter: SimpleCardAdapter
    private lateinit var repliesAdapter: SimpleCardAdapter

    private var messageId: String = ""
    private var authToken: String = ""
    private var currentMessage: AacMessageDetailsDto? = null
    private var isSendingReply = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_message)

        tvFromUser = findViewById(R.id.tvFromUser)
        tvCreatedAt = findViewById(R.id.tvCreatedAt)
        tvCurrentReply = findViewById(R.id.tvCurrentReply)
        progressBar = findViewById(R.id.progressBar)
        rvMessageCards = findViewById(R.id.rvMessageCards)
        rvSuggestedReplies = findViewById(R.id.rvSuggestedReplies)

        messageId = intent.getStringExtra(EXTRA_MESSAGE_ID).orEmpty()
        if (messageId.isBlank()) {
            finish()
            return
        }

        authToken = try {
            requireToken()
        } catch (e: Exception) {
            Toast.makeText(this, "No auth token", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        messageAdapter = SimpleCardAdapter { }

        repliesAdapter = SimpleCardAdapter { card ->
            if (!isSendingReply) {
                sendReply(card)
            }
        }

        rvMessageCards.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvMessageCards.adapter = messageAdapter

        rvSuggestedReplies.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSuggestedReplies.adapter = repliesAdapter

        ensureInitialized()
    }

    override fun onInitialized() {
        loadMessage()
    }

    private fun loadMessage() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    ApiClient.getAacMessage(authToken, messageId)
                }

                currentMessage = message
                renderMessage(message)
            } catch (e: Exception) {
                Toast.makeText(
                    this@IncomingMessageActivity,
                    "Failed to load message: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun renderMessage(message: AacMessageDetailsDto) {
        tvFromUser.text = "From: ${message.fromUser.name ?: message.fromUser.id}"
        tvCreatedAt.text = "Created: ${message.createdAt}"

        messageAdapter.submitItems(message.message)
        repliesAdapter.submitItems(message.suggestedReplies)

        if (message.reply != null) {
            tvCurrentReply.text = "Reply: ${message.reply.reply.label}"
            rvSuggestedReplies.isEnabled = false
        } else {
            tvCurrentReply.text = "No reply yet"
            rvSuggestedReplies.isEnabled = true
        }
    }

    private fun sendReply(card: AacCardDto) {
        isSendingReply = true
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.replyToAacMessage(
                        authToken = authToken,
                        messageId = messageId,
                        requestBody = SendAacReplyRequest(reply = card)
                    )
                }

                Toast.makeText(
                    this@IncomingMessageActivity,
                    "Reply sent",
                    Toast.LENGTH_SHORT
                ).show()

                loadMessage()
            } catch (e: Exception) {
                Toast.makeText(
                    this@IncomingMessageActivity,
                    "Failed to send reply: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isSendingReply = false
                progressBar.visibility = View.GONE
            }
        }
    }
}