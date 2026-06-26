package org.hql.hprof.reader.coroutines

import org.hql.hprof.heap.Heap
import org.hql.hprof.heap.Identifier
import org.hql.hprof.heap.instances.Instance

/**
 * Correlates running coroutines with the worker thread currently executing them
 *
 * A coroutine is tied to a thread only while it is actually running on it — computing, or blocked
 * in JVM code (e.g. monitor or `Object.wait`). A *suspended* coroutine lives on no thread and so
 * never appears here. This is the only way to observe blocking that happens in plain JVM code
 * (synchronized / wait / sleep): the coroutine state machine itself can't see it, but the carrier
 * thread's JVM state can
 *
 * The link is recovered from HPROF stack-frame roots ([ROOT JAVA FRAME] / [ROOT JNI LOCAL]): the
 * carrier thread's frames reference the running `DispatchedContinuation` / suspend lambda, whose
 * `continuation`/`completion` chain terminates at the coroutine (`AbstractCoroutine`) instance
 */
class CoroutineThreadLinker(private val heap: Heap) {

    /**
     * Maps coroutine instance id -> the object id of the thread running it.
     * Only running coroutines are present; suspended ones are absent.
     * The first thread found to reach a coroutine wins (a coroutine runs on at most one thread at a time)
     */
    fun carrierThreadByCoroutine(coroutineIds: Set<Identifier>): Map<Identifier, Identifier> {
        if (coroutineIds.isEmpty()) return emptyMap()

        val result = hashMapOf<Identifier, Identifier>()
        for ((threadId, rootIds) in heap.frameRootsByThreadObjectId) {
            for (rootId in rootIds) {
                walkContinuationChain(rootId) { reachedId ->
                    if (reachedId in coroutineIds) result.putIfAbsent(reachedId, threadId)
                }
            }
        }
        return result
    }

    // Follows the `continuation`/`completion` reference chain from a frame-rooted object, invoking
    // [onReach] for every object id visited. A visited set keeps the walk safe against the cyclic
    // and deeply-shared graphs these continuations form
    private fun walkContinuationChain(startId: Identifier, onReach: (Identifier) -> Unit) {
        val visited = hashSetOf(startId)
        val pending = ArrayDeque<Identifier>().apply { addLast(startId) }
        while (pending.isNotEmpty()) {
            val id = pending.removeLast()
            onReach(id)
            // Reading `fields` lazily resolves field names and values, which look up strings,
            // classes and instances in the dump via Map.getValue — that throws NoSuchElementException
            // on a dangling reference (an id present in a field but absent from the dump). Skip such
            // objects rather than abort the whole correlation; they are never the chain we're after
            val fields = try {
                heap.getObjectById(id)?.fields ?: continue
            } catch (_: NoSuchElementException) {
                continue
            }
            for (field in CHAIN_FIELDS) {
                // mark visited on enqueue (not on dequeue) so a node shared by many edges is added
                // to the queue at most once, keeping `pending` bounded on densely-linked graphs
                (fields[field] as? Instance.ObjectI)
                    ?.takeIf { visited.add(it.id) }
                    ?.let { pending.addLast(it.id) }
            }
        }
    }

    companion object {
        // kotlinx continuation linkage: DispatchedContinuation.continuation -> BaseContinuationImpl,
        // and BaseContinuationImpl.completion -> ... -> the coroutine (AbstractCoroutine)
        private val CHAIN_FIELDS = listOf("continuation", "completion")
    }
}
