package com.an0obis.comuginator.ui

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
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AddItemsToSetRequest
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.UpdateLibrarySetRequest
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.library.LibraryItemsAdapter
import kotlinx.coroutines.launch

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

    private val addItemPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val itemId = LibraryItemPickerActivity.parseResultItemId(result.data)
                if (!itemId.isNullOrBlank()) {
                    addExistingItemToSet(itemId)
                }
            }
        }

    private val coverPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val itemId = LibraryItemPickerActivity.parseResultItemId(result.data)
                if (!itemId.isNullOrBlank()) {
                    updateCover(itemId)
                }
            }
        }

    private lateinit var btnAddPhoto: Button
    private lateinit var btnAddArasaac: Button
    private lateinit var btnChangeCover: Button

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
            openAddItemPicker()
        }

        btnAddArasaac.setOnClickListener {
            openAddItemPicker()
        }

        btnChangeCover.setOnClickListener {
            openCoverPicker()
        }

        val auth = authHeaderOrNull().orEmpty()

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

    private fun addExistingItemToSet(itemId: String) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = getString(R.string.adding_item)

                ApiClient.api.addItemsToSet(
                    auth = auth,
                    setId = setId,
                    body = AddItemsToSetRequest(
                        itemIds = listOf(itemId)
                    )
                )

                loadSet()
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.failed_with_message, e.message)
            }
        }
    }

    private fun openAddItemPicker() {
        val intent = LibraryItemPickerActivity.createIntent(
            context = this,
            targetMode = LibraryItemPickerActivity.TargetMode.ADD_TO_SET
        )
        addItemPickerLauncher.launch(intent)
    }

    private fun openCoverPicker() {
        val intent = LibraryItemPickerActivity.createIntent(
            context = this,
            targetMode = LibraryItemPickerActivity.TargetMode.SET_COVER
        )
        coverPickerLauncher.launch(intent)
    }

    private fun updateCover(itemId: String) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = getString(R.string.changing_cover)

                ApiClient.api.updateLibrarySet(
                    auth = auth,
                    setId = setId,
                    body = UpdateLibrarySetRequest(
                        coverItemId = itemId
                    )
                )

                loadSet()
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.failed_with_message, e.message)
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
                tvStatus.text = getString(R.string.loading)

                val response = ApiClient.api.getLibrarySet(
                    auth = auth,
                    setId = setId
                )

                val set = response.set
                tvName.text = set.name
                tvStatus.text = getString(R.string.items_count_plain, set.items.size)
                ivCover.load(set.cover?.imageUrl)
                adapter.submit(set.items)

            } catch (e: Exception) {
                tvStatus.text = getString(R.string.failed_with_message, e.message)
            }
        }
    }

    private fun showRenameDialog() {
        val input = EditText(this)
        input.setText(tvName.text.toString())
        input.setSelection(input.text.length)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename_set))
            .setView(input)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
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
                tvStatus.text = getString(R.string.renaming)

                ApiClient.api.updateLibrarySet(
                    auth = auth,
                    setId = setId,
                    body = UpdateLibrarySetRequest(name = newName)
                )

                loadSet()
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.failed_with_message, e.message)
            }
        }
    }

    private fun deleteSet() {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = getString(R.string.deleting_set)

                ApiClient.api.deleteLibrarySet(
                    auth = auth,
                    setId = setId
                )

                finish()
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.failed_with_message, e.message)
            }
        }
    }

    private fun removeItemFromSet(itemId: String) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = getString(R.string.removing_from_set)

                ApiClient.api.removeItemFromSet(
                    auth = auth,
                    setId = setId,
                    itemId = itemId
                )

                loadSet()
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.failed_with_message, e.message)
            }
        }
    }

    private fun deleteLibraryItem(itemId: String) {
        lifecycleScope.launch {
            try {
                val auth = authHeaderOrNull() ?: return@launch
                tvStatus.text = getString(R.string.deleting_item)

                ApiClient.api.deleteLibraryItem(
                    auth = auth,
                    itemId = itemId
                )

                loadSet()
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.failed_with_message, e.message)
            }
        }
    }
}