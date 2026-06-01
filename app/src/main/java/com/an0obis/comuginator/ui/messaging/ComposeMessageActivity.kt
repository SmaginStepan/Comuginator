package com.an0obis.comuginator.ui.messaging

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.SendAacMessageRequest
import com.an0obis.comuginator.api.SuggestedReplyItem
import com.an0obis.comuginator.api.WaitStepDto
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.CardAdapter
import com.an0obis.comuginator.ui.base.BaseActivity
import com.an0obis.comuginator.ui.library.LibraryItemPickerActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.widget.CheckBox
import androidx.core.view.isVisible

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
    private lateinit var btnAddMore: Button
    private lateinit var btnSendMessage: Button
    private lateinit var cbMultipleReplies: CheckBox
    private lateinit var rgReplyCount: RadioGroup
    private lateinit var replyAdapter: CardAdapter

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
            vm.mode = intent.getStringExtra("mode") ?: "NORMAL"

            val initialRepliesJson =
                intent.getStringExtra(EXTRA_INITIAL_REPLY_CARDS)

            if (!initialRepliesJson.isNullOrBlank()) {
                val type = object : TypeToken<List<AacCardDto>>() {}.type
                val initialReplies: List<AacCardDto> =
                    Gson().fromJson(initialRepliesJson, type)

                vm.replyCards.clear()
                vm.replyCards.addAll(initialReplies)
            }

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
        btnAddMore = findViewById(R.id.btnAddMore)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        cbMultipleReplies = findViewById(R.id.cbMultipleReplies)
        rgReplyCount = findViewById(R.id.rgReplyCount)
    }

    private fun setupRecycler() {
        replyAdapter = CardAdapter(
            layoutResId = R.layout.item_selected_card,
            onClick = { card ->
                vm.replyCards.remove(card)
                render()
            }
        )

        rvReplyCards.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvReplyCards.adapter = replyAdapter
        rvReplyCards.itemAnimator = null

        val touchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0
            ) {

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {

                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition

                    if (from == RecyclerView.NO_POSITION ||
                        to == RecyclerView.NO_POSITION
                    ) {
                        return false
                    }

                    val item = vm.replyCards.removeAt(from)
                    vm.replyCards.add(to, item)

                    replyAdapter.notifyItemMoved(from, to)

                    return true
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int
                ) {
                }

                override fun isLongPressDragEnabled(): Boolean {
                    return true
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    render()
                }
            }
        )

        touchHelper.attachToRecyclerView(rvReplyCards)
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

        cbMultipleReplies.setOnCheckedChangeListener { _, isChecked ->
            vm.requiredReplyCount = if (isChecked) 2 else 1

            if (isChecked && rgReplyCount.checkedRadioButtonId == -1) {
                rgReplyCount.check(R.id.rbReply2)
            }

            render()
        }

        rgReplyCount.setOnCheckedChangeListener { _, checkedId ->
            vm.requiredReplyCount = when (checkedId) {
                R.id.rbReply3 -> 3
                R.id.rbReply4 -> 4
                else -> 2
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

        btnAddMore.setOnClickListener {
            val addTimerId = 1
            val addFullLibrarySetId = 2

            val popup = PopupMenu(it.context, it)
            popup.menu.add(0, addTimerId, 0, getString(R.string.add_timer))
            popup.menu.add(0, addFullLibrarySetId, 0, getString(R.string.add_full_library_set))
            popup.setOnMenuItemClickListener { btn ->
                when (btn.itemId) {
                    addTimerId -> {
                        showAddTimerDialog()
                        true
                    }
                    addFullLibrarySetId -> {
                        showChooseLibrarySetDialog()
                        true
                    }
                    else -> false
                }
            }

            popup.show()
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
        cbMultipleReplies.isVisible = vm.mode == "NORMAL"
        rgReplyCount.isVisible = vm.mode == "NORMAL" && cbMultipleReplies.isChecked

        if (vm.mode == "SEQUENCE") {
            cbMultipleReplies.isChecked = false
            vm.requiredReplyCount = 1
        }
    }

    private fun showAddTimerDialog() {
        val variants = arrayOf(
            getString(R.string.timer_30_seconds),
            getString(R.string.timer_1_minute),
            getString(R.string.timer_3_minutes),
            getString(R.string.timer_5_minutes),
            getString(R.string.timer_10_minutes)
        )

        val seconds = intArrayOf(30, 60, 180, 300, 600)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_timer)
            .setItems(variants) { _, which ->
                addTimerStep(seconds[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addTimerStep(seconds: Int) {
        vm.mode = "SEQUENCE"
        rbSequence.isChecked = true

        vm.replyCards.add(
            AacCardDto(
                id = "WAIT_$seconds",
                label = formatTimerLabel(seconds),
                imageUrl = "",
                source = "WAIT",
                sourceRef = seconds.toString()
            )
        )

        render()
    }

    private fun formatTimerLabel(seconds: Int): String {
        return if (seconds < 60) {
            "⏱ $seconds ${getString(R.string.timer_seconds_unit)}"
        } else {
            "⏱ ${seconds / 60} ${getString(R.string.timer_minutes_unit)}"
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
                val suggestedRepliesForApi: List<SuggestedReplyItem> =
                    vm.replyCards.map { card ->
                        if (card.source == "WAIT") {
                            WaitStepDto(
                                seconds = card.sourceRef?.toIntOrNull() ?: 60
                            )
                        } else {
                            card
                        }
                    }

                ApiClient.api.sendAacMessage(
                    auth = store.authHeaderOrThrow(),
                    body = SendAacMessageRequest(
                        targetUserId = vm.targetUserId,
                        mode = vm.mode,
                        cards = buildMessageCards(),
                        suggestedReplies = suggestedRepliesForApi,
                        requiredReplyCount = vm.requiredReplyCount
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