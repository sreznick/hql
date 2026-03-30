package org.hql.hprof.heap

import org.hql.hprof.reader.BasicValue

interface Instance : Comparable<Instance> {
    // default implementations
    fun type(): String
    override fun compareTo(other: Instance): Int {
        throw RuntimeException("comparing instances of type ${type()} and ${other.type()} is not supported")
    }
    operator fun plus(other: Instance): Instance {
        throw RuntimeException("adding instances of type ${type()} and ${other.type()} is not supported")
    }
    operator fun minus(other: Instance): Instance {
        throw RuntimeException("subtracting instances of type ${type()} and ${other.type()} is not supported")
    }
    operator fun times(other: Instance): Instance {
        throw RuntimeException("multiplying instances of type ${type()} and ${other.type()} is not supported")
    }
    operator fun div(other: Instance): Instance {
        throw RuntimeException("dividing instances of type ${type()} and ${other.type()} is not supported")
    }

    class NullI : Instance {
        override fun type(): String = "null"
        override fun toString() = "null"
        override fun compareTo(other: Instance): Int {
            if (other is NullI) return 0
            return super.compareTo(other)
        }
    }

    data class BooleanI(val v: Boolean) : Instance {
        override fun type(): String = "boolean"
        override fun toString() = "$v"
        override fun compareTo(other: Instance): Int {
            if (other is BooleanI) return v.compareTo(other.v)
            return super.compareTo(other)
        }
    }

    data class CharI(val v: Char) : Instance {
        override fun type(): String = "char"
        override fun toString() = "$v"
        override fun compareTo(other: Instance): Int {
            if (other is CharI) return v.compareTo(other.v)
            return super.compareTo(other)
        }
        override fun plus(other: Instance): Instance {
            if (other is StringI) return StringI(v + other.value)
            if (other is IntI) return CharI(v + other.v)
            return super.plus(other)
        }
        override fun minus(other: Instance): Instance {
            if (other is CharI) return IntI(v - other.v)
            if (other is IntI) return CharI(v - other.v)
            return super.minus(other)
        }
    }

    data class FloatI(val v: Float) : Instance {
        override fun type(): String = "float"
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
        override fun plus(other: Instance): Instance {
            if (other is ByteI) return FloatI(v + other.v)
            if (other is ShortI) return FloatI(v + other.v)
            if (other is IntI) return FloatI(v + other.v)
            if (other is LongI) return FloatI(v + other.v)
            if (other is FloatI) return FloatI(v + other.v)
            if (other is DoubleI) return DoubleI(v + other.v)
            return super.plus(other)
        }
        override fun minus(other: Instance): Instance {
            if (other is ByteI) return FloatI(v - other.v)
            if (other is ShortI) return FloatI(v - other.v)
            if (other is IntI) return FloatI(v - other.v)
            if (other is LongI) return FloatI(v - other.v)
            if (other is FloatI) return FloatI(v - other.v)
            if (other is DoubleI) return DoubleI(v - other.v)
            return super.plus(other)
        }
        override fun times(other: Instance): Instance {
            if (other is ByteI) return FloatI(v * other.v)
            if (other is ShortI) return FloatI(v * other.v)
            if (other is IntI) return FloatI(v * other.v)
            if (other is LongI) return FloatI(v * other.v)
            if (other is FloatI) return FloatI(v * other.v)
            if (other is DoubleI) return DoubleI(v * other.v)
            return super.plus(other)
        }
        override fun div(other: Instance): Instance {
            if (other is ByteI) return FloatI(v / other.v)
            if (other is ShortI) return FloatI(v / other.v)
            if (other is IntI) return FloatI(v / other.v)
            if (other is LongI) return FloatI(v / other.v)
            if (other is FloatI) return FloatI(v / other.v)
            if (other is DoubleI) return DoubleI(v / other.v)
            return super.plus(other)
        }
    }

    data class DoubleI(val v: Double) : Instance {
        override fun type(): String = "double"
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
        override fun plus(other: Instance): Instance {
            if (other is ByteI) return DoubleI(v + other.v)
            if (other is ShortI) return DoubleI(v + other.v)
            if (other is IntI) return DoubleI(v + other.v)
            if (other is LongI) return DoubleI(v + other.v)
            if (other is FloatI) return DoubleI(v + other.v)
            if (other is DoubleI) return DoubleI(v + other.v)
            return super.plus(other)
        }
        override fun minus(other: Instance): Instance {
            if (other is ByteI) return DoubleI(v - other.v)
            if (other is ShortI) return DoubleI(v - other.v)
            if (other is IntI) return DoubleI(v - other.v)
            if (other is LongI) return DoubleI(v - other.v)
            if (other is FloatI) return DoubleI(v - other.v)
            if (other is DoubleI) return DoubleI(v - other.v)
            return super.plus(other)
        }
        override fun times(other: Instance): Instance {
            if (other is ByteI) return DoubleI(v * other.v)
            if (other is ShortI) return DoubleI(v * other.v)
            if (other is IntI) return DoubleI(v * other.v)
            if (other is LongI) return DoubleI(v * other.v)
            if (other is FloatI) return DoubleI(v * other.v)
            if (other is DoubleI) return DoubleI(v * other.v)
            return super.plus(other)
        }
        override fun div(other: Instance): Instance {
            if (other is ByteI) return DoubleI(v / other.v)
            if (other is ShortI) return DoubleI(v / other.v)
            if (other is IntI) return DoubleI(v / other.v)
            if (other is LongI) return DoubleI(v / other.v)
            if (other is FloatI) return DoubleI(v / other.v)
            if (other is DoubleI) return DoubleI(v / other.v)
            return super.plus(other)
        }
    }

