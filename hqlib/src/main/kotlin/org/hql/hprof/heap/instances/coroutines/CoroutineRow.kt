package org.hql.hprof.heap.instances.coroutines

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineState
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineType
import org.hql.hprof.heap.instances.threads.ThreadRow

data class CoroutineRow(
    val instance: Instance.ObjectI,
    val type: CoroutineType,
    val state: CoroutineState,
    val parent: CoroutineRow?,
    val contextInfo: CoroutineContextInfo,
    // The live thread currently executing this coroutine, or null when it is suspended (on no
    // thread). Populated by the table after correlating frame roots; carries the JVM-level state
    // (e.g. BLOCKED on a monitor) that the coroutine's own state cannot express.
    val carrierThread: ThreadRow? = null
)