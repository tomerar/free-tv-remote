package io.github.tomerar.freetvremote.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.tomerar.freetvremote.FreeTvRemoteApp
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Quick Settings tile that toggles the power of the last used TV. */
class TvPowerTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            subtitleCompat(null)
            updateTile()
        }
    }

    override fun onClick() {
        val container = (applicationContext as FreeTvRemoteApp).container
        container.appScope.launch {
            val ok = container.remoteController.sendQuickKey(KeyCodes.POWER)
            qsTile?.apply {
                state = if (ok) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                subtitleCompat(getString(if (ok) R.string.tile_subtitle_sent else R.string.tile_subtitle_failed))
                updateTile()
            }
            delay(RESET_DELAY_MS)
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                subtitleCompat(null)
                updateTile()
            }
        }
    }

    private fun Tile.subtitleCompat(text: CharSequence?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = text
    }

    private companion object {
        const val RESET_DELAY_MS = 2_000L
    }
}
