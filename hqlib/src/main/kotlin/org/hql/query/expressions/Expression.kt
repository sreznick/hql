package org.hql.query.expressions

import org.hql.query.BooleanCell
import org.hql.query.Cell

private fun className(x: Any?) = if (x == null) "null" else x::class.simpleName

sealed class Expression {
    abstract fun eval(row: Map<String, Cell>): Cell

    data class Literal(val value: Cell): Expression() {
        override fun eval(row: Map<String, Cell>) = value
    }

    data class Field(val field: String): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            if (!row.containsKey(field))
                throw RuntimeException("no column named $field (columns: ${row.keys.joinToString(",")})")
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
            if (leftResult !is BooleanCell) {
                throw RuntimeException("left operand of AND should be boolean (got ${className(left)})")
            }
            if (rightResult !is BooleanCell) {
                throw RuntimeException("right operand of AND should be boolean (got ${className(right)})")
            }
            return BooleanCell(leftResult.v && rightResult.v)
        }
    }
    data class Or(val left: Expression, val right: Expression): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            val leftResult = left.eval(row)
            val rightResult = right.eval(row)
            if (leftResult !is BooleanCell) {
                throw RuntimeException("left operand of OR should be boolean (got ${className(left)})")
            }
            if (rightResult !is BooleanCell) {
                throw RuntimeException("right operand of OR should be boolean (got ${className(right)})")
            }
            return BooleanCell(leftResult.v || rightResult.v)
        }
    }
    data class Comparison(val left: Expression, val op: String, val right: Expression): Expression() {
        override fun eval(row: Map<String, Cell>): Cell {
            val leftResult = left.eval(row)
            val rightResult = right.eval(row)

            return BooleanCell(when (op) {
                "=" -> leftResult.compareTo(rightResult) == 0
                "!=" -> leftResult.compareTo(rightResult) != 0
                "<>" -> leftResult.compareTo(rightResult) != 0
                "<" -> leftResult < rightResult
                "<=" -> leftResult <= rightResult
                ">" -> leftResult > rightResult
                ">=" -> leftResult >= rightResult
                else -> throw RuntimeException("invalid operator: $op")
            })
        }
    }
}