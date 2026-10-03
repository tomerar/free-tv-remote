package io.github.tomerar.freetvremote.remote

import io.github.tomerar.freetvremote.data.AppShortcut
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutLauncherTest {
    private val netflix = AppShortcut("netflix", "Netflix", "https://www.netflix.com/title")
    private val plex = AppShortcut("plex", "Plex", "plex://")

    @Test
    fun `a delivered shortcut reports nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val sent = mutableListOf<String>()
            val launcher =
                ShortcutLauncher(this, { link ->
                    sent += link
                    true
                })
            val reports = mutableListOf<String>()
            val collector = launch { launcher.notSent.toList(reports) }
            launcher.launch(netflix)
            assertEquals(listOf("https://www.netflix.com/title"), sent)
            assertEquals(emptyList<String>(), reports)
            collector.cancel()
        }

    @Test
    fun `a shortcut that could not be sent is reported by name`() =
        runTest(UnconfinedTestDispatcher()) {
            val launcher = ShortcutLauncher(this, { false })
            val reports = mutableListOf<String>()
            val collector = launch { launcher.notSent.toList(reports) }
            launcher.launch(netflix)
            launcher.launch(plex)
            assertEquals(listOf("Netflix", "Plex"), reports)
            collector.cancel()
        }
}
