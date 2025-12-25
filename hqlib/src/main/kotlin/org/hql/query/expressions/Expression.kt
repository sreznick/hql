package org.hql.query.expressions

import org.hql.hprof.heap.Instance

private fun className(x: Any?) = if (x == null) "null" else x::class.simpleName

sealed class Expression {
    abstract fun eval(instance: Instance): Any?

    data class Literal(val value: Any?): Expression() {
        override fun eval(instance: Instance) = value
    }

    data class Field(val field: String): Expression() {
        override fun eval(instance: Instance) = instance[field]
    }

    data class Access(val expr: Expression, val field: String): Expression() {
        override fun eval(instance: Instance): Any? {
            val exprResult = expr.eval(instance)
                ?: throw RuntimeException("trying to access $field of null")
            if (exprResult !is Instance)
                throw RuntimeException("trying to access $field of a value of type ${className(exprResult)}")
            return exprResult[field]
        }
    }

    data class Plus(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Any {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)

            if (leftResult is String && rightResult is String)
                return leftResult + rightResult
            if (leftResult is Number && rightResult is Number)
                return leftResult.toDouble() + rightResult.toDouble()

            throw RuntimeException("adding values of type " +
                    "${className(leftResult)} and ${className(rightResult)} " +
                    "is not supported")
        }
    }

    data class Minus(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Any {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)

            if (leftResult is Number && rightResult is Number)
                return leftResult.toDouble() - rightResult.toDouble()
            throw RuntimeException("subtracting values of type " +
                    "${className(leftResult)} and ${className(rightResult)} " +
                    "is not supported")
        }
    }
    data class Mult(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Any {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)

            if (leftResult is Number && rightResult is Number)
                return leftResult.toDouble() * rightResult.toDouble()
            throw RuntimeException("multiplying values of type " +
                    "${className(leftResult)} and ${className(rightResult)} " +
                    "is not supported")
        }
    }

    data class Div(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Any {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)

            if (leftResult is Number && rightResult is Number)
                return leftResult.toDouble() / rightResult.toDouble()
            throw RuntimeException("dividing values of type " +
                    "${className(leftResult)} and ${className(rightResult)} " +
                    "is not supported")
        }
    }

    data class And(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Any {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)
            if (leftResult !is Boolean) {
                throw RuntimeException("left operand of AND should be boolean (got ${className(left)})")
            }
            if (rightResult !is Boolean) {
                throw RuntimeException("right operand of AND should be boolean (got ${className(right)})")
            }
            return leftResult && rightResult
        }
    }
    data class Or(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Any {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)
            if (leftResult !is Boolean) {
                throw RuntimeException("left operand of OR should be boolean (got ${className(left)})")
            }
            if (rightResult !is Boolean) {
                throw RuntimeException("right operand of OR should be boolean (got ${className(right)})")
            }
            return leftResult || rightResult
        }
    }
    data class Comparison(val left: Expression, val op: String, val right: Expression): Expression() {
        override fun eval(instance: Instance): Any {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)

            if (leftResult is Number && rightResult is Number) {
                return when (op) {
                    "=" -> leftResult == rightResult
                    "!=" -> leftResult != rightResult
                    "<>" -> leftResult != rightResult
                    "<" -> leftResult.toDouble() < rightResult.toDouble()
                    "<=" -> leftResult.toDouble() <= rightResult.toDouble()
                    ">" -> leftResult.toDouble() > rightResult.toDouble()
                    ">=" -> leftResult.toDouble() >= rightResult.toDouble()
                    else -> throw RuntimeException("invalid operator: $op")
                }
            }
            throw RuntimeException("comparing values of type " +
                    "${className(leftResult)} and ${className(rightResult)} " +
                    "is not supported")


        }
    }
}