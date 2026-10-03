package io.github.tomerar.freetvremote.discovery

import kotlinx.coroutines.flow.Flow

data class DiscoveredTv(
    val name: String,
    val host: String,
    val port: Int,
)

/** Finds Android TV / Google TV devices on the local network. Collecting starts discovery, cancelling stops it. */
fun interface TvDiscovery {
    fun discover(): Flow<List<DiscoveredTv>>
}
