package org.hql.query.tables

import org.hql.hprof.heap.Class
import org.hql.query.rows.ClassRow

/**
 * A table representing a JVM class, with the class fields acting as columns,
 * and the class instances acting as rows.
 */
class ClassTable(
    private val cls: Class
) : Table() {
    override val name = cls.name

    override val baseColumns: List<String>
        get() = cls.instanceFieldTypes.keys.toList()

    override val rows: List<ClassRow>
        get() = cls.instances.map { ClassRow(it) }
}
