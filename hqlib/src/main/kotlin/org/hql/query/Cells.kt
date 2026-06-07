package org.hql.query

import org.hql.IncompatibleTypesException
import org.hql.hprof.heap.Class
import org.hql.hprof.heap.instances.Instance

interface Cell : Comparable<Cell> {
    val type: String
        get() = throw UnsupportedOperationException()

    override fun compareTo(other: Cell): Int {
        throw IncompatibleTypesException("comparing instances", type, other.type)
    }
    operator fun plus(other: Cell): Cell {
        throw IncompatibleTypesException("adding instances", type, other.type)
    }
    operator fun minus(other: Cell): Cell {
        throw IncompatibleTypesException("subtracting instances", type, other.type)
    }
    operator fun times(other: Cell): Cell {
        throw IncompatibleTypesException("multiplying instances", type, other.type)
    }
    operator fun div(other: Cell): Cell {
        throw IncompatibleTypesException("dividing instances", type, other.type)
    }
    infix fun and(other: Cell): Cell {
        throw IncompatibleTypesException("AND operation", type, other.type)
    }
    infix fun or(other: Cell): Cell {
        throw IncompatibleTypesException("OR operation", type, other.type)
    }
    operator fun not(): Cell {
        throw IncompatibleTypesException("NOT operation", type)
    }

    fun access(field: String): Cell {
        throw IncompatibleTypesException("accessing a field $field", type)
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
                is Instance.NullI -> NullCell
                is Instance.ObjectI -> ObjectCell(instance)
                is Instance.ShortI -> IntCell(instance.v.toLong())
                is Instance.StringI -> StringCell(instance.value)
            }
    }
}

data object NullCell : Cell {
    override val type: String = "null"
    override fun toString() = "-"
    override fun compareTo(other: Cell): Int {
        if (other is NullCell) return 0
        return super.compareTo(other)
    }
}

data class BooleanCell(val v: Boolean) : Cell {
    override val type: String = "boolean"
    override fun toString() = "$v"
    override fun compareTo(other: Cell): Int {
        if (other is BooleanCell) return v.compareTo(other.v)
        return super.compareTo(other)
    }
    override fun and(other: Cell): Cell {
        if (other is BooleanCell) return BooleanCell(v && other.v)
        return super.and(other)
    }
    override fun or(other: Cell): Cell {
        if (other is BooleanCell) return BooleanCell(v || other.v)
        return super.and(other)
    }
    override fun not(): Cell {
        return BooleanCell(!v)
    }
}

data class CharCell(val v: Char) : Cell {
    override val type: String = "char"
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
    override val type: String = "float"
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
    override val type: String = "int"
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
    override val type: String = if (instance.values.isEmpty()) "array<>" else "array<${this[0].type}>"
    operator fun get(i: Int) = Cell.fromInstance(instance.values[i])
    override fun toString() = instance.values.indices
        .map { i -> this[i] }
        .joinToString(prefix = "[", postfix = "]")
}

data class StringCell(val value: String) : Cell {
    override val type: String = "string"
    override fun toString() = value
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
    override val type: String = "class"
    override fun toString() = "class<${cls.name}>"
    override fun compareTo(other: Cell): Int {
        if (other is ClassCell) return cls.id.compareTo(other.cls.id)
        return super.compareTo(other)
    }
    override fun equals(other: Any?): Boolean {
        return other is ClassCell && cls.id == other.cls.id
    }
    override fun hashCode(): Int = cls.id.hashCode()
}

class ObjectCell(val obj: Instance.ObjectI) : Cell {
    override val type: String = obj.cls.name
    override fun toString() = "object<$type>"
    override fun access(field: String): Cell = Cell.fromInstance(obj[field])
    override fun equals(other: Any?): Boolean {
        return other is ObjectCell && obj.id == other.obj.id
    }
    override fun hashCode(): Int = obj.id.hashCode()
}

class AggregateCell(val cells: List<Cell>) : Cell {
    override val type: String = "aggregate<${cells.first().type}>"
    override fun toString() = "<several values>"
    override fun equals(other: Any?): Boolean {
        return other is AggregateCell && cells == other.cells
    }
    override fun hashCode(): Int = cells.hashCode()
}

class RowCell(val row: Row) : Cell {
    override val type: String = "row"
    override fun toString() = "<row>"
    override fun access(field: String): Cell = row[field]
}