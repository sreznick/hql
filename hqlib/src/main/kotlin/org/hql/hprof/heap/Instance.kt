package org.hql.hprof.heap

open class Instance(val id: Identifier) {
    private var cls: Class? = null
    private val fieldValues = mutableMapOf<String, Any?>()

    /* functions intended for use only during construction */
    internal fun setClass(cls: Class) {
        this.cls = cls
    }
    internal fun addField(name: String, value: Any?) {
        this.fieldValues[name] = value
    }

    override fun toString() = "<Instance of class ${getType()}>"

    fun getClass() = cls!!
    fun getType() = getClass().getName()
    fun getFieldValues() = fieldValues.toMap()
    operator fun get(name: String) = fieldValues.getValue(name)
}

class StringInstance(id: Identifier): Instance(id), Comparable<StringInstance> {
    private val content
        get() = (get("value") as List<Byte>).toByteArray().toString(Charsets.UTF_8)

    override fun toString() = "\"" + content + "\""

    override fun compareTo(other: StringInstance) = content.compareTo(other.content)
}