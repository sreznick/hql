package org.hql.hprof.reader

import org.hql.hprof.heap.Identifier
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.lang.RuntimeException


class HprofReader(inputStream: InputStream) {
    private val stream = DataInputStream(inputStream)
    val format: String
    val identifierSize: Int
    val timestamp: Long

    private val hprof = Hprof()

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
    constructor(path: String) : this(File(path).inputStream())

    fun getHprof() = hprof

    private fun DataInputStream.readIdentifier() =
        Identifier(readNBytes(identifierSize))

    private fun DataInputStream.readBasicType(type: BasicType) : BasicValue {
        return when (type) {
            BasicType.OBJECT -> BasicValue.Object(Identifier(readNBytes(identifierSize)))
            BasicType.BOOLEAN -> BasicValue.BooleanV(read() != 0)
            BasicType.CHAR -> BasicValue.CharV(readShort().toInt().toChar())
            BasicType.FLOAT -> BasicValue.FloatV(readFloat())
            BasicType.DOUBLE -> BasicValue.DoubleV(readDouble())
            BasicType.BYTE -> BasicValue.ByteV(read().toByte())
            BasicType.SHORT -> BasicValue.ShortV(readShort())
            BasicType.INT -> BasicValue.IntV(readInt())
            BasicType.LONG -> BasicValue.LongV(readLong())
        }
    }

    private fun DataInputStream.readBasicType() =
        readBasicType(BasicType.from(read()))

    private fun readTag(): Boolean {
        val type = stream.read()
        if (type == -1) return false
        stream.readInt() // timestamp
        val length = stream.readInt()
        val contentStream = DataInputStream(stream.readNBytes(length).inputStream())
        when (type) {
            0x01 -> readStringTag(contentStream)
            0x02 -> readLoadClassTag(contentStream)
            0x0C -> readHeapDumpTag(contentStream)
            0x1C -> readHeapDumpTag(contentStream)
        }
        contentStream.close()
        return true
    }

    private fun readStringTag(stream: DataInputStream) {
        val id = stream.readIdentifier()
        val string = stream.readBytes().toString(Charsets.UTF_8)
        hprof.addString(id, string)
    }

    private fun readLoadClassTag(stream: DataInputStream) {
        stream.readInt() // class serial number
        val classId = stream.readIdentifier()
        stream.readInt() // stack trace serial number
        val classNameId = stream.readIdentifier()
        hprof.addClassName(classId, classNameId)
    }

    private fun readHeapDumpTag(stream: DataInputStream) {
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

    private fun readClassDump(stream: DataInputStream) {
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
        val staticFields = mutableMapOf<Identifier, BasicValue>()
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

        hprof.addClass(classId, superclassId, instanceSize, staticFields.toMap(), instanceFields.toList())
    }

    private fun readInstanceDump(stream: DataInputStream) {
        val id = stream.readIdentifier()
        stream.readInt() // stack trace serial number
        val classId = stream.readIdentifier()
        val contentSize = stream.readInt()

        val content = DataInputStream(stream.readNBytes(contentSize).inputStream())
        val fields = mutableMapOf<Identifier, BasicValue>()
        hprof.getInstanceFieldTypes(classId).forEach { (name, type) ->
            fields[name] = content.readBasicType(type)
        }
        content.close()
        hprof.addInstance(id, InstanceInternal.Object(
            classId = classId,
            fieldValues = fields.toMap()
        ))
    }

    private fun readObjectArrayDump(stream: DataInputStream) {
        val id = stream.readIdentifier()
        stream.readInt() // stack trace serial number
        val size = stream.readInt()
        stream.readIdentifier() // element type identifier
        val list = List(size) { stream.readIdentifier() }
        hprof.addInstance(id, InstanceInternal.ObjectArray(list))
    }

    private fun readPrimitiveArrayDump(stream: DataInputStream) {
        val id = stream.readIdentifier()
        stream.readInt() // stack trace serial number
        val size = stream.readInt()
        val type = BasicType.from(stream.read())
        val list = List(size) { stream.readBasicType(type) }
        hprof.addInstance(id, InstanceInternal.PrimitiveArray(list))
    }
}