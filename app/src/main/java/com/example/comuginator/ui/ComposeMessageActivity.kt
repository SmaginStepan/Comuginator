package com.example.comuginator.ui

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
import com.example.comuginator.R
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.AacCardDto
import com.example.comuginator.api.SendAacMessageRequest
import com.example.comuginator.storage.SessionStore
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

class ComposeMessageActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var btnAddPhoto: Button
    private lateinit var btnLoadFamilyPhotos: Button

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
    private var lastSearchQuery: String = ""

    private enum class AddMode {
        MESSAGE,
        REPLY
    }

    private var currentAddMode: AddMode = AddMode.MESSAGE

    private val selectedCards = mutableListOf<AacCardDto>()
    private val replyCards = mutableListOf<AacCardDto>()

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

        tvTarget.text = "Send to $targetUserName"

        selectedAdapter = SelectedCardAdapter(
            onClick = { card ->
                selectedCards.remove(card)
                selectedAdapter.submitItems(selectedCards.toList())
            }
        )

        replyAdapter = SelectedCardAdapter(
            onClick = { card ->
                replyCards.remove(card)
                replyAdapter.submitItems(replyCards.toList())
            }
        )

        resultsAdapter = SimpleCardAdapter(
            onClick = { card ->
                when (currentAddMode) {
                    AddMode.MESSAGE -> {
                        selectedCards.add(card)
                        selectedAdapter.submitItems(selectedCards.toList())
                    }
                    AddMode.REPLY -> {
                        replyCards.add(card)
                        replyAdapter.submitItems(replyCards.toList())
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

        tvMessageHeader.setOnClickListener {
            currentAddMode = AddMode.MESSAGE
            updateModeUi()
        }

        tvRepliesHeader.setOnClickListener {
            currentAddMode = AddMode.REPLY
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
                if (query != lastSearchQuery) {
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
        btnLoadFamilyPhotos = findViewById(R.id.btnLoadFamilyPhotos)

        btnAddPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnLoadFamilyPhotos.setOnClickListener {
            loadFamilyPhotos()
        }

        updateModeUi()
        loadDefaultReplies()
    }

    private fun authHeaderOrThrow(): String {
        val token = store.token ?: error("No token in SessionStore")
        return "Bearer $token"
    }

    private fun updateModeUi() {
        val activeColor = 0xFF4444FF.toInt()
        val inactiveColor = 0x00000000

        tvMessageHeader.setBackgroundColor(
            if (currentAddMode == AddMode.MESSAGE) activeColor else inactiveColor
        )
        tvRepliesHeader.setBackgroundColor(
            if (currentAddMode == AddMode.REPLY) activeColor else inactiveColor
        )
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
                    replyCards.clear()
                    replyCards.add(okCard)

                    runOnUiThread {
                        replyAdapter.submitItems(replyCards.toList())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun runSearch(query: String) {
        try {
            lastSearchQuery = query

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
        if (selectedCards.isEmpty()) return

        scope.launch {
            try {
                ApiClient.api.sendAacMessage(
                    auth = authHeaderOrThrow(),
                    body = SendAacMessageRequest(
                        targetUserId = targetUserId,
                        cards = selectedCards.map {
                            AacCardDto(
                                id = it.id,
                                label = it.label,
                                imageUrl = it.imageUrl
                            )
                        },
                        suggestedReplies = replyCards.map {
                            AacCardDto(
                                id = it.id,
                                label = it.label,
                                imageUrl = it.imageUrl
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

    private fun loadFamilyPhotos() {
        scope.launch {
            try {
                val response = ApiClient.api.getFamilyPhotos(
                    auth = authHeaderOrThrow()
                )

                runOnUiThread {
                    resultsAdapter.submitItems(response.items)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                    when (currentAddMode) {
                        AddMode.MESSAGE -> {
                            selectedCards.add(card)
                            selectedAdapter.submitItems(selectedCards.toList())
                        }
                        AddMode.REPLY -> {
                            replyCards.add(card)
                            replyAdapter.submitItems(replyCards.toList())
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