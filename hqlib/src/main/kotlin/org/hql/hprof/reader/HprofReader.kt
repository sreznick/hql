package org.hql.hprof.reader

import org.hql.hprof.heap.BasicType
import org.hql.hprof.heap.Identifier
import java.io.InputStream

private fun InputStream.readShort(): Short {
    val x = readNBytes(2).map { it.toUByte().toUInt() }
    val i = (x[0] shl 8) + x[1]
    return i.toShort()
}

private fun InputStream.readInt(): Int {
    val x = readNBytes(4).map { it.toUByte().toUInt() }
    val i = (x[0] shl 24) + (x[1] shl 16) + (x[2] shl 8) + x[3]
    return i.toInt()
}

private fun InputStream.readLong(): Long {
    val x = readNBytes(8).map { it.toUByte().toULong() }
    val i = (x[0] shl 56) + (x[1] shl 48) + (x[2] shl 40) + (x[3] shl 32) + (x[4] shl 24) + (x[5] shl 16) + (x[6] shl 8) + x[7]
    return i.toLong()
}

private fun InputStream.readFloat(): Float {
    return java.lang.Float.intBitsToFloat(readInt())
}

private fun InputStream.readDouble(): Double {
    return java.lang.Double.longBitsToDouble(readLong())
}


class HprofReader(private val stream: InputStream) {
    val format: String
    val identifierSize: Int
    val timestamp: Long

    val strings = hashMapOf<Identifier, String>()
    val classNames = hashMapOf<Identifier, Identifier>()
    val classes = hashMapOf<Identifier, ClassInternal>()
    val instances = hashMapOf<Identifier, Any>()

    init {
        val s = StringBuilder()
        var c: Char = stream.read().toChar()
        while (c != 0.toChar()) {
            s.append(c)
            c = stream.read().toChar()
        }
        format = s.toString()
        identifierSize = stream.readInt()
        timestamp = stream.readLong()
        while (readTag()) {}
    }

    private fun InputStream.readIdentifier() =
        Identifier(readNBytes(identifierSize))

    private fun InputStream.readBasicType() =
        readBasicType(BasicType.from(read()))

    private fun InputStream.readBasicType(type: BasicType): Any =
        when (type) {
            BasicType.OBJECT -> readIdentifier()
            BasicType.BOOLEAN -> read() != 0
            BasicType.CHAR -> readShort().toInt().toChar()
            BasicType.FLOAT -> readFloat()
            BasicType.DOUBLE -> readDouble()
            BasicType.BYTE -> read().toByte()
            BasicType.SHORT -> readShort()
            BasicType.INT -> readInt()
            BasicType.LONG -> readLong()
        }

    private fun readTag(): Boolean {
        val type = stream.read()
        if (type == -1) return false
        stream.readInt()
        val length = stream.readInt()
        val contentStream = stream.readNBytes(length).inputStream()
        when (type) {
            0x01 -> readStringTag(contentStream)
            0x02 -> readLoadClassTag(contentStream)
            0x0C -> readHeapDumpTag(contentStream)
            0x1C -> readHeapDumpTag(contentStream)
        }
        contentStream.close()
        return true
    }

    private fun readStringTag(stream: InputStream) {
        val id = stream.readIdentifier()
        val string = stream.readBytes().toString(Charsets.UTF_8)
        strings[id] = string
    }

    private fun readLoadClassTag(stream: InputStream) {
        stream.readInt()
        val classId = stream.readIdentifier()
        stream.readInt()
        val classNameId = stream.readIdentifier()

        classNames[classId] = classNameId
    }

    private fun readHeapDumpTag(stream: InputStream) {
        while (true) {
            val type = stream.read()
            if (type == -1) break
            when (type) {
                0xFF -> {
                    stream.readIdentifier()
                }
                0x01 -> {
                    stream.readIdentifier()
                    stream.readIdentifier()
                }
                0x02 -> {
                    stream.readIdentifier()
                    stream.readInt()
                    stream.readInt()
                }
                0x03 -> {
                    stream.readIdentifier()
                    stream.readInt()
                    stream.readInt()
                }
                0x04 -> {
                    stream.readIdentifier()
                    stream.readInt()
                }
                0x05 -> {
                    stream.readIdentifier()
                }
                0x06 -> {
                    stream.readIdentifier()
                    stream.readInt()
                }
                0x07 -> {
                    stream.readIdentifier()
                }
                0x08 -> {
                    stream.readIdentifier()
                    stream.readInt()
                    stream.readInt()
                }
                0x20 -> readClassDump(stream)
                0x21 -> readInstanceDump(stream)
                0x22 -> readObjectArrayDump(stream)
                0x23 -> readPrimitiveArrayDump(stream)
            }
        }
    }

    private fun readClassDump(stream: InputStream) {
        val classId = stream.readIdentifier()
        stream.readInt()
        val superclassId = stream.readIdentifier()
        stream.readIdentifier() // class loader object ID
        stream.readIdentifier() // signers object ID
        stream.readIdentifier() // protection domain object ID
        stream.readIdentifier() // reserved
        stream.readIdentifier() // reserved
        val instanceSize = stream.readInt()

        val constantPoolSize = stream.readShort().toInt()
        repeat(constantPoolSize) {
            stream.readShort()
            stream.readBasicType()
        }

        val staticFieldsSize = stream.readShort().toInt()
        val staticFields = mutableMapOf<Identifier, Any>()
        repeat(staticFieldsSize) {
            val fieldName = stream.readIdentifier()
            val fieldValue = stream.readBasicType()
            staticFields[fieldName] = fieldValue
        }

        val instanceFieldSize = stream.readShort().toInt()
        val instanceFields = mutableListOf<Pair<Identifier, BasicType>>()
        repeat(instanceFieldSize) {
            val fieldName = stream.readIdentifier()
            val fieldType = BasicType.from(stream.read())
            instanceFields.add(fieldName to fieldType)
        }

        classes[classId] = ClassInternal(
            id = classId,
            superclassId = superclassId,
            instanceSize = instanceSize,
            staticFields = staticFields.toMap(),
            instanceFieldTypes = instanceFields.toList()
        )
        //println("class $classId")
    }

    private fun readInstanceDump(stream: InputStream) {
        val id = stream.readIdentifier()
        stream.readInt() // stack trace serial number
        val classId = stream.readIdentifier()
        val contentSize = stream.readInt()

        val content = stream.readNBytes(contentSize).inputStream()
        val fields = mutableMapOf<Identifier, Any>()
        var cls = classes[classId]
        while (cls != null) {
            cls.instanceFieldTypes.forEach { (name, type) ->
                fields[name] = content.readBasicType(type)
            }
            cls = classes[cls.superclassId]
        }
        content.close()
        instances[id] = InstanceInternal(
            classId = classId,
            fieldValues = fields.toMap()
        )
        //println("objectInstance $id classId=$classId")
    }

    private fun readObjectArrayDump(stream: InputStream) {
        val id = stream.readIdentifier()
        stream.readInt() // stack trace serial number
        val n = stream.readInt()
        stream.readIdentifier() // element type identifier
        instances[id] = List(n) { stream.readIdentifier() }
        //println("objectArrayInstance $id")
    }

    private fun readPrimitiveArrayDump(stream: InputStream) {
        val id = stream.readIdentifier()
        stream.readInt() // stack trace serial number
        val n = stream.readInt()
        val type = BasicType.from(stream.read())
        instances[id] = List(n) { stream.readBasicType(type) }
        //println("primitiveArrayInstance $id")
    }
}