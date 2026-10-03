package com.snapnet.util

import android.content.Context
import android.util.Log
import com.snapnet.util.PreferenceUtil.getBoolean
import com.snapnet.util.PreferenceUtil.getString
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * A JavaScript engine that yt-dlp can use to run its external-JS (EJS) challenge solver.
 *
 * Why this exists: modern yt-dlp needs a JS runtime to solve YouTube's `n`/signature challenges and
 * to produce some Proof-of-Origin tokens. Without one, yt-dlp logs
 * *"No supported JavaScript runtime could be found ... some formats may be missing"* and silently
 * drops formats. This was verified against yt-dlp 2026.08.19.
 *
 * yt-dlp accepts `--js-runtimes RUNTIME[:PATH]`. The supported engines, in yt-dlp's own priority
 * order, are `deno`, `node`, `quickjs` and `bun`; only `deno` is enabled by default, so SnapNet
 * passes an explicit runtime instead of relying on those defaults.
 *
 * Android execution note: this binary is executed by yt-dlp, so it must live where Android permits
 * `exec()`. Since `targetSdk >= 29`, executables may not be run from writable app data (the "W^X"
 * restriction); only `nativeLibraryDir` is reliably executable. Bundled engines are therefore
 * packaged as `lib*.so` files in `jniLibs`.
 */
enum class JsRuntimeKind(
    /** The identifier yt-dlp expects in `--js-runtimes`. */
    val ytDlpName: String,
    /**
     * File names to look for, in order. The `lib*.so` spellings are what Android packaging requires
     * for anything shipped in `jniLibs`; the bare names are what a user-supplied install uses.
     */
    val fileNames: List<String>,
) {
    QUICKJS("quickjs", listOf("libqjs.so", "qjs", "quickjs")),
    DENO("deno", listOf("libdeno.so", "deno")),
    NODE("node", listOf("libnode.so", "node")),
    BUN("bun", listOf("libbun.so", "bun")),
    ;

    companion object {
        /** Same priority order yt-dlp documents (highest first). */
        val priorityOrder: List<JsRuntimeKind> = listOf(DENO, NODE, QUICKJS, BUN)
    }
}

/** A located, usable JavaScript engine. */
data class JsRuntime(
    val kind: JsRuntimeKind,
    val executable: File,
) {
    /** The value for yt-dlp's `--js-runtimes` option, e.g. `quickjs:/data/app/.../libqjs.so`. */
    val optionValue: String
        get() = "${kind.ytDlpName}:${executable.absolutePath}"

    val displayName: String
        get() = "${kind.ytDlpName} (${executable.name})"
}

/**
 * Finds a JavaScript runtime without any network access.
 *
 * Search order:
 * 1. an explicit user override (Advanced settings), if usable — this may name the binary or its
 *    containing directory, and the engine kind is inferred from the file name;
 * 2. the app's `nativeLibraryDir`, the only location Android reliably allows execution from.
 */
object JsRuntimeLocator {

    private const val TAG = "JsRuntimeLocator"

    /** The engine kind implied by a file name, or null when it cannot be inferred. */
    private fun kindOf(fileName: String): JsRuntimeKind? {
        for (kind in JsRuntimeKind.entries) {
            if (kind.fileNames.any { fileName.equals(it, ignoreCase = true) }) return kind
        }
        // Fall back to a substring match so `libquickjs.so` / `qjs-2024` still resolve.
        return JsRuntimeKind.entries.firstOrNull { fileName.contains(it.ytDlpName, ignoreCase = true) }
    }

    private fun usable(file: File): Boolean = file.isFile && file.canExecute()

    /** @return directories that should be scanned, in priority order. */
    private fun searchRoots(context: Context, overridePath: String?): List<File> {
        val roots = ArrayList<File>(2)
        overridePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.let { override ->
                // The override may point at the binary itself or at its directory.
                roots += if (override.isDirectory) override else override.parentFile ?: override
            }
        roots += File(context.applicationInfo.nativeLibraryDir)
        return roots
    }

    /**
     * @param overridePath optional user-specified binary or directory.
     * @return the highest-priority usable runtime, or null when none is present.
     */
    fun find(context: Context, overridePath: String? = null): JsRuntime? {
        // 1. Honour an explicit file override immediately.
        overridePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf(::usable)
            ?.let { file -> kindOf(file.name)?.let { return JsRuntime(it, file) } }

        // 2. Scan the known roots, preferring engines in yt-dlp's documented priority order.
        val roots = searchRoots(context, overridePath).filter { it.isDirectory }
        for (kind in JsRuntimeKind.priorityOrder) {
            for (root in roots) {
                for (name in kind.fileNames) {
                    val candidate = File(root, name)
                    if (usable(candidate)) {
                        Log.d(TAG, "found ${kind.ytDlpName} runtime at ${candidate.absolutePath}")
                        return JsRuntime(kind, candidate)
                    }
                }
            }
        }

        Log.d(TAG, "no JavaScript runtime available; EJS challenge solving will be disabled")
        return null
    }

    /** Human-readable summary used by the Advanced settings screen and the debug log. */
    fun describe(context: Context, overridePath: String? = null): String =
        find(context, overridePath)?.displayName ?: "none"
}

/**
 * Adds yt-dlp's `--js-runtimes` option when a runtime is available.
 *
 * Passing this explicitly makes SnapNet behave identically whether or not the user has deno
 * installed system-wide, and it makes the dependency visible in the command log.
 */
fun YoutubeDLRequest.enableJsRuntime(context: Context): YoutubeDLRequest = apply {
    if (!JS_RUNTIME_AUTO.getBoolean()) return@apply
    JsRuntimeLocator.find(context, JS_RUNTIME_PATH.getString())?.let {
        addOption("--js-runtimes", it.optionValue)
    }
}
