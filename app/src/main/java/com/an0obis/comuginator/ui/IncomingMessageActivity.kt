package com.an0obis.comuginator.ui

import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.api.AacMessageDetailsDto
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.SendAacReplyRequest
import com.an0obis.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.GridLayoutManager
import coil.Coil
import coil.request.ImageRequest

class IncomingMessageActivity : BaseActivity() {

    companion object {
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_COMMAND_ID = "command_id"
        const val EXTRA_MODE = "mode"
        const val MODE_MESSAGE = "message"
        const val MODE_REPLY = "reply"
    }
    private lateinit var tvFromUser: TextView
    private lateinit var tvCreatedAt: TextView
    private lateinit var tvCurrentReply: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rvMessageCards: RecyclerView
    private lateinit var rvSuggestedReplies: RecyclerView

    private lateinit var messageAdapter: SimpleCardAdapter
    private lateinit var repliesAdapter: SimpleCardAdapter
    private lateinit var tvRepliesLabel: TextView
    private lateinit var ivCurrentReply: ImageView
    private lateinit var ivFromAvatar: ImageView
    private var messageId: String = ""
    private var commandId: String = ""
    private var ackSent = false
    private var currentMessage: AacMessageDetailsDto? = null
    private var isSendingReply = false
    private var mode: String = MODE_MESSAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_message)

        tvFromUser = findViewById(R.id.tvFromUser)
        tvCreatedAt = findViewById(R.id.tvCreatedAt)
        tvCurrentReply = findViewById(R.id.tvCurrentReply)
        progressBar = findViewById(R.id.progressBar)
        rvMessageCards = findViewById(R.id.rvMessageCards)
        rvSuggestedReplies = findViewById(R.id.rvSuggestedReplies)
        tvRepliesLabel = findViewById(R.id.tvRepliesLabel)
        ivCurrentReply = findViewById(R.id.ivCurrentReply)

        ivFromAvatar = findViewById(R.id.ivFromAvatar)
        tvFromUser = findViewById(R.id.tvFromUser)
        messageId = intent.getStringExtra(EXTRA_MESSAGE_ID).orEmpty()
        commandId = intent.getStringExtra(EXTRA_COMMAND_ID).orEmpty()
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_MESSAGE

        if (messageId.isBlank()) {
            finish()
            return
        }

        ensureInitialized()
    }

    override fun onInitialized() {

        messageAdapter = SimpleCardAdapter { }

        repliesAdapter = SimpleCardAdapter { card ->
            if (!isSendingReply) {
                sendReply(card)
            }
        }

        rvMessageCards.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvMessageCards.adapter = messageAdapter

        rvSuggestedReplies.layoutManager = GridLayoutManager(this, 3)
        rvSuggestedReplies.adapter = repliesAdapter

        loadMessage()
    }

    private fun loadMessage() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val authHeader = store.authHeader() ?: return@launch
                val message = withContext(Dispatchers.IO) {
                    ApiClient.getAacMessageWithAuthHeader(authHeader, messageId)
                }

                currentMessage = message
                renderMessage(message)

                if (!ackSent && commandId.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        ApiClient.api.ackCommand(
                            auth = store.authHeaderOrThrow(),
                            commandId = commandId
                        )
                    }
                    ackSent = true
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@IncomingMessageActivity,
                    getString(R.string.failed_to_load_message, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun renderMessage(message: AacMessageDetailsDto) {
        tvCreatedAt.text = getString(R.string.created_at, message.createdAt)

        messageAdapter.submitItems(message.message)
        repliesAdapter.submitItems(message.suggestedReplies)

        tvFromUser.text = message.fromUser.name
        loadProtectedImage(message.fromUser.avatarImageUrl, ivFromAvatar)

        if (message.reply != null) {
            tvCurrentReply.text = getString(R.string.reply_prefix, message.reply.reply.label)
            rvSuggestedReplies.isEnabled = false
            loadProtectedImage(message.reply.reply.imageUrl, ivCurrentReply)
            ivCurrentReply.isVisible = true
        } else {
            tvCurrentReply.text = getString(R.string.no_reply_yet)
                rvSuggestedReplies.isEnabled = true
            ivCurrentReply.isVisible = false
        }

        if (mode == MODE_REPLY) {
            tvFromUser.text = getString(R.string.reply_from,message.toUser.name)
            tvCurrentReply.text = message.reply?.let {
                getString(R.string.reply_prefix, message.reply.reply.label)
            } ?: getString(R.string.reply_not_available)

            repliesAdapter.submitItems(
                message.reply?.let { listOf(it.reply) } ?: emptyList()
            )
            rvSuggestedReplies.isVisible = false

            tvRepliesLabel.isVisible = false

            return
        } else {
            rvSuggestedReplies.isVisible = true

            tvRepliesLabel.isVisible = true
        }

        tvFromUser.text = message.fromUser.name
        loadProtectedImage(message.fromUser.avatarImageUrl, ivFromAvatar)


    }

    private fun loadProtectedImage(url: String?, imageView: ImageView) {
        if (url.isNullOrBlank()) return

        val request = ImageRequest.Builder(this)
            .data(url)
            .addHeader("Authorization", store.authHeaderOrThrow())
            .target(imageView)
            .build()

        Coil.imageLoader(this).enqueue(request)
    }

    private fun sendReply(card: AacCardDto) {
        isSendingReply = true
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.replyToAacMessage(
                        authHeader = store.authHeaderOrThrow(),
                        messageId = messageId,
                        requestBody = SendAacReplyRequest(reply = card)
                    )
                }

                Toast.makeText(
                    this@IncomingMessageActivity,
                    getString(R.string.reply_sent),
                    Toast.LENGTH_SHORT
                ).show()

                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@IncomingMessageActivity,
                    getString(R.string.failed_send_reply, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isSendingReply = false
                progressBar.visibility = View.GONE
            }
        }
    }
}