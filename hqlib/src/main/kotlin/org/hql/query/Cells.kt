package org.hql.query

import org.hql.hprof.heap.Class
import org.hql.hprof.heap.Instance
import kotlin.RuntimeException

interface Cell : Comparable<Cell> {
    fun type(): String
    override fun compareTo(other: Cell): Int {
        throw RuntimeException("comparing instances of type ${type()} and ${other.type()} is not supported")
    }
    operator fun plus(other: Cell): Cell {
        throw RuntimeException("adding instances of type ${type()} and ${other.type()} is not supported")
    }
    operator fun minus(other: Cell): Cell {
        throw RuntimeException("subtracting instances of type ${type()} and ${other.type()} is not supported")
    }
    operator fun times(other: Cell): Cell {
        throw RuntimeException("multiplying instances of type ${type()} and ${other.type()} is not supported")
    }
    operator fun div(other: Cell): Cell {
        throw RuntimeException("dividing instances of type ${type()} and ${other.type()} is not supported")
    }

    fun access(field: String): Cell {
        throw RuntimeException("accessing a field $field of type ${type()} is not supported")
    }

    companion object {
        fun fromInstance(instance: Instance): Cell =
            when (instance) {
                is Instance.ArrayI -> ArrayCell(instance)
                is Instance.BooleanI -> BooleanCell(instance.v)
                is Instance.ByteI -> IntCell(instance.v.toLong())
                is Instance.CharI -> CharCell(instance.v)
                is Instance.ClassI -> ClassCell(instance.cls)
                is Instance.DoubleI -> FloatCell(instance.v)
                is Instance.FloatI -> FloatCell(instance.v.toDouble())
                is Instance.IntI -> IntCell(instance.v.toLong())
                is Instance.LongI -> IntCell(instance.v)
                is Instance.NullI -> NullCell()
                is Instance.ObjectI -> ObjectCell(instance)
                is Instance.ShortI -> IntCell(instance.v.toLong())
                is Instance.StringI -> StringCell(instance.value)
            }
    }
}

class NullCell : Cell {
    override fun type(): String = "null"
    override fun toString() = "null"
    override fun compareTo(other: Cell): Int {
        if (other is NullCell) return 0
        return super.compareTo(other)
    }
}

class BooleanCell(val v: Boolean) : Cell {
    override fun type(): String = "boolean"
    override fun toString() = "$v"
    override fun compareTo(other: Cell): Int {
        if (other is BooleanCell) return v.compareTo(other.v)
        return super.compareTo(other)
    }
}

data class CharCell(val v: Char) : Cell {
    override fun type(): String = "char"
    override fun toString() = "$v"
    override fun compareTo(other: Cell): Int {
        if (other is CharCell) return v.compareTo(other.v)
        return super.compareTo(other)
    }
    override fun plus(other: Cell): Cell {
        if (other is StringCell) return StringCell(v + other.value)
        if (other is IntCell) return CharCell(v + other.v.toInt())
        return super.plus(other)
    }
    override fun minus(other: Cell): Cell {
        if (other is CharCell) return IntCell((v - other.v).toLong())
        if (other is IntCell) return CharCell(v - other.v.toInt())
        return super.minus(other)
    }
}

data class FloatCell(val v: Double) : Cell {
    override fun type(): String = "float"
    override fun toString() = "$v"
    override fun compareTo(other: Cell): Int {
        if (other is IntCell) return v.compareTo(other.v)
        if (other is FloatCell) return v.compareTo(other.v)
        return super.compareTo(other)
    }
    override fun plus(other: Cell): Cell {
        if (other is IntCell) return FloatCell(v + other.v)
        if (other is FloatCell) return FloatCell(v + other.v)
        return super.plus(other)
    }
    override fun minus(other: Cell): Cell {
        if (other is IntCell) return FloatCell(v - other.v)
        if (other is FloatCell) return FloatCell(v - other.v)
        return super.plus(other)
    }
    override fun times(other: Cell): Cell {
        if (other is IntCell) return FloatCell(v * other.v)
        if (other is FloatCell) return FloatCell(v * other.v)
        return super.plus(other)
    }
    override fun div(other: Cell): Cell {
        if (other is IntCell) return FloatCell(v / other.v)
        if (other is FloatCell) return FloatCell(v / other.v)
        return super.plus(other)
    }
}

data class IntCell(val v: Long) : Cell {
    override fun type(): String = "int"
    override fun toString() = "$v"
    override fun compareTo(other: Cell): Int {
        if (other is IntCell) return v.compareTo(other.v)
        if (other is FloatCell) return v.compareTo(other.v)
        return super.compareTo(other)
    }
    override fun plus(other: Cell): Cell {
        if (other is IntCell) return IntCell(v + other.v)
        if (other is FloatCell) return FloatCell(v + other.v)
        return super.plus(other)
    }
    override fun minus(other: Cell): Cell {
        if (other is IntCell) return IntCell(v - other.v)
        if (other is FloatCell) return FloatCell(v - other.v)
        return super.plus(other)
    }
    override fun times(other: Cell): Cell {
        if (other is IntCell) return IntCell(v * other.v)
        if (other is FloatCell) return FloatCell(v * other.v)
        return super.plus(other)
    }
    override fun div(other: Cell): Cell {
        if (other is IntCell) return IntCell(v / other.v)
        if (other is FloatCell) return FloatCell(v / other.v)
        return super.div(other)
    }
}

data class ArrayCell(val instance: Instance.ArrayI) : Cell {
    operator fun get(i: Int) = Cell.fromInstance(instance.values[i])
    override fun type(): String = if (instance.values.isEmpty()) "[]" else "[${this[0].type()}]"
    override fun toString() = instance.values.indices
        .map { i -> this[i] }
        .joinToString(prefix = "[", postfix = "]")
}

data class StringCell(val value: String) : Cell {
    override fun type(): String = "string"
    override fun toString() = "\"$value\""
    override fun compareTo(other: Cell): Int {
        if (other is StringCell) return value.compareTo(other.value)
        return super.compareTo(other)
    }
    override fun plus(other: Cell): Cell {
        if (other is CharCell) return StringCell(value + other.v)
        if (other is StringCell) return StringCell(value + other.value)
        return super.plus(other)
    }
    override fun times(other: Cell): Cell {
        if (other is IntCell) return StringCell(value.repeat(other.v.toInt()))
        return super.times(other)
    }
}

data class ClassCell(val cls: Class) : Cell {
    override fun type(): String = "class"
    override fun toString() = "<class object ${cls.getName()}>"
    override fun compareTo(other: Cell): Int {
        if (other is ClassCell) return cls.id.compareTo(other.cls.id)
        return super.compareTo(other)
    }
}

class ObjectCell(val obj: Instance.ObjectI) : Cell {
    override fun type() = obj.getClass().getName()
    override fun toString() = "<instance of class ${type()}>"
    override fun access(field: String): Cell = Cell.fromInstance(obj[field])
}