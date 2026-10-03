package com.snapnet.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    private val policy = RetryPolicy.DEFAULT

    @Test
    fun `retries a transient error while attempts remain`() {
        val error = DownloadError(DownloadErrorKind.NETWORK_ERROR)
        assertTrue(policy.shouldRetry(attempt = 1, error = error))
        assertTrue(policy.shouldRetry(attempt = 2, error = error))
    }

    @Test
    fun `stops at maxAttempts so retries are bounded`() {
        val error = DownloadError(DownloadErrorKind.NETWORK_ERROR)
        assertFalse(policy.shouldRetry(attempt = policy.maxAttempts, error = error))
        assertFalse(policy.shouldRetry(attempt = policy.maxAttempts + 1, error = error))
    }

    @Test
    fun `never retries a permanent failure`() {
        (1 until policy.maxAttempts).forEach { attempt ->
            assertFalse(policy.shouldRetry(attempt, DownloadError(DownloadErrorKind.DRM_PROTECTED)))
            assertFalse(policy.shouldRetry(attempt, DownloadError(DownloadErrorKind.VIDEO_UNAVAILABLE)))
            assertFalse(policy.shouldRetry(attempt, DownloadError(DownloadErrorKind.UNSUPPORTED_URL)))
        }
    }

    @Test
    fun `backoff grows exponentially`() {
        val p = RetryPolicy(initialDelayMillis = 1_000, multiplier = 2.0, maxDelayMillis = 60_000)
        assertEquals(1_000, p.delayMillisFor(1))
        assertEquals(2_000, p.delayMillisFor(2))
        assertEquals(4_000, p.delayMillisFor(3))
    }

    @Test
    fun `backoff is capped`() {
        val p = RetryPolicy(initialDelayMillis = 1_000, multiplier = 10.0, maxDelayMillis = 5_000)
        assertEquals(5_000, p.delayMillisFor(5))
    }

    @Test
    fun `backoff never drops below the initial delay`() {
        val p = RetryPolicy(initialDelayMillis = 1_000, multiplier = 1.0)
        assertEquals(1_000, p.delayMillisFor(0))
        assertEquals(1_000, p.delayMillisFor(1))
    }
}
