package io.github.tomerar.freetvremote.ui

import io.github.tomerar.freetvremote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvAppNamesTest {
    @Test
    fun `known launcher and streaming packages get a readable name`() {
        assertEquals(R.string.tv_app_home, friendlyAppName("com.google.android.apps.tv.launcherx"))
        assertEquals(R.string.tv_app_netflix, friendlyAppName("com.netflix.ninja"))
        assertEquals(R.string.tv_app_youtube, friendlyAppName("com.google.android.youtube.tv"))
    }

    @Test
    fun `an unknown package is not shown to the user`() {
        assertNull(friendlyAppName("com.vendor.secret.service"))
    }
}
