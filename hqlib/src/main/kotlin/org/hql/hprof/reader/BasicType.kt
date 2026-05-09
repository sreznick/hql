package org.hql.hprof.reader

import org.hql.hprof.heap.Identifier
import java.lang.RuntimeException
import kotlin.enums.enumEntries

enum class BasicType(val typeId: Int) {
    OBJECT(2),
    BOOLEAN(4),
    CHAR(5),
    FLOAT(6),
    DOUBLE(7),
    BYTE(8),
    SHORT(9),
    INT(10),
    LONG(11);

    companion object {
        fun from(typeId: Int) = enumEntries<BasicType>().firstOrNull { it.typeId == typeId }
            ?: throw RuntimeException("unknown basic type: $typeId")
    }
}

sealed class BasicValue {
    data class Object(val id: Identifier) : BasicValue()
    sealed class Primitive : BasicValue()
    data class BooleanV(val v: Boolean) : Primitive()
    data class CharV(val v: Char) : Primitive()
    data class FloatV(val v: Float) : Primitive()
    data class DoubleV(val v: Double) : Primitive()
    data class ByteV(val v: Byte) : Primitive()
    data class ShortV(val v: Short) : Primitive()
    data class IntV(val v: Int) : Primitive()
    data class LongV(val v: Long) : Primitive()
}