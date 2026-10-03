package com.snapnet.download

/**
 * Machine-readable classification of a download/engine failure.
 *
 * The engine (yt-dlp) reports failures as free-form English text on stderr. Showing that text to a
 * user is unhelpful and leaks internals, so every failure is mapped onto one of these kinds and the
 * UI is responsible for localising the message.
 */
enum class DownloadErrorKind {
    /** The site needs an authenticated session before media can be resolved. */
    LOGIN_REQUIRED,

    /** The site needs cookies supplied by the user (sign-in wall, age gate, consent wall). */
    COOKIES_REQUIRED,

    /** YouTube wants a Proof-of-Origin token for this client and none could be produced. */
    PO_TOKEN_REQUIRED,

    /** Transient connectivity / DNS / TLS / socket failure. */
    NETWORK_ERROR,

    /** The media is not available in the user's region. */
    GEO_BLOCKED,

    /** Removed, private, or otherwise gone. */
    VIDEO_UNAVAILABLE,

    /** No usable format matched the requested quality/container. */
    FORMAT_UNAVAILABLE,

    /** The site is rate limiting or temporarily blocking us. */
    RATE_LIMITED,

    /** DRM-protected stream. SnapNet intentionally does not attempt to bypass this. */
    DRM_PROTECTED,

    /** The extractor found no video on the given URL. */
    UNSUPPORTED_URL,

    /** A JavaScript runtime is required (EJS) but none is available/usable. */
    UNSUPPORTED_JS_RUNTIME,

    /** Not enough free space for the download. */
    DISK_FULL,

    /** The destination file is in use / cannot be written. */
    FILE_CONFLICT,

    /** Post-processing (ffmpeg merge/convert) failed after a successful download. */
    POSTPROCESS_ERROR,

    /** The user cancelled. Never shown as an error. */
    CANCELED,

    /** Anything we could not classify. */
    UNKNOWN,
}

/**
 * A classified failure. [technicalMessage] is kept for the debug log only and must never be rendered
 * in the normal UI.
 */
data class DownloadError(
    val kind: DownloadErrorKind,
    val technicalMessage: String = "",
) {
    /**
     * Whether retrying the *same* request could plausibly succeed.
     *
     * Deliberately conservative: permanent conditions (DRM, deleted media, bad URL, login walls)
     * are never retried, because retrying them only burns the user's bandwidth and the site's
     * goodwill.
     */
    val isRetryable: Boolean
        get() =
            when (kind) {
                DownloadErrorKind.NETWORK_ERROR,
                DownloadErrorKind.RATE_LIMITED,
                DownloadErrorKind.FORMAT_UNAVAILABLE,
                DownloadErrorKind.POSTPROCESS_ERROR -> true
                else -> false
            }

    companion object {
        fun of(throwable: Throwable): DownloadError =
            DownloadError(
                kind = DownloadErrorMapper.classify(throwable.message ?: throwable.toString()),
                technicalMessage = throwable.message ?: throwable.toString(),
            )
    }
}

/**
 * Maps unstructured yt-dlp / network output onto [DownloadErrorKind].
 *
 * Order matters: the first matching rule wins, so more specific patterns are listed before the
 * generic ones.
 */
object DownloadErrorMapper {

    private val rules: List<Pair<DownloadErrorKind, List<String>>> =
        listOf(
            // --- DRM: must be checked before the generic "unavailable" rules ---
            DownloadErrorKind.DRM_PROTECTED to
                listOf("drm", "protected by widevine", "widevine", "playready", "fairplay"),
            // --- Sign-in / cookie walls ---
            DownloadErrorKind.PO_TOKEN_REQUIRED to
                listOf("po_token", "po token", "potoken", "missing_pot", "failed to fetch pot"),
            DownloadErrorKind.LOGIN_REQUIRED to
                listOf(
                    "sign in to confirm",
                    "sign in to view",
                    "login required",
                    "please log in",
                    "authentication required",
                    "this video is only available to",
                ),
            DownloadErrorKind.COOKIES_REQUIRED to
                listOf(
                    "cookies",
                    "consent",
                    "age-restricted",
                    "age restricted",
                    "confirm your age",
                    "not a bot",
                    "confirm you're not a bot",
                ),
            // --- JavaScript runtime / EJS ---
            DownloadErrorKind.UNSUPPORTED_JS_RUNTIME to
                listOf(
                    "no supported javascript runtime",
                    "js runtime",
                    "jsinterp",
                    "ejs",
                    "nsig extraction failed",
                    "failed to solve",
                ),
            // --- Geo ---
            DownloadErrorKind.GEO_BLOCKED to
                listOf(
                    "not available in your country",
                    "geo restrict",
                    "geo-restrict",
                    "blocked in your country",
                    "this video is unavailable in your",
                ),
            // --- Gone ---
            DownloadErrorKind.VIDEO_UNAVAILABLE to
                listOf(
                    "video unavailable",
                    "this video is unavailable",
                    "has been removed",
                    "no longer available",
                    "private video",
                    "removed by the uploader",
                    "account has been terminated",
                    "404",
                    "410",
                ),
            // --- Rate limiting ---
            DownloadErrorKind.RATE_LIMITED to
                listOf("http error 429", "too many requests", "rate limit", "try again later"),
            // --- Formats ---
            DownloadErrorKind.FORMAT_UNAVAILABLE to
                listOf(
                    "requested format is not available",
                    "no video formats found",
                    "no formats found",
                    "unable to extract any formats",
                    "requested format not available",
                ),
            // --- URL / extractor ---
            DownloadErrorKind.UNSUPPORTED_URL to
                listOf(
                    "unsupported url",
                    "no suitable extractor",
                    "is not a valid url",
                    "unable to extract",
                ),
            // --- Disk ---
            DownloadErrorKind.DISK_FULL to
                listOf("no space left", "enospc", "not enough space", "disk full"),
            DownloadErrorKind.FILE_CONFLICT to
                listOf("permission denied", "read-only file system", "ebusy", "file exists"),
            // --- Post-processing ---
            DownloadErrorKind.POSTPROCESS_ERROR to
                listOf("postprocessing", "post-processing", "ffmpeg", "merger", "conversion failed"),
            // --- Network ---
            DownloadErrorKind.NETWORK_ERROR to
                listOf(
                    "unable to download webpage",
                    "getaddrinfo",
                    "name or service not known",
                    "connection reset",
                    "connection refused",
                    "timed out",
                    "timeout",
                    "temporary failure in name resolution",
                    "ssl",
                    "certificate",
                    "network is unreachable",
                    "unable to connect",
                ),
            DownloadErrorKind.CANCELED to listOf("canceled", "cancelled", "interrupted by user"),
        )

    /** @return the most specific [DownloadErrorKind] matching [raw], or [DownloadErrorKind.UNKNOWN]. */
    fun classify(raw: String): DownloadErrorKind {
        val text = raw.lowercase()
        if (text.isBlank()) return DownloadErrorKind.UNKNOWN
        for ((kind, needles) in rules) {
            if (needles.any { text.contains(it) }) return kind
        }
        return DownloadErrorKind.UNKNOWN
    }
}
