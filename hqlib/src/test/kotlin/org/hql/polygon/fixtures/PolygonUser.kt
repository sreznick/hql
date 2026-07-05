package org.hql.polygon.fixtures

/**
 * Тестовая модель пользователя (fixture).
 * Используется для наполнения памяти контролируемыми объектами перед генерацией дампа.
 */
data class PolygonUser(
    val id: Int,
    val name: String,
    val age: Int,
    val isActive: Boolean,
    val balance: Double
)