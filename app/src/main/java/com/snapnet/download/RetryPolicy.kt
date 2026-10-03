package com.snapnet.download

/**
 * Bounded retry policy for a failed task.
 *
 * SnapNet must never loop forever: [maxAttempts] is a hard ceiling and [delayMillisFor] grows
 * exponentially with a cap so a flaky network does not turn into a request flood.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 2_000L,
    val maxDelayMillis: Long = 60_000L,
    val multiplier: Double = 2.0,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    }

    /** @return true when [attempt] (1-based) may still be retried for [error]. */
    fun shouldRetry(attempt: Int, error: DownloadError): Boolean =
        error.isRetryable && attempt < maxAttempts

    /** @return the backoff delay before retry number [attempt] (1-based). */
    fun delayMillisFor(attempt: Int): Long {
        val exponent = (attempt - 1).coerceAtLeast(0)
        val raw = initialDelayMillis * Math.pow(multiplier, exponent.toDouble()).toLong()
        return raw.coerceIn(initialDelayMillis, maxDelayMillis)
    }

    companion object {
        val DEFAULT = RetryPolicy()
    }
}
