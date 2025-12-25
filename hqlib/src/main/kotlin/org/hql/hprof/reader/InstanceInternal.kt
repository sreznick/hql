package org.hql.hprof.reader

import org.hql.hprof.heap.Identifier


data class InstanceInternal(
    val classId: Identifier,
    val fieldValues: Map<Identifier, Any>
)