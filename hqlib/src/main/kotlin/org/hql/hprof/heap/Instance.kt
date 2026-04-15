package org.hql.hprof.heap

import org.hql.hprof.reader.BasicValue

sealed class Instance {
    class NullI : Instance()

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
        override fun toString() = "<class object ${cls.getName()}>"
    }

    class ObjectI : Instance() {
        private var cls: Class? = null
        private val fields = mutableMapOf<String, Instance>()


        override fun toString() = "<instance of class ${getClass().getName()}>"
        fun getClass() = cls!!
        fun getFields() = fields.toMap()
        operator fun get(name: String) = fields.getValue(name)

        /* functions intended for use only during construction */
        internal fun setClass(cls: Class) {
            this.cls = cls
        }
        internal fun addField(name: String, value: Instance) {
            this.fields[name] = value
        }
    }

    companion object {
        fun fromPrimitive(v: BasicValue.Primitive) = when (v) {
            is BasicValue.IntV -> IntI(v.v)
            is BasicValue.LongV -> LongI(v.v)
            is BasicValue.FloatV -> FloatI(v.v)
            is BasicValue.DoubleV -> DoubleI(v.v)
            is BasicValue.BooleanV -> BooleanI(v.v)
            is BasicValue.ByteV -> ByteI(v.v)
            is BasicValue.ShortV -> ShortI(v.v)
            is BasicValue.CharV -> CharI(v.v)
        }
    }
}