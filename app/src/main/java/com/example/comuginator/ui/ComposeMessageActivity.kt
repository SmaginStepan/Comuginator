package com.example.comuginator.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.comuginator.R
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.ArasaacCardDto
import com.example.comuginator.api.SendAacCardDto
import com.example.comuginator.api.SendAacMessageRequest
import com.example.comuginator.storage.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ComposeMessageActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var store: SessionStore

    private lateinit var tvTarget: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnSendMessage: Button
    private lateinit var rvSelectedCards: RecyclerView
    private lateinit var rvResults: RecyclerView

    private lateinit var selectedAdapter: SimpleCardAdapter
    private lateinit var resultsAdapter: SimpleCardAdapter

    private lateinit var targetUserId: String
    private lateinit var targetUserName: String

    private val selectedCards = mutableListOf<ArasaacCardDto>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_message)

        store = SessionStore(this)

        targetUserId = intent.getStringExtra("targetUserId").orEmpty()
        targetUserName = intent.getStringExtra("targetUserName").orEmpty()

        tvTarget = findViewById(R.id.tvTarget)
        etSearch = findViewById(R.id.etSearch)
        btnSearch = findViewById(R.id.btnSearch)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        rvSelectedCards = findViewById(R.id.rvSelectedCards)
        rvResults = findViewById(R.id.rvResults)

        tvTarget.text = "Send to $targetUserName"

        selectedAdapter = SimpleCardAdapter(
            onClick = { card ->
                selectedCards.remove(card)
                selectedAdapter.submitItems(selectedCards.toList())
            }
        )

        resultsAdapter = SimpleCardAdapter(
            onClick = { card ->
                selectedCards.add(card)
                selectedAdapter.submitItems(selectedCards.toList())
            }
        )

        rvSelectedCards.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSelectedCards.adapter = selectedAdapter

        rvResults.layoutManager = GridLayoutManager(this, 3)
        rvResults.adapter = resultsAdapter

        btnSearch.setOnClickListener {
            searchCards()
        }

        btnSendMessage.setOnClickListener {
            sendMessage()
        }
    }

    private fun authHeaderOrThrow(): String {
        val token = store.token ?: error("No token in SessionStore")
        return "Bearer $token"
    }

    private fun searchCards() {
        val query = etSearch.text.toString().trim()
        if (query.isBlank()) return

        scope.launch {
            try {
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
                            SendAacCardDto(
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

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}