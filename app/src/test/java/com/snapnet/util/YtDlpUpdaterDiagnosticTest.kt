package com.snapnet.util

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YtDlpUpdaterDiagnosticTest {
    @Test
    fun classifiesGitHubHttpFailuresWithoutExposingResponseDetails() {
        val diagnostic =
            YtDlpUpdater.diagnosticFor(
                IOException("GitHub asset download failed with HTTP 403 at https://example.test/private")
            )

        assertEquals("github_http_403", diagnostic)
        assertFalse(diagnostic.contains("https://"))
        assertFalse(diagnostic.contains("private"))
    }

    @Test
    fun classifiesNetworkTimeoutsWithASafeCode() {
        assertEquals(
            "network_timeout",
            YtDlpUpdater.diagnosticFor(SocketTimeoutException("timeout")),
        )
    }

    @Test
    fun hidesUnknownIoMessagesFromDiagnostics() {
        val diagnostic =
            YtDlpUpdater.diagnosticFor(
                IOException("request failed for https://user:pass@example.test/?token=secret")
            )

        assertEquals("io_error", diagnostic)
        assertFalse(diagnostic.contains("secret"))
        assertFalse(diagnostic.contains("user:pass"))
    }
}
