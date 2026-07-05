package org.hql.polygon

import org.hql.hprof.heap.Heap
import org.hql.hprof.reader.HprofReader
import org.hql.polygon.dumper.ExternalProcessDumper
import org.hql.query.Database
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Disabled
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.streams.asSequence
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Полигон для тестирования парсера на дампах реальных популярных приложений.
 * Проверяет, что движок HQL не падает от OutOfMemoryError или StackOverflowError
 * при обходе сложных, глубоко вложенных структур памяти реальных программ.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealApplicationsDumpTest {

    private val log = KotlinLogging.logger {}

    private lateinit var dumpsDir: Path

    @BeforeAll
    fun setup() {
        dumpsDir = Path.of("build/polygon-dumps").apply { createDirectories() }
    }

    @Test
    fun `testRealApplication - Gradle Daemon memory parsing`() {
        val hprofPath = dumpsDir.resolve("gradle_daemon.hprof")

        if (!hprofPath.exists()) {
            // Собираем цепочку PIDs от текущего процесса вверх по дереву родителей
            // (Test Executor -> Gradle Worker -> Gradle Daemon), используя generateSequence
            val myAncestors = generateSequence(ProcessHandle.current()) { it.parent().orElse(null) }
                .map { it.pid() }
                .toSet()

            val gradleProcess = ProcessHandle.allProcesses()
                .asSequence()
                .filter { process ->
                    val cmd = process.info().commandLine().orElse("")
                    // Ищем GradleDaemon, но исключаем текущий процесс и всю ветку своих родителей.
                    cmd.contains("GradleDaemon") && process.pid() !in myAncestors
                }
                .firstOrNull()

            if (gradleProcess == null) {
                log.warn { "WARN: No independent Gradle Daemon found (only active build daemon running). Skipping live dump to prevent STW deadlock." }
                return
            }

            val pid = gradleProcess.pid()
            log.info { "Found independent Gradle Daemon (PID: $pid). Capturing dump (may take time)..." }
            ExternalProcessDumper(pid, dumpOnlyLiveObjects = false).dump(hprofPath, overwrite = true).getOrThrow()
        } else {
            log.info { "Using cached Gradle Daemon dump: $hprofPath" }
        }

        log.info { "Parsing Gradle Heap..." }
        val database = Database(Heap(HprofReader(hprofPath.inputStream()).getHprof()))

        assertDoesNotThrow {
            database.query("SELECT * FROM java.lang.String LIMIT 50")
        }

        assertDoesNotThrow {
            database.query("SELECT * FROM java.lang.Thread LIMIT 5")
        }

        log.info { "Gradle Daemon dump parsed successfully!" }
    }

    @Test
    fun `testRealApplication - JShell (Java REPL) memory parsing`() {
        val hprofPath = dumpsDir.resolve("jshell_repl.hprof")
        var jshellProcess: Process? = null

        try {
            if (!hprofPath.exists()) {
                // Высчитываем кроссплатформенный путь до jshell внутри установленной JDK
                val javaHome = System.getProperty("java.home")
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val jshellName = if (isWindows) "jshell.exe" else "jshell"
                val jshellBin = Path.of(javaHome, "bin", jshellName).toString()

                log.info { "Starting JShell to capture its memory..." }
                jshellProcess = ProcessBuilder(jshellBin).redirectErrorStream(true).start()

                val pid = jshellProcess.pid()
                // Даем JShell время на инициализацию классов компилятора в памяти
                Thread.sleep(3000)

                ExternalProcessDumper(pid, dumpOnlyLiveObjects = false).dump(hprofPath, overwrite = true).getOrThrow()
            } else {
                log.info { "Using cached JShell dump: $hprofPath" }
            }

            val database = Database(Heap(HprofReader(hprofPath.inputStream()).getHprof()))

            assertDoesNotThrow {
                // JShell содержит много классов компилятора. Проверяем, что парсер HQL с ними справляется.
                database.query("SELECT * FROM java.lang.Class LIMIT 20")
            }

            log.info { "JShell dump parsed successfully!" }

        } catch (e: Throwable) {
            // Принудительно гасим дочерний процесс при сбое инициализации на этапе подготовки
            if (jshellProcess?.isAlive == true) {
                jshellProcess.destroyForcibly()
                log.warn { "Initialization failed! JShell process was forcibly killed. Reason: ${e.message}" }
            }
            throw e
        } finally {
            // Успешный сценарий: убиваем наверняка
            if (jshellProcess?.isAlive == true) {
                jshellProcess.destroy()
            }
        }
    }
}