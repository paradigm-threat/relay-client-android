package com.clevertree.relayruntime.demo

/**
 * User-facing playback failure copy for Flashback / RelayVideoPlayerView.
 * Pure JVM — Media3 error codes are passed as ints so unit tests need no Android runtime.
 *
 * Media3 ranges (1.1.x): IO 2000–2999, parsing 3000–3999, decoder 4000–4999, audio 5000–5999.
 */
object PlaybackErrors {
    const val CODE_DECODER_INIT_FAILED = 4001
    const val CODE_DECODER_QUERY_FAILED = 4002
    const val CODE_DECODING_FAILED = 4003
    const val CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES = 4004
    const val CODE_DECODING_FORMAT_UNSUPPORTED = 4005
    const val CODE_IO_NETWORK_CONNECTION_FAILED = 2001
    const val CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002
    const val CODE_IO_BAD_HTTP_STATUS = 2004
    const val CODE_PARSING_CONTAINER_MALFORMED = 3001
    const val CODE_PARSING_CONTAINER_UNSUPPORTED = 3003

    enum class Kind {
        DECODER,
        NETWORK,
        FORMAT,
        AUDIO,
        UNKNOWN,
    }

    data class Message(
        val kind: Kind,
        /** Short title for the overlay card. */
        val title: String,
        /** Full body — never rely on Toast (truncates). */
        val body: String,
        val primaryAction: String = "Open in external player",
        val secondaryAction: String = "Get VLC",
    ) {
        fun bannerLine(): String = "$title $body".trim()
    }

    fun classify(errorCode: Int, causeMessage: String? = null): Message {
        val cause = (causeMessage ?: "").lowercase()
        val kind = when {
            errorCode in 4000..4999 -> Kind.DECODER
            errorCode in 2000..2999 -> Kind.NETWORK
            errorCode in 3000..3999 -> Kind.FORMAT
            errorCode in 5000..5999 -> Kind.AUDIO
            causeContainsHevcHint(cause) -> Kind.DECODER
            else -> Kind.UNKNOWN
        }
        return messageFor(kind)
    }

    fun messageFor(kind: Kind): Message = when (kind) {
        Kind.DECODER -> Message(
            kind = Kind.DECODER,
            title = "Can't play in this app",
            body = "This phone often lacks HEVC hardware decode for our full-quality MKV. " +
                "Open the stream in VLC (or another player) instead.",
        )
        Kind.NETWORK -> Message(
            kind = Kind.NETWORK,
            title = "Stream didn't load",
            body = "Check your connection, then try again. " +
                "Large titles sometimes need a moment while the stream finishes seeding — wait and retry. " +
                "You can also open the same URL in an external player.",
        )
        Kind.FORMAT -> Message(
            kind = Kind.FORMAT,
            title = "File couldn't be read",
            body = "The app couldn't parse this Matroska stream. " +
                "Open in external player — VLC usually handles it.",
        )
        Kind.AUDIO -> Message(
            kind = Kind.AUDIO,
            title = "Audio failed",
            body = "Video may still work in another player. Try Open in external player.",
        )
        Kind.UNKNOWN -> Message(
            kind = Kind.UNKNOWN,
            title = "Playback stopped",
            body = "Try Open in external player. Install VLC if nothing else can open the stream.",
        )
    }

    /** Toast is OK only for short confirmations — never for [Message.body]. */
    fun shortNoHandlerToast(): String = "No app found to open this stream"

    private fun causeContainsHevcHint(cause: String): Boolean {
        if (cause.isEmpty()) return false
        return cause.contains("hevc") ||
            cause.contains("h265") ||
            cause.contains("h.265") ||
            cause.contains("mediacodec") ||
            cause.contains("decoder")
    }
}
