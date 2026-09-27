package org.hql.polygon

import org.hql.hprof.heap.Heap
import org.hql.hprof.reader.HprofReader
import org.hql.polygon.dumper.ExternalProcessDumper
import org.hql.query.Database
import org.hql.polygon.HeapDumpException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Полигон для тестирования HQL-движка на дампах с разными настройками JVM.
 * Проверяет, что HprofReader корректно читает структуру памяти независимо от
 * выбранного алгоритма Garbage Collector-а и ограничений по памяти.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JvmSettingsDumpTest {

    private val log = KotlinLogging.logger {}

    private lateinit var dumpsDir: Path
    private lateinit var javaBin: String
    private lateinit var classpath: String

    @BeforeAll
    fun setup() {
        dumpsDir = Path.of("build/polygon-dumps/jvm-settings").apply { createDirectories() }

        // Подготавливаем команду запуска Java
        val javaHome = System.getProperty("java.home")
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        javaBin = Path.of(javaHome, "bin", if (isWindows) "java.exe" else "java").toString()
        classpath = System.getProperty("java.class.path")
    }

    @ParameterizedTest(name = "Parsing dump with GC: {0} and Heap: {1}")
    @CsvSource(
        "-XX:+UseSerialGC, -Xmx32m",    // Старый однопоточный сборщик, жесткое ограничение памяти
        "-XX:+UseParallelGC, -Xmx64m",  // Параллельный сборщик (разбивает на поколения)
        "-XX:+UseG1GC, -Xmx128m",       // Современный дефолт (региональная модель)
        "-XX:+UseZGC, -Xmx256m"         // Ультрасовременный ZGC (специфичные указатели в памяти)
    )
    fun `testHeapParsingWithDifferentJvmSettings`(gcFlag: String, memFlag: String) {
        // Формируем уникальное имя файла для конкретных настроек JVM, очищая флаги от спецсимволов
        val cleanGcName = gcFlag.replace(Regex("[^a-zA-Z0-9]"), "")
        val hprofPath = dumpsDir.resolve("target_${cleanGcName}_$memFlag.hprof")

        var targetProcess: Process? = null

        try {
            if (!hprofPath.exists()) {
                println("Starting TargetApp with flags: $gcFlag, $memFlag")

                targetProcess = ProcessBuilder(
                    javaBin,
                    gcFlag,
                    memFlag,
                    "-cp", classpath,
                    "org.hql.polygon.scenario.ExternalTargetApp"
                ).redirectErrorStream(true).start()

                val pid = targetProcess.pid()

                // Читаем поток вывода, пока приложение не скажет READY
                val reader = targetProcess.inputStream.bufferedReader()
                val line = reader.readLine()
                if (line != "READY") {
                    throw HeapDumpException("App failed to start with flags $gcFlag. Output: $line")
                }

                log.info { "App is ready (PID: $pid). Capturing dump..." }
                ExternalProcessDumper(pid, dumpOnlyLiveObjects = false).dump(hprofPath, overwrite = true).getOrThrow()
            } else {
                log.info { "Using cached dump for flags: $gcFlag, $memFlag" }
            }

            // Главная проверка: загружаем дамп с нестандартной структурой кучи
            log.info { "Parsing dump: ${hprofPath.fileName}..." }
            val database = hprofPath.inputStream().use { stream ->
                Database(Heap(HprofReader(stream).getHprof()))
            }

            // Убеждаемся, что парсер не сошел с ума от структуры памяти
            assertDoesNotThrow {
                database.query("SELECT * FROM java.lang.String LIMIT 10")
            }

            log.info { "Successfully parsed dump for $gcFlag!" }

        } catch (e: Throwable) {
            // Принудительно гасим дочерний процесс при сбое инициализации на этапе подготовки
            if (targetProcess?.isAlive == true) {
                targetProcess.destroyForcibly()
                log.warn { "Initialization failed! External process was forcibly killed. Reason: ${e.message}" }
            }
            throw e
        } finally {
            // Успешный сценарий: убиваем наверняка
            if (targetProcess?.isAlive == true) {
                targetProcess.destroy()
            }
        }
    }

    @AfterAll
    fun teardown() {
        log.info { "All JVM settings tests completed successfully." }
    }
}