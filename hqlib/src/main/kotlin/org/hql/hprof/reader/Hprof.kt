package org.hql.hprof.reader

import org.hql.hprof.heap.Identifier

class Hprof {
    private val _strings = hashMapOf<Identifier, String>()
    private val classNames = hashMapOf<Identifier, Identifier>()
    private val _classes = hashMapOf<Identifier, ClassInternal>()
    private val _instances = hashMapOf<Identifier, InstanceInternal>()
    private val instancesByClass = hashMapOf<Identifier, MutableList<InstanceInternal.Object>>()

    fun addString(id: Identifier, value: String) {
        if (_strings.containsKey(id)) {
            throw RuntimeException("Duplicate string '$id' with value '$value'")
        }
        _strings[id] = value
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
        staticFields: Map<Identifier, BasicValue>,
        instanceFieldTypes: List<Pair<Identifier, BasicType>>
    ) {
        if (_classes.containsKey(classId)) {
            throw RuntimeException("Duplicate class '$classId'")
        }
        _classes[classId] = ClassInternal(classId, superclassId, instanceSize, staticFields, instanceFieldTypes)
    }

    fun addInstance(id: Identifier, instance: InstanceInternal) {
        if (_instances.containsKey(id)) {
            throw RuntimeException("Duplicate instance '$id' with value '$instance'")
        }
        _instances[id] = instance
        if (instance is InstanceInternal.Object) {
            instancesByClass.getOrPut(instance.classId) { mutableListOf() }.add(instance)
        }
    }

    fun getInstanceFieldTypes(classId: Identifier): List<Pair<Identifier, BasicType>> {
        val types = mutableListOf<Pair<Identifier, BasicType>>()
        var cls = _classes[classId]
        while (cls != null) {
            types.addAll(cls.instanceFieldTypes)
            cls = _classes[cls.superclassId]
        }
        return types
    }

    fun getString(id: Identifier): String {
        return _strings.getValue(id)
    }
    fun getClassById(id: Identifier): ClassInternal {
        return _classes.getValue(id)
    }
    fun getClassName(classId: Identifier): String {
        return getString(classNames.getValue(classId)).replace("/", ".")
    }

    fun getInstanceById(id: Identifier): InstanceInternal {
        return _instances.getValue(id)
    }
    fun getInstancesOfClass(classId: Identifier): List<InstanceInternal.Object> {
        // if we were able to get id by class name that means the class exists
        // that means we can safely return emptyList()
        return instancesByClass.getOrElse(classId) { emptyList() }
    }

    val strings: Map<Identifier, String>
        get() = _strings
    val classes: Map<Identifier, ClassInternal>
        get() = _classes
    val instances: Map<Identifier, InstanceInternal>
        get() = _instances
}