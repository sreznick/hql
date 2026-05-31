package org.hql.hprof.heap.instances.threads

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.threads.enums.ThreadState

/**
 * A live thread extracted from the heap dump.

 */
data class ThreadRow(
    val instance: Instance.ObjectI,
    val className: String,
    val name: String?,
// is the JVM-level thread state (e.g. BLOCKED), decoded from the Thread's `threadStatus`
    val state: ThreadState,
    val daemon: Boolean?,
    val priority: Int?,
    val tid: Long?
)
