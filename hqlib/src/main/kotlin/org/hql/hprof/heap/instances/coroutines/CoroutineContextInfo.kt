package org.hql.hprof.heap.instances.coroutines

import org.hql.hprof.heap.instances.coroutines.enums.Dispatcher

/**
 * CoroutineContext representation
 */
data class CoroutineContextInfo(
    val dispatcher: Dispatcher?,
    val name: String?
) {
    constructor() : this(null, null)
}