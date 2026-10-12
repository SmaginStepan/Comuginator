package com.an0obis.comuginator.ui.conversation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.service.AdultMode
import com.an0obis.comuginator.storage.SettingsStore
import com.an0obis.comuginator.ui.OfflineBanner
import com.an0obis.comuginator.ui.base.BaseActivity
import com.an0obis.comuginator.ui.childhome.ChildHomeActivity
import com.an0obis.comuginator.ui.family.FamilyActivity
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Conversation mode: an AAC board in the style of Proloquo2Go. The bottom of
 * the screen is the family's conversation library (folders and word cards,
 * compact cards, vertical scroll); above it is the line of cards chosen so
 * far. Tapping a word speaks it and adds it to the line.
 *
 * The mode stays active (also across app restarts) until Exit is pressed.
 */
class ConversationActivity : BaseActivity(), TextToSpeech.OnInitListener {

    companion object {
        /** Turns conversation mode on and opens the screen. */
        fun start(context: Context) {
            SettingsStore(context).conversationMode = true
            context.startActivity(
                Intent(context, ConversationActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        }

        /** Width of one library cell, used to decide how many columns fit. */
        private const val CELL_WIDTH_DP = 96
        private const val MIN_COLUMNS = 3
        private const val MAX_COLUMNS = 12
    }

    private val viewModel: ConversationViewModel by viewModels()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var resumedOnce = false

    private lateinit var libraryAdapter: ConversationCardAdapter
    private lateinit var lineAdapter: ConversationCardAdapter
    private lateinit var rvLine: RecyclerView
    private lateinit var tvPath: TextView
    private lateinit var tvLineHint: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnUp: Button
    private lateinit var etSearch: EditText
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectedByRoleGuard) return
        ensureInitialized()
    }

