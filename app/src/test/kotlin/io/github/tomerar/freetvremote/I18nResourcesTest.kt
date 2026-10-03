package io.github.tomerar.freetvremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the English / Hebrew string files against drifting apart (keys and format placeholders). */
class I18nResourcesTest {
    private val res = File("src/main/res")

    private fun strings(dir: String): Map<String, String> {
        val text = File(res, "$dir/strings.xml").readText()
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private val english get() = strings("values")
    private val hebrew get() = strings("values-iw")

    private fun placeholders(value: String) =
        Regex("""%\d+\$[sd]""")
            .findAll(value)
            .map { it.value }
            .sorted()
            .toList()

    @Test
    fun `hebrew defines exactly the same keys as english`() {
        assertEquals(english.keys.sorted(), hebrew.keys.sorted())
    }

    @Test
    fun `format placeholders match in every translation`() {
        for ((key, value) in english) {
            assertEquals("placeholders of $key", placeholders(value), placeholders(hebrew.getValue(key)))
        }
    }

    @Test
    fun `no translation is blank and the hebrew folder uses the legacy iw code`() {
        assertTrue(hebrew.values.none { it.isBlank() })
        assertTrue(File(res, "values-iw").isDirectory)
        assertTrue("values-he would be ignored by Android", !File(res, "values-he").exists())
    }
}
