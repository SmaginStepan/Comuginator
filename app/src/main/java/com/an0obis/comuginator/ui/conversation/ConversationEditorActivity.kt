package com.an0obis.comuginator.ui.conversation

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.UpdateChildHomeNodeRequest
import com.an0obis.comuginator.service.OfflinePrefetchScheduler
import com.an0obis.comuginator.ui.base.BaseActivity
import com.an0obis.comuginator.ui.library.LibraryItemPickerActivity
import kotlinx.coroutines.launch

/**
 * Parents build the conversation board here: the same fixed grid the child
 * sees, with "+" in every empty cell. Tap an empty cell to add a card, tap a
 * card for its menu, long-press and drag a card to move it (onto an occupied
 * cell it swaps places). The starter vocabulary can be installed in one tap.
 */
class ConversationEditorActivity : BaseActivity() {

    private val viewModel: ConversationEditorViewModel by viewModels()

    private lateinit var board: BoardController
    private lateinit var tvPath: TextView
    private lateinit var btnUp: Button
    private lateinit var btnHome: Button
    private lateinit var progress: ProgressBar

    private val pickItemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = viewModel.pending
            viewModel.pending = null
            if (result.resultCode != RESULT_OK || pending == null) return@registerForActivityResult
            val itemId = LibraryItemPickerActivity.parseResultItemId(result.data)
                ?: return@registerForActivityResult

