package io.github.tomerar.freetvremote.ui

import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.protocol.remote.TvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusFormatTest {
    @Test
    fun `the name the TV sends wins over our own list`() {
        val tv = TvState(currentApp = "com.netflix.ninja", currentAppLabel = "Netflix (TV)")
        assertEquals(AppName.Label("Netflix (TV)"), appNameOf(tv))
    }

    @Test
    fun `without a label a known package gets its name, an unknown one stays a package id`() {
        assertEquals(AppName.Known(R.string.tv_app_netflix), appNameOf(TvState(currentApp = "com.netflix.ninja")))
        assertEquals(AppName.Package("com.vendor.x"), appNameOf(TvState(currentApp = "com.vendor.x")))
    }

    @Test
    fun `no app reported gives no name and a blank label is ignored`() {
        assertNull(appNameOf(TvState()))
        assertEquals(
            AppName.Known(R.string.tv_app_home),
            appNameOf(TvState(currentApp = "com.google.android.tvlauncher", currentAppLabel = " ")),
        )
    }

    @Test
    fun `model name combines vendor and model without repeating the vendor`() {
        assertEquals("TCL 65C735", tvModelName(TvState(deviceVendor = "TCL", deviceModel = "65C735")))
        assertEquals("TCL 65C735", tvModelName(TvState(deviceVendor = "TCL", deviceModel = "TCL 65C735")))
        assertEquals("TCL", tvModelName(TvState(deviceVendor = "TCL")))
        assertEquals("65C735", tvModelName(TvState(deviceModel = "65C735")))
        assertNull(tvModelName(TvState()))
    }

    @Test
    fun `volume fraction needs both values and stays within 0 to 1`() {
        assertEquals(0.13f, volumeFraction(TvState(volumeLevel = 13, volumeMax = 100))!!, 0.0001f)
        assertEquals(1f, volumeFraction(TvState(volumeLevel = 130, volumeMax = 100))!!, 0.0001f)
        assertNull(volumeFraction(TvState(volumeLevel = 13)))
        assertNull(volumeFraction(TvState(volumeLevel = 13, volumeMax = 0)))
    }

    @Test
    fun `connected minutes are whole minutes and never negative`() {
        assertEquals(0, connectedMinutes(1_000, 59_000))
        assertEquals(1, connectedMinutes(0, 60_000))
        assertEquals(12, connectedMinutes(0, 12 * 60_000 + 59_000))
        assertEquals(0, connectedMinutes(10_000, 1_000))
    }
}
