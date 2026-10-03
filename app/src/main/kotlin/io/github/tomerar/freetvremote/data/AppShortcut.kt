package io.github.tomerar.freetvremote.data

import kotlinx.serialization.Serializable

/**
 * A button that opens an app on the TV through a deep link. Names are plain
 * text (brand names are not translated) and no brand logos are bundled.
 */
@Serializable
data class AppShortcut(
    val id: String,
    val name: String,
    val link: String,
    val builtIn: Boolean = false,
    val enabled: Boolean = true,
) {
    companion object {
        val defaults: List<AppShortcut> = listOf(
            AppShortcut("netflix", "Netflix", "https://www.netflix.com/title", builtIn = true),
            AppShortcut("youtube", "YouTube", "https://www.youtube.com", builtIn = true),
            AppShortcut("disney", "Disney+", "https://www.disneyplus.com", builtIn = true),
            AppShortcut("prime", "Prime Video", "https://app.primevideo.com", builtIn = true),
            AppShortcut("spotify", "Spotify", "spotify://", builtIn = true),
            AppShortcut("plex", "Plex", "plex://", builtIn = true),
            AppShortcut("appletv", "Apple TV", "https://tv.apple.com", builtIn = true, enabled = false),
            AppShortcut("twitch", "Twitch", "https://www.twitch.tv", builtIn = true, enabled = false),
        )

        /** Accepts `scheme://...` links with a plain scheme; anything else cannot be launched on a TV. */
        fun isValidLink(link: String): Boolean = LINK_REGEX.matches(link.trim())

        private val LINK_REGEX = Regex("^[A-Za-z][A-Za-z0-9+.-]*://\\S*$")
    }
}
