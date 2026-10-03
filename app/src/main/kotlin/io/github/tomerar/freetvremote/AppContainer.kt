package io.github.tomerar.freetvremote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.tomerar.freetvremote.data.SettingsRepository
import io.github.tomerar.freetvremote.data.ShortcutsRepository
import io.github.tomerar.freetvremote.data.TvRepository
import io.github.tomerar.freetvremote.diagnostics.EventLog
import io.github.tomerar.freetvremote.discovery.NsdTvDiscovery
import io.github.tomerar.freetvremote.discovery.TvDiscovery
import io.github.tomerar.freetvremote.protocol.tls.IdentityStore
import io.github.tomerar.freetvremote.remote.DefaultSessionFactory
import io.github.tomerar.freetvremote.remote.IdentityProvider
import io.github.tomerar.freetvremote.remote.RemoteController
import io.github.tomerar.freetvremote.remote.StoredIdentityProvider
import io.github.tomerar.freetvremote.security.KeystoreKeyProtector
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "free_tv_remote")

/** Hand-rolled dependency container; everything lives as long as the process. */
class AppContainer(
    context: Context,
    val eventLog: EventLog = EventLog(File(context.applicationContext.noBackupFilesDir, "logs")),
    val crashReporter: CrashReporter = CrashReporter(eventLog),
) {
    private val appContext = context.applicationContext

    /** Process-wide scope: the remote session must outlive any Activity or Composable. */
    val appScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                // An unexpected error in a background job must not take the whole app down; it is recorded instead.
                CoroutineExceptionHandler { _, error -> runCatching { crashReporter.record("app-scope", error, fatal = false) } },
        )

    private val dataStore = appContext.appDataStore

    val tvRepository = TvRepository(dataStore)
    val settingsRepository = SettingsRepository(dataStore)
    val shortcutsRepository = ShortcutsRepository(dataStore)

    val identityProvider: IdentityProvider =
        StoredIdentityProvider(
            IdentityStore(File(appContext.noBackupFilesDir, "client_identity.bin"), KeystoreKeyProtector()),
        )

    val discovery: TvDiscovery = NsdTvDiscovery(appContext)

    val volumeKeys = VolumeKeyRouter()

    val remoteController =
        RemoteController(
            appScope,
            tvRepository,
            DefaultSessionFactory(identityProvider, log = { eventLog.log("Connection", it) }),
            log = { eventLog.log("Connection", it) },
        )
}
