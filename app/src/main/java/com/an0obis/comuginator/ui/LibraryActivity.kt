package com.an0obis.comuginator.ui

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
import com.an0obis.comuginator.ui.library.LibrarySetsAdapter
import kotlinx.coroutines.launch

class LibraryActivity : AppCompatActivity() {

    private lateinit var sessionStore: SessionStore
    private lateinit var tvStatus: TextView
    private lateinit var adapter: LibrarySetsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        sessionStore = SessionStore(this)

        tvStatus = findViewById(R.id.tvStatus)
        val rv = findViewById<RecyclerView>(R.id.rvSets)
        val btnCreate = findViewById<Button>(R.id.btnCreateSet)

        val token = sessionStore.token ?: ""
        val auth = "Bearer $token"

        adapter = LibrarySetsAdapter(auth) { set ->
            val intent = Intent(this, LibrarySetActivity::class.java)
            intent.putExtra("setId", set.id)
            startActivity(intent)
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnCreate.setOnClickListener {
            createSimpleSet()
        }

        loadSets()
    }

    override fun onResume() {
        super.onResume()
        loadSets()
    }

    private fun loadSets() {
        lifecycleScope.launch {
            try {
                tvStatus.text = "Loading..."

                val token = sessionStore.token ?: return@launch
                val resp = ApiClient.api.getLibrarySets("Bearer $token")

                adapter.submit(resp.sets)
                tvStatus.text = "${resp.sets.size} sets"

            } catch (e: Exception) {
                tvStatus.text = e.message
            }
        }
    }

    private fun createSimpleSet() {
        lifecycleScope.launch {
            try {
                val token = sessionStore.token ?: return@launch

                ApiClient.api.createLibrarySet(
                    "Bearer $token",
                    com.an0obis.comuginator.api.CreateLibrarySetRequest(
                        name = "New set"
                    )
                )

                loadSets()

            } catch (e: Exception) {
                tvStatus.text = e.message
            }
        }
    }
}