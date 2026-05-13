package com.an0obis.comuginator.ui

import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.api.AacMessageDetailsDto
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
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
import androidx.core.app.NotificationManagerCompat
import com.an0obis.comuginator.service.NotificationHelper
import com.an0obis.comuginator.ui.lib.TimerDrawable
import kotlinx.coroutines.delay

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

    private lateinit var messageAdapter: CardAdapter
    private lateinit var repliesAdapter: CardAdapter
    private lateinit var tvRepliesLabel: TextView
    private lateinit var ivCurrentReply: ImageView
    private lateinit var ivFromAvatar: ImageView
    private lateinit var btnClose: Button

    private var sequenceStepIndex = 0
    private var isSequenceBlinking = false
    private var isWaitTimerRunning = false
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
        btnClose = findViewById(R.id.btnClose)

        btnClose.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        if (messageId.isBlank()) {
            finish()
            return
        }

        cancelCurrentNotification()

        ensureInitialized()
    }

    private fun cancelCurrentNotification() {
        if (messageId.isBlank()) return

        NotificationManagerCompat.from(this).cancel(
            NotificationHelper.notificationIdForMessage(messageId)
        )
    }

    override fun onInitialized() {

        messageAdapter = CardAdapter()

        repliesAdapter = CardAdapter(
            onClick = { card ->
                if (!isSendingReply && !isSequenceBlinking) {
                    handleReplyClick(card)
                }
            },
            alphaProvider = { card ->
                val message = currentMessage

                if (message?.mode != "SEQUENCE") {
                    1f
                } else {
                    val index =
                        message.suggestedReplies.indexOfFirst { it.id == card.id }
                    val isPassed = index < sequenceStepIndex
                    Log.d("IncomingMessageActivity","alpha $index $sequenceStepIndex $isPassed")
                    if (isPassed) {
                        0.35f
                    } else {
                        1f
                    }
                }
            }
        )

        rvMessageCards.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvMessageCards.adapter = messageAdapter

        rvSuggestedReplies.layoutManager = GridLayoutManager(this, 3)
        rvSuggestedReplies.adapter = repliesAdapter
        rvSuggestedReplies.itemAnimator = null

        loadMessage()
    }

    private fun formatTimerLabel(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60

        return if (minutes > 0) {
            "⏱ %d:%02d".format(minutes, secs)
        } else {
            "⏱ ${seconds}s"
        }
    }

    private fun startCurrentWaitStep(message: AacMessageDetailsDto) {
        if (isWaitTimerRunning) return

        val waitStep = message.suggestedReplies.getOrNull(sequenceStepIndex) ?: return
        val seconds = waitStep.seconds ?: return

        isWaitTimerRunning = true
        rvSuggestedReplies.isEnabled = false

        lifecycleScope.launch {
            for (remaining in seconds downTo 1) {
                tvCurrentReply.text = getString(R.string.wait_timer_remaining, remaining)

                rvSuggestedReplies.post {
                    val holder = rvSuggestedReplies
                        .findViewHolderForAdapterPosition(sequenceStepIndex)
                            as? CardAdapter.CardViewHolder

                    val drawable = TimerDrawable().apply {
                        progress = remaining.toFloat() / seconds.toFloat()
                        text = if (remaining >= 60) {
                            "${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}"
                        } else {
                            remaining.toString()
                        }
                    }

                    holder?.ivCardImage?.setImageDrawable(drawable)
                    holder?.tvCardLabel?.text = formatTimerLabel(remaining)
                }

                delay(1000)
            }

            isWaitTimerRunning = false
            rvSuggestedReplies.isEnabled = true

            val isLastStep = sequenceStepIndex >= message.suggestedReplies.lastIndex

            if (isLastStep) {
                sendSequenceCompletedAfterWait()
            } else {
                sequenceStepIndex += 1
                renderSequenceReplies(message)
            }
        }
    }

    private suspend fun sendSequenceCompletedAfterWait() {
        val completionCard = AacCardDto(
            id = "SEQUENCE_COMPLETED",
            label = getString(R.string.sequence_completed),
            imageUrl = "",
            source = "SYSTEM",
            sourceRef = "SEQUENCE_COMPLETED"
        )

        try {
            withContext(Dispatchers.IO) {
                ApiClient.replyToAacMessage(
                    authHeader = store.authHeaderOrThrow(),
                    messageId = messageId,
                    requestBody = SendAacReplyRequest(reply = completionCard)
                )
            }
        } catch (e: Exception) {
            // Не блокируем закрытие экрана, но логируем.
            e.printStackTrace()
        }

        cancelCurrentNotification()
        setResult(RESULT_OK)
        finish()
    }

    private fun handleReplyClick(card: AacCardDto) {
        val message = currentMessage ?: return
        if (isCurrentStepWait(message)) {
            return
        }

        if (message.mode == "SEQUENCE") {
            val currentCard = currentSuggestedCards(message).getOrNull(sequenceStepIndex) ?: return

            if (card.id != currentCard.id) {
                Toast.makeText(
                    this,
                    getString(R.string.sequence_choose_current_step),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            sendSequenceStepReply(currentCard)
        } else {
            sendReply(card)
        }
    }

    private fun sendSequenceStepReply(card: AacCardDto) {
        val message = currentMessage ?: return

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

                tvCurrentReply.text = getString(R.string.reply_prefix, card.label)
                loadProtectedImage(card.imageUrl, ivCurrentReply)
                ivCurrentReply.isVisible = true

                progressBar.visibility = View.GONE

                blinkSelectedReplyCard(card)

                val isLastStep = sequenceStepIndex >= message.suggestedReplies.lastIndex

                if (isLastStep) {
                    cancelCurrentNotification()
                    Toast.makeText(
                        this@IncomingMessageActivity,
                        getString(R.string.reply_sent),
                        Toast.LENGTH_SHORT
                    ).show()

                    setResult(RESULT_OK)
                    finish()
                } else {
                    sequenceStepIndex += 1
                    renderSequenceReplies(message)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@IncomingMessageActivity,
                    getString(R.string.failed_send_reply, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isSendingReply = false
                isSequenceBlinking = false
                progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun blinkSelectedReplyCard(card: AacCardDto) {
        isSequenceBlinking = true

        val message = currentMessage
        val cards = message?.let { currentSuggestedCards(it) } ?: emptyList()
        val selectedIndex = cards.indexOfFirst { it.id == card.id }

        val views = cards.mapIndexedNotNull { index, _ ->
            rvSuggestedReplies.findViewHolderForAdapterPosition(index)?.itemView
        }

        views.forEachIndexed { index, view ->
            if (index == selectedIndex) {
                view.visibility = View.VISIBLE
                view.alpha = 1f
            } else {
                view.visibility = View.INVISIBLE
            }
        }

        repeat(6) {
            val selectedView =
                rvSuggestedReplies.findViewHolderForAdapterPosition(selectedIndex)?.itemView

            selectedView?.alpha = if (it % 2 == 0) 0.15f else 1f
            delay(500)
        }

        views.forEach { view ->
            view.visibility = View.VISIBLE
        }

        rvSuggestedReplies.post {
            applySequenceAlphas()
        }

        currentMessage?.let { repliesAdapter.submitItems(currentSuggestedCards(it)) }
    }

    private fun currentSuggestedCards(message: AacMessageDetailsDto): List<AacCardDto> {
        return message.suggestedReplies.map { it.toCardDto() }
    }

    private fun isCurrentStepWait(message: AacMessageDetailsDto): Boolean {
        return message.suggestedReplies
            .getOrNull(sequenceStepIndex)
            ?.isWait() == true
    }

    private fun applySequenceAlphas() {
        val message = currentMessage ?: return
        if (message.mode != "SEQUENCE") return

        message.suggestedReplies.forEachIndexed { index, _ ->
            val view = rvSuggestedReplies
                .findViewHolderForAdapterPosition(index)
                ?.itemView

            view?.visibility = View.VISIBLE
            view?.alpha = if (index < sequenceStepIndex) 0.35f else 1f
        }
    }
    private fun renderSequenceReplies(message: AacMessageDetailsDto) {
        val replies = currentSuggestedCards(message)

        repliesAdapter.submitItems(replies.toList())

        tvRepliesLabel.text = getString(
            R.string.sequence_step_status,
            sequenceStepIndex + 1,
            replies.size
        )

        rvSuggestedReplies.isVisible = replies.isNotEmpty()
        tvRepliesLabel.isVisible = replies.isNotEmpty()
        btnClose.isVisible = false

        if (isCurrentStepWait(message)) {
            startCurrentWaitStep(message)
        }

        rvSuggestedReplies.post {
            applySequenceAlphas()
        }
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
        repliesAdapter.submitItems(currentSuggestedCards(message))
        val hasSuggestedReplies = message.suggestedReplies.isNotEmpty()

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

        tvFromUser.text = message.fromUser.name
        loadProtectedImage(message.fromUser.avatarImageUrl, ivFromAvatar)

        if (mode == MODE_REPLY) {
            tvFromUser.text = getString(R.string.reply_from,message.toUser.name)
            tvCurrentReply.text = message.reply?.let {
                getString(R.string.reply_prefix, message.reply.reply.label)
            } ?: getString(R.string.reply_not_available)

            repliesAdapter.submitItems(
                message.reply?.let { listOf(it.reply) } ?: emptyList()
            )
            rvSuggestedReplies.isVisible = true

            tvRepliesLabel.isVisible = false

            btnClose.isVisible = true
            return
        } else {
            if (message.mode == "SEQUENCE") {
                val currentReplyId = message.reply?.reply?.id

                sequenceStepIndex = if (currentReplyId == null) {
                    0
                } else {
                    val currentIndex = currentSuggestedCards(message).indexOfFirst { it.id == currentReplyId }
                    if (currentIndex >= 0) currentIndex + 1 else 0
                }

                if (sequenceStepIndex > message.suggestedReplies.lastIndex) {
                    sequenceStepIndex = message.suggestedReplies.lastIndex
                }

                renderSequenceReplies(message)
            } else {
                sequenceStepIndex = 0
                rvSuggestedReplies.isVisible = hasSuggestedReplies
                tvRepliesLabel.isVisible = hasSuggestedReplies
                btnClose.isVisible = !hasSuggestedReplies
            }
        }

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

                tvCurrentReply.text = getString(R.string.reply_prefix, card.label)
                loadProtectedImage(card.imageUrl, ivCurrentReply)
                ivCurrentReply.isVisible = true

                progressBar.visibility = View.GONE

                blinkSelectedReplyCard(card)

                cancelCurrentNotification()

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
                isSequenceBlinking = false
                progressBar.visibility = View.GONE
            }
        }
    }
}