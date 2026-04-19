package com.example.comuginator.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.comuginator.R
import com.example.comuginator.api.AddItemsToSetRequest
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.CreateArasaacLibraryItemRequest
import com.example.comuginator.api.LibraryItemDto
import com.example.comuginator.api.UpdateLibrarySetRequest
import com.example.comuginator.storage.SessionStore
import com.example.comuginator.ui.library.LibraryItemsAdapter
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.example.comuginator.api.AacCardDto

class LibrarySetActivity : AppCompatActivity() {

    private lateinit var sessionStore: SessionStore
    private lateinit var setId: String

    private lateinit var ivCover: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnRenameSet: Button
    private lateinit var btnDeleteSet: Button
    private lateinit var rvItems: RecyclerView

    private lateinit var adapter: LibraryItemsAdapter

    private val pickPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                uploadPhotoToSet(uri)
            }
        }
    private lateinit var btnAddPhoto: Button
    private lateinit var btnAddArasaac: Button
    private lateinit var btnChangeCover: Button

    private var currentItems: List<LibraryItemDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library_set)

        sessionStore = SessionStore(this)
        setId = intent.getStringExtra("setId") ?: ""

        ivCover = findViewById(R.id.ivCover)
        tvName = findViewById(R.id.tvName)
        tvStatus = findViewById(R.id.tvStatus)
        btnRenameSet = findViewById(R.id.btnRenameSet)
        btnDeleteSet = findViewById(R.id.btnDeleteSet)
        rvItems = findViewById(R.id.rvItems)

        btnAddPhoto = findViewById(R.id.btnAddPhoto)
        btnAddArasaac = findViewById(R.id.btnAddArasaac)
        btnChangeCover = findViewById(R.id.btnChangeCover)

        btnAddPhoto.setOnClickListener {
            pickPhotoLauncher.launch("image/*")
        }

        btnAddArasaac.setOnClickListener {
            showAddArasaacDialog()
        }

        btnChangeCover.setOnClickListener {
            showChangeCoverDialog()
        }

        val token = sessionStore.token ?: ""
        val auth = "Bearer $token"

        adapter = LibraryItemsAdapter(
            auth,
            onRemoveFromSetClick = { item ->
                removeItemFromSet(item.id)
            },
            onDeleteItemClick = { item ->
                deleteLibraryItem(item.id)
            }
        )

        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = adapter

        btnRenameSet.setOnClickListener {
            showRenameDialog()
        }

        btnDeleteSet.setOnClickListener {
            deleteSet()
        }

        loadSet()
    }

    private fun updateCover(itemId: String) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = "Changing cover..."

                ApiClient.api.updateLibrarySet(
                    auth = auth,
                    setId = setId,
                    body = UpdateLibrarySetRequest(
                        coverItemId = itemId
                    )
                )

                loadSet()
            } catch (e: Exception) {
                tvStatus.text = "Change cover failed: ${e.message}"
            }
        }
    }

    private fun showChangeCoverDialog() {
        if (currentItems.isEmpty()) {
            tvStatus.text = "Set is empty"
            return
        }

        val labels = currentItems.map { it.label }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose cover")
            .setItems(labels) { _, which ->
                val selected = currentItems[which]
                updateCover(selected.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun uploadPhotoToSet(uri: Uri) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = "Uploading photo..."

                val contentResolver = applicationContext.contentResolver
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
                    tempFile.name,
                    fileBody
                )

                val label = tempFile.nameWithoutExtension.ifBlank { "Photo" }
                    .toRequestBody("text/plain".toMediaTypeOrNull())

                val uploaded = ApiClient.api.uploadFamilyPhoto(
                    auth = auth,
                    file = filePart,
                    label = label
                )

                ApiClient.api.addItemsToSet(
                    auth = auth,
                    setId = setId,
                    body = AddItemsToSetRequest(
                        itemIds = listOf(uploaded.item.id)
                    )
                )

                tempFile.delete()
                loadSet()
            } catch (e: Exception) {
                tvStatus.text = "Add photo failed: ${e.message}"
            }
        }
    }

    private fun showAddArasaacDialog() {
        val input = EditText(this)

        AlertDialog.Builder(this)
            .setTitle("Search ARASAAC")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    searchAndAddArasaac(query)
                }
            }
            .show()
    }

    private fun searchAndAddArasaac(query: String) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = "Searching ARASAAC..."

                val response = ApiClient.api.searchArasaac(auth = auth, query = query)
                val results = response.items

                if (results.isEmpty()) {
                    tvStatus.text = "No ARASAAC results"
                    return@launch
                }

                val adapter = object : ArrayAdapter<AacCardDto>(
                    this@LibrarySetActivity,
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

                        val imageUrl = item.imageUrl
                            ?: "https://static.arasaac.org/pictograms/${item.id}/${item.id}_300.png"

                        iv.load(imageUrl)

                        return view
                    }
                }

                AlertDialog.Builder(this@LibrarySetActivity)
                    .setTitle("Choose ARASAAC card")
                    .setAdapter(adapter) { _, which ->
                        val chosen = results[which]
                        addArasaacResultToSet(
                            label = chosen.label,
                            sourceRef = chosen.id
                        )
                    }
                    .setNegativeButton("Cancel", null)
                    .show()

                tvStatus.text = ""
            } catch (e: Exception) {
                tvStatus.text = "ARASAAC search failed: ${e.message}"
            }
        }
    }

    private fun addArasaacResultToSet(label: String, sourceRef: String) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = "Adding ARASAAC..."

                val created = ApiClient.api.createArasaacLibraryItem(
                    auth = auth,
                    body = CreateArasaacLibraryItemRequest(
                        label = label,
                        sourceRef = sourceRef
                    )
                )

                ApiClient.api.addItemsToSet(
                    auth = auth,
                    setId = setId,
                    body = AddItemsToSetRequest(
                        itemIds = listOf(created.item.id)
                    )
                )

                loadSet()
            } catch (e: Exception) {
                tvStatus.text = "Add ARASAAC failed: ${e.message}"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSet()
    }

    private fun authHeaderOrNull(): String? {
        val token = sessionStore.token ?: return null
        return "Bearer $token"
    }

    private fun loadSet() {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = "Loading..."

                val response = ApiClient.api.getLibrarySet(
                    auth = auth,
                    setId = setId
                )

                val set = response.set
                tvName.text = set.name
                tvStatus.text = "${set.items.size} items"
                ivCover.load(set.cover?.imageUrl)
                adapter.submit(set.items)
                currentItems = set.items
            } catch (e: Exception) {
                tvStatus.text = "Failed: ${e.message}"
            }
        }
    }

    private fun showRenameDialog() {
        val input = EditText(this)
        input.setText(tvName.text.toString())
        input.setSelection(input.text.length)

        AlertDialog.Builder(this)
            .setTitle("Rename set")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty()) {
                    renameSet(newName)
                }
            }
            .show()
    }

    private fun renameSet(newName: String) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = "Renaming..."

                ApiClient.api.updateLibrarySet(
                    auth = auth,
                    setId = setId,
                    body = UpdateLibrarySetRequest(name = newName)
                )

                loadSet()
            } catch (e: Exception) {
                tvStatus.text = "Rename failed: ${e.message}"
            }
        }
    }

    private fun deleteSet() {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = "Deleting set..."

                ApiClient.api.deleteLibrarySet(
                    auth = auth,
                    setId = setId
                )

                finish()
            } catch (e: Exception) {
                tvStatus.text = "Delete failed: ${e.message}"
            }
        }
    }

    private fun removeItemFromSet(itemId: String) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = "Removing from set..."

                ApiClient.api.removeItemFromSet(
                    auth = auth,
                    setId = setId,
                    itemId = itemId
                )

                loadSet()
            } catch (e: Exception) {
                tvStatus.text = "Remove failed: ${e.message}"
            }
        }
    }

    private fun deleteLibraryItem(itemId: String) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = "Deleting item..."

                ApiClient.api.deleteLibraryItem(
                    auth = auth,
                    itemId = itemId
                )

                loadSet()
            } catch (e: Exception) {
                tvStatus.text = "Delete item failed: ${e.message}"
            }
        }
    }
}