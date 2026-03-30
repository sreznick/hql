package org.hql.hprof

import org.hql.hprof.reader.HprofReader
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import com.charleskorn.kaml.Yaml
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

const val EXPECTED_HPROF_COUNT = 1

private fun Path.withExtension(ext: String): Path {
    return parent / (this.nameWithoutExtension + ext)
}

@Serializable
data class HprofExpectations (
    val stringsCount: Int
)

class HprofTest {
    private val repoRoot: Path

    init {

        println(Yaml.default.encodeToString(
            HprofExpectations.serializer(),
            HprofExpectations(stringsCount = 1234)
        ))

        val classLoader = this.javaClass.getClassLoader()
        val file = File(classLoader.getResource(".")?.file
            ?: throw java.lang.IllegalStateException("unable to bet resource"))
        val p = file.absoluteFile.toPath()

        repoRoot = generateSequence(p) {
            it.parent
        }.firstOrNull {
            (it / ".git").isDirectory() &&
                    (it / "examples" / "dumps").isDirectory()
        } ?: throw IllegalStateException("unable to find repo root")
    }

    private val allHProfFiles: Sequence<Path> = (repoRoot  / "examples" / "dumps").walk().filter {
        it.toFile().absolutePath.endsWith(".hprof")
    }

    @Test
    fun testCommon() {
        allHProfFiles.forEach { hprofPath ->
            val yamlPath = hprofPath.withExtension(".yaml")
            val yamlData = yamlPath.readText()
            println("data: " + yamlData)

            val expectations = Yaml.default.decodeFromString<HprofExpectations>(
                HprofExpectations.serializer(),
                yamlData
            )

            hprofPath.inputStream().use {
                val reader = HprofReader(it)
                val hprof = reader.getHprof()
                assertEquals(expectations.stringsCount, hprof.getAllStrings().size)
            }
        }
    }

    @Test
    fun testHProfCount() {
        assertEquals(EXPECTED_HPROF_COUNT, allHProfFiles.count())
    }
}

