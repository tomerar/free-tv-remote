package io.github.tomerar.freetvremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** mDNS discovery of `_androidtvremote2._tcp` through the platform [NsdManager] (no extra dependencies). */
class NsdTvDiscovery(
    context: Context,
) : TvDiscovery {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    override fun discover(): Flow<List<DiscoveredTv>> =
        callbackFlow {
            // One book per scan: results of an earlier scan can never leak into this one.
            val book = ServiceBook { trySend(it) }
            val resolver =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ModernResolver(nsd, ContextCompat.getMainExecutor(appContext), book)
                } else {
                    LegacyResolver(nsd, book)
                }

            val listener =
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        resolver.resolve(serviceInfo)
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        resolver.lost(serviceInfo)
                    }

                    override fun onDiscoveryStopped(serviceType: String) = Unit

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        close(DiscoveryException("start failed: $errorCode"))
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                }

            trySend(emptyList())
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            awaitClose {
                runCatching { nsd.stopServiceDiscovery(listener) }
                resolver.close()
            }
        }

    class DiscoveryException(
        message: String,
    ) : Exception(message)

    private interface Resolver {
        fun resolve(info: NsdServiceInfo)

        fun lost(info: NsdServiceInfo)

        fun close()
    }

    /** API 34+: a per-service callback that stays registered and reports address changes. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private class ModernResolver(
        private val nsd: NsdManager,
        private val executor: Executor,
        private val book: ServiceBook,
    ) : Resolver {
        private val callbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()

        override fun resolve(info: NsdServiceInfo) {
            val name = info.serviceName
            if (callbacks.containsKey(name)) return
            val token = book.found(name)
            val callback =
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        callbacks.remove(name, this)
                    }

                    // A callback that was unregistered or replaced can still deliver; the token rejects it.
                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        val host = hostOf(serviceInfo) ?: return
                        book.resolved(name, token, DiscoveredTv(name, host, serviceInfo.port))
                    }

                    override fun onServiceLost() {
                        book.lostIfCurrent(name, token)
                    }

                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
            callbacks[name] = callback
            nsd.registerServiceInfoCallback(info, executor, callback)
        }

        override fun lost(info: NsdServiceInfo) {
            book.lost(info.serviceName)
            callbacks.remove(info.serviceName)?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }

        override fun close() {
            book.close()
            callbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
            callbacks.clear()
        }
    }

    /** API 26-33: see [LegacyResolveQueue]. */
    @Suppress("DEPRECATION")
    private class LegacyResolver(
        nsd: NsdManager,
        book: ServiceBook,
    ) : Resolver {
        private val queue =
            LegacyResolveQueue<NsdServiceInfo>(book) { info, callback ->
                nsd.resolveService(
                    info,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = callback.onFailed()

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = hostOf(serviceInfo)
                            if (host == null) {
                                callback.onFailed()
                            } else {
                                callback.onResolved(DiscoveredTv(serviceInfo.serviceName, host, serviceInfo.port))
                            }
                        }
                    },
                )
            }

        override fun resolve(info: NsdServiceInfo) = queue.enqueue(info.serviceName, info)

        override fun lost(info: NsdServiceInfo) = queue.lost(info.serviceName)

        override fun close() = queue.close()
    }

    companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp."

        @Suppress("DEPRECATION")
        private fun hostOf(info: NsdServiceInfo): String? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                info.hostAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress
                    ?: info.hostAddresses.firstOrNull()?.hostAddress
            } else {
                info.host?.hostAddress
            }
    }
}
