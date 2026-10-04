package com.snapnet.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Reporting helper for the JavaScript runtime that yt-dlp uses to solve YouTube's JS challenges.
 *
 * **SnapNet does not inject `--js-runtimes` itself.** `youtubedl-android` 0.18.1 bundles its own
 * QuickJS build for every ABI and appends `--js-runtimes quickjs:<nativeLibraryDir>/libqjs.so` while
 * it builds the yt-dlp command. It also ships the `yt-dlp-ejs` package that yt-dlp needs.
 *
 * Doing it here as well would be redundant, and on 0.17.3 — whose bundled yt-dlp predates the option
 * entirely — it produced:
 *
 *     yt-dlp: error: no such option: --js-runtimes
 *
 * This object therefore only *locates* the runtime so the app can show which engine is in use.
 */
object JsRuntimeLocator {

    private const val TAG = "JsRuntimeLocator"

    /** Engines yt-dlp can drive, in yt-dlp's own order of preference. */
    enum class Kind(val ytDlpName: String, val fileNames: List<String>) {
        QUICKJS("quickjs", listOf("libqjs.so", "qjs")),
        DENO("deno", listOf("libdeno.so", "deno")),
        NODE("node", listOf("libnode.so", "node")),
        BUN("bun", listOf("libbun.so", "bun")),
    }

    data class Runtime(val kind: Kind, val file: File) {
        val ytDlpValue: String
            get() = "${kind.ytDlpName}:${file.absolutePath}"

        val displayName: String
            get() = "${kind.ytDlpName} (${file.name})"
    }

    /**
     * Locates a runtime.
     *
     * Only `nativeLibraryDir` is searched: it is where `youtubedl-android` ships `libqjs.so` and the
     * only location Android reliably permits `exec()` from, so a copy placed in writable app storage
     * would never run anyway.
     */
    fun find(context: Context): Runtime? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        for (kind in Kind.entries) {
            for (name in kind.fileNames) {
                val candidate = File(nativeDir, name)
                if (candidate.isFile) {
                    Log.d(TAG, "detected ${kind.ytDlpName} runtime at ${candidate.absolutePath}")
                    return Runtime(kind, candidate)
                }
            }
        }
        Log.d(TAG, "no JavaScript runtime found in ${nativeDir.absolutePath}")
        return null
    }

    /** e.g. `quickjs (libqjs.so)`; used by the advanced settings and the crash report. */
    fun describe(context: Context): String = find(context)?.displayName ?: "none"
}
