package org.hql.query.expressions

import org.hql.HQLQueryException
import org.hql.query.Cell
import org.hql.query.Row
import org.hql.query.StringCell

object BuiltinFunctions {

    private val functions = mutableMapOf<String, (Row, List<Cell>) -> Cell>()

    fun register(name: String, fn: (Row, List<Cell>) -> Cell) {
        functions[name.lowercase()] = fn
    }

    fun call(name: String, row: Row, args: List<Cell>): Cell {
        val fn = functions[name.lowercase()] ?: error("Unknown function: $name")
        return fn(row, args)
    }

    fun List<Cell>.requireSingleString(fnName: String): String {
        if (size != 1) throw HQLQueryException("$fnName: expected 1 argument")
        return (this[0] as? StringCell)?.value
            ?: throw HQLQueryException("$fnName: argument must be a string")
    }
}
