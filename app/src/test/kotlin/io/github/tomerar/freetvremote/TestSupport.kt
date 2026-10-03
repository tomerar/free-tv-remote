package io.github.tomerar.freetvremote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.tomerar.freetvremote.protocol.tls.ClientIdentity
import io.github.tomerar.freetvremote.protocol.tls.SelfSignedCertificate
import io.github.tomerar.freetvremote.remote.IdentityProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File

fun testDataStore(scope: CoroutineScope, dir: File, name: String = "test"): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "$name.preferences_pb") })

/** One RSA key pair per test run: generating 2048-bit keys is the slow part. */
val sharedIdentity: ClientIdentity by lazy { SelfSignedCertificate.generate("app-test") }

/** Generates the shared key now, so its slow CPU-bound creation never counts against a test's timeout on a busy CI runner. */
fun warmUpTestIdentity() {
    check(sharedIdentity.certificate.encoded.isNotEmpty())
}

val testIdentityProvider = IdentityProvider { sharedIdentity }

suspend fun <T> Flow<T>.awaitValue(timeoutMs: Long = 15_000, predicate: (T) -> Boolean): T =
    withTimeout(timeoutMs) { first(predicate) }

suspend fun awaitUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
    withTimeout(timeoutMs) { while (!condition()) delay(10) }
}
