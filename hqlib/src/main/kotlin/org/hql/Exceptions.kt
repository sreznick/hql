package org.hql

sealed class HQLException(override val message: String): Exception()

class HQLQueryException(override val message: String): HQLException(message)

class ClassNotFoundException(val className: String) :
    HQLException("No class with name $className")

class ColumnNotFoundException(columnName: String, existingColumns: List<String>) :
    HQLException("no column named $columnName (columns: ${existingColumns.joinToString(", ")})")

class IncompatibleTypesException(operation: String, vararg types: String) :
    HQLException(
        if (types.size == 1)
            "$operation not supported for type ${types.first()}"
        else
            "$operation not supported for types: ${types.joinToString(", ")}"
    )