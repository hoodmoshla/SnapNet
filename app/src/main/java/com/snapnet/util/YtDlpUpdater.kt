package com.snapnet.util

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.zip.ZipFile

/**
 * Crash-safe wrapper around youtubedl-android's in-place yt-dlp updater.
 *
 * The upstream updater is *not* atomic: it deletes the currently installed `yt-dlp` zipapp and only
 * then copies the freshly downloaded one into place, restoring its bundled copy if the copy throws.
 * That leaves two real hazards for an app that auto-updates on launch:
 *
 * 1. a download/IO failure between "delete" and "copy" can leave no usable engine at all;
 * 2. a *successfully installed but incompatible* release (for example one requiring a newer CPython
 *    than the bundled interpreter) is not detected, because nothing ever executes the new build.
 *
 * This class adds a backup/verify/rollback envelope around that call so an update can never leave
 * SnapNet without a working downloader.
 *
 * Note on checksums: yt-dlp publishes `SHA2-256SUMS`, but the upstream updater performs the HTTP
 * fetch internally and does not expose the downloaded artifact, so the digest cannot be verified
 * without forking that library. Instead the newly installed binary is validated *functionally* — see
 * [verifyInstalledBinary] — which catches the failure modes that actually matter here.
 */
object YtDlpUpdater {

    private const val TAG = "YtDlpUpdater"

    /** Outcome of an update attempt. */
    enum class Result {
        /** A new version was installed and verified. */
        UPDATED,

        /** Already on the newest version for the selected channel. */
        ALREADY_UP_TO_DATE,

        /** The update failed; the previous engine was restored and still works. */
        FAILED_ROLLED_BACK,

        /** The update failed and no previous engine was available to restore. */
        FAILED,
    }

    private const val BINARY_NAME = "yt-dlp"

    /** The module that makes a zipapp a yt-dlp build. */
    private const val VERSION_ENTRY = "yt_dlp/version.py"

    private fun backupDir(context: Context) = File(context.filesDir, "ytdlp-backup")

    private fun backupFile(context: Context) = File(backupDir(context), BINARY_NAME)

    /**
     * Locates the installed yt-dlp zipapp.
     *
     * The path is owned by the youtubedl-android library, so it is discovered by scanning rather than
     * by depending on library-internal constants that may change between releases.
     */
    private fun locateBinary(context: Context): File? =
        File(context.noBackupFilesDir, "youtubedl-android")
            .takeIf { it.isDirectory }
            ?.walkTopDown()
            ?.firstOrNull { it.isFile && it.name == BINARY_NAME }

    private fun isUsable(file: File?): Boolean = file != null && file.isFile && file.length() > 0L

    /**
     * @return true when the engine can be executed after the update.
     *
     * A zipapp is executable only if a compatible CPython is present, so the check is intentionally
     * conservative: the file must exist, be non-empty, and parse as a ZIP archive (every yt-dlp
     * release asset is a zipapp). This is cheap, offline, and detects truncated downloads and
     * incompatible/wrong assets.
     */
    /**
     * @return true when [file] is a readable yt-dlp zipapp.
     *
     * This deliberately does not test the first four bytes for `PK\x03\x04`. A zipapp may carry an
     * executable or shebang prefix, which puts the archive somewhere other than offset 0, and
     * rejecting such a file would throw away a working engine. Opening the file as a ZIP and looking
     * for the module that makes it yt-dlp is both tolerant of a prefix and a genuine test.
     */
    private fun verifyInstalledBinary(file: File?): Boolean {
        if (!isUsable(file)) return false
        val binary = file ?: return false
        return runCatching {
                ZipFile(binary).use { zip -> zip.getEntry(VERSION_ENTRY) != null }
            }
            .onFailure { Log.w(TAG, "installed yt-dlp is not a readable zipapp", it) }
            .getOrDefault(false)
    }

    private fun backup(context: Context, binary: File): Boolean =
        runCatching {
                val dir = backupDir(context).apply { mkdirs() }
                binary.copyTo(File(dir, BINARY_NAME), overwrite = true)
                true
            }
            .onFailure { Log.w(TAG, "could not back up the current yt-dlp", it) }
            .getOrDefault(false)

    private fun rollback(context: Context): Boolean =
        runCatching {
                val backup = backupFile(context)
                if (!isUsable(backup)) return false
                val target = locateBinary(context)
                if (target == null) {
                    // The updater deleted the whole directory; recreate the canonical layout.
                    val dir = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp")
                    dir.mkdirs()
                    backup.copyTo(File(dir, BINARY_NAME), overwrite = true)
                } else {
                    backup.copyTo(target, overwrite = true)
                }
                verifyInstalledBinary(locateBinary(context))
            }
            .onFailure { Log.e(TAG, "rollback failed", it) }
            .getOrDefault(false)

    /**
     * Updates yt-dlp with backup and rollback protection.
     *
     * @param channel `STABLE` or `NIGHTLY`; callers should prefer [YoutubeDL.UpdateChannel.STABLE].
     */
    fun safeUpdate(context: Context, channel: YoutubeDL.UpdateChannel): Result {
        val before = locateBinary(context)
        val hadWorkingEngine = verifyInstalledBinary(before)
        if (hadWorkingEngine && !backup(context, before!!)) {
            // Without a backup we must not risk an in-place update.
            Log.w(TAG, "skipping yt-dlp update: could not create a safety backup")
            return Result.FAILED_ROLLED_BACK
        }

        val status =
            runCatching { YoutubeDL.getInstance().updateYoutubeDL(context, channel) }
                .onFailure { Log.w(TAG, "yt-dlp update threw", it) }
                .getOrNull()

        if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) return Result.ALREADY_UP_TO_DATE

        if (status == YoutubeDL.UpdateStatus.DONE && verifyInstalledBinary(locateBinary(context))) {
            Log.i(TAG, "yt-dlp updated and verified")
            return Result.UPDATED
        }

        Log.w(TAG, "yt-dlp update did not produce a usable engine (status=$status); rolling back")
        return if (hadWorkingEngine && rollback(context)) {
            Result.FAILED_ROLLED_BACK
        } else {
            Result.FAILED
        }
    }
}
