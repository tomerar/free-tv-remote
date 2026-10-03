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
) {
    val pinBytes: ByteArray get() = Base64.getDecoder().decode(pin)

    companion object {
        const val DEFAULT_REMOTE_PORT = 6466
        const val DEFAULT_PAIRING_PORT = 6467

        fun encodePin(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    }
}
