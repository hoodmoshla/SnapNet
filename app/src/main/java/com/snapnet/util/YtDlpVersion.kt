package com.snapnet.util

import android.content.Context
import android.util.Log
import java.util.zip.ZipInputStream

/**
 * Reads the version of the yt-dlp build that is bundled inside `youtubedl-android`.
 *
 * The library only records a version in its own preferences after a *runtime update*, so a freshly
 * installed app used to report an empty engine version — which made bug reports useless and hid the
 * fact that the bundled engine was years out of date.
 *
 * The bundled engine is a Python zipapp, so its `yt_dlp/version.py` can be read straight out of the
 * APK without executing anything. That gives an exact answer even offline and on first launch.
 */
object YtDlpVersion {

    private const val TAG = "YtDlpVersion"

    private const val VERSION_ENTRY = "yt_dlp/version.py"

    /** `__version__ = '2025.11.12'` */
    private val VERSION_PATTERN = Regex("""__version__\s*=\s*'([^']+)'""")

    /**
     * @return the version of the bundled engine, or null when it cannot be determined.
     */
    fun resolveBundledVersion(context: Context): String? = runCatching {
            context.resources
                .openRawResource(com.yausername.youtubedl_android.R.raw.ytdlp)
                .use { raw ->
                    ZipInputStream(raw).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == VERSION_ENTRY) {
                                val text = zip.readBytes().toString(Charsets.UTF_8)
                                return@runCatching VERSION_PATTERN.find(text)?.groupValues?.get(1)
                            }
                            entry = zip.nextEntry
                        }
                        null
                    }
                }
        }
        .onFailure { Log.w(TAG, "could not read the bundled yt-dlp version", it) }
        .getOrNull()
}
