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

    private val procedureIds get() = ids(testing, Regex("""^\| ([A-P]\d+) \|""", RegexOption.MULTILINE))
    private val templateIds get() =
        ids(
            template,
            Regex("""^\| ([A-P]\d+) \| (not tested|pass|fail|not supported) \|""", RegexOption.MULTILINE),
        )

    @Test
    fun `every procedure item has a result row and vice versa`() {
        assertTrue("no checklist items found", procedureIds.size > 50)
        assertEquals(procedureIds.sorted(), templateIds.sorted())
        assertEquals("duplicate ids in TESTING.md", procedureIds.size, procedureIds.toSet().size)
    }

    @Test
    fun `the template ships with every result set to not tested`() {
        val rows = Regex("""^\| [A-P]\d+ \| ([^|]+) \|""", RegexOption.MULTILINE).findAll(template).map { it.groupValues[1].trim() }
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
    fun `the docs claim exactly what was verified and not more`() {
        // Sentences wrap across lines (and blockquote markers) in Markdown; compare on flattened text.
        fun flat(file: String) = File(root, file).readText().replace(Regex("""\s*\n(>\s*)?"""), " ")
        val readme = flat("README.md")
        val compatibility = flat("docs/COMPATIBILITY.md")
        // The one real check so far is informal, on one TCL Google TV; the full checklist is not done.
        assertTrue(readme.contains("TCL Google TV"))
        assertTrue(readme.contains("has not been completed", ignoreCase = true))
        assertTrue(compatibility.contains("has **not been completed**", ignoreCase = true))
        // LG and Samsung must be stated as unsupported, never as working or planned as a promise.
        assertTrue(readme.contains("Not supported"))
        assertTrue(compatibility.contains("not promised", ignoreCase = true))
        // Result files may only exist together with an updated claim: today only the template and the index exist.
        val results = File(root, "docs/hardware-results").listFiles().orEmpty().map { it.name }
        assertEquals(setOf("README.md", "TEMPLATE.md"), results.toSet())
    }

    @Test
    fun `every document linked from the README exists`() {
        val readme = File(root, "README.md").readText()
        val links = Regex("""\]\(((?:docs/|[A-Z_]+\.md)[^)#\s]*)""").findAll(readme).map { it.groupValues[1] }.toSet()
        assertTrue("no links found", links.size > 8)
        links.forEach { assertTrue("README links to a missing file: $it", File(root, it).exists()) }
    }
}
