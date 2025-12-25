package org.hql.hprof.heap

import org.hql.hprof.reader.HprofReader
import org.hql.hprof.reader.InstanceInternal
import java.io.File
import kotlin.collections.forEach

class Heap(path: String) {
    val format: String
    val timestamp: Long

    private val classes: Map<String, Class>
    private val instances: Map<Identifier, Instance>

    fun dfs(
        objects: MutableMap<Identifier, Any>,
        instances: MutableMap<Identifier, Any>,
        id: Identifier
    ): Any? {
        //println(id)
        if (id.isNull())
            return null
        if (objects.contains(id))
            return objects[id]!!
        val x = instances[id]!!
        if (x is InstanceInternal) {
            val inst = Instance(id)
            objects[id] = inst
            return inst
        }
        if (x is List<*>) {
            if (x.isNotEmpty() && x[0] is Identifier) {
                val list = x.map {
                    dfs(objects, instances, it as Identifier)
                }
                objects[id] = list
                return list
            } else {
                objects[id] = x
                return x
            }
        }
        throw RuntimeException("shouldn't be here")
    }

    fun fillInstances(objects: MutableMap<Identifier, Any>, instances: MutableMap<Identifier, Any>) {
        instances.forEach { (id, _) ->
            dfs(objects, instances, id)
        }
    }

    init {
        val stream = File(path).inputStream()
        val reader = HprofReader(stream)
        stream.close()

        format = reader.format
        timestamp = reader.timestamp

        val classes = mutableMapOf<Identifier, Class>()
        val objects = mutableMapOf<Identifier, Any>()

        reader.classes.forEach { (id, cls) ->
            val classObj = Class(id)
            classes[id] = classObj
            objects[id] = classObj
        }
        fillInstances(objects, reader.instances)

        reader.classes.forEach { (id, cls) ->
            val classObj = classes[id]!!
            val className = reader.strings[reader.classNames[id]!!]!!.replace("/", ".")
            classObj.setName(className)
            classObj.setSuperclass(
                if (cls.superclassId.isNull()) null else classes[cls.superclassId]!!
            )
            cls.staticFields.forEach { (fieldId, value) ->
                val fieldName = reader.strings[fieldId]!!
                if (value is Identifier) {
                    if (value.isNull())
                        classObj.addStaticField(fieldName, null)
                    else
                        classObj.addStaticField(fieldName, objects[value]!!)
                } else {
                    classObj.addStaticField(fieldName, value)
                }
            }
            cls.instanceFieldTypes.forEach { (fieldId, value) ->
                val fieldName = reader.strings[fieldId]!!
                classObj.addInstanceFieldType(fieldName, value)
            }
        }
        reader.instances.forEach { (id, inst) ->
            if (inst is InstanceInternal) {
                val instObj = objects[id] as Instance
                val classObj = classes[inst.classId]!!
                instObj.setClass(classObj)
                classObj.addInstance(instObj)
                inst.fieldValues.forEach { (fieldId, value) ->
                    val fieldName = reader.strings[fieldId]!!
                    if (value is Identifier) {
                        if (value.isNull())
                            instObj.addField(fieldName, null)
                        else
                            instObj.addField(fieldName, objects[value]!!)
                    } else {
                        instObj.addField(fieldName, value)
                    }
                }
            }
        }

        this.classes = classes.values.associateBy { it.getName() }
        this.instances = objects.mapValues { (k, v) -> v as? Instance }
            .filterValues { it != null }
            as Map<Identifier, Instance>
    }

    fun getClasses() = classes.values.toList()
    fun getClassByName(name: String) = classes[name]
        ?: throw RuntimeException("no class with name $name")
    fun getInstances() = instances.values.toList()
    fun getInstancesById(id: Identifier) = instances[id]
        ?: throw RuntimeException("no instance with id $id")
}