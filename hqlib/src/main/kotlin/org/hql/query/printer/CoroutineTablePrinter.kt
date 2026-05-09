package org.hql.query.printer

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.coroutines.CoroutineRow

/**
 * Renders coroutine table rows to a textual representation.
 * Responsible only for formatting and output
 */
object CoroutineTablePrinter {

    // default column set used when no explicit projection is provided
    private val DEFAULT_COLUMN_SET = listOf("id", "type", "state", "parent", "job_type", "dispatcher", "name")

    private const val COLUMN_WIDTH = 20

    fun print(
        rows: List<CoroutineRow>,
        columnNames: List<String>,
        resolve: (CoroutineRow, String) -> Any?,
        out: Appendable = System.out
    ) {
        val selectedColumns = columnNames.ifEmpty { DEFAULT_COLUMN_SET }

        out.appendLine(selectedColumns.joinToString(" | ") { it.padEnd(COLUMN_WIDTH) })

        for (row in rows) {
            val line = selectedColumns.joinToString(" | ") { col ->
                format(resolve(row, col)).padEnd(COLUMN_WIDTH)
            }
            out.appendLine(line)
        }
    }

    private fun format(value: Any?): String =
        when (value) {
            // heap object instances are rendered as their class names to avoid noisy object identity output
            is Instance.ObjectI -> value.cls.name
            null -> "-"
            else -> value.toString()
        }
}