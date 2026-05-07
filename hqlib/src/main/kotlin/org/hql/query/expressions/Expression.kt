package org.hql.query.expressions

import org.hql.ColumnNotFoundException
import org.hql.HQLQueryException
import org.hql.query.BooleanCell
import org.hql.query.Cell

sealed class Expression {
    abstract fun eval(row: Map<String, Cell>): Cell

    data class Literal(val value: Cell): Expression() {
        override fun eval(row: Map<String, Cell>) = value
    }

    data class Field(val field: String): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            if (!row.containsKey(field))
                throw ColumnNotFoundException(field, row.keys.toList())
            return row[field]!!
        }
    }

    data class Access(val expr: Expression, val field: String): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            val exprResult = expr.eval(row)
            return exprResult.access(field)
        }
    }

    data class Plus(val left: Expression, val right: Expression): Expression() {
        override fun eval(row: Map<String, Cell>): Cell  {
            val leftResult = left.eval(row)
            val rightResult = right.eval(row)
            return leftResult + rightResult
        }
    }

    data class Minus(val left: Expression, val right: Expression): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            val leftResult = left.eval(row)
            val rightResult = right.eval(row)
            return leftResult - rightResult
        }
    }
    data class Mult(val left: Expression, val right: Expression): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            val leftResult = left.eval(row)
            val rightResult = right.eval(row)
            return leftResult * rightResult
        }
    }

    data class Div(val left: Expression, val right: Expression): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            val leftResult = left.eval(row)
            val rightResult = right.eval(row)
            return leftResult / rightResult
        }
    }

    data class And(val left: Expression, val right: Expression): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            val leftResult = left.eval(row)
            val rightResult = right.eval(row)
            return leftResult and rightResult
        }
    }
    data class Or(val left: Expression, val right: Expression): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            val leftResult = left.eval(row)
            val rightResult = right.eval(row)
            return leftResult or rightResult
        }
    }
    data class Comparison(val left: Expression, val op: String, val right: Expression): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            val leftResult = left.eval(row)
            val rightResult = right.eval(row)

            val compareResult = leftResult.compareTo(rightResult)
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