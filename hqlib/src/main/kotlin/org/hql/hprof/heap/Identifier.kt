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

    override fun hashCode(): Int = value.contentHashCode()

    fun isNull() = value.all { it == 0.toByte() }

    override fun toString(): String {
        return value.joinToString(separator = " ") { it.toUByte().toHexString() }
    }

    fun toCompactHex(): String {
        if (value.isEmpty()) return "0"

        val sb = StringBuilder(value.size * 2)
        for (b in value) {
            sb.append("%02x".format(b))
        }

        val result = sb.toString().trimStart('0')
        return result.ifEmpty { "0" }
    }
}