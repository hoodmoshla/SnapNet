package com.snapnet.util

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Proves that the yt-dlp engine can actually run, by running it.
 *
 * Earlier revisions decided this by reading `yt_dlp/version.py` out of the zipapp and by checking
 * that the file began with the ZIP local-header signature `PK\x03\x04`. Both are proxies for the
 * real question, and both fail for reasons that have nothing to do with the engine working:
 *
 *  * a zipapp may carry an executable/shebang prefix, so the archive does not start at byte 0 and a
 *    four-byte signature test rejects a perfectly good binary;
 *  * a version that cannot be parsed was treated as "too old", which blocked downloads and then
 *    failed a hard `check()` — crashing the app with
 *    `yt-dlp engine does not support --js-runtimes after update`.
 *
 * The probe below asks the question directly. `youtubedl-android` appends
 * `--js-runtimes quickjs:<path>` and `--ffmpeg-location` while building the command for *every*
 * request, so if `--version` succeeds, the engine accepted those options too.
 */
object YtDlpEngine {

    private const val TAG = "YtDlpEngine"

    /** yt-dlp versions are date based: `2025.11.12`. */
    private val VERSION_PATTERN = Regex("""\b(\d{4}\.\d{1,2}\.\d{1,2}(?:\.\d+)?)\b""")

    /**
     * Runs the engine and returns the version it reports.
     *
     * @return success with the reported version, or failure when the engine could not be executed,
     *   exited non-zero, or did not report a version.
     */
    suspend fun probeVersion(): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                    val request = YoutubeDLRequest(listOf("--version"))
                    val response = YoutubeDL.getInstance().execute(request)
                    val output = "${response.out}\n${response.err}"
                    VERSION_PATTERN.find(output)?.groupValues?.get(1)
                        ?: throw IllegalStateException(
                            "the engine did not report a version; output was: ${output.trim().take(200)}"
                        )
                }
                .onFailure { Log.w(TAG, "yt-dlp engine probe failed", it) }
        }

    /**
     * Deletes any runtime-installed engine so that `youtubedl-android` re-extracts the copy bundled
     * inside the APK.
     *
     * The bundled engine in 0.18.1 is a current release that understands `--js-runtimes`, so this is
     * the recovery path when an update has left an unusable binary behind: it is always better than
     * refusing to download at all.
     */
    fun restoreBundledEngine(context: Context): Result<Unit> =
        runCatching {
                val dir = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp")
                if (dir.exists() && !dir.deleteRecursively()) {
                    Log.w(TAG, "could not fully clear ${dir.absolutePath} before restoring")
                }
                YoutubeDL.getInstance().init_ytdlp(context, dir)
                Log.i(TAG, "restored the engine bundled inside the APK")
                Unit
            }
            .onFailure { Log.e(TAG, "could not restore the bundled yt-dlp engine", it) }
}
