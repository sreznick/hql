package org.hql.polygon

/**
 * Базовое исключение для ошибок, возникающих в рамках тестового полигона HQL.
 */
open class PolygonException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Исключение, выбрасываемое при сбоях генерации, сохранения или считывания дампа памяти.
 */
class HeapDumpException(message: String, cause: Throwable? = null) : PolygonException(message, cause)