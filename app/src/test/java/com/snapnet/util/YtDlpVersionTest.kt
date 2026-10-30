package com.snapnet.util

import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun readsVersionFromShebangPrefixedZipApp() {
        val archiveBytes = ByteArrayOutputStream()
        ZipOutputStream(archiveBytes).use { archive ->
            archive.putNextEntry(ZipEntry("yt_dlp/version.py"))
            archive.write("__version__ = '2026.08.19'\n".toByteArray(Charsets.UTF_8))
            archive.closeEntry()
        }

        val zipApp = File.createTempFile("yt-dlp-version-test-", ".zip")
        try {
            zipApp.outputStream().buffered().use { output ->
                output.write("#!/usr/bin/env python3\n".toByteArray(Charsets.UTF_8))
                output.write(archiveBytes.toByteArray())
            }

            assertEquals("2026.08.19", YtDlpVersion.readVersionFromFile(zipApp))
            assertTrue(YtDlpUpdater.verifyInstalledBinary(zipApp))
        } finally {
            zipApp.delete()
        }
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

    @Test
    fun rejectsZipAppsWithoutYtDlpVersionModule() {
        val archiveBytes = ByteArrayOutputStream()
        ZipOutputStream(archiveBytes).use { archive ->
            archive.putNextEntry(ZipEntry("not_yt_dlp.txt"))
            archive.write("not a yt-dlp engine".toByteArray(Charsets.UTF_8))
            archive.closeEntry()
        }

        val zipApp = File.createTempFile("yt-dlp-invalid-test-", ".zip")
        try {
            zipApp.writeBytes(archiveBytes.toByteArray())

            assertFalse(YtDlpUpdater.verifyInstalledBinary(zipApp))
            assertNull(YtDlpVersion.readVersionFromFile(zipApp))
        } finally {
            zipApp.delete()
        }
    }

    private fun utcTimestamp(date: String): Long =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(date)!!.time
}
