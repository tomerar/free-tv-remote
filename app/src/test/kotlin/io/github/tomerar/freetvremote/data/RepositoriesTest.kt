package io.github.tomerar.freetvremote.data

import io.github.tomerar.freetvremote.testDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositoriesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun tvRepo() = TvRepository(testDataStore(scope, tmp.root, "tv"))

    @Test
    fun `saving TVs keeps several and the last one saved is the last used`() = runBlocking {
        val repo = tvRepo()
        val a = repo.savePaired("Shield", "10.0.0.2", byteArrayOf(1), 6466, 6467)
        val b = repo.savePaired("TCL", "10.0.0.3", byteArrayOf(2), 6466, 6467)
        assertEquals(listOf(a.id, b.id), repo.tvs.first().map { it.id })
        assertEquals(b, repo.lastUsed.first())
        repo.setLastUsed(a.id)
        assertEquals(a, repo.lastUsed.first())
    }

    @Test
    fun `re-pairing the same host updates the pin and keeps id and custom name`() = runBlocking {
        val repo = tvRepo()
        val first = repo.savePaired("Shield", "10.0.0.2", byteArrayOf(1), 6466, 6467)
        repo.rename(first.id, "Living room")
        val again = repo.savePaired("Android TV", "10.0.0.2", byteArrayOf(9), 6466, 6467)
        assertEquals(first.id, again.id)
        assertEquals("Living room", again.name)
        assertEquals(1, repo.tvs.first().size)
        assertEquals(SavedTv.encodePin(byteArrayOf(9)), again.pin)
    }

    @Test
    fun `removing the last used TV falls back to another one`() = runBlocking {
        val repo = tvRepo()
        val a = repo.savePaired("A", "10.0.0.2", byteArrayOf(1), 6466, 6467)
        val b = repo.savePaired("B", "10.0.0.3", byteArrayOf(2), 6466, 6467)
        repo.remove(b.id)
        assertEquals(a, repo.lastUsed.first())
        repo.remove(a.id)
        assertNull(repo.lastUsed.first())
    }

    @Test
    fun `rename ignores blanks and host updates are stored`() = runBlocking {
        val repo = tvRepo()
        val a = repo.savePaired("A", "10.0.0.2", byteArrayOf(1), 6466, 6467)
        repo.rename(a.id, "   ")
        assertEquals("A", repo.get(a.id)?.name)
        repo.updateHost(a.id, "10.0.0.50")
        assertEquals("10.0.0.50", repo.get(a.id)?.host)
    }

    @Test
    fun `unknown ids are ignored`() = runBlocking {
        val repo = tvRepo()
        val a = repo.savePaired("A", "10.0.0.2", byteArrayOf(1), 6466, 6467)
        repo.setLastUsed("nope")
        assertEquals(a.id, repo.lastUsedId.first())
    }

    @Test
    fun `settings have sensible defaults and persist changes`() = runBlocking {
        val repo = SettingsRepository(testDataStore(scope, tmp.root, "settings"))
        assertEquals(AppSettings(), repo.settings.first())
        repo.setHaptics(false)
        repo.setKeepScreenOn(true)
        repo.setTheme(ThemeMode.LIGHT)
        repo.setUseVolumeKeys(false)
        assertEquals(AppSettings(hapticsEnabled = false, keepScreenOn = true, theme = ThemeMode.LIGHT, useVolumeKeys = false), repo.settings.first())
    }

    @Test
    fun `shortcuts start with the built-in defaults and only enabled ones are shown`() = runBlocking {
        val repo = ShortcutsRepository(testDataStore(scope, tmp.root, "shortcuts"))
        val names = repo.enabled.first().map { it.name }
        assertTrue("Netflix" in names && "YouTube" in names && "Disney+" in names && "Prime Video" in names)
        assertFalse("Twitch" in names)
        assertTrue(repo.all.first().size > names.size)
    }

    @Test
    fun `custom shortcuts are validated, added, ordered and removed`() = runBlocking {
        val repo = ShortcutsRepository(testDataStore(scope, tmp.root, "shortcuts"))
        assertNull(repo.addCustom("", "https://example.com"))
        assertNull(repo.addCustom("Bad", "not a link"))
        val custom = repo.addCustom("  Jellyfin ", "jellyfin://open")
        assertNotNull(custom)
        assertEquals("Jellyfin", repo.all.first().last().name)

        repo.move(custom!!.id, -1)
        assertEquals(custom.id, repo.all.first()[repo.all.first().size - 2].id)

        repo.remove(custom.id)
        assertTrue(repo.all.first().none { it.id == custom.id })
    }

    @Test
    fun `built-in shortcuts can be disabled but not deleted`() = runBlocking {
        val repo = ShortcutsRepository(testDataStore(scope, tmp.root, "shortcuts"))
        repo.remove("netflix")
        assertTrue(repo.all.first().any { it.id == "netflix" })
        repo.setEnabled("netflix", false)
        assertTrue(repo.enabled.first().none { it.id == "netflix" })
        repo.setEnabled("twitch", true)
        assertTrue(repo.enabled.first().any { it.id == "twitch" })
    }

    @Test
    fun `link validation accepts deep links and rejects text`() {
        assertTrue(AppShortcut.isValidLink("https://www.netflix.com/title"))
        assertTrue(AppShortcut.isValidLink("spotify://"))
        assertTrue(AppShortcut.isValidLink(" plex://foo "))
        assertFalse(AppShortcut.isValidLink("netflix"))
        assertFalse(AppShortcut.isValidLink("https:// spaces"))
        assertFalse(AppShortcut.isValidLink(""))
    }
}
