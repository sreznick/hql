package org.hql.hprof.reader.coroutines

import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.instances.coroutines.CoroutineRow

/**
 * Scans heap dump for known coroutine implementations and converts heap instances into coroutine models
 */
class CoroutineHeapSearcher(
    private val heap: Heap,
    private val mapper: CoroutineInstanceMapper = CoroutineInstanceMapper
) {

    // available coroutine types to search in heap
    private val coroutineClassNames = listOf(
        "kotlinx.coroutines.BlockingCoroutine",
        "kotlinx.coroutines.StandaloneCoroutine",
        "kotlinx.coroutines.LazyStandaloneCoroutine",
        "kotlinx.coroutines.internal.ScopeCoroutine"
    )

    fun findAll(): List<CoroutineRow> {
        val result = mutableListOf<CoroutineRow>()

        for (className in coroutineClassNames) {
            val cls = try {
                heap.getClassByName(className)
            } catch (_: Exception) {
                // class is absent in heap
                continue
            }

            for (instance in cls.getInstances()) {
                result += mapper.getCoroutineRow(instance, cls.getName())
            }
        }

        return result
    }
}