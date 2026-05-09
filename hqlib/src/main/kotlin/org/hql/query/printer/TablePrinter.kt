package org.hql.query.printer

import org.hql.query.Cell
import org.hql.query.NullCell
import org.hql.query.ObjectCell
import org.hql.query.StringCell
import kotlin.math.max

/**
 * Renders a table as plain text. Format-only — knows nothing about how rows are produced.
 */
object TablePrinter {

    private const val MIN_COLUMN_WIDTH = 0

    fun print(
        columns: List<String>,
        rows: List<List<Cell>>,
        out: Appendable = System.out
    ) {
        if (columns.isEmpty()) return

        val rendered = rows.map { row -> row.map { it.formatForCell() } }
        val widths = columns.indices.map { i ->
            val maxRowWidth = rendered.maxOfOrNull { it[i].length } ?: MIN_COLUMN_WIDTH
            max(columns[i].length, maxRowWidth)
        }

        out.appendLine(columns.indices.joinToString(" | ") { i -> columns[i].padEnd(widths[i]) })
        for (row in rendered) {
            out.appendLine(row.indices.joinToString(" | ") { i -> row[i].padEnd(widths[i]) })
        }
    }

    /**
     * Cell rendering tuned for tables: strings without surrounding quotes,
     * heap objects as their class name, and null as a dash placeholder
     */
    private fun Cell.formatForCell(): String = when (this) {
        is NullCell -> "-"
        is StringCell -> value
        is ObjectCell -> obj.cls.name
        else -> toString()
    }
}