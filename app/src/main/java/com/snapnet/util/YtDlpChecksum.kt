package com.snapnet.util

import java.io.File
import java.security.MessageDigest

/** SHA-256 helpers for validating yt-dlp release assets before installation. */
internal object YtDlpChecksum {
    private val manifestLine = Regex("^([0-9a-fA-F]{64})\\s+\\*?(.+?)\\s*$")

    /** Returns the digest only when the manifest contains exactly one entry for [assetName]. */
    fun parseManifest(manifest: String, assetName: String): String? {
        val matches =
            manifest.lineSequence().mapNotNull { line ->
                val match = manifestLine.matchEntire(line.trim()) ?: return@mapNotNull null
                if (match.groupValues[2] == assetName) match.groupValues[1].lowercase() else null
            }.toList()
        return matches.singleOrNull()
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    fun matches(file: File, expectedSha256: String): Boolean {
        if (!file.isFile || !expectedSha256.matches(Regex("[0-9a-fA-F]{64}"))) return false
        return runCatching { sha256(file).equals(expectedSha256, ignoreCase = true) }.getOrDefault(false)
    }
}
