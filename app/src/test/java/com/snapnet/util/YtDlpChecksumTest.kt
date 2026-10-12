package com.snapnet.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpChecksumTest {
    @Test
    fun parsesOnlyTheRequestedAssetAndAcceptsSha256sumFormat() {
        val expected = "a".repeat(64)
        val manifest = "${expected}  yt-dlp\n${"b".repeat(64)} *yt-dlp.tar.gz\n"

        assertEquals(expected, YtDlpChecksum.parseManifest(manifest, "yt-dlp"))
    }

    @Test
    fun rejectsMissingOrAmbiguousAssetEntries() {
        val first = "a".repeat(64)
        val second = "b".repeat(64)
        assertNull(YtDlpChecksum.parseManifest("$first  yt-dlp-other\n", "yt-dlp"))
        assertNull(
            YtDlpChecksum.parseManifest(
                "$first  yt-dlp\n$second *yt-dlp\n",
                "yt-dlp",
            )
        )
    }

    @Test
    fun computesAndChecksSha256ForDownloadedFile() {
        val file = File.createTempFile("ytdlp-checksum-test", ".bin")
        try {
            file.writeText("abc")

            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                YtDlpChecksum.sha256(file),
            )
            assertTrue(YtDlpChecksum.matches(file, YtDlpChecksum.sha256(file)))
            assertFalse(YtDlpChecksum.matches(file, "0".repeat(64)))
            assertFalse(YtDlpChecksum.matches(file, "not-a-digest"))
        } finally {
            file.delete()
        }
    }
}
