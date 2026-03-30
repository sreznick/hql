package org.hql.hprof.reader

import org.hql.hprof.heap.BasicType
import org.hql.hprof.heap.Identifier

class Hprof {
    private val strings = hashMapOf<Identifier, String>()
    private val classNames = hashMapOf<Identifier, Identifier>()
    private val classes = hashMapOf<Identifier, ClassInternal>()
    private val instances = hashMapOf<Identifier, Any>()

    fun addString(id: Identifier, value: String) {
        if (strings.containsKey(id)) {
            throw RuntimeException("Duplicate string '$id' with value '$value'")
        }
        strings[id] = value
    }

    fun addClassName(classId: Identifier, nameId: Identifier) {
        if (classNames.containsKey(classId)) {
            throw RuntimeException("Duplicate className '$classId' with value '$nameId'")
        }
        classNames[classId] = nameId
    }

    fun addClass(
        classId: Identifier,
        superclassId: Identifier,
        instanceSize: Int,
        staticFields: Map<Identifier, Any>,
        instanceFieldTypes: List<Pair<Identifier, BasicType>>
    ) {
        if (classes.containsKey(classId)) {
            throw RuntimeException("Duplicate class '$classId'")
        }
        classes[classId] = ClassInternal(classId, superclassId, instanceSize, staticFields, instanceFieldTypes)
    }

    fun addInstance(id: Identifier, instance: Any) {
        if (instances.containsKey(id)) {
            throw RuntimeException("Duplicate instance '$id' with value '$instance'")
        }
        instances[id] = instance
    }

    fun getInstanceFieldTypes(classId: Identifier): List<Pair<Identifier, BasicType>> {
        val types = mutableListOf<Pair<Identifier, BasicType>>()
        var cls = classes[classId]
        while (cls != null) {
            types.addAll(cls.instanceFieldTypes)
            cls = classes[cls.superclassId]
        }
        return types
    }

    fun getString(id: Identifier): String {
        return strings.getValue(id)
    }
    fun getClassName(classId: Identifier): String {
        return getString(classNames.getValue(classId)).replace("/", ".")
    }

    fun getAllStrings() = strings.toMap()
    fun getAllClasses() = classes.toMap()
    fun getAllInstances() = instances.toMap()
}