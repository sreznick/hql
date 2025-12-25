package org.hql.hprof.reader

import org.hql.hprof.heap.BasicType
import org.hql.hprof.heap.Identifier


data class ClassInternal(
    val id: Identifier,
    val superclassId: Identifier,
    val instanceSize: Int,
    val staticFields: Map<Identifier, Any>,
    val instanceFieldTypes: List<Pair<Identifier, BasicType>>
)