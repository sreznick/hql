package org.hql.query.expressions

import org.hql.HQLQueryException
import org.hql.query.AggregateCell
import org.hql.query.ArrayCell
import org.hql.query.BooleanCell
import org.hql.query.Cell
import org.hql.query.CharCell
import org.hql.query.FloatCell
import org.hql.query.IntCell
import org.hql.query.NullCell
import org.hql.query.Row
import org.hql.query.StringCell
import org.hql.query.rows.AggregateRow
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt

data class FunctionScope(
    val name: String,
    val row: Row,
    val args: List<Cell>
) {
    fun requireArgsSize(size: Int) {
        if (args.size != size) {
            throw HQLQueryException("$name: requires $size arguments (provided: ${args.size})")
        }
    }
    fun requireNoArgs() {
        if (args.isNotEmpty()) {
            throw HQLQueryException("$name: doesn't accept arguments (provided: ${args.size})")
        }
    }

    fun stringArgument(index: Int): String {
        val cell = args[index]
        return (cell as? StringCell)?.value
            ?: throw HQLQueryException("$name: argument ${index + 1} must be a string (provided: ${cell.type})")
    }
    fun booleanArgument(index: Int): Boolean {
        val cell = args[index]
        return (cell as? BooleanCell)?.v
            ?: throw HQLQueryException("$name: argument $index must be a boolean (provided: ${cell.type})")
    }
    fun numberArgument(index: Int): Double {
        val cell = args[index]
        return (cell as? IntCell)?.v?.toDouble()
            ?: (cell as? FloatCell)?.v
            ?: throw HQLQueryException("$name: argument $index must be a number (provided: ${cell.type})")
    }
    fun intArgument(index: Int): Long {
        val cell = args[index]
        return (cell as? IntCell)?.v
            ?: throw HQLQueryException("$name: argument $index must be an integer (provided: ${cell.type})")
    }
    fun aggregateArgument(index: Int): List<Cell> {
        val cell = args[index]
        return (cell as? AggregateCell)?.cells
            ?: listOf(cell)
    }
    fun argumentsAsNumberList(): List<Double> {
        val values = mutableListOf<Double>()
        args.forEachIndexed { i, cell ->
            when (cell) {
                is IntCell -> values.add(cell.v.toDouble())
                is FloatCell -> values.add(cell.v)
                is AggregateCell -> cell.cells.forEach { aggCell ->
                    when (aggCell) {
                        is IntCell -> values.add(aggCell.v.toDouble())
                        is FloatCell -> values.add(aggCell.v)
                        else -> throw HQLQueryException("$name: arguments should be numbers or aggregates of numbers (argument ${i + 1} was ${cell.type})")
                    }
                }
                else -> throw HQLQueryException("$name: arguments should be numbers or aggregates of numbers (argument ${i + 1} was ${cell.type})")
            }
        }
        return values
    }

    fun singleStringArgument(): String {
        requireArgsSize(1)
        return stringArgument(0)
    }
}

object BuiltinFunctions {

    private val functions = mutableMapOf<String, FunctionScope.() -> Cell>()

    fun register(vararg names: String, fn: FunctionScope.() -> Cell) {
        names.forEach { name ->
            functions[name.lowercase()] = fn
        }
    }

    fun call(name: String, row: Row, args: List<Cell>): Cell {
        val fn = functions[name.lowercase()] ?: error("Unknown function: $name")
        val scope = FunctionScope(
            name = name.lowercase(),
            row = row,
            args = args
        )
        return scope.fn()
    }

    init {
        // basic functions / null checks
        register("coalesce") {
            args.forEach { cell ->
                if (cell !is NullCell)
                    return@register cell
            }
            NullCell
        }
        register("isnull") {
            requireArgsSize(1)
            BooleanCell(args[0] is NullCell)
        }
        register("ifnull") {
            requireArgsSize(2)
            if (args[0] is NullCell) args[1] else args[0]
        }
        register("if") {
            requireArgsSize(3)
            val cond = booleanArgument(0)
            if (cond) args[1] else args[2]
        }

        // number functions
        register("count") {
            requireNoArgs()
            if (row !is AggregateRow)
                throw HQLQueryException("calling COUNT without or before a GROUP BY clause")
            IntCell(row.group.size.toLong())
        }
        register("distinct") {
            requireArgsSize(1)
            val target = aggregateArgument(0)
            val cells = mutableSetOf<Cell>()
            cells.addAll(target)
            IntCell(cells.size.toLong())
        }
        register("round") {
            requireArgsSize(2)
            val value = numberArgument(0)
            val decimals = numberArgument(1)
            val decimals10 = 10.toDouble().pow(decimals.toInt())
            FloatCell((value * decimals10).roundToInt() / decimals10)
        }
        register("abs") {
            requireArgsSize(1)
            when (val value = args[0]) {
                is IntCell -> IntCell(value.v.absoluteValue)
                is FloatCell -> FloatCell(value.v.absoluteValue)
                else -> throw HQLQueryException("abs: argument must be a number (provided: ${value.type}")
            }
        }
        register("min") {
            val values = argumentsAsNumberList()
            if (values.isEmpty()) NullCell else FloatCell(values.min())
        }
        register("max") {
            val values = argumentsAsNumberList()
            if (values.isEmpty()) NullCell else FloatCell(values.max())
        }
        register("sum") {
            val values = argumentsAsNumberList()
            if (values.isEmpty()) NullCell else FloatCell(values.sum())
        }
        register("avg") {
            val values = argumentsAsNumberList()
            if (values.isEmpty()) NullCell else FloatCell(values.average())
        }

        // string functions
        register("length") {
            val value = singleStringArgument()
            IntCell(value.length.toLong())
        }
        register("lower", "lcase") {
            val value = singleStringArgument()
            StringCell(value.lowercase())
        }
        register("upper", "ucase") {
            val value = singleStringArgument()
            StringCell(value.uppercase())
        }
        register("reverse") {
            val value = singleStringArgument()
            StringCell(value.reversed())
        }
        register("trim") {
            val value = singleStringArgument()
            StringCell(value.trim())
        }
        register("ltrim") {
            val value = singleStringArgument()
            StringCell(value.trimStart())
        }
        register("rtrim") {
            val value = singleStringArgument()
            StringCell(value.trimEnd())
        }
        register("substr", "substring", "mid") {
            requireArgsSize(3)
            val value = stringArgument(0)
            val start = intArgument(1).toInt()
            val length = intArgument(2).toInt()
            StringCell(value.substring(start, start + length))
        }
        register("locate", "position") {
            requireArgsSize(2)
            val value = stringArgument(0)
            val search = stringArgument(1)
            IntCell(value.indexOf(search).toLong())
        }
        register("index") {
            requireArgsSize(2)
            val index = intArgument(1).toInt()
            when (val value = args[0]) {
                is StringCell -> CharCell(value.value[index])
                is ArrayCell -> value[index]
                else -> throw HQLQueryException("index: argument 1 must be a string or array (provided: ${value.type}")
            }
        }
    }
}
