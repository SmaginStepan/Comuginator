package com.an0obis.comuginator.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.ImageRequest
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.CreateArasaacLibraryItemRequest
import com.an0obis.comuginator.api.LibrarySetDto
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.library.LibraryPickerItemsAdapter
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import coil.imageLoader

class LibraryItemPickerActivity : AppCompatActivity() {

    enum class TargetMode {
        USER_AVATAR,
        SET_COVER,
        ADD_TO_SET
    }

    companion object {
        private const val EXTRA_TARGET_MODE = "target_mode"
        private const val EXTRA_RESULT_ITEM_ID = "result_item_id"

        fun createIntent(
            context: Context,
            targetMode: TargetMode
        ): Intent {
            return Intent(context, LibraryItemPickerActivity::class.java)
                .putExtra(EXTRA_TARGET_MODE, targetMode.name)
        }

        fun parseResultItemId(data: Intent?): String? {
            return data?.getStringExtra(EXTRA_RESULT_ITEM_ID)
        }
    }

    private lateinit var sessionStore: SessionStore
    private lateinit var targetMode: TargetMode

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView

    private lateinit var btnTakePhoto: Button
    private lateinit var btnChooseFromDevice: Button
    private lateinit var btnSearchArasaac: Button
    private lateinit var btnChooseFromLibrary: Button

    private lateinit var libraryFiltersBlock: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var actSetFilter: AutoCompleteTextView
    private lateinit var rvItems: RecyclerView

    private lateinit var confirmBlock: LinearLayout
    private lateinit var ivPreview: ImageView
    private lateinit var etLabel: EditText
    private lateinit var btnConfirm: Button
    private lateinit var btnCancelSelection: Button

    private lateinit var btnPasteFromClipboard: Button

    private lateinit var itemsAdapter: LibraryPickerItemsAdapter

    private var allLibraryItems: List<AacCardDto> = emptyList()
    private var allSets: List<LibrarySetDto> = emptyList()
    private var allSetItems: Map<String, List<AacCardDto>> = emptyMap()
    private var selectedSetId: String? = null
    private var currentVisibleItems: List<AacCardDto> = emptyList()

    private var pendingSelectedUri: Uri? = null
    private var pendingSelectedArasaac: AacCardDto? = null
    private var pendingCameraUri: Uri? = null

    private var libraryFilterWatcherAttached = false

    private val pickFromDeviceLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                startConfirmForPhoto(uri)
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                val uri = pendingCameraUri
                if (uri != null) {
                    startConfirmForPhoto(uri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library_item_picker)

        sessionStore = SessionStore(this)
        targetMode = TargetMode.valueOf(
            intent.getStringExtra(EXTRA_TARGET_MODE) ?: TargetMode.ADD_TO_SET.name
        )

        bindViews()
        setupRecycler()
        setupUi()
    }

