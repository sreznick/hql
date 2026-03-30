package org.hql.hprof.reader

import org.hql.hprof.heap.BasicType
import org.hql.hprof.heap.Identifier
import java.io.DataInputStream
import java.io.InputStream


class HprofReader(inputStream: InputStream) {
    private val stream = DataInputStream(inputStream)
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

    private fun DataInputStream.readIdentifier() =
        Identifier(readNBytes(identifierSize))

    private fun DataInputStream.readBasicType() =
        readBasicType(BasicType.from(read()))

    private fun DataInputStream.readBasicType(type: BasicType): Any =
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
        strings[id] = string
    }

    private fun readLoadClassTag(stream: DataInputStream) {
        stream.readInt()
        val classId = stream.readIdentifier()
        stream.readInt()
        val classNameId = stream.readIdentifier()

        classNames[classId] = classNameId
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

    private fun readInstanceDump(stream: DataInputStream) {
        val id = stream.readIdentifier()
        stream.readInt() // stack trace serial number
        val classId = stream.readIdentifier()
        val contentSize = stream.readInt()

        val content = DataInputStream(stream.readNBytes(contentSize).inputStream())
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

    private fun readObjectArrayDump(stream: DataInputStream) {
        val id = stream.readIdentifier()
        stream.readInt() // stack trace serial number
        val n = stream.readInt()
        stream.readIdentifier() // element type identifier
        instances[id] = List(n) { stream.readIdentifier() }
        //println("objectArrayInstance $id")
    }

    private fun readPrimitiveArrayDump(stream: DataInputStream) {
        val id = stream.readIdentifier()
        stream.readInt() // stack trace serial number
        val n = stream.readInt()
        val type = BasicType.from(stream.read())
        instances[id] = List(n) { stream.readBasicType(type) }
        //println("primitiveArrayInstance $id")
    }
}