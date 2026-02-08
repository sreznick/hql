package org.hql.hprof.heap.instances.coroutines

import org.hql.hprof.heap.instances.coroutines.enums.Dispatcher
import org.hql.hprof.heap.instances.coroutines.enums.JobType

/**
 * CoroutineContext representation
 */
data class CoroutineContextInfo(
    var job: JobType?,
    val dispatcher: Dispatcher?,
    val name: String?
) {
    constructor() : this(null, null, null)
}