package org.hql.query.tables

import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.instances.coroutines.CoroutineRow
import org.hql.hprof.reader.coroutines.CoroutineHeapSearcher
import org.hql.query.Cell

/**
 * Table implementation over coroutine data extracted from a heap dump
 */
class CoroutineTable(heap: Heap) : AbstractTable<CoroutineRow>() {

    private val resolver = CoroutineColumnResolver()

    override val baseColumns: List<String> = DEFAULT_COLUMNS

    // converted coroutine rows to internal format from found coroutines in the dump
    override val rows: List<CoroutineRow> = CoroutineHeapSearcher(heap).findAll()

    override fun resolveCell(row: CoroutineRow, column: String): Cell = resolver.resolve(row, column)

    companion object {
        private val DEFAULT_COLUMNS = listOf(
            "id", "type", "state", "parent", "job_type", "dispatcher", "name"
        )
    }
}
