package io.github.tomerar.freetvremote

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.tomerar.freetvremote.diagnostics.DeviceInfo
import kotlinx.coroutines.launch

class FreeTvRemoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.crashReporter.installAsDefaultHandler()
        container.eventLog.log("App", "started: ${DeviceInfo.header()}")
        container.remoteController.start()
        // A timer left by a killed process or an update: put its alarm and notification back, or report it as missed.
        container.appScope.launch { container.sleepTimer.reconcile() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.remoteController.onAppForeground()

                override fun onStop(owner: LifecycleOwner) = container.remoteController.onAppBackground()
            },
        )
    }
}
