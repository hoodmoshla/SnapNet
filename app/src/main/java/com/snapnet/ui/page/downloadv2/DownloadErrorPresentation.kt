package com.snapnet.ui.page.downloadv2

import com.snapnet.R
import com.snapnet.download.DownloadErrorKind
import com.snapnet.download.DownloadErrorMapper
import com.snapnet.util.Redactor
import java.net.URI
import java.util.Locale

private val DIAGNOSTIC_ERROR_ID = Regex("""\b[a-fA-F0-9]{16}-[A-Z]{3}\b""")
private val URL_QUERY_PARAMETER = Regex("""([?&][^=&#\s]+=)[^&#\s]*""")
private val X_TWITTER_HOSTS =
    setOf(
        "x.com",
        "www.x.com",
        "m.x.com",
        "mobile.x.com",
        "twitter.com",
        "www.twitter.com",
        "m.twitter.com",
        "mobile.twitter.com",
    )

internal fun downloadErrorMessageRes(throwable: Throwable, sourceUrl: String? = null): Int {
    val message =
        generateSequence(throwable) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
            .ifBlank { throwable.toString() }
    return when (DownloadErrorMapper.classify(message)) {
        DownloadErrorKind.NETWORK_ERROR -> R.string.download_error_network
        DownloadErrorKind.LOGIN_REQUIRED,
        DownloadErrorKind.COOKIES_REQUIRED,
        DownloadErrorKind.PO_TOKEN_REQUIRED -> R.string.download_error_auth
        DownloadErrorKind.GEO_BLOCKED,
        DownloadErrorKind.VIDEO_UNAVAILABLE -> R.string.download_error_unavailable
        DownloadErrorKind.FORMAT_UNAVAILABLE -> R.string.download_error_formats
        DownloadErrorKind.RATE_LIMITED -> R.string.download_error_rate_limited
        DownloadErrorKind.DRM_PROTECTED -> R.string.download_error_drm
        DownloadErrorKind.UNSUPPORTED_URL -> R.string.download_error_unsupported_url
        DownloadErrorKind.UNSUPPORTED_JS_RUNTIME -> R.string.download_error_js_runtime
        DownloadErrorKind.DISK_FULL -> R.string.download_error_disk_full
        DownloadErrorKind.FILE_CONFLICT -> R.string.download_error_file_conflict
        DownloadErrorKind.POSTPROCESS_ERROR -> R.string.download_error_postprocess
        DownloadErrorKind.UNKNOWN ->
            if (sourceUrl.isXTwitterUrl()) R.string.download_error_x_unknown
            else R.string.fetch_info_error_msg
        else -> R.string.fetch_info_error_msg
    }
}

internal fun downloadErrorDiagnosticId(throwable: Throwable): String? =
    generateSequence(throwable) { it.cause }
        .mapNotNull { it.message }
        .firstNotNullOfOrNull { DIAGNOSTIC_ERROR_ID.find(it)?.value }

internal fun redactDownloadErrorDetails(text: String): String =
    Redactor.redact(text).replace(URL_QUERY_PARAMETER) { match ->
        match.groupValues[1] + Redactor.MASK
    }

private fun String?.isXTwitterUrl(): Boolean {
    val host =
        this?.let { url ->
            runCatching { URI(url.trim()).host?.lowercase(Locale.ROOT) }.getOrNull()
        }
    return host in X_TWITTER_HOSTS
}
