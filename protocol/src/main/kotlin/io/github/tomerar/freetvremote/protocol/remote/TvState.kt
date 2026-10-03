package io.github.tomerar.freetvremote.protocol.remote

/** What the TV told us about itself. `null` means "not reported yet". */
public data class TvState(
    val isOn: Boolean? = null,
    val volumeLevel: Int? = null,
    val volumeMax: Int? = null,
    val isMuted: Boolean? = null,
    val currentApp: String? = null,
    /** The readable name of [currentApp] as the TV reports it (for example "Netflix"), when it sends one. */
    val currentAppLabel: String? = null,
    /** The TV asked for text input (its on-screen keyboard is up). Best effort: reset on reconnect and on app change. */
    val textFieldActive: Boolean = false,
    val deviceModel: String? = null,
    val deviceVendor: String? = null,
)

public enum class FailureReason {
    /** The TV closed the connection straight after TLS: this phone is not (or no longer) paired. */
    NOT_PAIRED,

    /** The TV presented a different certificate than the one pinned at pairing time. */
    CERTIFICATE_MISMATCH,
}

public sealed interface ConnectionState {
    public data object Idle : ConnectionState

    public data object Connecting : ConnectionState

    public data object Connected : ConnectionState

    /** Connection lost; the next attempt happens in [retryInMs]. */
    public data class Reconnecting(
        val attempt: Int,
        val retryInMs: Long,
    ) : ConnectionState

    /** Retrying cannot help; the user has to act (usually re-pair). */
    public data class Failed(
        val reason: FailureReason,
    ) : ConnectionState
}

public data class RemoteSessionConfig(
    val port: Int = DEFAULT_PORT,
    val connectTimeoutMs: Int = 5_000,
    /** The TV pings every few seconds; silence for this long means it is gone (asleep, unplugged, Wi-Fi lost). */
    val idleTimeoutMs: Int = 20_000,
    val backoffMs: List<Long> = DEFAULT_BACKOFF_MS,
    /** Connections closed before the handshake completed, in a row, before giving up as [FailureReason.NOT_PAIRED]. */
    val maxEarlyCloses: Int = 2,
    val deviceModel: String = "Free TV Remote",
    val deviceVendor: String = "Free TV Remote",
    val appVersion: String = "1",
) {
    public companion object {
        public const val DEFAULT_PORT: Int = 6466
        public val DEFAULT_BACKOFF_MS: List<Long> = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
    }
}
