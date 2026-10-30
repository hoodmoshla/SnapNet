package com.snapnet.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

/**
 * Reads the version of the yt-dlp build that SnapNet will actually execute, and answers whether that
 * build is new enough for the features the app relies on.
 *
 * Two copies of the engine can exist on a device:
 *
 *  * the one bundled in `youtubedl-android`, inside the APK (`res/raw/ytdlp`);
 *  * the one installed by a runtime update, under `noBackupFilesDir/youtubedl-android/yt-dlp/yt-dlp`.
 *
 * The installed copy wins when present, because that is the process yt-dlp will actually run. Both
 * are Python zipapps, so `yt_dlp/version.py` can be read straight out of them without executing
 * anything — which is what makes the version report correct even before the first update.
 */
object YtDlpVersion {

    private const val TAG = "YtDlpVersion"

    private const val VERSION_ENTRY = "yt_dlp/version.py"

    /** `__version__ = '2025.11.12'` */
    private val VERSION_PATTERN = Regex("""__version__\s*=\s*'([^']+)'""")

    /**
     * The oldest engine that understands `--js-runtimes`.
     *
     * yt-dlp added the option in early 2025. Anything older rejects it outright with
     * `no such option: --js-runtimes`, which aborts every request — including plain YouTube and
     * Facebook extraction. The engine must be updated before any request is issued.
     */
    const val MIN_VERSION_FOR_JS_RUNTIMES = "2025.01.01"

    /** Match yt-dlp's own warning threshold for binaries that have aged past 90 days. */
    const val STALE_AFTER_DAYS = 90

    private val VERSION_DATE_PATTERN =
        Regex("""^(\d{4}\.\d{1,2}\.\d{1,2})(?:\.\d+)?(?:[-+].*)?$""")

    /** Where `youtubedl-android` keeps a runtime-installed engine. */
    private fun installedBinary(context: Context): File =
        File(File(File(context.noBackupFilesDir, "youtubedl-android"), "yt-dlp"), "yt-dlp")

    private fun readVersion(zip: ZipInputStream): String? {
        zip.use {
            var entry = it.nextEntry
            while (entry != null) {
                if (entry.name == VERSION_ENTRY) {
                    val text = it.readBytes().toString(Charsets.UTF_8)
                    return VERSION_PATTERN.find(text)?.groupValues?.get(1)
                }
                entry = it.nextEntry
            }
        }
        return null
    }

    /** Version of the runtime-installed engine, or null when no update has been installed. */
    fun resolveInstalledVersion(context: Context): String? =
        runCatching {
                val file = installedBinary(context)
                if (!file.isFile) return@runCatching null
                readVersion(ZipInputStream(file.inputStream()))
            }
            .onFailure { Log.w(TAG, "could not read the installed yt-dlp version", it) }
            .getOrNull()

    /** Version of the engine bundled inside the APK. */
    fun resolveBundledVersion(context: Context): String? =
        runCatching {
                readVersion(
                    ZipInputStream(
                        context.resources.openRawResource(
                            com.yausername.youtubedl_android.R.raw.ytdlp
                        )
                    )
                )
            }
            .onFailure { Log.w(TAG, "could not read the bundled yt-dlp version", it) }
            .getOrNull()

    /**
     * The version that will actually run: the installed engine when an update exists, otherwise the
     * bundled one. This is what the app reports and what the readiness check uses.
     */
    fun resolveEffectiveVersion(context: Context): String? =
        resolveInstalledVersion(context) ?: resolveBundledVersion(context)

    /** Exact file that youtubedl-android 0.18.1 passes to Python. */
    fun effectiveBinaryPath(context: Context): String = installedBinary(context).absolutePath

    /** yt-dlp versions are date based (`YYYY.MM.DD`), so a lexicographic compare is correct. */
    fun compare(a: String?, b: String?): Int {
        if (a == null) return -1
        if (b == null) return 1
        return a.trim().compareTo(b.trim())
    }

    /** Returns true when the version date is older than yt-dlp's 90-day update warning threshold. */
    fun isOlderThan90Days(
        version: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val versionDateText =
            VERSION_DATE_PATTERN.matchEntire(version.orEmpty().trim())
                ?.groupValues
                ?.get(1)
                ?: return true
        val formatter =
            SimpleDateFormat("yyyy.M.d", Locale.ROOT).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
        val position = ParsePosition(0)
        val releaseDate = formatter.parse(versionDateText, position)
        if (releaseDate == null || position.index != versionDateText.length) return true

        val cutoff =
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, -STALE_AFTER_DAYS)
            }.time
        return releaseDate.before(cutoff)
    }

    /**
     * Forces an update for each stale version, then retries after the normal interval if no newer
     * release was available.
     */
    fun shouldForceStaleRefresh(
        version: String?,
        lastForcedRefreshVersion: String?,
        lastUpdateTime: Long,
        updateIntervalMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean =
        isOlderThan90Days(version, nowMillis) &&
            (
                (version ?: "unknown") != lastForcedRefreshVersion ||
                    lastUpdateTime <= 0L ||
                    nowMillis >= lastUpdateTime + updateIntervalMillis
            )

    /** @return true when [version] understands `--js-runtimes`. */
    fun supportsJsRuntimes(version: String?): Boolean =
        compare(version, MIN_VERSION_FOR_JS_RUNTIMES) >= 0

    /** Convenience: does the engine that will run support `--js-runtimes`? */
    fun effectiveEngineSupportsJsRuntimes(context: Context): Boolean =
        runCatching { supportsJsRuntimes(resolveEffectiveVersion(context)) }.getOrDefault(false)
}
