package org.hql.query.rows

import org.hql.hprof.heap.instances.Instance
import org.hql.query.Cell
import org.hql.query.Row

class ClassRow(private val obj: Instance.ObjectI) : Row {
    override fun get(column: String) = Cell.fromInstance(obj[column])
}