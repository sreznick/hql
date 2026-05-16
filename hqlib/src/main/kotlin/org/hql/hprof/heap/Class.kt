package org.hql.hprof.heap

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.Instance.ObjectI
import org.hql.hprof.reader.BasicType
import org.hql.hprof.reader.ClassInternal
import org.hql.hprof.reader.Hprof

class Class(private val hprof: Hprof, private val cls: ClassInternal) {
    val id: Identifier = cls.id

    val name: String by lazy {
        hprof.getClassName(cls.id)
    }

    val superclass: Class? by lazy {
        if (cls.superclassId.isNull())
            null
        else Class(hprof, hprof.getClassById(cls.superclassId))
    }

    val instanceFieldTypes: Map<String, BasicType> by lazy {
        cls.instanceFieldTypes.associate { (fieldId, value) ->
            hprof.getString(fieldId) to value
        }
    }

    val staticFields: Map<String, Instance> by lazy {
        cls.staticFields
            .mapKeys { (fieldId, _) -> hprof.getString(fieldId) }
            .mapValues { (_, inst) -> Instance.create(hprof, inst) }
    }

    val instances: List<ObjectI> by lazy {
        hprof.getInstancesOfClass(cls.id).map {
            ObjectI(hprof, it)
        }.toList()
    }

    override fun toString() = "<Class $name>"
    operator fun get(name: String) = staticFields.getValue(name)
}