    data class ByteI(val v: Byte) : Instance {
        override fun type(): String = "byte"
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
        override fun plus(other: Instance): Instance {
            if (other is ByteI) return IntI(v + other.v)
            if (other is ShortI) return IntI(v + other.v)
            if (other is IntI) return IntI(v + other.v)
            if (other is LongI) return LongI(v + other.v)
            if (other is FloatI) return FloatI(v + other.v)
            if (other is DoubleI) return DoubleI(v + other.v)
            return super.plus(other)
        }
        override fun minus(other: Instance): Instance {
            if (other is ByteI) return IntI(v - other.v)
            if (other is ShortI) return IntI(v - other.v)
            if (other is IntI) return IntI(v - other.v)
            if (other is LongI) return LongI(v - other.v)
            if (other is FloatI) return FloatI(v - other.v)
            if (other is DoubleI) return DoubleI(v - other.v)
            return super.plus(other)
        }
        override fun times(other: Instance): Instance {
            if (other is ByteI) return IntI(v * other.v)
            if (other is ShortI) return IntI(v * other.v)
            if (other is IntI) return IntI(v * other.v)
            if (other is LongI) return LongI(v * other.v)
            if (other is FloatI) return FloatI(v * other.v)
            if (other is DoubleI) return DoubleI(v * other.v)
            return super.plus(other)
        }
        override fun div(other: Instance): Instance {
            if (other is ByteI) return IntI(v / other.v)
            if (other is ShortI) return IntI(v / other.v)
            if (other is IntI) return IntI(v / other.v)
            if (other is LongI) return LongI(v / other.v)
            if (other is FloatI) return FloatI(v / other.v)
            if (other is DoubleI) return DoubleI(v / other.v)
            return super.div(other)
        }
    }

    data class ShortI(val v: Short) : Instance {
        override fun type(): String = "short"
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
        override fun plus(other: Instance): Instance {
            if (other is ByteI) return IntI(v + other.v)
            if (other is ShortI) return IntI(v + other.v)
            if (other is IntI) return IntI(v + other.v)
            if (other is LongI) return LongI(v + other.v)
            if (other is FloatI) return FloatI(v + other.v)
            if (other is DoubleI) return DoubleI(v + other.v)
            return super.plus(other)
        }
        override fun minus(other: Instance): Instance {
            if (other is ByteI) return IntI(v - other.v)
            if (other is ShortI) return IntI(v - other.v)
            if (other is IntI) return IntI(v - other.v)
            if (other is LongI) return LongI(v - other.v)
            if (other is FloatI) return FloatI(v - other.v)
            if (other is DoubleI) return DoubleI(v - other.v)
            return super.plus(other)
        }
        override fun times(other: Instance): Instance {
            if (other is ByteI) return IntI(v * other.v)
            if (other is ShortI) return IntI(v * other.v)
            if (other is IntI) return IntI(v * other.v)
            if (other is LongI) return LongI(v * other.v)
            if (other is FloatI) return FloatI(v * other.v)
            if (other is DoubleI) return DoubleI(v * other.v)
            return super.plus(other)
        }
        override fun div(other: Instance): Instance {
            if (other is ByteI) return IntI(v / other.v)
            if (other is ShortI) return IntI(v / other.v)
            if (other is IntI) return IntI(v / other.v)
            if (other is LongI) return LongI(v / other.v)
            if (other is FloatI) return FloatI(v / other.v)
            if (other is DoubleI) return DoubleI(v / other.v)
            return super.div(other)
        }
    }

