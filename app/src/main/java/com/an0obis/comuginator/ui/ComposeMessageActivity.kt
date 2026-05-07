package com.an0obis.comuginator.ui

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.SendAacMessageRequest
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ComposeMessageActivity : BaseActivity() {

    companion object {
        const val EXTRA_INITIAL_REPLY_CARDS = "initial_reply_cards"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vm: ComposeMessageViewModel by viewModels()

    private lateinit var tvTarget: TextView
    private lateinit var tvStatus: TextView
    private lateinit var rgMode: RadioGroup
    private lateinit var rbNormal: RadioButton
    private lateinit var rbSequence: RadioButton
    private lateinit var rvReplyCards: RecyclerView
    private lateinit var btnAddFromLibrary: Button
    private lateinit var btnAddLibrarySet: Button
    private lateinit var btnSendMessage: Button

    private lateinit var replyAdapter: SelectedCardAdapter

    private val pickLibraryItemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val itemId = LibraryItemPickerActivity.parseResultItemId(result.data)
                ?: return@registerForActivityResult

            addLibraryItemById(itemId)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (redirectedByRoleGuard) return

        setContentView(R.layout.activity_compose_message)

        store = SessionStore(this)

        if (!vm.initialized) {
            vm.targetUserId = intent.getStringExtra("targetUserId").orEmpty()
            vm.targetUserName = intent.getStringExtra("targetUserName").orEmpty()
            vm.mode = "NORMAL"
            vm.initialized = true
        }

        bindViews()
        setupRecycler()
        setupMode()
        setupButtons()
        render()
    }

    private fun bindViews() {
        tvTarget = findViewById(R.id.tvTarget)
        tvStatus = findViewById(R.id.tvStatus)
        rgMode = findViewById(R.id.rgMode)
        rbNormal = findViewById(R.id.rbNormal)
        rbSequence = findViewById(R.id.rbSequence)
        rvReplyCards = findViewById(R.id.rvReplyCards)
        btnAddFromLibrary = findViewById(R.id.btnAddFromLibrary)
        btnAddLibrarySet = findViewById(R.id.btnAddLibrarySet)
        btnSendMessage = findViewById(R.id.btnSendMessage)
    }

    private fun setupRecycler() {
        replyAdapter = SelectedCardAdapter(
            onClick = { card ->
                vm.replyCards.remove(card)
                render()
            }
        )

        rvReplyCards.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvReplyCards.adapter = replyAdapter
    }

    private fun setupMode() {
        when (vm.mode) {
            "SEQUENCE" -> rbSequence.isChecked = true
            else -> rbNormal.isChecked = true
        }

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            vm.mode = when (checkedId) {
                R.id.rbSequence -> "SEQUENCE"
                else -> "NORMAL"
            }
            render()
        }
    }

    private fun setupButtons() {
        btnAddFromLibrary.setOnClickListener {
            pickLibraryItemLauncher.launch(
                LibraryItemPickerActivity.createIntent(
                    this,
                    LibraryItemPickerActivity.TargetMode.PICK_SINGLE
                )
            )
        }

        btnAddLibrarySet.setOnClickListener {
            showChooseLibrarySetDialog()
        }

        btnSendMessage.setOnClickListener {
            sendMessage()
        }
    }

    private fun render() {
        tvTarget.text = getString(R.string.send_to_name, vm.targetUserName)

        replyAdapter.submitItems(vm.replyCards.toList())

        tvStatus.text = when {
            vm.replyCards.isEmpty() ->
                getString(R.string.compose_no_replies)

            vm.mode == "SEQUENCE" ->
                getString(R.string.compose_sequence_status, vm.replyCards.size)

            vm.replyCards.size == 1 ->
                getString(R.string.compose_normal_one_reply_status)

            else ->
                getString(R.string.compose_normal_question_status, vm.replyCards.size)
        }
    }

    private fun addLibraryItemById(itemId: String) {
        scope.launch {
            try {
                val response = ApiClient.api.getLibraryItems(
                    auth = store.authHeaderOrThrow(),
                    source = null
                )

                val card = response.items.firstOrNull { it.id == itemId }
                    ?: error("Library item not found: $itemId")

                runOnUiThread {
                    if (vm.replyCards.none { it.id == card.id }) {
                        vm.replyCards.add(card)
                    }
                    render()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@ComposeMessageActivity,
                        getString(R.string.load_library_failed, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showChooseLibrarySetDialog() {
        scope.launch {
            try {
                val auth = store.authHeaderOrThrow()
                val response = ApiClient.api.getLibrarySets(auth)
                val sets = response.sets

                if (sets.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(
                            this@ComposeMessageActivity,
                            getString(R.string.no_library_sets),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val labels = sets.map { "${it.name} (${it.itemsCount})" }.toTypedArray()

                runOnUiThread {
                    AlertDialog.Builder(this@ComposeMessageActivity)
                        .setTitle(R.string.choose_set)
                        .setItems(labels) { _, which ->
                            addAllItemsFromSet(sets[which].id)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@ComposeMessageActivity,
                        getString(R.string.load_sets_failed, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun addAllItemsFromSet(setId: String) {
        scope.launch {
            try {
                val auth = store.authHeaderOrThrow()
                val response = ApiClient.api.getLibrarySet(auth, setId)
                val items = response.set.items

                runOnUiThread {
                    if (items.isEmpty()) {
                        Toast.makeText(
                            this@ComposeMessageActivity,
                            getString(R.string.set_is_empty),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@runOnUiThread
                    }

                    val existingIds = vm.replyCards.map { it.id }.toSet()
                    vm.replyCards.addAll(items.filter { it.id !in existingIds })

                    Toast.makeText(
                        this@ComposeMessageActivity,
                        resources.getQuantityString(
                            R.plurals.added_items,
                            items.size,
                            items.size
                        ),
                        Toast.LENGTH_SHORT
                    ).show()

                    render()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@ComposeMessageActivity,
                        getString(R.string.load_sets_failed, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun buildMessageCards(): List<AacCardDto> {
        val symbol = when {
            vm.mode == "SEQUENCE" -> "PLAN"
            vm.replyCards.size <= 1 -> "EXCLAMATION"
            else -> "QUESTION"
        }

        return listOf(
            AacCardDto(
                id = symbol,
                label = symbol,
                imageUrl = "",
                source = "SYSTEM",
                sourceRef = symbol
            )
        )
    }

    private fun sendMessage() {
        if (vm.replyCards.isEmpty()) {
            Toast.makeText(this, R.string.compose_add_at_least_one_reply, Toast.LENGTH_SHORT).show()
            return
        }

        if (vm.mode == "SEQUENCE" && vm.replyCards.size < 2) {
            Toast.makeText(this, R.string.compose_sequence_needs_two_steps, Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            try {
                ApiClient.api.sendAacMessage(
                    auth = store.authHeaderOrThrow(),
                    body = SendAacMessageRequest(
                        targetUserId = vm.targetUserId,
                        mode = vm.mode,
                        cards = buildMessageCards(),
                        suggestedReplies = vm.replyCards
                    )
                )

                runOnUiThread {
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@ComposeMessageActivity,
                        e.message ?: getString(R.string.send_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}