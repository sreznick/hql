package org.hql.hprof.heap

import java.lang.RuntimeException

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
        fun from(typeId: Int) = enumValues<BasicType>().firstOrNull { it.typeId == typeId }
            ?: throw RuntimeException("unknown basic type: $typeId")
    }
}