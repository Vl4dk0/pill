package sk.vladko.pill.rules

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PureTest {
    /** The rules run in plain JVM tests only while they use no Android class. */
    @Test fun rulesPackageImportsNoAndroid() {
        val dir = File("src/main/java/sk/vladko/pill/rules")
        val files = dir.listFiles { f -> f.extension == "kt" }.orEmpty()
        assertEquals(true, files.isNotEmpty(), "no rules files under ${dir.absolutePath}")
        val bad = files.filter { f -> f.readLines().any { it.startsWith("import android.") || it.startsWith("import androidx.") } }
        assertEquals(emptyList(), bad.map { it.name })
    }
}
