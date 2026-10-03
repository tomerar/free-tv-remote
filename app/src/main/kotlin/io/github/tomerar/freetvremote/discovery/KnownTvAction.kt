package io.github.tomerar.freetvremote.discovery

import io.github.tomerar.freetvremote.data.SavedTv

/** What to do about a TV the search found that matches a saved TV. */
sealed interface KnownTvAction {
    val saved: SavedTv
    val found: DiscoveredTv

    /** Same address: only remember the network name so the TV can be recognised later. */
    data class RememberName(
        override val saved: SavedTv,
        override val found: DiscoveredTv,
    ) : KnownTvAction

    /** A different address: after confirming it is the same TV, point the saved entry at the new address. */
    data class MoveAddress(
        override val saved: SavedTv,
        override val found: DiscoveredTv,
    ) : KnownTvAction
}

/**
 * Matches search results to saved TVs by the name the TV announces on the network. A saved TV is matched by its
 * recorded [SavedTv.serviceName], or, for TVs saved before that was recorded, by its original name (which was the
 * announced name unless the user renamed it). A name that matches more than one saved TV, or a TV found more than
 * once, is ambiguous and ignored: guessing could point a saved TV at the wrong device.
 */
fun matchKnownTvs(saved: List<SavedTv>, found: List<DiscoveredTv>): List<KnownTvAction> =
    found
        .groupBy { it.name }
        .filterValues { it.size == 1 }
        .mapNotNull { (name, devices) ->
            val device = devices.single()
            val candidates = saved.filter { it.serviceName == name || (it.serviceName == null && it.name == name) }
            val tv = candidates.singleOrNull() ?: return@mapNotNull null
            when {
                tv.host != device.host -> KnownTvAction.MoveAddress(tv, device)
                tv.serviceName != name -> KnownTvAction.RememberName(tv, device)
                else -> null
            }
        }
