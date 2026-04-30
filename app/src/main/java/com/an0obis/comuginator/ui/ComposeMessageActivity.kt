package com.an0obis.comuginator.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.api.SendAacMessageRequest
import com.an0obis.comuginator.storage.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.app.AlertDialog
import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.activity.viewModels
import com.an0obis.comuginator.ui.base.BaseActivity

class ComposeMessageActivity : BaseActivity() {

    companion object {
        const val EXTRA_INITIAL_MESSAGE_CARDS = "initial_message_cards"
        const val EXTRA_INITIAL_REPLY_CARDS = "initial_reply_cards"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var btnAddPhoto: Button

    private lateinit var btnLoadLibrarySet: Button
    private lateinit var tvTarget: TextView
    private lateinit var tvMessageHeader: TextView
    private lateinit var tvRepliesHeader: TextView
    private lateinit var btnSendMessage: Button
    private lateinit var rvSelectedCards: RecyclerView
    private lateinit var rvReplyCards: RecyclerView

    private lateinit var selectedAdapter: SelectedCardAdapter
    private lateinit var replyAdapter: SelectedCardAdapter

    private lateinit var targetUserId: String
    private lateinit var targetUserName: String

    private val pickLibraryItemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val itemId = LibraryItemPickerActivity.parseResultItemId(result.data) ?: return@registerForActivityResult
            addLibraryItemById(itemId)
        }

