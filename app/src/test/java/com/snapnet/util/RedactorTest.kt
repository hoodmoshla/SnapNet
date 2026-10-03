package com.snapnet.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These tests exist because a leaked credential in a shared debug log is a real-world incident, not a
 * theoretical one: the upstream project this fork came from shipped a live GitHub token in its source.
 */
class RedactorTest {

    @Test
    fun `redacts bearer tokens but keeps the label readable`() {
        val out = Redactor.redact("Authorization: Bearer abc123DEF456ghi")
        assertTrue(out.contains("Authorization"))
        assertFalse(out.contains("abc123DEF456ghi"))
    }

    @Test
    fun `redacts github personal access tokens`() {
        val out = Redactor.redact("token=ghp_0123456789abcdefghijklmnopqrstuvwxyz")
        assertFalse(out.contains("ghp_0123456789"))
    }

    @Test
    fun `redacts fine grained github tokens`() {
        val out = Redactor.redact("using github_pat_11ABCDEFG0123456789 now")
        assertFalse(out.contains("github_pat_11ABCDEFG"))
    }

    @Test
    fun `redacts cookie headers`() {
        val out = Redactor.redact("Cookie: SID=secretvalue; HSID=another")
        assertFalse(out.contains("secretvalue"))
        assertFalse(out.contains("another"))
    }

    @Test
    fun `redacts the cookie jar path passed to yt-dlp`() {
        val out = Redactor.redact("--cookies /data/user/0/com.snapnet/cache/cookies.txt")
        assertTrue(out.contains("--cookies"))
        assertFalse(out.contains("cookies.txt"))
    }

    @Test
    fun `redacts passwords and api keys`() {
        assertFalse(Redactor.redact("password=hunter2").contains("hunter2"))
        assertFalse(Redactor.redact("api_key: sk-live-9f8a7b6c").contains("sk-live-9f8a7b6c"))
        assertFalse(Redactor.redact("secret=topsecret").contains("topsecret"))
    }

    @Test
    fun `redacts aws style access key ids`() {
        assertFalse(Redactor.redact("AKIAIOSFODNN7EXAMPLE").contains("AKIAIOSFODNN7EXAMPLE"))
    }

    @Test
    fun `redacts private key blocks`() {
        val pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIabc\n-----END RSA PRIVATE KEY-----"
        val out = Redactor.redact(pem)
        assertFalse(out.contains("MIIabc"))
    }

    @Test
    fun `leaves ordinary text untouched`() {
        val text = "ERROR: Video unavailable. yt-dlp 2026.08.19 (https://youtube.com/watch?v=abc)"
        assertEquals(text, Redactor.redact(text))
    }

    @Test
    fun `handles null and empty input`() {
        assertEquals("", Redactor.redact(null))
        assertEquals("", Redactor.redact(""))
    } 

    @Test
    fun `redacts every credential on a multi line command dump`() {
        val dump =
            """
            --cookies /cache/cookies.txt
            --add-header Authorization: Bearer tok_12345
            password=s3cret
            """.trimIndent()
        val out = Redactor.redact(dump)
        assertFalse(out.contains("/cache/cookies.txt"))
        assertFalse(out.contains("tok_12345"))
        assertFalse(out.contains("s3cret"))
        assertTrue(out.contains("--cookies"))
    }
}
