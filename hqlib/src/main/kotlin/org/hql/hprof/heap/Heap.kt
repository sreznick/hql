package org.hql.hprof.heap

import org.hql.hprof.reader.BasicValue
import org.hql.hprof.reader.Hprof
import org.hql.hprof.reader.InstanceInternal
import kotlin.collections.forEach
import kotlin.collections.get

class Heap(private val hprof: Hprof) {
    private val classes: Map<String, Class>
    private val instances: Map<Identifier, Instance>

    init {
        val classes = mutableMapOf<Identifier, Class>()
        val objects = mutableMapOf<Identifier, Instance>()
        val instances = hprof.getAllInstances()

        fun fillInstances(id: Identifier): Instance {
            if (id.isNull())
                return Instance.NullI()
            if (objects.contains(id))
                return objects[id]!!
            if (id !in instances) {
                println("warning: couldn't find instance with id $id, returning null")
                return Instance.NullI()
            }
            val x = instances[id]!!

            when (x) {
                is InstanceInternal.Object -> {
                    if (objects.containsKey(id))
                        return objects[id]!!

                    val classObj = classes[x.classId]!!
                    if (classObj.getName() == "java.lang.String") {
                        val fieldValue = x.fieldValues.values.first() as BasicValue.Object
                        val contentsI = fillInstances(fieldValue.id) as Instance.ArrayI
                        val contents = contentsI.values.map { (it as Instance.ByteI).v }
                        val contentsAsString = contents.toByteArray().toString(Charsets.UTF_8)
                        val inst = Instance.StringI(contentsAsString)
                        objects[id] = inst
                        return inst
                    }

                    val inst = Instance.ObjectI()
                    inst.setClass(classObj)
                    classObj.addInstance(inst)
                    objects[id] = inst
                    x.fieldValues.forEach { (fieldId, value) ->
                        val fieldName = hprof.getString(fieldId)
                        val fieldValue = when (value) {
                            is BasicValue.Object -> {
                                if (value.id.isNull())
                                    Instance.NullI()
                                else
                                    fillInstances(value.id)
                            }
                            is BasicValue.Primitive -> Instance.fromPrimitive(value)
                        }
                        inst.addField(fieldName, fieldValue)
                    }

                    return inst
                }
                is InstanceInternal.ObjectArray -> {
                    val list = x.ids.map { fillInstances(it) }
                    val inst = Instance.ArrayI(list)
                    objects[id] = inst
                    return inst
                }
                is InstanceInternal.PrimitiveArray -> {
                    val inst = Instance.ArrayI(
                        x.values.map {
                            when (it) {
                                is BasicValue.Primitive -> Instance.fromPrimitive(it)
                                else -> throw RuntimeException("object reference in PrimitiveArray")
                            }
                        }
                    )
                    objects[id] = inst
                    return inst
                }
            }
        }

        hprof.getAllClasses().forEach { (id, cls) ->
            val classObj = Class(id)
            val className = hprof.getClassName(id)
            classObj.setName(className)
            classes[id] = classObj
            objects[id] = Instance.ClassI(classObj)
        }

        instances.forEach { (id, _) ->
            fillInstances(id)
        }

        hprof.getAllClasses().forEach { (id, cls) ->
            val classObj = classes[id]!!
            classObj.setSuperclass(
                if (cls.superclassId.isNull()) null else classes[cls.superclassId]!!
            )
            cls.staticFields.forEach { (fieldId, value) ->
                val fieldName = hprof.getString(fieldId)
                val fieldValue = when (value) {
                    is BasicValue.Object -> {
                        if (value.id.isNull())
                            Instance.NullI()
                        else
                            objects[value.id]!!
                    }
                    is BasicValue.Primitive -> Instance.fromPrimitive(value)
                }
                classObj.addStaticField(fieldName, fieldValue)
            }
            cls.instanceFieldTypes.forEach { (fieldId, value) ->
                val fieldName = hprof.getString(fieldId)
                classObj.addInstanceFieldType(fieldName, value)
            }
        }

        this.classes = classes.values.associateBy { it.getName() }
        this.instances = objects
    }

    fun getClasses() = classes.values.toList()
    fun getClassByName(name: String) = classes[name]
        ?: throw RuntimeException("no class with name $name")
    fun getInstances() = instances.values.toList()
    fun getInstancesById(id: Identifier) = instances[id]
        ?: throw RuntimeException("no instance with id $id")
}