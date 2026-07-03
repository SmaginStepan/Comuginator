package com.an0obis.comuginator.ui.library

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.storage.SessionStore
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.ItemTouchHelper
import com.an0obis.comuginator.api.CreateLibrarySetRequest
import com.an0obis.comuginator.api.LibrarySetDto
import com.an0obis.comuginator.api.MoveLibrarySetsRequest
import com.an0obis.comuginator.storage.OfflineCache
import com.an0obis.comuginator.storage.SettingsStore
import com.an0obis.comuginator.ui.ConnectionErrorHelper
import com.an0obis.comuginator.ui.OfflineBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryActivity : AppCompatActivity() {

    private lateinit var sessionStore: SessionStore
    private lateinit var tvStatus: TextView
    private lateinit var rvSets: RecyclerView
    private lateinit var adapter: LibrarySetsAdapter

    private val connectionErrorHelper = ConnectionErrorHelper(this) { loadSets() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        sessionStore = SessionStore(this)

        tvStatus = findViewById(R.id.tvStatus)
        rvSets = findViewById(R.id.rvSets)
        val btnCreate = findViewById<Button>(R.id.btnCreateSet)
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }


        adapter = LibrarySetsAdapter(sessionStore.authHeaderOrThrow()) { set ->
            val intent = Intent(this, LibrarySetActivity::class.java)
            intent.putExtra("setId", set.id)
            startActivity(intent)
        }

        rvSets.layoutManager = LinearLayoutManager(this)
        rvSets.adapter = adapter
        rvSets.itemAnimator = null

        setupDragAndDrop()

        btnCreate.setOnClickListener {
            createSimpleSet()
        }

        OfflineBanner.setup(this) { loadSets() }

        loadSets()
    }

    private fun setupDragAndDrop() {
        val touchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition

                    if (
                        from == RecyclerView.NO_POSITION ||
                        to == RecyclerView.NO_POSITION ||
                        from == to
                    ) {
                        return false
                    }

                    adapter.moveItem(from, to)
                    return true
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int
                ) {
                    // no-op
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    persistCurrentSetOrder()
                }
            }
        )

        touchHelper.attachToRecyclerView(rvSets)
    }

    private fun persistCurrentSetOrder() {
        val setIds = adapter.readItems().map { it.id }

        lifecycleScope.launch {
            try {
                tvStatus.text = getString(R.string.saving)

                withContext(Dispatchers.IO) {
                    ApiClient.api.moveLibrarySets(
                        auth = sessionStore.authHeaderOrThrow(),
                        body = MoveLibrarySetsRequest(
                            setIds = setIds
                        )
                    )
                }

                tvStatus.text = resources.getQuantityString(
                    R.plurals.sets_count,
                    setIds.size,
                    setIds.size
                )
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.failed_with_message, e.message)
                loadSets()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSets()
    }

    private fun loadSets() {
        OfflineBanner.refresh(this)
        lifecycleScope.launch {
            val cache = OfflineCache(this@LibraryActivity)
            val familyId = sessionStore.familyId

            if (SettingsStore(this@LibraryActivity).offlineMode) {
                showSets(cache.loadLibrarySets(familyId) ?: emptyList(), offline = true)
                return@launch
            }

            try {
                tvStatus.text = getString(R.string.loading)

                val resp = ApiClient.api.getLibrarySets(sessionStore.authHeaderOrThrow())

                cache.saveLibrarySets(familyId, resp.sets)
                showSets(resp.sets, offline = false)
            } catch (e: Exception) {
                if (connectionErrorHelper.handle(e)) return@launch
                val cached = cache.loadLibrarySets(familyId)
                if (cached != null) {
                    showSets(cached, offline = true)
                } else {
                    tvStatus.text = e.message
                }
            }
        }
    }

    private fun showSets(sets: List<LibrarySetDto>, offline: Boolean) {
        adapter.submitItems(sets)
        val countText =
            resources.getQuantityString(R.plurals.sets_count, sets.size, sets.size)
        tvStatus.text = if (offline) {
            getString(R.string.status_with_offline, countText, getString(R.string.offline))
        } else {
            countText
        }
        findViewById<Button>(R.id.btnCreateSet).isEnabled = !offline
    }

    private fun createSimpleSet() {
        lifecycleScope.launch {
            try {

                ApiClient.api.createLibrarySet(
                    sessionStore.authHeaderOrThrow(),
                    CreateLibrarySetRequest(
                        name = getString(R.string.new_set)
                    )
                )

                loadSets()

            } catch (e: Exception) {
                tvStatus.text = e.message
            }
        }
    }
}