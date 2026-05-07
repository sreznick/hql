package org.hql.hprof.heap

import org.hql.hprof.reader.BasicValue
import org.hql.hprof.reader.Hprof
import org.hql.hprof.reader.InstanceInternal

sealed class Instance {
    data object NullI : Instance()

    data class BooleanI(val v: Boolean) : Instance()

    data class CharI(val v: Char) : Instance()

    data class FloatI(val v: Float) : Instance()

    data class DoubleI(val v: Double) : Instance()

    data class ByteI(val v: Byte) : Instance()

    data class ShortI(val v: Short) : Instance()

    data class IntI(val v: Int) : Instance()

    data class LongI(val v: Long) : Instance()

    data class ArrayI(val values: List<Instance>) : Instance() {
        override fun toString() = values.joinToString(prefix = "[", postfix = "]")
    }

    data class StringI(val value: String) : Instance() {
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
        operator fun get(name: String): Instance = fields.getValue(name)
    }

    companion object {
        private val cache = mutableMapOf<Identifier, Instance>()
        private fun convertObject(hprof: Hprof, inst: InstanceInternal.Object): Instance {
            val className = hprof.getClassName(inst.classId)
            return when (className) {
                "java.lang.String" -> {
                    val contentsAsBasicValue = inst.fieldValues.values.first() as BasicValue.Object
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
            return cache.getOrPut(id) {
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