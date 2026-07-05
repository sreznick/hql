package org.hql.polygon.dumper

import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.absolutePathString
import com.sun.management.HotSpotDiagnosticMXBean
import org.hql.polygon.HeapDumpException

/**
 * Общий интерфейс для создания дампов кучи (heap dump) в формате .hprof.
 */
interface HeapDumper {
    /**
     * Создает дамп памяти и сохраняет его по указанному пути.
     * Если файл уже существует и флаг [overwrite] равен false, повторная генерация пропускается.
     */
    fun dump(targetPath: Path, overwrite: Boolean = false): Result<Path>
}

abstract class AbstractHeapDumper : HeapDumper {
    override fun dump(targetPath: Path, overwrite: Boolean): Result<Path> = runCatching {
        try {
            if (targetPath.exists() && !overwrite) {
                return@runCatching targetPath
            }
            if (targetPath.exists()) {
                targetPath.toFile().delete()
            }
            executeDump(targetPath)
            targetPath
        } catch (e: Throwable) {
            if (e is HeapDumpException) throw e
            throw HeapDumpException("Failed to generate heap dump at $targetPath", e)
        }
    }

    protected abstract fun executeDump(targetPath: Path)
}

/**
 * Реализация дампера для снятия снимка памяти внутри текущего процесса JVM через JMX API.
 */
class JmxHeapDumper(
    private val dumpOnlyLiveObjects: Boolean = true
) : AbstractHeapDumper() {

    override fun executeDump(targetPath: Path) {
        // Получаем доступ к диагностическому MBean для работы с HotSpot JVM
        val server = ManagementFactory.getPlatformMBeanServer()
        val mxBean = ManagementFactory.newPlatformMXBeanProxy(
            server,
            "com.sun.management:type=HotSpotDiagnostic",
            HotSpotDiagnosticMXBean::class.java
        )

        mxBean.dumpHeap(targetPath.absolutePathString(), dumpOnlyLiveObjects)
    }
}

/**
 * Реализация дампера для снятия снимка памяти внешнего JVM-процесса по его PID.
 * Использует системную утилиту jcmd.
 */
class ExternalProcessDumper(
    private val pid: Long,
    private val dumpOnlyLiveObjects: Boolean = true
) : AbstractHeapDumper() {

    override fun executeDump(targetPath: Path) {
        // Запуск системного процесса jcmd для генерации дампа целевой JVM
        val process = ProcessBuilder(
            "jcmd",
            pid.toString(),
            "GC.heap_dump",
            if (dumpOnlyLiveObjects) "-all=false" else "-all=true",
            targetPath.absolutePathString()
        ).start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            // Читаем поток ошибок и закрываем стрим через .use
            val errorMsg = process.errorStream.bufferedReader().readText()
            throw HeapDumpException("jcmd failed with exit code $exitCode: $errorMsg")
        }
    }
}