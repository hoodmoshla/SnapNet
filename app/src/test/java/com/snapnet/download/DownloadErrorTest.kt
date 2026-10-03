package com.snapnet.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorMapperTest {

    private fun kindOf(message: String) = DownloadErrorMapper.classify(message)

    @Test
    fun `detects sign-in walls`() {
        assertEquals(
            DownloadErrorKind.LOGIN_REQUIRED,
            kindOf("ERROR: Sign in to confirm you're not a bot. Use --cookies-from-browser"),
        )
    }

    @Test
    fun `detects explicit cookie requirements`() {
        assertEquals(
            DownloadErrorKind.COOKIES_REQUIRED,
            kindOf("ERROR: This video is age-restricted and requires cookies"),
        )
    }

    @Test
    fun `detects po token requirements before the generic cookie rule`() {
        assertEquals(
            DownloadErrorKind.PO_TOKEN_REQUIRED,
            kindOf("ERROR: missing_po_token: no PO token available for client web"),
        )
    }

    @Test
    fun `detects drm and never suggests retrying`() {
        val error = DownloadError.of(RuntimeException("ERROR: This stream is DRM protected by Widevine"))
        assertEquals(DownloadErrorKind.DRM_PROTECTED, error.kind)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `detects missing javascript runtime`() {
        assertEquals(
            DownloadErrorKind.UNSUPPORTED_JS_RUNTIME,
            kindOf("WARNING: No supported JavaScript runtime could be found"),
        )
    }

    @Test
    fun `detects geo blocking`() {
        assertEquals(
            DownloadErrorKind.GEO_BLOCKED,
            kindOf("ERROR: This video is not available in your country"),
        )
    }

    @Test
    fun `detects unavailable media`() {
        assertEquals(
            DownloadErrorKind.VIDEO_UNAVAILABLE,
            kindOf("ERROR: Video unavailable"),
        )
    }

    @Test
    fun `detects rate limiting and marks it retryable`() {
        val error = DownloadError.of(RuntimeException("ERROR: HTTP Error 429: Too Many Requests"))
        assertEquals(DownloadErrorKind.RATE_LIMITED, error.kind)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `detects unavailable formats`() {
        assertEquals(
            DownloadErrorKind.FORMAT_UNAVAILABLE,
            kindOf("ERROR: Requested format is not available"),
        )
    }

    @Test
    fun `detects unsupported urls`() {
        assertEquals(
            DownloadErrorKind.UNSUPPORTED_URL,
            kindOf("ERROR: Unsupported URL: https://example.invalid/x"),
        )
    }

    @Test
    fun `detects network failures as retryable`() {
        val error = DownloadError.of(RuntimeException("ERROR: Unable to download webpage: connection reset"))
        assertEquals(DownloadErrorKind.NETWORK_ERROR, error.kind)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `detects post-processing failures`() {
        assertEquals(
            DownloadErrorKind.POSTPROCESS_ERROR,
            kindOf("[Merger] ffmpeg exited with code 1"),
        )
    }

    @Test
    fun `detects disk exhaustion`() {
        assertEquals(
            DownloadErrorKind.DISK_FULL,
            kindOf("OSError: [Errno 28] No space left on device"),
        )
    }

    @Test
    fun `unknown messages degrade to UNKNOWN and are not retried`() {
        val error = DownloadError.of(RuntimeException("something entirely unexpected"))
        assertEquals(DownloadErrorKind.UNKNOWN, error.kind)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `classification is case insensitive`() {
        assertEquals(DownloadErrorKind.DRM_PROTECTED, kindOf("DRM PROTECTED"))
        assertEquals(DownloadErrorKind.DRM_PROTECTED, kindOf("drm protected"))
    }

    @Test
    fun `permanent failures are never retryable`() {
        val permanent =
            listOf(
                DownloadErrorKind.LOGIN_REQUIRED,
                DownloadErrorKind.COOKIES_REQUIRED,
                DownloadErrorKind.PO_TOKEN_REQUIRED,
                DownloadErrorKind.GEO_BLOCKED,
                DownloadErrorKind.VIDEO_UNAVAILABLE,
                DownloadErrorKind.DRM_PROTECTED,
                DownloadErrorKind.UNSUPPORTED_URL,
                DownloadErrorKind.UNKNOWN,
            )
        permanent.forEach { kind ->
            assertFalse("$kind must not be retryable", DownloadError(kind).isRetryable)
        }
    }
}
