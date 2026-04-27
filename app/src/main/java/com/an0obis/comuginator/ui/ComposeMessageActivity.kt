package com.an0obis.comuginator.ui

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.api.SendAacMessageRequest
import com.an0obis.comuginator.storage.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.net.Uri
import android.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.activity.viewModels
import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import android.view.inputmethod.InputMethodManager

class ComposeMessageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INITIAL_MESSAGE_CARDS = "initial_message_cards"
        const val EXTRA_INITIAL_REPLY_CARDS = "initial_reply_cards"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var btnAddPhoto: Button

    private lateinit var btnLoadLibrarySet: Button
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                askPhotoLabelAndUpload(uri)
            }
        }
    private lateinit var store: SessionStore
    private lateinit var tvTarget: TextView
    private lateinit var tvMessageHeader: TextView
    private lateinit var tvRepliesHeader: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnSendMessage: Button
    private lateinit var rvSelectedCards: RecyclerView
    private lateinit var rvReplyCards: RecyclerView
    private lateinit var rvResults: RecyclerView

    private lateinit var selectedAdapter: SelectedCardAdapter
    private lateinit var replyAdapter: SelectedCardAdapter
    private lateinit var resultsAdapter: SimpleCardAdapter

    private lateinit var targetUserId: String
    private lateinit var targetUserName: String

    private var searchJob: Job? = null

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
        etSearch = findViewById(R.id.etSearch)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        rvSelectedCards = findViewById(R.id.rvSelectedCards)
        rvReplyCards = findViewById(R.id.rvReplyCards)
        rvResults = findViewById(R.id.rvResults)

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

        resultsAdapter = SimpleCardAdapter(
            onClick = { card ->
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
        )

        rvSelectedCards.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSelectedCards.adapter = selectedAdapter

        rvReplyCards.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvReplyCards.adapter = replyAdapter

        rvResults.layoutManager = GridLayoutManager(this, 3)
        rvResults.adapter = resultsAdapter

        selectedAdapter.submitItems(vm.selectedCards.toList())
        replyAdapter.submitItems(vm.replyCards.toList())

        tvTarget.text = "Send to $targetUserName"

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

        etSearch.doAfterTextChanged { editable ->
            val query = editable?.toString()?.trim().orEmpty()

            searchJob?.cancel()

            if (query.isBlank()) {
                resultsAdapter.submitItems(emptyList())
                return@doAfterTextChanged
            }

            searchJob = scope.launch {
                delay(2000)
                if (query != vm.lastSearchQuery) {
                    runSearch(query)
                }
            }
        }

        etSearch.setOnEditorActionListener { _: TextView, actionId: Int, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = etSearch.text.toString().trim()
                if (query.isNotBlank()) {
                    searchJob?.cancel()
                    scope.launch {
                        runSearch(query)
                    }
                }
                true
            } else {
                false
            }
        }

        btnAddPhoto = findViewById(R.id.btnAddPhoto)
        btnLoadLibrarySet = findViewById(R.id.btnLoadLibrarySet)

        btnAddPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnLoadLibrarySet.setOnClickListener {
            showChooseLibrarySetDialog()
        }

        etSearch.setOnEditorActionListener { view, actionId, event ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP

            if (isSearchAction || isEnter) {
                val query = etSearch.text.toString().trim()
                if (query.isNotBlank()) {
                    lifecycleScope.launch {
                        runSearch(query)
                    }
                }

                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()

                true
            } else {
                false
            }
        }

        updateModeUi()

        if (vm.replyCards.isEmpty()) {
            loadDefaultReplies()
        }
    }

    private fun authHeaderOrThrow(): String {
        val token = store.token ?: error("No token in SessionStore")
        return "Bearer $token"
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
            emptyList()
        }
    }

    private fun loadDefaultReplies() {
        scope.launch {
            try {
                val response = ApiClient.api.searchArasaac(
                    auth = authHeaderOrThrow(),
                    query = "ok"
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

    private suspend fun runSearch(query: String) {
        try {
            vm.lastSearchQuery = query

            val response = ApiClient.api.searchArasaac(
                auth = authHeaderOrThrow(),
                query = query
            )

            runOnUiThread {
                resultsAdapter.submitItems(response.items)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendMessage() {
        if (vm.selectedCards.isEmpty()) return

        scope.launch {
            try {
                ApiClient.api.sendAacMessage(
                    auth = authHeaderOrThrow(),
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
                val auth = authHeaderOrThrow()
                val response = ApiClient.api.getLibrarySets(auth)
                val sets = response.sets

                if (sets.isEmpty()) {
                    runOnUiThread {
                        tvTarget.text = "No library sets"
                    }
                    return@launch
                }

                val labels = sets.map { "${it.name} (${it.itemsCount})" }.toTypedArray()

                runOnUiThread {
                    AlertDialog.Builder(this@ComposeMessageActivity)
                        .setTitle("Choose set")
                        .setItems(labels) { _, which ->
                            addAllItemsFromSet(sets[which].id)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvTarget.text = "Load sets failed: ${e.message}"
                }
            }
        }
    }

    private fun addAllItemsFromSet(setId: String) {
        scope.launch {
            try {
                val auth = authHeaderOrThrow()
                val response = ApiClient.api.getLibrarySet(auth, setId)
                val items = response.set.items

                if (items.isEmpty()) {
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@ComposeMessageActivity,
                            "Set is empty",
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
                        "Added ${items.size} items",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@ComposeMessageActivity,
                        "Load set failed: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun getMimeType(uri: Uri): String {
        return contentResolver.getType(uri) ?: "image/jpeg"
    }

    private fun guessExtension(mimeType: String): String {
        return when (mimeType) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/jpeg", "image/jpg" -> ".jpg"
            else -> ".jpg"
        }
    }

    private fun createTempFileFromUri(uri: Uri): File {
        val mimeType = getMimeType(uri)
        val extension = guessExtension(mimeType)

        val inputStream = contentResolver.openInputStream(uri)
            ?: error("Cannot open input stream")

        val tempFile = File.createTempFile("upload_", extension, cacheDir)

        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }

    private fun uploadFamilyPhoto(uri: Uri, label: String) {
        scope.launch {
            try {
                val mimeType = getMimeType(uri)
                val tempFile = createTempFileFromUri(uri)

                val fileBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData(
                    "file",
                    tempFile.name,
                    fileBody
                )

                val labelBody = label.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = ApiClient.api.uploadFamilyPhoto(
                    auth = authHeaderOrThrow(),
                    file = filePart,
                    label = labelBody
                )

                val card = response.item

                runOnUiThread {
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

                tempFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    tvTarget.text = "Upload failed: ${e.message}"
                }
            }
        }
    }

    private fun askPhotoLabelAndUpload(uri: Uri) {
        val input = EditText(this).apply {
            hint = "Label"
        }

        AlertDialog.Builder(this)
            .setTitle("Photo label")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Upload") { _, _ ->
                val label = input.text?.toString()?.trim().orEmpty()
                if (label.isNotBlank()) {
                    uploadFamilyPhoto(uri, label)
                }
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}