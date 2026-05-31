package org.hql.hprof.reader.threads

import org.hql.hprof.heap.instances.Instance
import org.hql.hprof.heap.instances.threads.ThreadRow
import org.hql.hprof.heap.instances.threads.enums.ThreadState

/**
 * Builds [ThreadRow] models from raw heap instances of java.lang.Thread (and its subclasses).
 */
interface ThreadMapper {
    operator fun invoke(instance: Instance.ObjectI): ThreadRow
}

/**
 * Default [ThreadMapper]. Reads the Thread fields directly, transparently falling back to the
 * `holder` (java.lang.Thread$FieldHolder) layout introduced in JDK 19, where `priority`,
 * `daemon` and `threadStatus` moved off Thread itself.
 */
object ThreadInstanceMapper : ThreadMapper {

    override operator fun invoke(instance: Instance.ObjectI): ThreadRow =
        ThreadRow(
            instance = instance,
            className = instance.cls.name,
            name = instance.threadField("name").asString(),
            state = ThreadState.fromThreadStatus(instance.threadField("threadStatus").asInt()),
            daemon = instance.threadField("daemon").asBoolean(),
            priority = instance.threadField("priority").asInt(),
            tid = instance.threadField("tid").asLong()
        )

    // Looks up a Thread field on the instance, falling back to the JDK 19+ `holder` sub-object.
    private fun Instance.ObjectI.threadField(name: String): Instance? {
        fields[name]?.let { return it }
        return (fields["holder"] as? Instance.ObjectI)?.fields?.get(name)
    }

    private fun Instance?.asString(): String? = (this as? Instance.StringI)?.value

    private fun Instance?.asBoolean(): Boolean? = (this as? Instance.BooleanI)?.v

    private fun Instance?.asInt(): Int? = when (this) {
        is Instance.IntI -> v
        is Instance.ShortI -> v.toInt()
        is Instance.ByteI -> v.toInt()
        else -> null
    }

    private fun Instance?.asLong(): Long? = when (this) {
        is Instance.LongI -> v
        is Instance.IntI -> v.toLong()
        else -> null
    }
}
