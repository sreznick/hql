package org.hql.hprof.heap.instances

import org.hql.ColumnNotFoundException
import org.hql.hprof.heap.Class
import org.hql.hprof.heap.Identifier

import org.hql.hprof.reader.BasicValue
import org.hql.hprof.reader.Hprof
import org.hql.hprof.reader.InstanceInternal

sealed class Instance {
    // Typed-value accessors. Default to null for instances that don't carry the requested type;
    // the subclasses that do override the relevant one. Lets callers read a field as a primitive
    // without matching on the concrete Instance subtype.
    open fun asString(): String? = null
    open fun asBoolean(): Boolean? = null
    open fun asInt(): Int? = null
    open fun asLong(): Long? = null

    data object NullI : Instance()

    data class BooleanI(val v: Boolean) : Instance() {
        override fun asBoolean() = v
    }

    data class CharI(val v: Char) : Instance()

    data class FloatI(val v: Float) : Instance()

    data class DoubleI(val v: Double) : Instance()

    data class ByteI(val v: Byte) : Instance() {
        override fun asInt() = v.toInt()
    }

    data class ShortI(val v: Short) : Instance() {
        override fun asInt() = v.toInt()
    }

    data class IntI(val v: Int) : Instance() {
        override fun asInt() = v
        override fun asLong() = v.toLong()
    }

    data class LongI(val v: Long) : Instance() {
        override fun asLong() = v
    }

    data class ArrayI(val values: List<Instance>) : Instance() {
        override fun toString() = values.joinToString(prefix = "[", postfix = "]")
    }

    data class StringI(val value: String) : Instance() {
        override fun asString() = value
        override fun toString() = "\"$value\""
    }

    data class ClassI(val cls: Class) : Instance() {
        override fun toString() = "<class object ${cls.name}>"
    }

    class ObjectI(private val hprof: Hprof, private val inst: InstanceInternal.Object) : Instance() {
        val id: Identifier = inst.id

        val cls: Class by lazy {
            Class(hprof, hprof.getClassById(inst.classId))
        }

        val fields: Map<String, Instance> by lazy {
            inst.fieldValues
                .mapKeys { (key, _) -> hprof.getString(key) }
                .mapValues { (_, value) -> Instance.create(hprof, value) }
        }

        override fun toString() = "<instance of class ${cls.name}>"
        operator fun get(name: String): Instance = fields[name] ?:
            throw ColumnNotFoundException(name, fields.keys.toList())
    }

    companion object {
        private fun convertObject(hprof: Hprof, inst: InstanceInternal.Object): Instance {
            val className = hprof.getClassName(inst.classId)
            return when (className) {
                "java.lang.String" -> {
                    val valueFieldId = hprof.getClassById(inst.classId).instanceFieldTypes
                        .first { (nameId, _) -> hprof.getString(nameId) == "value" }
                        .first
                    val contentsAsBasicValue = inst.fieldValues.getValue(valueFieldId) as BasicValue.Object
                    val contentsAsArray = hprof.getInstanceById(contentsAsBasicValue.id) as InstanceInternal.PrimitiveArray
                    val contents = contentsAsArray.values.map { (it as BasicValue.ByteV).v }
                    val contentsAsString = contents.toByteArray().toString(Charsets.UTF_8)
                    StringI(contentsAsString)
                }
                else -> ObjectI(hprof, inst)
            }
        }

        internal fun createObject(hprof: Hprof, id: Identifier): Instance {
            if (id.isNull()) return NullI
            return hprof.instanceCache.getOrPut(id) {
                val inst = hprof.getInstanceById(id)
                when (inst) {
                    is InstanceInternal.ObjectArray ->
                        ArrayI(inst.ids.map { createObject(hprof, it) })
                    is InstanceInternal.PrimitiveArray ->
                        ArrayI(inst.values.map { create(hprof, it) })
                    is InstanceInternal.Object ->
                        convertObject(hprof, inst)
                }
            }
        }

        internal fun create(hprof: Hprof, value: BasicValue): Instance = when (value) {
            is BasicValue.Object -> createObject(hprof, value.id)
            is BasicValue.BooleanV -> BooleanI(value.v)
            is BasicValue.ByteV -> ByteI(value.v)
            is BasicValue.CharV -> CharI(value.v)
            is BasicValue.DoubleV -> DoubleI(value.v)
            is BasicValue.FloatV -> FloatI(value.v)
            is BasicValue.IntV -> IntI(value.v)
            is BasicValue.LongV -> LongI(value.v)
            is BasicValue.ShortV -> ShortI(value.v)
        }
    }
}