package org.hql.query.tables

import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.instances.threads.ThreadRow
import org.hql.hprof.reader.threads.ThreadHeapSearcher

/**
 * Table implementation over the threads extracted from a heap dump
 *
 * The row count is the live thread count; `state` is the JVM thread state.
 * Any unrecognized column falls back to raw heap-field access on the Thread instance,
 * so framework-specific fields (e.g. a worker's `state` WorkerState) remain reachable via `<class-field>` selection.
 */
class ThreadTable(heap: Heap) : Table() {

    override val baseColumns: List<String> = DEFAULT_COLUMNS

    override val rows: List<ThreadRow> = ThreadHeapSearcher(heap).findAll()

    companion object {
        private val DEFAULT_COLUMNS = listOf(
            "id", "name", "state", "daemon", "priority", "tid", "class"
        )
    }
}
