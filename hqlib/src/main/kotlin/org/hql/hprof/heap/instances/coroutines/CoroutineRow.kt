package org.hql.hprof.heap.instances.coroutines

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineState
import org.hql.hprof.heap.instances.coroutines.enums.CoroutineType

data class CoroutineRow(
    val instance: Instance.ObjectI,
    val type: CoroutineType,
    val state: CoroutineState,
    val parent: CoroutineRow?,
    val contextInfo: CoroutineContextInfo
)