    override fun onInitialized() {
        setContentView(R.layout.activity_conversation)

        // Edge-to-edge: keep the board clear of the status and navigation bars.
        val root = findViewById<View>(R.id.conversationRoot)
        val basePaddingTop = root.paddingTop
        val basePaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                top = basePaddingTop + bars.top,
                bottom = basePaddingBottom + bars.bottom
            )
            insets
        }

        val token = viewModel.store.token.orEmpty()

        tvPath = findViewById(R.id.tvConvPath)
        tvLineHint = findViewById(R.id.tvConvLineHint)
        tvEmpty = findViewById(R.id.tvConvEmpty)
        btnUp = findViewById(R.id.btnConvUp)
        etSearch = findViewById(R.id.etConvSearch)
        progress = findViewById(R.id.progressConv)
        rvLine = findViewById(R.id.rvConvLine)
        val rvLibrary = findViewById<RecyclerView>(R.id.rvConvLibrary)

        // Library: a folder opens, a word is spoken and added to the line.
        libraryAdapter = ConversationCardAdapter(token, R.layout.item_conversation_card) { cell, _ ->
            if (cell.isFolder) {
                viewModel.openFolder(cell)
            } else {
                viewModel.addToLine(cell)
                speak(cell.label)
            }
        }
        rvLibrary.layoutManager = GridLayoutManager(this, columnCount())
        rvLibrary.adapter = libraryAdapter
        rvLibrary.itemAnimator = null

        // Line: tapping a card takes it out of the message again.
        lineAdapter = ConversationCardAdapter(token, R.layout.item_conversation_line) { _, position ->
            viewModel.removeFromLine(position)
        }
        rvLine.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvLine.adapter = lineAdapter
        rvLine.itemAnimator = null

        findViewById<Button>(R.id.btnConvExit).setOnClickListener { exitConversation() }
        btnUp.setOnClickListener { viewModel.goUp() }
        findViewById<Button>(R.id.btnConvSpeak).setOnClickListener {
            val text = viewModel.lineText()
            if (text.isNotBlank()) speak(text)
        }
        findViewById<Button>(R.id.btnConvBackspace).setOnClickListener { viewModel.backspace() }
        findViewById<Button>(R.id.btnConvClear).setOnClickListener { viewModel.clearLine() }

        findViewById<Button>(R.id.btnConvSearch).setOnClickListener { runSearch() }
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch()
                true
            } else {
                false
            }
        }

        // Back goes up a folder; at the top it does nothing, so the mode is
        // only left deliberately with the Exit button.
        onBackPressedDispatcher.addCallback(this) { viewModel.goUp() }

        OfflineBanner.setup(this) { viewModel.loadNodes() }

        tts = TextToSpeech(applicationContext, this)

        bindState()
        viewModel.loadNodes()
    }

    override fun onResume() {
        super.onResume()
        // The parent may have changed the library meanwhile.
        if (resumedOnce && ::libraryAdapter.isInitialized) viewModel.loadNodes()
        resumedOnce = true
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    // ── Text to speech ───────────────────────────────────────────────────────

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Toast.makeText(this, R.string.conversation_tts_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val result = tts?.setLanguage(Locale(getArasaacLang()))
        ttsReady = result != null &&
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ttsReady) {
            Toast.makeText(this, R.string.conversation_tts_language_missing, Toast.LENGTH_LONG).show()
        }
    }

    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conversation")
    }

    // ── State ────────────────────────────────────────────────────────────────

    private fun bindState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.cells.collect { cells ->
                        libraryAdapter.submit(cells)
                        updateEmptyHint()
                    }
                }
                launch {
                    viewModel.loaded.collect { updateEmptyHint() }
                }
                launch {
                    viewModel.line.collect { line ->
                        lineAdapter.submit(line)
                        tvLineHint.visibility = if (line.isEmpty()) View.VISIBLE else View.GONE
                        if (line.isNotEmpty()) rvLine.scrollToPosition(line.size - 1)
                    }
                }
                launch {
                    viewModel.path.collect { path ->
                        val inFolder = path.size > 1
                        btnUp.visibility = if (inFolder) View.VISIBLE else View.INVISIBLE
                        tvPath.text = if (inFolder) {
                            path.drop(1).joinToString(" > ") { it.title }
                        } else {
                            getString(R.string.conversation_title)
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        progress.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ConversationViewModel.Event.ShowToast ->
                                Toast.makeText(this@ConversationActivity, event.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun updateEmptyHint() {
        tvEmpty.visibility =
            if (viewModel.loaded.value && viewModel.cells.value.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Cards stay a fixed small size; bigger screens simply fit more columns. */
    private fun columnCount(): Int {
        val metrics = resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        return (widthDp / CELL_WIDTH_DP).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }

    // ── ARASAAC search (adds to the line only, never to the library) ─────────

    private fun runSearch() {
        val query = etSearch.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return

        if (SettingsStore(this).offlineMode) {
            Toast.makeText(this, R.string.unavailable_offline, Toast.LENGTH_SHORT).show()
            return
        }

        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(etSearch.windowToken, 0)

        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.searchArasaac(
                    auth = viewModel.store.authHeaderOrThrow(),
                    query = query,
                    lang = getArasaacLang()
                )
                val results = response.items.map {
                    ConvCell(id = it.id, label = it.label, imageUrl = it.imageUrl)
                }
                if (results.isEmpty()) {
                    Toast.makeText(
                        this@ConversationActivity, R.string.conversation_search_none, Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showSearchResults(results)
                }
            } catch (e: Exception) {
                if (!handleUnauthorized(e)) {
                    Toast.makeText(
                        this@ConversationActivity,
                        getString(R.string.arasaac_search_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun showSearchResults(results: List<ConvCell>) {
        var dialog: AlertDialog? = null
        val adapter = ConversationCardAdapter(
            viewModel.store.token.orEmpty(), R.layout.item_conversation_card
        ) { cell, _ ->
            viewModel.addToLine(cell)
            speak(cell.label)
            dialog?.dismiss()
        }
        adapter.submit(results)

        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@ConversationActivity, 3)
            this.adapter = adapter
            setPadding(8, 8, 8, 8)
        }

        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.choose_arasaac_card)
            .setView(grid)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    // ── Leaving ──────────────────────────────────────────────────────────────

    private fun exitConversation() {
        SettingsStore(this).conversationMode = false

        val childDevice = viewModel.store.role == "CHILD" && !AdultMode.active
        val target = Intent(
            this,
            if (childDevice) ChildHomeActivity::class.java else FamilyActivity::class.java
        ).apply {
            if (childDevice) putExtra(ChildHomeActivity.EXTRA_EDITOR_MODE, false)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(target)
        finish()
    }
}
