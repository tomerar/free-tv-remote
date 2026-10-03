package io.github.tomerar.freetvremote

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.tomerar.freetvremote.diagnostics.DeviceInfo

class FreeTvRemoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.crashReporter.installAsDefaultHandler()
        container.eventLog.log("App", "started: ${DeviceInfo.header()}")
        container.remoteController.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.remoteController.onAppForeground()

                override fun onStop(owner: LifecycleOwner) = container.remoteController.onAppBackground()
            },
        )
    }
}
