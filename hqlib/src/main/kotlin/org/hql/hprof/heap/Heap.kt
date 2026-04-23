package org.hql.hprof.heap

import org.hql.ClassNotFoundException
import org.hql.hprof.heap.Instance.BooleanI
import org.hql.hprof.heap.Instance.ByteI
import org.hql.hprof.heap.Instance.CharI
import org.hql.hprof.heap.Instance.DoubleI
import org.hql.hprof.heap.Instance.FloatI
import org.hql.hprof.heap.Instance.IntI
import org.hql.hprof.heap.Instance.LongI
import org.hql.hprof.heap.Instance.ShortI
import org.hql.hprof.reader.BasicValue
import org.hql.hprof.reader.Hprof
import org.hql.hprof.reader.InstanceInternal
import kotlin.collections.forEach

private fun primitiveToInstance(v: BasicValue.Primitive) = when (v) {
    is BasicValue.IntV -> IntI(v.v)
    is BasicValue.LongV -> LongI(v.v)
    is BasicValue.FloatV -> FloatI(v.v)
    is BasicValue.DoubleV -> DoubleI(v.v)
    is BasicValue.BooleanV -> BooleanI(v.v)
    is BasicValue.ByteV -> ByteI(v.v)
    is BasicValue.ShortV -> ShortI(v.v)
    is BasicValue.CharV -> CharI(v.v)
}

class Heap(private val hprof: Hprof) {
    private val classes: Map<String, Class>
    private val instances: Map<Identifier, Instance>

    init {
        val classesById = mutableMapOf<Identifier, Class>()
        val objects = mutableMapOf<Identifier, Instance>()
        val instancesInternal = hprof.getAllInstances()

        fun fillInstances(id: Identifier): Instance {
            if (id.isNull())
                return Instance.NullI
            if (id in objects)
                return objects.getValue(id)
            if (id !in instancesInternal) {
                System.err.println("warning: couldn't find instance with id $id, returning null")
                return Instance.NullI
            }
            val x = instancesInternal.getValue(id)

            when (x) {
                is InstanceInternal.Object -> {
                    if (objects.containsKey(id))
                        return objects.getValue(id)

                    val classObj = classesById.getValue(x.classId)
                    if (classObj.getName() == "java.lang.String") {
                        val fieldValue = x.fieldValues.values.first() as BasicValue.Object
                        val contentsI = fillInstances(fieldValue.id) as Instance.ArrayI
                        val contents = contentsI.values.map { (it as Instance.ByteI).v }
                        val contentsAsString = contents.toByteArray().toString(Charsets.UTF_8)
                        val inst = Instance.StringI(contentsAsString)
                        objects[id] = inst
                        return inst
                    }

                    val inst = Instance.ObjectI(classObj)
                    classObj.addInstance(inst)
                    objects[id] = inst
                    x.fieldValues.forEach { (fieldId, value) ->
                        val fieldName = hprof.getString(fieldId)
                        val fieldValue = when (value) {
                            is BasicValue.Object -> {
                                if (value.id.isNull())
                                    Instance.NullI
                                else
                                    fillInstances(value.id)
                            }
                            is BasicValue.Primitive -> primitiveToInstance(value)
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
                                is BasicValue.Primitive -> primitiveToInstance(it)
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
            classesById[id] = classObj
            objects[id] = Instance.ClassI(classObj)
        }

        instancesInternal.forEach { (id, _) ->
            fillInstances(id)
        }

        hprof.getAllClasses().forEach { (id, cls) ->
            val classObj = classesById.getValue(id)
            classObj.setSuperclass(
                if (cls.superclassId.isNull()) null else classesById[cls.superclassId]!!
            )
            cls.staticFields.forEach { (fieldId, value) ->
                val fieldName = hprof.getString(fieldId)
                val fieldValue = when (value) {
                    is BasicValue.Object -> {
                        if (value.id.isNull())
                            Instance.NullI
                        else
                            objects.getValue(value.id)
                    }
                    is BasicValue.Primitive -> primitiveToInstance(value)
                }
                classObj.addStaticField(fieldName, fieldValue)
            }
            cls.instanceFieldTypes.forEach { (fieldId, value) ->
                val fieldName = hprof.getString(fieldId)
                classObj.addInstanceFieldType(fieldName, value)
            }
        }

        classes = classesById.values.associateBy { it.getName() }
        this.instances = objects
    }

    fun getClassByName(name: String) = classes.getOrElse(name) {
        throw ClassNotFoundException(name)
    }
}