    data class IntI(val v: Int) : Instance {
        override fun type(): String = "int"
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
        override fun plus(other: Instance): Instance {
            if (other is ByteI) return IntI(v + other.v)
            if (other is ShortI) return IntI(v + other.v)
            if (other is IntI) return IntI(v + other.v)
            if (other is LongI) return LongI(v + other.v)
            if (other is FloatI) return FloatI(v + other.v)
            if (other is DoubleI) return DoubleI(v + other.v)
            return super.plus(other)
        }
        override fun minus(other: Instance): Instance {
            if (other is ByteI) return IntI(v - other.v)
            if (other is ShortI) return IntI(v - other.v)
            if (other is IntI) return IntI(v - other.v)
            if (other is LongI) return LongI(v - other.v)
            if (other is FloatI) return FloatI(v - other.v)
            if (other is DoubleI) return DoubleI(v - other.v)
            return super.plus(other)
        }
        override fun times(other: Instance): Instance {
            if (other is ByteI) return IntI(v * other.v)
            if (other is ShortI) return IntI(v * other.v)
            if (other is IntI) return IntI(v * other.v)
            if (other is LongI) return LongI(v * other.v)
            if (other is FloatI) return FloatI(v * other.v)
            if (other is DoubleI) return DoubleI(v * other.v)
            return super.plus(other)
        }
        override fun div(other: Instance): Instance {
            if (other is ByteI) return IntI(v / other.v)
            if (other is ShortI) return IntI(v / other.v)
            if (other is IntI) return IntI(v / other.v)
            if (other is LongI) return LongI(v / other.v)
            if (other is FloatI) return FloatI(v / other.v)
            if (other is DoubleI) return DoubleI(v / other.v)
            return super.div(other)
        }
    }

    data class LongI(val v: Long) : Instance {
        override fun type(): String = "long"
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
        override fun plus(other: Instance): Instance {
            if (other is ByteI) return LongI(v + other.v)
            if (other is ShortI) return LongI(v + other.v)
            if (other is IntI) return LongI(v + other.v)
            if (other is LongI) return LongI(v + other.v)
            if (other is FloatI) return FloatI(v + other.v)
            if (other is DoubleI) return DoubleI(v + other.v)
            return super.plus(other)
        }
        override fun minus(other: Instance): Instance {
            if (other is ByteI) return LongI(v - other.v)
            if (other is ShortI) return LongI(v - other.v)
            if (other is IntI) return LongI(v - other.v)
            if (other is LongI) return LongI(v - other.v)
            if (other is FloatI) return FloatI(v - other.v)
            if (other is DoubleI) return DoubleI(v - other.v)
            return super.plus(other)
        }
        override fun times(other: Instance): Instance {
            if (other is ByteI) return LongI(v * other.v)
            if (other is ShortI) return LongI(v * other.v)
            if (other is IntI) return LongI(v * other.v)
            if (other is LongI) return LongI(v * other.v)
            if (other is FloatI) return FloatI(v * other.v)
            if (other is DoubleI) return DoubleI(v * other.v)
            return super.plus(other)
        }
        override fun div(other: Instance): Instance {
            if (other is ByteI) return LongI(v / other.v)
            if (other is ShortI) return LongI(v / other.v)
            if (other is IntI) return LongI(v / other.v)
            if (other is LongI) return LongI(v / other.v)
            if (other is FloatI) return FloatI(v / other.v)
            if (other is DoubleI) return DoubleI(v / other.v)
            return super.div(other)
        }
    }

    data class ArrayI(val values: List<Instance>) : Instance {
        override fun type(): String = if (values.isEmpty()) "[]" else "[${values.first().type()}]"
        override fun toString() = values.joinToString(prefix = "[", postfix = "]")
    }

    data class StringI(val value: String) : Instance {
        override fun type(): String = "string"
        override fun toString() = "\"$value\""
        override fun compareTo(other: Instance): Int {
            if (other is StringI) return value.compareTo(other.value)
            return super.compareTo(other)
        }
        override fun plus(other: Instance): Instance {
            if (other is CharI) return StringI(value + other.v)
            if (other is StringI) return StringI(value + other.value)
            return super.plus(other)
        }
        override fun times(other: Instance): Instance {
            if (other is ByteI) return StringI(value.repeat(other.v.toInt()))
            if (other is ShortI) return StringI(value.repeat(other.v.toInt()))
            if (other is IntI) return StringI(value.repeat(other.v))
            if (other is LongI) return StringI(value.repeat(other.v.toInt()))
            return super.times(other)
        }
    }

    data class ClassI(val cls: Class) : Instance {
        override fun type(): String = "class"
        override fun toString() = "<class object ${cls.getName()}>"
        override fun compareTo(other: Instance): Int {
            if (other is ClassI) return cls.id.compareTo(other.cls.id)
            return super.compareTo(other)
        }
    }

    class ObjectI : Instance {
        private var cls: Class? = null
        private val fields = mutableMapOf<String, Instance>()

        /* functions intended for use only during construction */
        internal fun setClass(cls: Class) {
            this.cls = cls
        }
        internal fun addField(name: String, value: Instance) {
            this.fields[name] = value
        }

        override fun toString() = "<instance of class ${type()}>"

        fun getClass() = cls!!
        override fun type() = getClass().getName()
        fun getFields() = fields.toMap()
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