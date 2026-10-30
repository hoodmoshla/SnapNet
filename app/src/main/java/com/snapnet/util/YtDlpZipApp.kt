package com.snapnet.util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.PushbackInputStream
import java.util.zip.ZipInputStream

/** Reads entries from Python zipapps that may have an executable shebang before the ZIP payload. */
internal object YtDlpZipApp {

    private const val MAX_PREAMBLE_BYTES = 4096
    private const val MAX_ENTRY_BYTES = 64 * 1024

    /** Returns the named entry's uncompressed bytes, or null when the archive does not contain it. */
    fun readEntry(file: File, entryName: String): ByteArray? {
        val input = PushbackInputStream(file.inputStream().buffered(), ZIP_LOCAL_FILE_HEADER.size)
        if (!seekToZipPayload(input)) {
            input.close()
            return null
        }

        return ZipInputStream(input).use { archive ->
            var entry = archive.nextEntry
            var content: ByteArray? = null

            while (entry != null && content == null) {
                if (entry.name == entryName) {
                    content = readCurrentEntry(archive)
                } else {
                    archive.closeEntry()
                    entry = archive.nextEntry
                }
            }

            content
        }
    }

    private fun seekToZipPayload(input: PushbackInputStream): Boolean {
        val signature = ZIP_LOCAL_FILE_HEADER
        var previous1 = -1
        var previous2 = -1
        var previous3 = -1
        var scanned = 0

        while (scanned < MAX_PREAMBLE_BYTES + signature.size) {
            val next = input.read()
            if (next == -1) return false
            scanned++

            if (
                previous1 == (signature[0].toInt() and 0xff) &&
                    previous2 == (signature[1].toInt() and 0xff) &&
                    previous3 == (signature[2].toInt() and 0xff) &&
                    next == (signature[3].toInt() and 0xff)
            ) {
                input.unread(signature)
                return true
            }

            previous1 = previous2
            previous2 = previous3
            previous3 = next
        }

        return false
    }

    private fun readCurrentEntry(archive: ZipInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var totalBytes = 0

        while (true) {
            val bytesRead = archive.read(buffer)
            if (bytesRead == -1) break
            if (totalBytes + bytesRead > MAX_ENTRY_BYTES) {
                throw IOException("yt-dlp version entry exceeds the size limit")
            }
            output.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
        }

        return output.toByteArray()
    }

    private val ZIP_LOCAL_FILE_HEADER = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
}
