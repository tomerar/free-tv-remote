package io.github.tomerar.freetvremote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import io.github.tomerar.freetvremote.data.AppSettings
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.ui.AppNav
import io.github.tomerar.freetvremote.ui.LocalAppContainer
import io.github.tomerar.freetvremote.ui.theme.FreeTvRemoteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as FreeTvRemoteApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
            // Decide the start destination once, from storage, so rotation never re-triggers onboarding.
            val hasTvs by produceState<Boolean?>(initialValue = null) {
                value =
                    container.tvRepository.tvs
                        .first()
                        .isNotEmpty()
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                FreeTvRemoteTheme(settings.theme) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        hasTvs?.let { AppNav(startOnboarding = !it) }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val tvKey =
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> KeyCodes.VOLUME_UP
                KeyEvent.KEYCODE_VOLUME_DOWN -> KeyCodes.VOLUME_DOWN
                else -> null
            }
        if (tvKey != null && container.volumeKeys.active) {
            // Key auto-repeat delivers repeated onKeyDown calls: holding the button repeats on the TV too.
            lifecycleScope.launch { container.remoteController.pressKey(tvKey) }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val isVolume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        return if (isVolume && container.volumeKeys.active) true else super.onKeyUp(keyCode, event)
    }
}
