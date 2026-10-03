package io.github.tomerar.freetvremote.data

import kotlinx.serialization.Serializable
import java.util.Base64

/** A paired TV. [pin] is the Base64 SHA-256 of the TV's public key, recorded at pairing time. */
@Serializable
data class SavedTv(
    val id: String,
    val name: String,
    val host: String,
    val pin: String,
    val remotePort: Int = DEFAULT_REMOTE_PORT,
    val pairingPort: Int = DEFAULT_PAIRING_PORT,
    /**
     * The name the TV announces on the network (mDNS) when it was found by the search. It does not change when the
     * user renames the TV or the router gives it a new address, so it is how the app recognises it again.
     */
    val serviceName: String? = null,
) {
    val pinBytes: ByteArray get() = Base64.getDecoder().decode(pin)

    companion object {
        const val DEFAULT_REMOTE_PORT = 6466
        const val DEFAULT_PAIRING_PORT = 6467

        fun encodePin(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    }
}
