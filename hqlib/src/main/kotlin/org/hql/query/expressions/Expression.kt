package org.hql.query.expressions

import org.hql.hprof.heap.Instance

private fun className(x: Any?) = if (x == null) "null" else x::class.simpleName

sealed class Expression {
    abstract fun eval(instance: Instance): Instance

    data class Literal(val value: Instance): Expression() {
        override fun eval(instance: Instance) = value
    }

    data class Field(val field: String): Expression() {
        override fun eval(instance: Instance): Instance {
            if (instance !is Instance.ObjectI)
                throw RuntimeException("trying to access a field of ${className(instance)}")
            return instance[field]
        }
    }

    data class Access(val expr: Expression, val field: String): Expression() {
        override fun eval(instance: Instance): Instance {
            val exprResult = expr.eval(instance)
            if (exprResult !is Instance.ObjectI)
                throw RuntimeException("trying to access $field of a value of type ${className(exprResult)}")
            return exprResult[field]
        }
    }

    data class Plus(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Instance {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)

            if (leftResult is Instance.ByteI && rightResult is Instance.ByteI)
                return Instance.IntI(leftResult.v + rightResult.v)
            if (leftResult is Instance.ShortI && rightResult is Instance.ShortI)
                return Instance.IntI(leftResult.v + rightResult.v)
            if (leftResult is Instance.IntI && rightResult is Instance.IntI)
                return Instance.IntI(leftResult.v + rightResult.v)
            if (leftResult is Instance.LongI && rightResult is Instance.LongI)
                return Instance.LongI(leftResult.v + rightResult.v)
            if (leftResult is Instance.FloatI && rightResult is Instance.FloatI)
                return Instance.FloatI(leftResult.v + rightResult.v)
            if (leftResult is Instance.DoubleI && rightResult is Instance.DoubleI)
                return Instance.DoubleI(leftResult.v + rightResult.v)

            throw RuntimeException("adding values of type " +
                    "${className(leftResult)} and ${className(rightResult)} " +
                    "is not supported")
        }
    }

    data class Minus(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Instance {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)

            if (leftResult is Instance.ByteI && rightResult is Instance.ByteI)
                return Instance.IntI(leftResult.v - rightResult.v)
            if (leftResult is Instance.ShortI && rightResult is Instance.ShortI)
                return Instance.IntI(leftResult.v - rightResult.v)
            if (leftResult is Instance.IntI && rightResult is Instance.IntI)
                return Instance.IntI(leftResult.v - rightResult.v)
            if (leftResult is Instance.LongI && rightResult is Instance.LongI)
                return Instance.LongI(leftResult.v - rightResult.v)
            if (leftResult is Instance.FloatI && rightResult is Instance.FloatI)
                return Instance.FloatI(leftResult.v - rightResult.v)
            if (leftResult is Instance.DoubleI && rightResult is Instance.DoubleI)
                return Instance.DoubleI(leftResult.v - rightResult.v)

            throw RuntimeException("subtracting values of type " +
                    "${className(leftResult)} and ${className(rightResult)} " +
                    "is not supported")
        }
    }
    data class Mult(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Instance {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)

            if (leftResult is Instance.ByteI && rightResult is Instance.ByteI)
                return Instance.IntI(leftResult.v * rightResult.v)
            if (leftResult is Instance.ShortI && rightResult is Instance.ShortI)
                return Instance.IntI(leftResult.v * rightResult.v)
            if (leftResult is Instance.IntI && rightResult is Instance.IntI)
                return Instance.IntI(leftResult.v * rightResult.v)
            if (leftResult is Instance.LongI && rightResult is Instance.LongI)
                return Instance.LongI(leftResult.v * rightResult.v)
            if (leftResult is Instance.FloatI && rightResult is Instance.FloatI)
                return Instance.FloatI(leftResult.v * rightResult.v)
            if (leftResult is Instance.DoubleI && rightResult is Instance.DoubleI)
                return Instance.DoubleI(leftResult.v * rightResult.v)

            throw RuntimeException("multiplying values of type " +
                    "${className(leftResult)} and ${className(rightResult)} " +
                    "is not supported")
        }
    }

    data class Div(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Instance {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)

            if (leftResult is Instance.ByteI && rightResult is Instance.ByteI)
                return Instance.IntI(leftResult.v / rightResult.v)
            if (leftResult is Instance.ShortI && rightResult is Instance.ShortI)
                return Instance.IntI(leftResult.v / rightResult.v)
            if (leftResult is Instance.IntI && rightResult is Instance.IntI)
                return Instance.IntI(leftResult.v / rightResult.v)
            if (leftResult is Instance.LongI && rightResult is Instance.LongI)
                return Instance.LongI(leftResult.v / rightResult.v)
            if (leftResult is Instance.FloatI && rightResult is Instance.FloatI)
                return Instance.FloatI(leftResult.v / rightResult.v)
            if (leftResult is Instance.DoubleI && rightResult is Instance.DoubleI)
                return Instance.DoubleI(leftResult.v / rightResult.v)

            throw RuntimeException("dividing values of type " +
                    "${className(leftResult)} and ${className(rightResult)} " +
                    "is not supported")
        }
    }

    data class And(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Instance {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)
            if (leftResult !is Instance.BooleanI) {
                throw RuntimeException("left operand of AND should be boolean (got ${className(left)})")
            }
            if (rightResult !is Instance.BooleanI) {
                throw RuntimeException("right operand of AND should be boolean (got ${className(right)})")
            }
            return Instance.BooleanI(leftResult.v && rightResult.v)
        }
    }
    data class Or(val left: Expression, val right: Expression): Expression() {
        override fun eval(instance: Instance): Instance {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)
            if (leftResult !is Instance.BooleanI) {
                throw RuntimeException("left operand of OR should be boolean (got ${className(left)})")
            }
            if (rightResult !is Instance.BooleanI) {
                throw RuntimeException("right operand of OR should be boolean (got ${className(right)})")
            }
            return Instance.BooleanI(leftResult.v || rightResult.v)
        }
    }
    data class Comparison(val left: Expression, val op: String, val right: Expression): Expression() {
        override fun eval(instance: Instance): Instance {
            val leftResult = left.eval(instance)
            val rightResult = right.eval(instance)

            fun matches(compareResult: Int): Boolean {
                return when (op) {
                    "=" -> compareResult == 0
                    "!=" -> compareResult != 0
                    "<>" -> compareResult != 0
                    "<" -> compareResult < 0
                    "<=" -> compareResult <= 0
                    ">" -> compareResult > 0
                    ">=" -> compareResult >= 0
                    else -> throw RuntimeException("invalid operator: $op")
                }
            }

            return Instance.BooleanI(matches(leftResult.compareTo(rightResult)))
        }
    }
}