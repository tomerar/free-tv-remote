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
            val found = ConcurrentHashMap<String, DiscoveredTv>()

            fun publish() {
                trySend(found.values.sortedBy { it.name.lowercase() })
            }

            val resolver =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ModernResolver(nsd, ContextCompat.getMainExecutor(appContext), found, ::publish)
                } else {
                    LegacyResolver(nsd, found, ::publish)
                }

            val listener =
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        resolver.resolve(serviceInfo)
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        resolver.lost(serviceInfo)
                        found.remove(serviceInfo.serviceName)
                        publish()
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
        private val found: MutableMap<String, DiscoveredTv>,
        private val publish: () -> Unit,
    ) : Resolver {
        private val callbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()

        override fun resolve(info: NsdServiceInfo) {
            if (callbacks.containsKey(info.serviceName)) return
            val callback =
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        callbacks.remove(info.serviceName)
                    }

                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        record(serviceInfo, found, publish)
                    }

                    override fun onServiceLost() {
                        found.remove(info.serviceName)
                        publish()
                    }

                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
            callbacks[info.serviceName] = callback
            nsd.registerServiceInfoCallback(info, executor, callback)
        }

        override fun lost(info: NsdServiceInfo) {
            callbacks.remove(info.serviceName)?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }

        override fun close() {
            callbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
            callbacks.clear()
        }
    }

    /** API 26-33: `resolveService` allows only one resolution at a time, so requests are queued. */
    @Suppress("DEPRECATION")
    private class LegacyResolver(
        private val nsd: NsdManager,
        private val found: MutableMap<String, DiscoveredTv>,
        private val publish: () -> Unit,
    ) : Resolver {
        private val queue = ArrayDeque<NsdServiceInfo>()
        private var busy = false
        private var closed = false

        @Synchronized
        override fun resolve(info: NsdServiceInfo) {
            if (closed) return
            queue.addLast(info)
            next()
        }

        @Synchronized
        private fun next() {
            if (busy || closed) return
            val info = queue.removeFirstOrNull() ?: return
            busy = true
            nsd.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = done()

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        record(serviceInfo, found, publish)
                        done()
                    }

                    private fun done() {
                        synchronized(this@LegacyResolver) { busy = false }
                        next()
                    }
                },
            )
        }

        override fun lost(info: NsdServiceInfo) = Unit

        @Synchronized
        override fun close() {
            closed = true
            queue.clear()
        }
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

        private fun record(info: NsdServiceInfo, found: MutableMap<String, DiscoveredTv>, publish: () -> Unit) {
            val host = hostOf(info) ?: return
            found[info.serviceName] = DiscoveredTv(name = info.serviceName, host = host, port = info.port)
            publish()
        }
    }
}
