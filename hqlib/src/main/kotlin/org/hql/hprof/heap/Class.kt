package org.hql.hprof.heap

class Class(val id: Identifier) {
    private var name: String? = null
    private var superclass: Class? = null
    private val staticFields = mutableMapOf<String, Any?>()
    private val instanceFieldTypes = mutableMapOf<String, BasicType>()
    private val instances = mutableListOf<Instance>()

    /* functions intended for use only during construction */
    internal fun setName(name: String) {
        this.name = name
    }
    internal fun setSuperclass(superclass: Class?) {
        this.superclass = superclass
    }
    internal fun addStaticField(name: String, value: Any?) {
        this.staticFields[name] = value
    }
    internal fun addInstanceFieldType(name: String, type: BasicType) {
        this.instanceFieldTypes[name] = type
    }
    internal fun addInstance(instance: Instance) {
        instances.add(instance)
    }

    override fun toString() = "<Class ${getName()}>"

    fun getName() = name!!
    fun getSuperclass() = superclass
    fun getStaticFields() = staticFields.toMap()
    fun getInstanceFieldTypes() = instanceFieldTypes.toMap()
    fun getInstances() = instances.toList()
    operator fun get(name: String) = staticFields.getValue(name)
}