    private val vm: ComposeMessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_message)

        store = SessionStore(this)

        targetUserId = intent.getStringExtra("targetUserId").orEmpty()
        targetUserName = intent.getStringExtra("targetUserName").orEmpty()


        tvTarget = findViewById(R.id.tvTarget)
        tvMessageHeader = findViewById(R.id.tvMessageHeader)
        tvRepliesHeader = findViewById(R.id.tvRepliesHeader)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        rvSelectedCards = findViewById(R.id.rvSelectedCards)
        rvReplyCards = findViewById(R.id.rvReplyCards)

        if (!vm.initialized) {
            vm.targetUserId = intent.getStringExtra("targetUserId").orEmpty()
            vm.targetUserName = intent.getStringExtra("targetUserName").orEmpty()

            vm.selectedCards.addAll(parseInitialCards(EXTRA_INITIAL_MESSAGE_CARDS))
            vm.replyCards.addAll(parseInitialCards(EXTRA_INITIAL_REPLY_CARDS))

            vm.initialized = true
        }

        targetUserId = vm.targetUserId
        targetUserName = vm.targetUserName

        selectedAdapter = SelectedCardAdapter(
            onClick = { card ->
                vm.selectedCards.remove(card)
                selectedAdapter.submitItems(vm.selectedCards.toList())
            }
        )

        replyAdapter = SelectedCardAdapter(
            onClick = { card ->
                vm.replyCards.remove(card)
                replyAdapter.submitItems(vm.replyCards.toList())
            }
        )


        rvSelectedCards.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSelectedCards.adapter = selectedAdapter

        rvReplyCards.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvReplyCards.adapter = replyAdapter


        selectedAdapter.submitItems(vm.selectedCards.toList())
        replyAdapter.submitItems(vm.replyCards.toList())

        tvTarget.text = getString(R.string.send_to_name, targetUserName)

        tvMessageHeader.setOnClickListener {
            vm.currentAddMode = ComposeMessageViewModel.AddMode.MESSAGE
            updateModeUi()
        }

        tvRepliesHeader.setOnClickListener {
            vm.currentAddMode = ComposeMessageViewModel.AddMode.REPLY
            updateModeUi()
        }

        btnSendMessage.setOnClickListener {
            sendMessage()
        }

        btnAddPhoto = findViewById(R.id.btnAddPhoto)
        btnLoadLibrarySet = findViewById(R.id.btnLoadLibrarySet)

        btnAddPhoto.setOnClickListener {
            val intent = Intent(this, LibraryItemPickerActivity::class.java)
            pickLibraryItemLauncher.launch(intent)
        }

        btnLoadLibrarySet.setOnClickListener {
            showChooseLibrarySetDialog()
        }

        updateModeUi()

        if (vm.replyCards.isEmpty()) {
            loadDefaultReplies()
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
                    addCardToCurrentMode(card)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    tvTarget.text = getString(R.string.load_library_failed, e.message)
                }
            }
        }
    }

    private fun addCardToCurrentMode(card: AacCardDto) {
        when (vm.currentAddMode) {
            ComposeMessageViewModel.AddMode.MESSAGE -> {
                vm.selectedCards.add(card)
                selectedAdapter.submitItems(vm.selectedCards.toList())
            }

            ComposeMessageViewModel.AddMode.REPLY -> {
                vm.replyCards.add(card)
                replyAdapter.submitItems(vm.replyCards.toList())
            }
        }
    }
    private fun updateModeUi() {
        val activeColor = 0xFF4444FF.toInt()
        val inactiveColor = 0x00000000

        tvMessageHeader.setBackgroundColor(
            if (vm.currentAddMode == ComposeMessageViewModel.AddMode.MESSAGE) activeColor else inactiveColor
        )
        tvRepliesHeader.setBackgroundColor(
            if (vm.currentAddMode == ComposeMessageViewModel.AddMode.REPLY) activeColor else inactiveColor
        )
    }

    private fun parseInitialCards(extraName: String): List<AacCardDto> {
        val json = intent.getStringExtra(extraName).orEmpty()
        if (json.isBlank()) return emptyList()

        return try {
            val type = object : TypeToken<List<AacCardDto>>() {}.type
            Gson().fromJson<List<AacCardDto>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ComposeMessageActivity", "parseInitialCards failed", e)
            emptyList()
        }
    }

    private fun loadDefaultReplies() {
        scope.launch {
            try {
                val response = ApiClient.api.searchArasaac(
                    auth = store.authHeaderOrThrow(),
                    query = "yes",
                    lang = "en"
                )

                val okCard = response.items.firstOrNull()
                if (okCard != null) {
                    vm.replyCards.clear()
                    vm.replyCards.add(okCard)

                    runOnUiThread {
                        replyAdapter.submitItems(vm.replyCards.toList())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun sendMessage() {
        if (vm.selectedCards.isEmpty()) return

        scope.launch {
            try {
                ApiClient.api.sendAacMessage(
                    auth = store.authHeaderOrThrow(),
                    body = SendAacMessageRequest(
                        targetUserId = targetUserId,
                        cards = vm.selectedCards.map {
                            AacCardDto(
                                id = it.id,
                                label = it.label,
                                imageUrl = it.imageUrl,
                                source = it.source,
                                sourceRef = it.sourceRef,
                            )
                        },
                        suggestedReplies = vm.replyCards.map {
                            AacCardDto(
                                id = it.id,
                                label = it.label,
                                imageUrl = it.imageUrl,
                                source = it.source,
                                sourceRef = it.sourceRef,
                            )
                        }
                    )
                )

                runOnUiThread {
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                        tvTarget.text = getString(R.string.no_library_sets)
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
                runOnUiThread {
                    tvTarget.text = getString(R.string.load_sets_failed, e.message)
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

                if (items.isEmpty()) {
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@ComposeMessageActivity,
                            getString(R.string.set_is_empty),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                runOnUiThread {
                    when (vm.currentAddMode) {
                        ComposeMessageViewModel.AddMode.MESSAGE -> {
                            val existingIds = vm.selectedCards.map { it.id }.toSet()
                            vm.selectedCards.addAll(items.filter { it.id !in existingIds })
                            selectedAdapter.submitItems(vm.selectedCards.toList())
                        }

                        ComposeMessageViewModel.AddMode.REPLY -> {
                            val existingIds = vm.replyCards.map { it.id }.toSet()
                            vm.replyCards.addAll(items.filter { it.id !in existingIds })
                            replyAdapter.submitItems(vm.replyCards.toList())
                        }
                    }

                    android.widget.Toast.makeText(
                        this@ComposeMessageActivity,
                        resources.getQuantityString(R.plurals.added_items, items.size, items.size),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@ComposeMessageActivity,
                        getString(R.string.load_sets_failed, e.message),
                        android.widget.Toast.LENGTH_SHORT
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