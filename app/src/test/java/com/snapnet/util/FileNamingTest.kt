package com.snapnet.util

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamingTest {

    // ---------- sanitize ----------

    @Test
    fun `strips every path separator so a title cannot escape its directory`() {
        val result = FileNaming.sanitize("../../etc/passwd")
        assertFalse("must not contain '/'", result.contains('/'))
        assertFalse("must not contain '\\'", result.contains('\\'))
        assertFalse("must not be a traversal segment", result == "..")
    }

    @Test
    fun `removes characters illegal on common filesystems`() {
        val result = FileNaming.sanitize("a/b\\c:d*e?f\"g<h>i|j")
        assertEquals("a_b_c_d_e_f_g_h_i_j", result)
    }

    @Test
    fun `removes control characters`() {
        val result = FileNaming.sanitize("bad\u0000name\u001Fhere")
        assertEquals("bad_name_here", result)
    }

    @Test
    fun `collapses whitespace runs and trims`() {
        assertEquals("A B", FileNaming.sanitize("   A    B   "))
    }

    @Test
    fun `strips trailing dots and spaces`() {
        assertEquals("name", FileNaming.sanitize("name...  "))
    }

    @Test
    fun `escapes reserved device names`() {
        assertEquals("_CON", FileNaming.sanitize("CON"))
        assertEquals("_nul", FileNaming.sanitize("nul"))
    }

    @Test
    fun `falls back to Unknown for empty or dot-only input`() {
        assertEquals(FileNaming.UNKNOWN, FileNaming.sanitize(""))
        assertEquals(FileNaming.UNKNOWN, FileNaming.sanitize("   "))
        assertEquals(FileNaming.UNKNOWN, FileNaming.sanitize("."))
        assertEquals(FileNaming.UNKNOWN, FileNaming.sanitize(".."))
        assertEquals(FileNaming.UNKNOWN, FileNaming.sanitize("..."))
    }

    @Test
    fun `enforces the maximum length`() {
        val result = FileNaming.sanitize("x".repeat(500))
        assertEquals(FileNaming.MAX_LENGTH, result.length)
    }

    @Test
    fun `never exceeds the limit even when truncation lands on a dot`() {
        val result = FileNaming.sanitize("a".repeat(179) + "...")
        assertTrue(result.length <= FileNaming.MAX_LENGTH)
        assertFalse(result.endsWith("."))
    }

    // ---------- buildBaseName ----------

    @Test
    fun `builds Uploader - Title`() {
        assertEquals(
            "Rick Astley - Never Gonna Give You Up",
            FileNaming.buildBaseName("Rick Astley", "Never Gonna Give You Up"),
        )
    }

    @Test
    fun `falls back to the title when the uploader is missing`() {
        assertEquals("Some Title", FileNaming.buildBaseName(null, "Some Title"))
        assertEquals("Some Title", FileNaming.buildBaseName("  ", "Some Title"))
    }

    @Test
    fun `does not repeat identical uploader and title`() {
        assertEquals("Same", FileNaming.buildBaseName("Same", "Same"))
    }

    @Test
    fun `degrades gracefully when both parts are missing`() {
        assertEquals(FileNaming.UNKNOWN, FileNaming.buildBaseName(null, null))
    }

    // ---------- uniqueFile ----------

    @Test
    fun `returns the base name when nothing collides`() {
        val dir = Files.createTempDirectory("snapnet").toFile()
        assertEquals("Video.mp4", FileNaming.uniqueFile(dir, "Video", ".mp4").name)
    }

    @Test
    fun `appends a counter on collision`() {
        val dir = Files.createTempDirectory("snapnet").toFile()
        Files.createFile(dir.resolve("Video.mp4").toPath())
        assertEquals("Video (1).mp4", FileNaming.uniqueFile(dir, "Video", ".mp4").name)
        Files.createFile(dir.resolve("Video (1).mp4").toPath())
        assertEquals("Video (2).mp4", FileNaming.uniqueFile(dir, "Video", ".mp4").name)
    }

    @Test
    fun `sanitises the base name before building the path`() {
        val dir = Files.createTempDirectory("snapnet").toFile()
        val file = FileNaming.uniqueFile(dir, "../evil", ".mp4")
        assertEquals(dir, file.parentFile)
        assertNotEquals("evil", file.name)
    }

    // ---------- extensionOf ----------

    @Test
    fun `extracts the extension including the dot`() {
        assertEquals(".mp4", FileNaming.extensionOf("a.mp4"))
        // The "extension" is what follows the final dot. For media files (mp4/mkv/webm/m4a/mp3)
        // that is exactly the container, which is what callers need when rebuilding a name.
        assertEquals(".gz", FileNaming.extensionOf("a.tar.gz"))
    }

    @Test
    fun `returns empty when there is no extension`() {
        assertEquals("", FileNaming.extensionOf("noext"))
        assertEquals("", FileNaming.extensionOf(".hidden"))
        assertEquals("", FileNaming.extensionOf("trailing."))
    }

    // ---------- output template ----------

    @Test
    fun `default template produces the documented Uploader - Title shape`() {
        assertTrue(FileNaming.OUTPUT_TEMPLATE.contains("%(uploader"))
        assertTrue(FileNaming.OUTPUT_TEMPLATE.contains("%(title)"))
        assertTrue(FileNaming.OUTPUT_TEMPLATE.endsWith(".%(ext)s"))
    }
}