            when (pending) {
                is PendingEdit.NewCard -> askTypeThenCreate(pending.row, pending.col, itemId)
                is PendingEdit.ChangePicture ->
                    viewModel.update(pending.nodeId, UpdateChildHomeNodeRequest(itemId = itemId))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectedByRoleGuard) return
        ensureInitialized()
    }

    override fun onInitialized() {
        setContentView(R.layout.activity_conversation_editor)

        val root = findViewById<View>(R.id.editorRoot)
        val basePaddingTop = root.paddingTop
        val basePaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(top = basePaddingTop + bars.top, bottom = basePaddingBottom + bars.bottom)
            insets
        }

        tvPath = findViewById(R.id.tvEditorPath)
        btnUp = findViewById(R.id.btnEditorUp)
        btnHome = findViewById(R.id.btnEditorHome)
        progress = findViewById(R.id.progressEditor)

        board = BoardController(
            context = this,
            container = findViewById(R.id.boardScroll),
            grid = findViewById(R.id.boardGrid),
            authToken = viewModel.store.token.orEmpty(),
            editor = true,
            onCellClick = ::showCardMenu,
            onEmptyClick = ::startNewCard,
            onMove = { id, row, col -> viewModel.move(id, row, col) }
        )

        btnUp.setOnClickListener { viewModel.goUp() }
        btnHome.setOnClickListener { viewModel.goHome() }
        findViewById<Button>(R.id.btnStarter).setOnClickListener { confirmStarter() }

        onBackPressedDispatcher.addCallback(this) {
            if (!viewModel.goUp()) finish()
        }

        bindState()
        viewModel.loadNodes()
    }

    private fun bindState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.page.collect { page ->
                        board.show(page, viewModel.homeCols, viewModel.homeRows)
                    }
                }
                launch {
                    viewModel.path.collect { path ->
                        val inFolder = path.size > 1
                        btnUp.visibility = if (inFolder) View.VISIBLE else View.INVISIBLE
                        btnHome.visibility = if (inFolder) View.VISIBLE else View.INVISIBLE
                        tvPath.text = if (inFolder) {
                            path.drop(1).joinToString(" > ") { it.title }
                        } else {
                            getString(R.string.conversation_library)
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { progress.visibility = if (it) View.VISIBLE else View.GONE }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ConversationEditorViewModel.Event.ShowToast ->
                                Toast.makeText(this@ConversationEditorActivity, event.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    // ── Adding a card ────────────────────────────────────────────────────────

    private fun startNewCard(row: Int, col: Int) {
        viewModel.pending = PendingEdit.NewCard(row, col)
        pickItemLauncher.launch(
            LibraryItemPickerActivity.createIntent(this, LibraryItemPickerActivity.TargetMode.NODE_CARD)
        )
    }

    private fun askTypeThenCreate(row: Int, col: Int, itemId: String) {
        val labels = arrayOf(getString(R.string.element_menu), getString(R.string.element_word))
        AlertDialog.Builder(this)
            .setTitle(R.string.child_home_node_type_title)
            .setItems(labels) { _, which ->
                if (which == 0) {
                    // A section (folder) has its own look; no colour to choose.
                    viewModel.createCard(itemId, "MENU", null, row, col)
                } else {
                    askColor(skippable = true) { color -> viewModel.createCard(itemId, "WORD", color, row, col) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Card menu ────────────────────────────────────────────────────────────

    private fun showCardMenu(cell: ConvCell) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (cell.isFolder) {
            actions += getString(R.string.open_folder) to { viewModel.openFolder(cell) }
        }
        actions += getString(R.string.rename) to { askRename(cell) }
        actions += getString(R.string.change_picture) to {
            viewModel.pending = PendingEdit.ChangePicture(cell.id)
            pickItemLauncher.launch(
                LibraryItemPickerActivity.createIntent(this, LibraryItemPickerActivity.TargetMode.NODE_CARD)
            )
        }
        if (!cell.isFolder) {
            actions += getString(R.string.change_color) to {
                askColor(skippable = false) { color ->
                    viewModel.update(cell.id, UpdateChildHomeNodeRequest(color = color))
                }
            }
        }
        actions += getString(R.string.delete) to { confirmDelete(cell) }

        AlertDialog.Builder(this)
            .setTitle(cell.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun askRename(cell: ConvCell) {
        val input = EditText(this).apply {
            setText(cell.label)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    viewModel.update(cell.id, UpdateChildHomeNodeRequest(labelOverride = name))
                }
            }
            .show()
    }

    private fun confirmDelete(cell: ConvCell) {
        AlertDialog.Builder(this)
            .setTitle(cell.label)
            .setMessage(if (cell.isFolder) R.string.delete_folder_confirm else R.string.delete_card_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(cell.id) }
            .show()
    }

    // ── Colour ───────────────────────────────────────────────────────────────

    private val colourKeys = CardPalette.KEYS.filter { it != "FOLDER" }

    private fun colourLabel(key: String): String = getString(
        when (key) {
            "PRONOUN" -> R.string.color_pronoun
            "VERB" -> R.string.color_verb
            "NOUN" -> R.string.color_noun
            "DESCRIPTOR" -> R.string.color_descriptor
            "ADVERB" -> R.string.color_adverb
            "PREPOSITION" -> R.string.color_preposition
            "QUESTION" -> R.string.color_question
            "NEGATION" -> R.string.color_negation
            "SOCIAL" -> R.string.color_social
            else -> R.string.color_misc
        }
    )

    private fun askColor(skippable: Boolean, onChosen: (String?) -> Unit) {
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.choose_color)
            .setItems(colourKeys.map(::colourLabel).toTypedArray()) { _, which -> onChosen(colourKeys[which]) }
            .setNegativeButton(R.string.cancel, null)
        if (skippable) builder.setNeutralButton(R.string.no_color) { _, _ -> onChosen(null) }
        builder.show()
    }

    // ── Starter vocabulary ───────────────────────────────────────────────────

    private fun confirmStarter() {
        val replace = !viewModel.libraryEmpty
        AlertDialog.Builder(this)
            .setTitle(R.string.starter_install_title)
            .setMessage(if (replace) R.string.starter_replace_message else R.string.starter_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.install) { _, _ ->
                viewModel.installStarter(getArasaacLang(), replace) { created ->
                    Toast.makeText(
                        applicationContext, getString(R.string.starter_done, created), Toast.LENGTH_LONG
                    ).show()
                    // Save the new cards and their pictures for offline use.
                    OfflinePrefetchScheduler.enqueueIfDue(applicationContext, force = true)
                }
            }
            .show()
    }
}
