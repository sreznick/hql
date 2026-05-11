package org.hql.hprof.reader.coroutines

import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.instances.coroutines.CoroutineRow

/**
 * Scans heap dump for known coroutine implementations and converts heap instances into coroutine models
 */
class CoroutineHeapSearcher(
    private val heap: Heap,
    private val mapper: CoroutineMapper = CoroutineInstanceMapper
) {

    fun findAll(): List<CoroutineRow> =
        COROUTINE_CLASS_NAMES
            .mapNotNull { heap.findClassByName(it) }
            .flatMap { cls -> cls.instances.map { mapper(it, cls.name) } }

    companion object {
        // available coroutine types to search in heap
        private val COROUTINE_CLASS_NAMES = listOf(
            "kotlinx.coroutines.BlockingCoroutine",
            "kotlinx.coroutines.StandaloneCoroutine",
            "kotlinx.coroutines.LazyStandaloneCoroutine",
            "kotlinx.coroutines.internal.ScopeCoroutine",
            "kotlinx.coroutines.JobImpl",
            "kotlinx.coroutines.SupervisorJobImpl",
        )
    }
}
