package com.an0obis.comuginator.ui.conversation

import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.Coil
import coil.dispose
import coil.request.ImageRequest
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws a [BoardPage] into a [FixedGridLayout] that lives inside a scroll
 * container, and keeps it sized to the container: cards scale down a little on
 * smaller screens and the container scrolls when they would get too small.
 *
 * In editor mode empty cells are shown as tappable "+" placeholders (one extra
 * row is always available) and cards can be dragged onto other cells.
 */
class BoardController(
    private val context: Context,
    private val container: View,
    private val grid: FixedGridLayout,
    private val authToken: String,
    private val editor: Boolean,
    private val onCellClick: (ConvCell) -> Unit,
    private val onEmptyClick: (row: Int, col: Int) -> Unit = { _, _ -> },
    private val onMove: (nodeId: String, row: Int, col: Int) -> Unit = { _, _, _ -> }
) {
    private var page = BoardPage.EMPTY
    private var homeCols = 0
    private var homeRows = 0
    private var renderedW = -1
    private var renderedH = -1

    init {
        container.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) render()
        }
    }

    /**
     * [homeCols] and [homeRows] are the size of the home page: every page uses
     * the card size derived from it, so cards never change size between pages.
     */
    fun show(page: BoardPage, homeCols: Int, homeRows: Int) {
        this.page = page
        this.homeCols = homeCols
        this.homeRows = homeRows
        render()
    }

    private fun render() {
        val availW = container.width - container.paddingLeft - container.paddingRight
        val availH = container.height - container.paddingTop - container.paddingBottom
        if (availW <= 0 || availH <= 0) return
        renderedW = availW
        renderedH = availH

        val dispCols = if (editor) max(max(page.cols, homeCols), BoardLayout.DEFAULT_COLS) else page.cols
        val dispRows = if (editor) max(page.rows + 1, MIN_EDITOR_ROWS) else page.rows

        val sizingCols = if (editor) max(max(homeCols, 1), BoardLayout.DEFAULT_COLS) else max(homeCols, page.cols)
        val sizingRows = if (editor) max(homeRows + 1, MIN_EDITOR_ROWS) else max(homeRows, page.rows)
        val cellW = BoardSizing.cellWidthPx(
            availW, availH, sizingCols, sizingRows, context.resources.displayMetrics.density
        )
        val cellH = (cellW * BoardSizing.ASPECT).toInt()

        grid.removeAllViews()
        grid.configure(dispCols, dispRows, cellW, cellH)

        val occupied = HashSet<Long>()
        for (p in page.placed) {
            occupied += p.row.toLong() * 1000L + p.col
            addCard(p, cellW)
        }
        if (editor) {
            for (r in 0 until dispRows) for (c in 0 until dispCols) {
                if (r.toLong() * 1000L + c !in occupied) addEmpty(r, c, cellW)
            }
        }
    }

    private fun addCard(p: PlacedCell, cellW: Int) {
        val cell = p.cell
        val view = LayoutInflater.from(context).inflate(R.layout.item_board_card, grid, false)
        val card = view.findViewById<LinearLayout>(R.id.boardCard)
        val image = view.findViewById<ImageView>(R.id.ivCard)
        val label = view.findViewById<TextView>(R.id.tvCardLabel)
        val badge = view.findViewById<ImageView>(R.id.ivFolderBadge)

        val margin = (cellW * 0.03f).roundToInt().coerceAtLeast(1)
        (card.layoutParams as FrameLayout.LayoutParams).setMargins(margin, margin, margin, margin)
        val pad = (cellW * 0.04f).roundToInt()
        card.setPadding(pad, pad, pad, pad)
        card.background = CardPalette.background(
            context, CardPalette.effectiveKey(cell.color, cell.isFolder), cell.isFolder, cellW
        )

        val imageSize = (cellW * 0.62f).roundToInt()
        image.layoutParams = (image.layoutParams as LinearLayout.LayoutParams).apply {
            width = imageSize
            height = imageSize
        }
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, max(cellW * 0.125f, 9f * context.resources.displayMetrics.density))
        label.text = cell.label
        badge.visibility = if (cell.isFolder) View.VISIBLE else View.GONE

        val url = cell.imageUrl
        if (url.isNullOrBlank()) {
            image.dispose()
            image.setImageDrawable(null)
        } else {
            val request = ImageRequest.Builder(context)
                .data(url)
                .apply {
                    // Never send our token to third-party hosts (ARASAAC).
                    if (ApiClient.isProtectedImageUrl(url)) addHeader("Authorization", "Bearer $authToken")
                }
                .target(image)
                .build()
            Coil.imageLoader(context).enqueue(request)
        }

        view.setOnClickListener { onCellClick(cell) }
        if (editor) {
            view.setOnLongClickListener {
                it.startDragAndDrop(
                    ClipData.newPlainText("node", cell.id), View.DragShadowBuilder(it), null, 0
                )
                true
            }
            attachDrop(view, p.row, p.col)
        }
        grid.addView(view, FixedGridLayout.LayoutParams(p.row, p.col))
    }

    private fun addEmpty(row: Int, col: Int, cellW: Int) {
        val density = context.resources.displayMetrics.density
        val margin = (cellW * 0.03f).roundToInt().coerceAtLeast(1)
        val plus = TextView(context).apply {
            text = "+"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#BDBDBD"))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, cellW * 0.4f)
            contentDescription = context.getString(R.string.add_card_here)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = cellW * 0.09f
                setStroke((1.5f * density).toInt(), Color.parseColor("#D0D0D0"), 6f * density, 4f * density)
            }
        }
        val holder = FrameLayout(context).apply {
            addView(plus, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(margin, margin, margin, margin) })
            setOnClickListener { onEmptyClick(row, col) }
        }
        attachDrop(holder, row, col)
        grid.addView(holder, FixedGridLayout.LayoutParams(row, col))
    }

    /** Every cell, empty or not, accepts a dragged card. */
    private fun attachDrop(view: View, row: Int, col: Int) {
        view.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = 0.55f; true }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1f; true }
                DragEvent.ACTION_DROP -> {
                    v.alpha = 1f
                    val id = event.clipData?.getItemAt(0)?.text?.toString()
                    if (id != null) onMove(id, row, col)
                    true
                }
                else -> true
            }
        }
    }

    private companion object {
        const val MIN_EDITOR_ROWS = 3
    }
}
