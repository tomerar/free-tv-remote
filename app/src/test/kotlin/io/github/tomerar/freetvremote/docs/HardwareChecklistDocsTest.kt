package io.github.tomerar.freetvremote.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Keeps the hardware procedure, the results template and the "nothing verified yet" claim honest. */
class HardwareChecklistDocsTest {
    private val root = File("..")
    private val testing = File(root, "TESTING.md").readText()
    private val template = File(root, "docs/hardware-results/TEMPLATE.md").readText()

    private fun ids(text: String, table: Regex) = table.findAll(text).map { it.groupValues[1] }.toList()

    private val procedureIds get() = ids(testing, Regex("""^\| ([A-O]\d+) \|""", RegexOption.MULTILINE))
    private val templateIds get() =
        ids(
            template,
            Regex("""^\| ([A-O]\d+) \| (not tested|pass|fail|not supported) \|""", RegexOption.MULTILINE),
        )

    @Test
    fun `every procedure item has a result row and vice versa`() {
        assertTrue("no checklist items found", procedureIds.size > 50)
        assertEquals(procedureIds.sorted(), templateIds.sorted())
        assertEquals("duplicate ids in TESTING.md", procedureIds.size, procedureIds.toSet().size)
    }

    @Test
    fun `the template ships with every result set to not tested`() {
        val rows = Regex("""^\| [A-O]\d+ \| ([^|]+) \|""", RegexOption.MULTILINE).findAll(template).map { it.groupValues[1].trim() }
        assertTrue(rows.all { it == "not tested" })
    }

    @Test
    fun `the template asks for the exact build and device details`() {
        listOf(
            "Device exact model",
            "Android TV Remote Service version",
            "Phone Android version",
            "App version",
            "Tested commit",
        ).forEach { assertTrue("template misses: $it", template.contains(it)) }
    }

    @Test
    fun `required scenarios are covered by the procedure`() {
        listOf("Hebrew", "emoji", "Wi-Fi", "standby", "widget", "rotate", "certificate", "Quick Settings tile", "phone volume").forEach {
            assertTrue("TESTING.md does not cover: $it", testing.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun `the docs do not claim any physical device was verified`() {
        val readme = File(root, "README.md").readText()
        assertTrue(readme.contains("no physical-device result has been", ignoreCase = true))
        val results = File(root, "docs/hardware-results").listFiles().orEmpty().map { it.name }
        // Only the template and the index may exist until real evidence is added by a person who ran the tests.
        assertFalse(results.any { it !in setOf("README.md", "TEMPLATE.md") } && readme.contains("no physical-device result has been"))
    }
}
