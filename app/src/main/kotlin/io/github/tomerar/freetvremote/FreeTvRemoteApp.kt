package io.github.tomerar.freetvremote

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class FreeTvRemoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.remoteController.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.remoteController.onAppForeground()

                override fun onStop(owner: LifecycleOwner) = container.remoteController.onAppBackground()
            },
        )
    }
}