    private fun bindViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)

        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnChooseFromDevice = findViewById(R.id.btnChooseFromDevice)
        btnSearchArasaac = findViewById(R.id.btnSearchArasaac)
        btnChooseFromLibrary = findViewById(R.id.btnChooseFromLibrary)

        libraryFiltersBlock = findViewById(R.id.libraryFiltersBlock)
        etSearch = findViewById(R.id.etSearch)
        actSetFilter = findViewById(R.id.actSetFilter)
        rvItems = findViewById(R.id.rvItems)

        btnPasteFromClipboard = findViewById(R.id.btnPasteFromClipboard)

        confirmBlock = findViewById(R.id.confirmBlock)
        ivPreview = findViewById(R.id.ivPreview)
        etLabel = findViewById(R.id.etLabel)
        btnConfirm = findViewById(R.id.btnConfirm)
        btnCancelSelection = findViewById(R.id.btnCancelSelection)
    }

    private fun setupRecycler() {
        itemsAdapter = LibraryPickerItemsAdapter(
            authToken = authHeaderOrThrow(),
            onClick = { item ->
                finishWithItem(item.id)
            }
        )

        rvItems.layoutManager = GridLayoutManager(this, 3)
        rvItems.adapter = itemsAdapter
    }

    private fun setupUi() {
        tvTitle.text = when (targetMode) {
            TargetMode.USER_AVATAR -> "Choose user avatar"
            TargetMode.SET_COVER -> "Choose set cover"
            TargetMode.ADD_TO_SET -> "Add item to set"
        }

        btnChooseFromLibrary.visibility =
            if (targetMode == TargetMode.USER_AVATAR || targetMode == TargetMode.SET_COVER)
                Button.VISIBLE else Button.GONE

        btnTakePhoto.setOnClickListener {
            launchCamera()
        }

        btnChooseFromDevice.setOnClickListener {
            pickFromDeviceLauncher.launch("image/*")
        }

        btnSearchArasaac.setOnClickListener {
            showSearchArasaacDialog()
        }

        btnChooseFromLibrary.setOnClickListener {
            openLibraryBrowse()
        }

        btnCancelSelection.setOnClickListener {
            hideConfirmBlock()
        }

        btnConfirm.setOnClickListener {
            confirmPendingSelection()
        }

        btnPasteFromClipboard.setOnClickListener {
            pasteImageFromClipboard()
        }

        hideLibraryBrowse()
        hideConfirmBlock()
    }

    private fun launchCamera() {
        val file = File.createTempFile("camera_", ".jpg", cacheDir)
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun openLibraryBrowse() {
        lifecycleScope.launch {
            try {
                tvStatus.text = getString(R.string.loading_library)

                val auth = authHeaderOrThrow()

                val itemsResponse = ApiClient.api.getLibraryItems(auth = auth, source = null)
                val setsResponse = ApiClient.api.getLibrarySets(auth = auth)

                allLibraryItems = itemsResponse.items
                allSets = setsResponse.sets

                allSetItems = buildMap {
                    for (set in allSets) {
                        try {
                            val details = ApiClient.api.getLibrarySet(auth, set.id)
                            put(set.id, details.set.items)
                        } catch (_: Exception) {
                            put(set.id, emptyList())
                        }
                    }
                }

                setupSetFilter()
                applyLibraryFilter()

                libraryFiltersBlock.visibility = LinearLayout.VISIBLE
                rvItems.visibility = RecyclerView.VISIBLE
                confirmBlock.visibility = LinearLayout.GONE
                tvStatus.text = ""
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.load_library_failed, e.message)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateClipboardButtonVisibility()
    }

    private fun updateClipboardButtonVisibility() {
        btnPasteFromClipboard.visibility =
            if (hasImageInClipboard()) View.VISIBLE else View.GONE
    }

    private fun hasImageInClipboard(): Boolean {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        val clip = clipboard.primaryClip ?: return false
        if (clip.itemCount == 0) return false

        val item = clip.getItemAt(0)
        val uri = item.uri ?: return false

        val type = contentResolver.getType(uri)

        return type?.startsWith("image/") == true
    }

    private fun pasteImageFromClipboard() {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)

        if (clipboard == null) {
            tvStatus.text = getString(R.string.clipboard_unavailable)
            return
        }

        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            tvStatus.text = getString(R.string.clipboard_empty)
            return
        }

        val item = clip.getItemAt(0)

        val uri = item.uri
        if (uri != null) {
            startConfirmForPhoto(uri)
            return
        }

        val text = item.coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            tvStatus.text = getString(R.string.clipboard_text_not_supported)
            return
        }

        tvStatus.text = getString(R.string.no_image_in_clipboard)
    }

    private fun setupSetFilter() {
        val names = mutableListOf(getString(R.string.all_sets))
        names.addAll(allSets.map { it.name })

        actSetFilter.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        )
        actSetFilter.setText(names.first(), false)

        actSetFilter.setOnItemClickListener { _, _, position, _ ->
            selectedSetId = if (position == 0) null else allSets[position - 1].id
            applyLibraryFilter()
        }

        if (!libraryFilterWatcherAttached) {
            etSearch.addTextChangedListener(
                object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: android.text.Editable?) {
                        applyLibraryFilter()
                    }
                }
            )
            libraryFilterWatcherAttached = true
        }
    }

    private fun applyLibraryFilter() {
        val query = etSearch.text?.toString()?.trim().orEmpty()

        val baseItems = if (selectedSetId == null) {
            allLibraryItems
        } else {
            allSetItems[selectedSetId].orEmpty()
        }

        currentVisibleItems = baseItems.filter { item ->
            query.isBlank() || item.label.contains(query, ignoreCase = true)
        }

        itemsAdapter.submit(currentVisibleItems)
        tvStatus.text = getString(R.string.items_count_plain, currentVisibleItems.size)
    }

    private fun startConfirmForPhoto(uri: Uri) {
        pendingSelectedUri = uri
        pendingSelectedArasaac = null

        ivPreview.load(uri)
        etLabel.setText(
            sanitizeSuggestedLabel(extractDisplayName(uri) ?: fallbackPhotoName())
        )

        showConfirmBlock()
    }

    private fun sanitizeSuggestedLabel(raw: String): String {
        val dot = raw.lastIndexOf('.')
        val noExt = if (dot > 0) raw.substring(0, dot) else raw
        return noExt.replace('_', ' ').replace('-', ' ').trim()
    }

    private fun startConfirmForArasaac(card: AacCardDto) {
        pendingSelectedUri = null
        pendingSelectedArasaac = card

        ivPreview.load(card.imageUrl)
        etLabel.setText(card.label)

        showConfirmBlock()
    }

    private fun showSearchArasaacDialog() {
        val input = EditText(this)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.search_arasaac))
            .setView(input)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.search)) { _, _ ->
                val query = input.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    searchArasaac(query)
                }
            }
            .show()
    }

    private fun searchArasaac(query: String) {
        lifecycleScope.launch {
            try {
                tvStatus.text = getString(R.string.searching_arasaac)
                val response = ApiClient.api.searchArasaac(authHeaderOrThrow(), query)
                val results = response.items

                if (results.isEmpty()) {
                    tvStatus.text = getString(R.string.nothing_found)
                    return@launch
                }

                val dialogAdapter = object : ArrayAdapter<AacCardDto>(
                    this@LibraryItemPickerActivity,
                    0,
                    results
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = convertView ?: layoutInflater.inflate(
                            R.layout.item_arasaac_dialog,
                            parent,
                            false
                        )

                        val item = getItem(position)!!
                        val iv = view.findViewById<ImageView>(R.id.ivImage)
                        val tv = view.findViewById<TextView>(R.id.tvLabel)

                        tv.text = item.label

                        val request = ImageRequest.Builder(this@LibraryItemPickerActivity)
                            .data(item.imageUrl)
                            .target(iv)
                            .build()

                        imageLoader.enqueue(request)

                        return view
                    }
                }

                AlertDialog.Builder(this@LibraryItemPickerActivity)
                    .setTitle(getString(R.string.choose_arasaac_card))
                    .setAdapter(dialogAdapter) { _, which ->
                        startConfirmForArasaac(results[which])
                    }
                    .setNegativeButton("Cancel", null)
                    .show()

                tvStatus.text = ""
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.arasaac_search_failed, e.message)
            }
        }
    }

    private fun confirmPendingSelection() {
        val label = etLabel.text?.toString()?.trim().orEmpty()
        if (label.isBlank()) {
            tvStatus.text = getString(R.string.label_required)
            return
        }

        lifecycleScope.launch {
            try {
                when {
                    pendingSelectedUri != null -> {
                        val created = uploadPhotoToLibrary(pendingSelectedUri!!, label)
                        finishWithItem(created.id)
                    }
                    pendingSelectedArasaac != null -> {
                        val created = ApiClient.api.createArasaacLibraryItem(
                            auth = authHeaderOrThrow(),
                            body = CreateArasaacLibraryItemRequest(
                                label = label,
                                sourceRef = pendingSelectedArasaac!!.id
                            )
                        )
                        finishWithItem(created.item.id)
                    }
                    else -> {
                        tvStatus.text = getString(R.string.nothing_selected)
                    }
                }
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.save_failed, e.message)
            }
        }
    }

    private suspend fun uploadPhotoToLibrary(uri: Uri, label: String): AacCardDto {
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open selected file")

        val tempFile = File.createTempFile("library_upload_", ".tmp", cacheDir)
        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val fileBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData(
            "file",
            extractDisplayName(uri) ?: tempFile.name,
            fileBody
        )

        val labelPart = label.toRequestBody("text/plain".toMediaTypeOrNull())

        return ApiClient.api.uploadFamilyPhoto(
            auth = authHeaderOrThrow(),
            file = filePart,
            label = labelPart
        ).item
    }

    private fun authHeaderOrThrow(): String {
        val token = sessionStore.token ?: error("No token")
        return "Bearer $token"
    }

    private fun extractDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    private fun fallbackPhotoName(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "Photo ${fmt.format(Date())}"
    }

    private fun hideLibraryBrowse() {
        libraryFiltersBlock.visibility = LinearLayout.GONE
        rvItems.visibility = RecyclerView.GONE
    }

    private fun showConfirmBlock() {
        hideLibraryBrowse()
        confirmBlock.visibility = LinearLayout.VISIBLE
        tvStatus.text = ""
    }

    private fun hideConfirmBlock() {
        confirmBlock.visibility = LinearLayout.GONE
        pendingCameraUri = null
        pendingSelectedUri = null
        pendingSelectedArasaac = null
        ivPreview.setImageDrawable(null)
        etLabel.setText("")
        tvStatus.text = ""
    }

    private fun finishWithItem(itemId: String) {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_RESULT_ITEM_ID, itemId)
        )
        finish()
    }
}