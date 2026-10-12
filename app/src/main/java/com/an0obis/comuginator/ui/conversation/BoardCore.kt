package com.an0obis.comuginator.ui.conversation

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min

/** A card together with the cell it occupies on its page. */
data class PlacedCell(val cell: ConvCell, val row: Int, val col: Int)

/** One page of the board: the cards at their cells and the page's grid size. */
data class BoardPage(val placed: List<PlacedCell>, val cols: Int, val rows: Int) {
    companion object {
        val EMPTY = BoardPage(emptyList(), 0, 0)
    }
}

/**
 * Turns the nodes of one level into a [BoardPage]. Cards that have a cell keep
 * it; cards without one (older libraries, cards added elsewhere) flow into the
 * first free cells, row by row, so nothing is ever lost.
 */
object BoardLayout {
    const val DEFAULT_COLS = 8

    fun build(cells: List<ConvCell>): BoardPage {
        val taken = HashSet<Long>()
        val placed = ArrayList<PlacedCell>()
        val unplaced = ArrayList<ConvCell>()

        for (c in cells) {
            val r = c.row
            val col = c.col
            if (r != null && col != null && r >= 0 && col >= 0 && taken.add(key(r, col))) {
                placed += PlacedCell(c, r, col)
            } else {
                unplaced += c
            }
        }

        var cols = (placed.maxOfOrNull { it.col } ?: -1) + 1
        if (unplaced.isNotEmpty()) {
            cols = max(cols, min(DEFAULT_COLS, max(unplaced.size, 1)))
        }

        var r = 0
        var c = 0
        for (cell in unplaced) {
            while (!taken.add(key(r, c))) {
                c++
                if (c >= cols) { c = 0; r++ }
            }
            placed += PlacedCell(cell, r, c)
            c++
            if (c >= cols) { c = 0; r++ }
        }

        val rows = (placed.maxOfOrNull { it.row } ?: -1) + 1
        return BoardPage(placed.sortedWith(compareBy({ it.row }, { it.col })), cols, rows)
    }

    private fun key(row: Int, col: Int) = row.toLong() * 1000L + col
}

/**
 * Card size rule: as big as fits the viewport (both ways), but never bigger
 * than [MAX_CELL_DP] and never smaller than [MIN_CELL_DP]. Below the minimum
 * the board keeps its size and the viewport scrolls instead.
 */
object BoardSizing {
    const val MAX_CELL_DP = 96f
    const val MIN_CELL_DP = 64f
    /** Cell height relative to its width. */
    const val ASPECT = 1.18f

    fun cellWidthPx(availW: Int, availH: Int, cols: Int, rows: Int, density: Float): Int {
        val byWidth = availW.toFloat() / max(cols, 1)
        val byHeight = availH.toFloat() / (max(rows, 1) * ASPECT)
        return min(byWidth, byHeight).coerceIn(MIN_CELL_DP * density, MAX_CELL_DP * density).toInt()
    }
}

/** Word-class colours (Modified Fitzgerald Key idea), as soft tints. */
object CardPalette {
    val KEYS = listOf(
        "PRONOUN", "VERB", "NOUN", "DESCRIPTOR", "ADVERB",
        "PREPOSITION", "QUESTION", "NEGATION", "SOCIAL", "MISC", "FOLDER"
    )

    private val TINTS = mapOf(
        "PRONOUN" to 0xFFFFF2A8.toInt(),
        "VERB" to 0xFFBFE8B0.toInt(),
        "NOUN" to 0xFFFFD9A8.toInt(),
        "DESCRIPTOR" to 0xFFBCDDFB.toInt(),
        "ADVERB" to 0xFFE2CBB3.toInt(),
        "PREPOSITION" to 0xFFF6C6E2.toInt(),
        "QUESTION" to 0xFFD9C7F2.toInt(),
        "NEGATION" to 0xFFF7B7B7.toInt(),
        "SOCIAL" to 0xFFFBC1D8.toInt(),
        "MISC" to 0xFFF0F0F0.toInt(),
        "FOLDER" to 0xFFFFF3D6.toInt()
    )

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val FOLDER_STROKE = 0xFFF0A500.toInt()
    private const val STROKE = 0xFF9E9E9E.toInt()

    /** The colour key actually used: folders default to FOLDER, words to white. */
    fun effectiveKey(color: String?, isFolder: Boolean): String? =
        color?.takeIf { it in TINTS } ?: if (isFolder) "FOLDER" else null

    fun tint(key: String?): Int = key?.let { TINTS[it] } ?: WHITE

    fun background(context: Context, key: String?, isFolder: Boolean, cellWidthPx: Int): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(tint(key))
            cornerRadius = cellWidthPx * 0.09f
            setStroke(
                (if (isFolder) 2f else 1.2f).times(density).toInt().coerceAtLeast(1),
                if (isFolder) FOLDER_STROKE else STROKE
            )
        }
    }
}

/**
 * Lays its children out on a fixed grid: each child sits at the (row, col) of
 * its [LayoutParams], every cell has the same size. The view is exactly as big
 * as the grid, so a scroll container around it can pan when it is larger than
 * the screen.
 */
class FixedGridLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    class LayoutParams(val row: Int, val col: Int) : ViewGroup.LayoutParams(0, 0)

    private var cols = 0
    private var rows = 0
    private var cellW = 0
    private var cellH = 0

    fun configure(cols: Int, rows: Int, cellW: Int, cellH: Int) {
        this.cols = cols
        this.rows = rows
        this.cellW = cellW
        this.cellH = cellH
        requestLayout()
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = p is LayoutParams
    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(0, 0)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams = LayoutParams(0, 0)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val childW = MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY)
        val childH = MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(childW, childH)
        setMeasuredDimension(cols * cellW, rows * cellH)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child: View = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            val left = lp.col * cellW
            val top = lp.row * cellH
            child.layout(left, top, left + cellW, top + cellH)
        }
    }
}
