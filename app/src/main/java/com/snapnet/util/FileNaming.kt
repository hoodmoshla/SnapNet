package com.snapnet.util

import java.io.File

/**
 * Filename construction and sanitisation.
 *
 * The default output name is `Uploader - Title.ext`, matching the product requirement. yt-dlp
 * performs sanitisation of its own, but SnapNet also needs these rules for the cases it controls:
 * names typed by the user, playlist/chapter sub-directories, and the collision-suffixed names used
 * when re-downloading into a folder that already contains the file.
 */
object FileNaming {

    /** Characters that are illegal on at least one supported filesystem, plus control characters. */
    private val ILLEGAL_CHARACTERS = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    /** Windows silently strips trailing dots and spaces; do it explicitly so behaviour is stable. */
    private val TRAILING_DOTS_OR_SPACES = Regex("""[. ]+$""")

    private val WHITESPACE_RUN = Regex("""\s+""")

    /**
     * Device-independent reserved device names. A file called `CON` cannot be created on Windows and
     * such names also break some Android file managers and MTP implementations.
     */
    private val RESERVED_NAMES: Set<String> =
        buildSet {
            addAll(listOf("CON", "PRN", "AUX", "NUL"))
            for (i in 1..9) {
                add("COM$i")
                add("LPT$i")
            }
        }

    /**
     * Maximum length of the generated base name, in UTF-16 code units.
     *
     * Android's `ext4`/`f2fs` limit a name to 255 bytes, and non-ASCII titles can use 3-4 bytes per
     * character, so 180 leaves generous headroom for multi-byte titles and the extension.
     */
    const val MAX_LENGTH: Int = 180

    /** Placeholder used when a title or uploader is missing or reduces to nothing. */
    const val UNKNOWN = "Unknown"

    /**
     * yt-dlp output template producing `Uploader - Title [id].ext`.
     *
     * The `%(uploader,channel,creator|Unknown)s` alternates cover the different field names various
     * extractors use, and `[%(id)s]` keeps the name unique across re-uploads with identical titles.
     * `.180B` truncates by *bytes*, which is what the filesystem limit is actually expressed in.
     */
    const val OUTPUT_TEMPLATE: String =
        "%(uploader,channel,creator|$UNKNOWN)s - %(title).160B [%(id)s].%(ext)s"

    /**
     * Sanitises [raw] into a string safe to use as a single path segment.
     *
     * The result never contains a path separator, is never `.`/`..`, is never empty, and is
     * truncated to [maxLength].
     */
    fun sanitize(raw: String, maxLength: Int = MAX_LENGTH): String {
        var name = raw.replace(ILLEGAL_CHARACTERS, "_")
        name = WHITESPACE_RUN.replace(name, " ")
        name = name.trim()
        name = TRAILING_DOTS_OR_SPACES.replace(name, "")
        if (name.isEmpty() || name == "." || name == "..") return UNKNOWN
        if (RESERVED_NAMES.contains(name.uppercase())) name = "_$name"
        if (name.length > maxLength) {
            name =
                name.substring(0, maxLength)
                    .let { TRAILING_DOTS_OR_SPACES.replace(it, "") }
                    .ifEmpty { UNKNOWN }
        }
        return name
    }

    /**
     * Builds the base name (without extension) as `Uploader - Title`, falling back to [UNKNOWN] for
     * either half and degrading to a single component when both would be identical.
     */
    fun buildBaseName(uploader: String?, title: String?): String {
        val cleanUploader = sanitize(uploader.orEmpty().ifBlank { UNKNOWN })
        val cleanTitle = sanitize(title.orEmpty().ifBlank { UNKNOWN })
        val base =
            if (cleanUploader == cleanTitle || cleanUploader == UNKNOWN) cleanTitle
            else "$cleanUploader - $cleanTitle"
        // The combined name may exceed the limit even when each half does not.
        return sanitize(base)
    }

    /**
     * Returns a file path inside [dir] that does not yet exist, appending ` (1)`, ` (2)`, ... to the
     * base name as needed.
     *
     * @param extension extension *with* the leading dot (e.g. `.mp4`); pass an empty string for none.
     */
    fun uniqueFile(dir: File, baseName: String, extension: String, maxAttempts: Int = 1000): File {
        val safeBase = sanitize(baseName)
        val first = File(dir, "$safeBase$extension")
        if (!first.exists()) return first
        for (index in 1..maxAttempts) {
            val candidate = File(dir, "$safeBase ($index)$extension")
            if (!candidate.exists()) return candidate
        }
        return first
    }

    /** Extracts the extension of [fileName] including the dot, or an empty string when there is none. */
    fun extensionOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot <= 0 || dot == fileName.length - 1) "" else fileName.substring(dot)
    }
}
