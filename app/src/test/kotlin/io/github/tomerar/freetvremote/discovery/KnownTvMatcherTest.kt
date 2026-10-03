package io.github.tomerar.freetvremote.discovery

import io.github.tomerar.freetvremote.data.SavedTv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownTvMatcherTest {
    private fun saved(id: String, name: String, host: String, service: String? = null) =
        SavedTv(id = id, name = name, host = host, pin = "AA==", serviceName = service)

    private fun found(name: String, host: String) = DiscoveredTv(name, host, 6466)

    @Test
    fun `a saved TV found at a new address is moved`() {
        val actions = matchKnownTvs(listOf(saved("1", "Living room", "10.0.0.2", "shield")), listOf(found("shield", "10.0.0.50")))
        assertEquals(1, actions.size)
        assertTrue(actions.single() is KnownTvAction.MoveAddress)
        assertEquals("10.0.0.50", actions.single().found.host)
    }

    @Test
    fun `a saved TV found at the same address only remembers its network name`() {
        val actions = matchKnownTvs(listOf(saved("1", "shield", "10.0.0.2")), listOf(found("shield", "10.0.0.2")))
        assertTrue(actions.single() is KnownTvAction.RememberName)
    }

    @Test
    fun `nothing to do when address and name are already recorded`() {
        assertTrue(matchKnownTvs(listOf(saved("1", "x", "10.0.0.2", "shield")), listOf(found("shield", "10.0.0.2"))).isEmpty())
    }

    @Test
    fun `an old entry is matched by its original name, a renamed old entry is not`() {
        val old = saved("1", "shield", "10.0.0.2")
        assertEquals(1, matchKnownTvs(listOf(old), listOf(found("shield", "10.0.0.9"))).size)
        val renamed = saved("2", "Living room", "10.0.0.2")
        assertTrue(matchKnownTvs(listOf(renamed), listOf(found("shield", "10.0.0.9"))).isEmpty())
    }

    @Test
    fun `an entry with a recorded network name is not matched by its display name`() {
        val tv = saved("1", "shield", "10.0.0.2", service = "something else")
        assertTrue(matchKnownTvs(listOf(tv), listOf(found("shield", "10.0.0.9"))).isEmpty())
    }

    @Test
    fun `a name shared by two saved TVs is ambiguous and ignored`() {
        val tvs = listOf(saved("1", "Android TV", "10.0.0.2"), saved("2", "Android TV", "10.0.0.3"))
        assertTrue(matchKnownTvs(tvs, listOf(found("Android TV", "10.0.0.9"))).isEmpty())
    }

    @Test
    fun `a name announced twice in one search is ambiguous and ignored`() {
        val tvs = listOf(saved("1", "Android TV", "10.0.0.2", "Android TV"))
        assertTrue(matchKnownTvs(tvs, listOf(found("Android TV", "10.0.0.8"), found("Android TV", "10.0.0.9"))).isEmpty())
    }

    @Test
    fun `unknown TVs are ignored`() {
        assertTrue(matchKnownTvs(listOf(saved("1", "a", "10.0.0.2", "a")), listOf(found("b", "10.0.0.3"))).isEmpty())
    }
}
