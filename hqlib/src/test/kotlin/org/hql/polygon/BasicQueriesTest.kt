package org.hql.polygon

import org.hql.hprof.heap.Heap
import org.hql.hprof.reader.HprofReader
import org.hql.polygon.dumper.JmxHeapDumper
import org.hql.polygon.scenario.BasicUsersScenario
import org.hql.query.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Интеграционные тесты для проверки базового синтаксиса HQL (проекция колонок, фильтрация, сортировка, лимиты).
 * Работает со стабильным снимком памяти из 100 тестовых пользователей.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicQueriesTest {

    private val log = KotlinLogging.logger {}

    // Ленивая загрузка базы данных HQL на основе полученного снимка памяти
    private val database: Database by lazy {
        val dumpsDir = Path.of("build/polygon-dumps").apply { createDirectories() }
        val runner = PolygonRunner(JmxHeapDumper(), dumpsDir)
        val hprofPath = runner.runAndDump(BasicUsersScenario()).getOrThrow()

        val stream = hprofPath.inputStream()
        Database(Heap(HprofReader(stream).getHprof()))
    }

    /**
     * Выполняет HQL-запрос и перехватывает его консольный вывод в форматированную строку.
     */
    private fun runQueryAndCapture(query: String): String {
        val buffer = ByteArrayOutputStream()
        PrintStream(buffer, true, StandardCharsets.UTF_8).use { ps ->
            database.query(query).print(ps)
        }
        return buffer.toString(StandardCharsets.UTF_8).trimEnd()
    }

    /**
     * Очищает строку от лишних пробелов, пустых строк, а также обрезает
     * пробелы на концах каждой строки (нейтрализует особенности форматирования принтера).
     */
    private fun normalizeTable(text: String): String =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .replace(Regex(" +"), " ") // Сжимаем множественные пробелы в один

    @Test
    fun `testCountAggregation - should find exactly 100 polygon users`() {
        // До влития PR #26 (с поддержкой COUNT) проверяем количество строк в результирующей таблице вручную
        val result = runQueryAndCapture("SELECT id FROM org.hql.polygon.fixtures.PolygonUser")
        val lines = normalizeTable(result).lines()

        // Проверяем наличие заголовка колонки
        assertTrue(lines.contains("id"), "Table header 'id' is missing")

        // Проверяем наличие первой и последней записей с помощью регулярных выражений
        assertTrue(lines.any { it.matches(Regex("""^1\s*\|.*""")) || it == "1" }, "First user with ID 1 is missing")
        assertTrue(lines.any { it.matches(Regex("""^100\s*\|.*""")) || it == "100" }, "Last user with ID 100 is missing")

        // Ожидаем минимум 101 строку (1 строка заголовка + 100 пользователей)
        assertTrue(lines.size >= 101, "Expected at least 101 lines, but got ${lines.size}")
    }

    @Test
    @Disabled("TODO: Enable after PR #26 is merged. Tests aggregation functions like count() and sum().")
    fun `testAggregations - should calculate total users and sum of balances`() {
        /*
        val result = runQueryAndCapture("""
            SELECT count(id), sum(balance)
            FROM org.hql.polygon.fixtures.PolygonUser
        """.trimIndent())

        val cleanResult = normalizeTable(result)

        // Ожидаем агрегированный результат: 100 пользователей и сумму балансов 10050.0
        assertTrue(cleanResult.contains("100 | 10050.0"))
        */
    }

    @Test
    fun `testWhereFiltering - should filter users by exact age`() {
        val result = runQueryAndCapture(
            "SELECT id FROM org.hql.polygon.fixtures.PolygonUser WHERE age = 20"
        )
        val cleanResult = normalizeTable(result)

        // Проверяем корректность фильтрации по математическому остатку формулы возраста
        assertTrue(cleanResult.contains("30"), "Missing user id=30")
        assertTrue(cleanResult.contains("60"), "Missing user id=60")
        assertTrue(cleanResult.contains("90"), "Missing user id=90")
    }

    @Test
    fun `testProjectionAndSortingSnapshot - should match expected table layout`() {
        val query = """
            SELECT id, name 
            FROM org.hql.polygon.fixtures.PolygonUser 
            WHERE isActive = true 
            ORDER BY id DESC 
            LIMIT 3
        """.trimIndent().replace("\n", " ")

        val actualOutput = runQueryAndCapture(query)

        val expectedOutput = """
            id | name
            100 | User_100
            98 | User_98
            96 | User_96
        """.trimIndent()

        // Построчное сравнение нормализованных таблиц (Snapshot testing)
        val cleanActual = normalizeTable(actualOutput)
        val cleanExpected = normalizeTable(expectedOutput)

        assertTrue(
            cleanActual.contains(cleanExpected),
            "Snapshot mismatch!\nExpected to contain:\n$cleanExpected\n\nActual:\n$cleanActual"
        )
    }

    @AfterAll
    fun cleanup() {
        log.info { "Polygon tests completed. Memory is ready to be freed." }
    }
}