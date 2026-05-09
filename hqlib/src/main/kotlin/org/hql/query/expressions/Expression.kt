package org.hql.query.expressions

import org.hql.HQLQueryException
import org.hql.query.BooleanCell
import org.hql.query.Cell
import org.hql.query.Row

sealed class Expression {
    abstract fun eval(row: Row): Cell

    data class Literal(val value: Cell) : Expression() {
        override fun eval(row: Row) = value
    }

    data class Field(val field: String) : Expression() {
        override fun eval(row: Row): Cell = row[field]
    }

    data class Access(val expr: Expression, val field: String) : Expression() {
        override fun eval(row: Row): Cell = expr.eval(row).access(field)
    }

    data class Plus(val left: Expression, val right: Expression) : Expression() {
        override fun eval(row: Row): Cell = left.eval(row) + right.eval(row)
    }

    data class Minus(val left: Expression, val right: Expression) : Expression() {
        override fun eval(row: Row): Cell = left.eval(row) - right.eval(row)
    }

    data class Mult(val left: Expression, val right: Expression) : Expression() {
        override fun eval(row: Row): Cell = left.eval(row) * right.eval(row)
    }

    data class Div(val left: Expression, val right: Expression) : Expression() {
        override fun eval(row: Row): Cell = left.eval(row) / right.eval(row)
    }

    data class And(val left: Expression, val right: Expression) : Expression() {
        override fun eval(row: Row): Cell = left.eval(row) and right.eval(row)
    }

    data class Or(val left: Expression, val right: Expression) : Expression() {
        override fun eval(row: Row): Cell = left.eval(row) or right.eval(row)
    }

    data class Comparison(val left: Expression, val op: String, val right: Expression) : Expression() {
        override fun eval(row: Row): Cell {
            val compareResult = left.eval(row).compareTo(right.eval(row))
            return BooleanCell(when (op) {
                "=" -> compareResult == 0
                "!=" -> compareResult != 0
                "<>" -> compareResult != 0
                "<" -> compareResult < 0
                "<=" -> compareResult <= 0
                ">" -> compareResult > 0
                ">=" -> compareResult >= 0
                else -> throw HQLQueryException("invalid operator: $op")
            })
        }
    }
}
