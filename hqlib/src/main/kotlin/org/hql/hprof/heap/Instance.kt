package org.hql.hprof.heap

import org.hql.hprof.reader.BasicValue

sealed class Instance : Comparable<Instance> {
    // default implementation
    override fun compareTo(other: Instance): Int {
        throw RuntimeException("comparing instances of type ${this::class.simpleName} and ${other::class.simpleName} is not supported")
    }

    class NullI : Instance() {
        override fun toString() = "null"
        override fun compareTo(other: Instance): Int {
            if (other is NullI) return 0
            return super.compareTo(other)
        }
    }

    data class BooleanI(val v: Boolean) : Instance(), Comparable<Instance> {
        override fun toString() = "$v"
        override fun compareTo(other: Instance): Int {
            if (other is BooleanI) return v.compareTo(other.v)
            return super.compareTo(other)
        }
    }

    data class CharI(val v: Char) : Instance() {
        override fun toString() = "$v"
        override fun compareTo(other: Instance): Int {
            if (other is CharI) return v.compareTo(other.v)
            return super.compareTo(other)
        }
    }

    data class FloatI(val v: Float) : Instance() {
        override fun toString() = "$v"
        override fun compareTo(other: Instance): Int {
            if (other is ByteI) return v.compareTo(other.v)
            if (other is ShortI) return v.compareTo(other.v)
            if (other is IntI) return v.compareTo(other.v)
            if (other is LongI) return v.compareTo(other.v)
            if (other is FloatI) return v.compareTo(other.v)
            if (other is DoubleI) return v.compareTo(other.v)
            return super.compareTo(other)
        }
    }

    data class DoubleI(val v: Double) : Instance() {
        override fun toString() = "$v"
        override fun compareTo(other: Instance): Int {
            if (other is ByteI) return v.compareTo(other.v)
            if (other is ShortI) return v.compareTo(other.v)
            if (other is IntI) return v.compareTo(other.v)
            if (other is LongI) return v.compareTo(other.v)
            if (other is FloatI) return v.compareTo(other.v)
            if (other is DoubleI) return v.compareTo(other.v)
            return super.compareTo(other)
        }
    }

    data class ByteI(val v: Byte) : Instance() {
        override fun toString() = "$v"
        override fun compareTo(other: Instance): Int {
            if (other is ByteI) return v.compareTo(other.v)
            if (other is ShortI) return v.compareTo(other.v)
            if (other is IntI) return v.compareTo(other.v)
            if (other is LongI) return v.compareTo(other.v)
            if (other is FloatI) return v.compareTo(other.v)
            if (other is DoubleI) return v.compareTo(other.v)
            return super.compareTo(other)
        }
    }

    data class ShortI(val v: Short) : Instance() {
        override fun toString() = "$v"
        override fun compareTo(other: Instance): Int {
            if (other is ByteI) return v.compareTo(other.v)
            if (other is ShortI) return v.compareTo(other.v)
            if (other is IntI) return v.compareTo(other.v)
            if (other is LongI) return v.compareTo(other.v)
            if (other is FloatI) return v.compareTo(other.v)
            if (other is DoubleI) return v.compareTo(other.v)
            return super.compareTo(other)
        }
    }

    data class IntI(val v: Int) : Instance() {
        override fun toString() = "$v"
        override fun compareTo(other: Instance): Int {
            if (other is ByteI) return v.compareTo(other.v)
            if (other is ShortI) return v.compareTo(other.v)
            if (other is IntI) return v.compareTo(other.v)
            if (other is LongI) return v.compareTo(other.v)
            if (other is FloatI) return v.compareTo(other.v)
            if (other is DoubleI) return v.compareTo(other.v)
            return super.compareTo(other)
        }
    }

    data class LongI(val v: Long) : Instance() {
        override fun toString() = "$v"
        override fun compareTo(other: Instance): Int {
            if (other is ByteI) return v.compareTo(other.v)
            if (other is ShortI) return v.compareTo(other.v)
            if (other is IntI) return v.compareTo(other.v)
            if (other is LongI) return v.compareTo(other.v)
            if (other is FloatI) return v.compareTo(other.v)
            if (other is DoubleI) return v.compareTo(other.v)
            return super.compareTo(other)
        }
    }

    data class ArrayI(val values: List<Instance?>) : Instance() {
        override fun toString() = values.joinToString(prefix = "[", postfix = "]")
    }
    data class StringI(val value: String) : Instance() {
        override fun toString() = "\"$value\""
        override fun compareTo(other: Instance): Int {
            if (other is StringI) return value.compareTo(other.value)
            return super.compareTo(other)
        }
    }

    data class ClassI(val cls: Class) : Instance() {
        override fun toString() = "<class object ${cls.getName()}>"
        override fun compareTo(other: Instance): Int {
            if (other is ClassI) return cls.id.compareTo(other.cls.id)
            return super.compareTo(other)
        }
    }

    class ObjectI : Instance() {
        private var cls: Class? = null
        private val fields = mutableMapOf<String, Instance>()

        /* functions intended for use only during construction */
        internal fun setClass(cls: Class) {
            this.cls = cls
        }
        internal fun addField(name: String, value: Instance) {
            this.fields[name] = value
        }

        override fun toString() = "<instance of class ${getType()}>"

        fun getClass() = cls!!
        fun getType() = getClass().getName()
        operator fun get(name: String) = fields.getValue(name)
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