package org.hql.query.tables

import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.instances.coroutines.CoroutineRow
import org.hql.hprof.reader.coroutines.CoroutineHeapSearcher

/**
 * Table implementation over coroutine data extracted from a heap dump
 */
class CoroutineTable(heap: Heap) : Table() {

    override val baseColumns: List<String> = DEFAULT_COLUMNS

    // converted coroutine rows to internal format from found coroutines in the dump
    override val rows: List<CoroutineRow> = CoroutineHeapSearcher(heap).findAll()

    companion object {
        private val DEFAULT_COLUMNS = listOf(
            "id", "type", "state", "parent", "job_type", "dispatcher", "name"
        )
    }
}
