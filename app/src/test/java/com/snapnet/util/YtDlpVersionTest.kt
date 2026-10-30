package com.snapnet.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpVersionTest {
    @Test
    fun detectsVersionsOlderThanYtDlpWarningThreshold() {
        val now = utcTimestamp("2026-10-30")

        assertTrue(YtDlpVersion.isOlderThan90Days("2025.11.12", now))
        assertFalse(YtDlpVersion.isOlderThan90Days("2026.08.19", now))
    }

    @Test
    fun forcesRefreshForAnUnseenStaleVersionAndRetriesAfterTheInterval() {
        val now = utcTimestamp("2026-10-30")
        val lastUpdate = utcTimestamp("2026-10-29")
        val week = 7L * 24 * 60 * 60 * 1000

        assertTrue(YtDlpVersion.shouldForceStaleRefresh("2025.11.12", null, 0L, week, now))
        assertFalse(
            YtDlpVersion.shouldForceStaleRefresh(
                "2025.11.12",
                "2025.11.12",
                lastUpdate,
                week,
                now,
            )
        )
        assertTrue(
            YtDlpVersion.shouldForceStaleRefresh(
                "2025.11.12",
                "2025.11.12",
                lastUpdate - week,
                week,
                now,
            )
        )
        assertFalse(YtDlpVersion.shouldForceStaleRefresh("2026.08.19", null, 0L, week, now))
    }

    @Test
    fun treatsUnknownOrMalformedVersionsAsStale() {
        val now = utcTimestamp("2026-10-30")

        assertTrue(YtDlpVersion.isOlderThan90Days(null, now))
        assertTrue(YtDlpVersion.isOlderThan90Days("unknown", now))
        assertTrue(YtDlpVersion.isOlderThan90Days("2025.02.31", now))
    }

    private fun utcTimestamp(date: String): Long =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(date)!!.time
}
