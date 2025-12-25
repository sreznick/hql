package org.hql.hprof.heap

class Identifier(private val value: ByteArray): Comparable<Identifier> {
    override fun equals(other: Any?): Boolean {
        return other is Identifier && value.zip(other.value).all { it.first == it.second }
    }

    override fun compareTo(other: Identifier): Int {
        value.zip(other.value).forEach { (v1, v2) ->
            if (v1 != v2) return v1 - v2
        }
        return 0
    }

    override fun hashCode(): Int {
        return (value[0] * (1 shl 24) + value[1] * (1 shl 16) + value[2] * (1 shl 8) + value[3]) +
                (value[4] * (1 shl 24) + value[5] * (1 shl 16) + value[6] * (1 shl 8) + value[7])
    }

    fun isNull() = value.all { it == 0.toByte() }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return value.joinToString(separator = " ") { it.toUByte().toHexString() }
    }
}