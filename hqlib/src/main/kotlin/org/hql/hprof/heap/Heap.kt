package org.hql.hprof.heap

import org.hql.ClassNotFoundException
import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.reader.Hprof

class Heap(private val hprof: Hprof) {
    private val classes = hprof.classes.map { (id, value) ->
        val cls = Class(hprof, value)
        cls.name to cls
    }.toMap()

    fun getClassByName(name: String) = classes.getOrElse(name) {
        throw ClassNotFoundException(name)
    }

    fun findClassByName(name: String): Class? = classes[name]

    // ids of the Thread instances flagged as live threads (one per [ROOT THREAD OBJECT] root)
    val threadObjectIds: List<Identifier>
        get() = hprof.threadObjectIds

    // stack-frame-local object roots grouped by the object id of the live thread that owns them
    val frameRootsByThreadObjectId: Map<Identifier, List<Identifier>>
        get() = hprof.frameRootsByThreadObjectId

    // resolves id to its object instance, or null if the id is null or absent from the dump
    fun getObjectById(id: Identifier): Instance.ObjectI? {
        if (id.isNull() || !hprof.instances.containsKey(id)) return null
        return Instance.createObject(hprof, id) as? Instance.ObjectI
    }
}