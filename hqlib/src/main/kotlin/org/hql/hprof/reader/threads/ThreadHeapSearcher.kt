package org.hql.hprof.reader.threads

import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.instances.threads.ThreadRow

/**
 * Collects the live threads recorded in a heap dump.
 *
 * Threads are identified by the [ROOT THREAD OBJECT] roots captured while reading the dump
 * (one per live thread), so the result count is the live thread count.
 *It includes Thread subclasses such as kotlinx's CoroutineScheduler$Worker.
 */
class ThreadHeapSearcher(
    private val heap: Heap,
    private val mapper: ThreadMapper = ThreadInstanceMapper
) {

    fun findAll(): List<ThreadRow> =
        heap.threadObjectIds
            .distinct()
            .mapNotNull { heap.getObjectById(it) }
            .map { mapper(it) }
}
