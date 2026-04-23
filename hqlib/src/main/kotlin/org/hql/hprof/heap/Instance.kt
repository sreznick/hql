package org.hql.hprof.heap

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
        override fun toString() = "<class object ${cls.getName()}>"
    }

    class ObjectI(val cls: Class) : Instance() {
        private val fields = mutableMapOf<String, Instance>()


        override fun toString() = "<instance of class ${cls.getName()}>"
        fun getClass(): Class = cls
        fun getFields(): Map<String, Instance> = fields.toMap()
        operator fun get(name: String): Instance = fields.getValue(name)

        /* functions intended for use only during construction */
        internal fun addField(name: String, value: Instance) {
            this.fields[name] = value
        }
    }
}