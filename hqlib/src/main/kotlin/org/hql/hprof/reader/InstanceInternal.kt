package org.hql.hprof.reader

import org.hql.hprof.heap.Identifier


sealed class InstanceInternal {
    data class Object(
        val id: Identifier,
        val classId: Identifier,
        val fieldValues: Map<Identifier, BasicValue>
    ) : InstanceInternal()

    data class ObjectArray(
        val ids: List<Identifier>
    ) : InstanceInternal()

    data class PrimitiveArray(
        val values: List<BasicValue>
    ) : InstanceInternal()
}