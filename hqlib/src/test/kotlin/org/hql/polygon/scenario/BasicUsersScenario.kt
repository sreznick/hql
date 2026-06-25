package org.hql.polygon.scenario

import org.hql.polygon.fixtures.PolygonUser

/**
 * Сценарий, наполняющий память фиксированным списком объектов PolygonUser.
 * Предназначен для валидации базовых операций HQL: проекции колонок, фильтрации и сортировки.
 */
class BasicUsersScenario : PolygonScenario {
    override val scenarioName = "basic_users"

    // Сохраняем сильные (Strong) ссылки на объекты, чтобы GC не очистил их до снятия дампа
    private val heldUsers = mutableListOf<PolygonUser>()

    override fun setupAndWaitForReady() {
        for (i in 1..100) {
            heldUsers.add(
                PolygonUser(
                    id = i,
                    name = "User_$i",
                    age = 20 + (i % 30),
                    isActive = i % 2 == 0,
                    balance = 100.5
                )
            )
        }
    }

    override fun close() {
        // Очищаем коллекцию, освобождая память для будущих тестов
        heldUsers.clear()
    }
}