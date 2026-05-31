package org.hql.query.tables

import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.instances.threads.ThreadRow
import org.hql.hprof.reader.threads.ThreadHeapSearcher
import org.hql.query.BooleanCell
import org.hql.query.Cell
import org.hql.query.IntCell
import org.hql.query.NullCell
import org.hql.query.StringCell

/**
 * Table implementation over the threads extracted from a heap dump
 *
 * The row count is the live thread count; `state` is the JVM thread state.
 * Any unrecognized column falls back to raw heap-field access on the Thread instance,
 * so framework-specific fields (e.g. a worker's `state` WorkerState) remain reachable via `<class-field>` selection.
 */
class ThreadTable(heap: Heap) : AbstractTable<ThreadRow>() {

    override val baseColumns: List<String> = DEFAULT_COLUMNS

    override val rows: List<ThreadRow> = ThreadHeapSearcher(heap).findAll()

    override fun resolveCell(row: ThreadRow, column: String): Cell =
        when (column) {
            "id" -> StringCell(row.instance.id.toCompactHex())
            "name" -> row.name?.let(::StringCell) ?: NullCell
            "state" -> StringCell(row.state.name)
            "daemon" -> row.daemon?.let(::BooleanCell) ?: NullCell
            "priority" -> row.priority?.let { IntCell(it.toLong()) } ?: NullCell
            "tid" -> row.tid?.let(::IntCell) ?: NullCell
            "class" -> StringCell(row.className)

            // Fallback to raw heap field access for fields not exposed as table columns.
            else -> Cell.fromInstance(row.instance[column])
        }

    companion object {
        private val DEFAULT_COLUMNS = listOf(
            "id", "name", "state", "daemon", "priority", "tid", "class"
        )
    }
}
