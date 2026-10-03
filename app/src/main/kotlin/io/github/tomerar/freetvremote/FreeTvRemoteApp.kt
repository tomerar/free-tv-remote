package io.github.tomerar.freetvremote

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.io.File

class FreeTvRemoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val crashReporter = CrashReporter(File(noBackupFilesDir, "last_crash.txt"), CrashReporter::deviceHeader)
        crashReporter.installAsDefaultHandler()
        container = AppContainer(this, crashReporter)
        container.remoteController.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.remoteController.onAppForeground()

                override fun onStop(owner: LifecycleOwner) = container.remoteController.onAppBackground()
            },
        )
    }
}
