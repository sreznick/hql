package org.hql.hprof.reader

import org.hql.hprof.heap.Identifier
import org.hql.hprof.heap.instances.Instance

class Hprof {
    private val _strings = hashMapOf<Identifier, String>()
    private val classNames = hashMapOf<Identifier, Identifier>()
    private val _classes = hashMapOf<Identifier, ClassInternal>()
    private val _instances = hashMapOf<Identifier, InstanceInternal>()
    private val instancesByClass = hashMapOf<Identifier, MutableList<InstanceInternal.Object>>()
    private val _threadObjectIds = mutableListOf<Identifier>()
    private val _threadSerialToObjectId = hashMapOf<Int, Identifier>()
    private val _frameRootsByThreadSerial = hashMapOf<Int, MutableList<Identifier>>()

    /**
     * Identity cache of resolved instances, keyed by object id. Scoped to this dump (not global) so
     * that ids — which are heap addresses and routinely collide across dumps — never resolve to an
     * instance belonging to a different dump.
     */
    val instanceCache: MutableMap<Identifier, Instance> = hashMapOf()

    fun addString(id: Identifier, value: String) {
        if (_strings.containsKey(id)) {
            throw RuntimeException("Duplicate string '$id' with value '$value'")
        }
        _strings[id] = value
    }

    fun addClassName(classId: Identifier, nameId: Identifier) {
        val existing = classNames[classId]
        if (existing != null && existing != nameId) {
            throw RuntimeException("Conflicting className '$classId': '$existing' vs '$nameId'")
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

    /**
     * Records a live thread, identified by the [ROOT THREAD OBJECT] subtag, by its Thread instance id
     * and the dump-local thread serial number (used to attribute stack-frame roots to it)
     */
    fun addThreadRoot(id: Identifier, serial: Int) {
        _threadObjectIds.add(id)
        _threadSerialToObjectId[serial] = id
    }

    /**
     * Records a stack-frame-local object root ([ROOT JAVA FRAME] / [ROOT JNI LOCAL]) owned by the
     * thread with the given serial number. These tie running objects (e.g. a coroutine's executing
     * continuation) to the thread currently executing them
     */
    fun addFrameRoot(threadSerial: Int, objectId: Identifier) {
        if (objectId.isNull()) return
        _frameRootsByThreadSerial.getOrPut(threadSerial) { mutableListOf() }.add(objectId)
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

    // ids of the Thread instances that were roots of type [ROOT THREAD OBJECT] — one per live thread
    val threadObjectIds: List<Identifier>
        get() = _threadObjectIds

    // Stack-frame-local object roots grouped by the object id of the live thread that owns them
    val frameRootsByThreadObjectId: Map<Identifier, List<Identifier>>
        get() = _frameRootsByThreadSerial.entries.mapNotNull { (serial, ids) ->
            _threadSerialToObjectId[serial]?.let { threadId -> threadId to ids.toList() }
        }.toMap()
}