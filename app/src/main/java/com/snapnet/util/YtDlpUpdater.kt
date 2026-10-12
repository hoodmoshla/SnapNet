package com.snapnet.util

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads yt-dlp release assets only after validating them against the SHA-256 manifest published
 * with the same official GitHub release. The new asset is staged and verified before it replaces
 * the installed engine; the previous working engine is retained for rollback.
 */
object YtDlpUpdater {

    private const val TAG = "YtDlpUpdater"
    private const val BINARY_NAME = "yt-dlp"
    private const val CHECKSUMS_NAME = "SHA2-256SUMS"
    private const val VERSION_ENTRY = "yt_dlp/version.py"
    private const val STABLE_REPOSITORY = "yt-dlp/yt-dlp"
    private const val NIGHTLY_REPOSITORY = "yt-dlp/yt-dlp-nightly-builds"
    private const val MASTER_REPOSITORY = "yt-dlp/yt-dlp-master-builds"

    private val json = Json { ignoreUnknownKeys = true }
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .build()

    /** Outcome of an update attempt. */
    enum class Result {
        /** A new version was installed and verified. */
        UPDATED,

        /** The installed binary already matches the selected channel's latest release. */
        ALREADY_UP_TO_DATE,

        /** The update failed; the previous working engine is still available. */
        FAILED_ROLLED_BACK,

        /** The update failed and no previous engine was available to restore. */
        FAILED,
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String? = null,
        val assets: List<ReleaseAsset> = emptyList(),
    )

    @Serializable
    private data class ReleaseAsset(
        val name: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    )

    private fun repositoryFor(channel: YoutubeDL.UpdateChannel): String? =
        when (channel) {
            YoutubeDL.UpdateChannel.STABLE -> STABLE_REPOSITORY
            YoutubeDL.UpdateChannel.NIGHTLY -> NIGHTLY_REPOSITORY
            YoutubeDL.UpdateChannel.MASTER -> MASTER_REPOSITORY
            else -> null
        }

    private fun installedBinary(context: Context): File =
        File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")

    /** Finds the runtime-installed zipapp, falling back to the library's canonical install path. */
    private fun locateBinary(context: Context): File? =
        File(context.noBackupFilesDir, "youtubedl-android")
            .takeIf { it.isDirectory }
            ?.walkTopDown()
            ?.firstOrNull { it.isFile && it.name == BINARY_NAME }

    private fun isUsable(file: File?): Boolean = file != null && file.isFile && file.length() > 0L

    /** Accepts zipapps with or without a shebang/executable prefix, but requires yt-dlp's version module. */
    private fun verifyInstalledBinary(file: File?): Boolean {
        if (!isUsable(file)) return false
        val binary = file ?: return false
        return runCatching { ZipFile(binary).use { it.getEntry(VERSION_ENTRY) != null } }
            .onFailure { Log.w(TAG, "yt-dlp asset is not a readable zipapp", it) }
            .getOrDefault(false)
    }

    private fun backupDir(context: Context) = File(context.noBackupFilesDir, "ytdlp-backup")

    private fun backupFile(context: Context) = File(backupDir(context), BINARY_NAME)

    private fun backup(context: Context, binary: File): Boolean =
        runCatching {
                val dir = backupDir(context)
                if (!dir.exists() && !dir.mkdirs()) throw IOException("could not create yt-dlp backup directory")
                binary.copyTo(backupFile(context), overwrite = true)
                verifyInstalledBinary(backupFile(context))
            }
            .onFailure { Log.w(TAG, "could not back up the current yt-dlp", it) }
            .getOrDefault(false)

    private fun rollback(context: Context): Boolean =
        runCatching {
                val backup = backupFile(context)
                if (!verifyInstalledBinary(backup)) return false
                val target = locateBinary(context) ?: installedBinary(context)
                val dir = target.parentFile ?: return false
                if (!dir.exists() && !dir.mkdirs()) return false
                backup.copyTo(target, overwrite = true)
                verifyInstalledBinary(target)
            }
            .onFailure { Log.e(TAG, "rollback failed", it) }
            .getOrDefault(false)

    private fun responseText(url: String, accept: String): String {
        val request =
            Request.Builder()
                .url(url)
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub returned HTTP ${response.code}")
            response.body?.string() ?: throw IOException("GitHub returned an empty response")
        }
    }

    private fun releaseAssetUrl(release: Release, name: String, repository: String): String {
        val url =
            release.assets.singleOrNull { it.name == name }?.browserDownloadUrl
                ?: throw IOException("release is missing the $name asset")
        val tagName = release.tagName?.takeIf { it.isNotBlank() }
            ?: throw IOException("release has no tag")
        val uri = runCatching { URI(url) }.getOrNull()
        val expectedPrefix = "/$repository/releases/download/$tagName/"
        if (
            uri?.scheme != "https" ||
                uri.host != "github.com" ||
                uri.path?.startsWith(expectedPrefix) != true ||
                uri.path?.substringAfterLast('/') != name
        ) {
            throw IOException("release asset URL is not from the expected GitHub repository")
        }
        return url
    }

    private fun downloadAsset(url: String, destination: File) {
        val request = Request.Builder().url(url).header("Accept", "application/octet-stream").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub asset download failed with HTTP ${response.code}")
            val body = response.body ?: throw IOException("GitHub returned an empty asset")
            body.byteStream().use { input ->
                destination.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        }
    }

    /**
     * Downloads the selected release's yt-dlp asset and checksum manifest, verifies SHA-256, then
     * installs the verified zipapp with rollback protection.
     */
    fun safeUpdate(context: Context, channel: YoutubeDL.UpdateChannel): Result {
        val previousBinary = locateBinary(context)
        val hadWorkingEngine = verifyInstalledBinary(previousBinary)
        var downloadedAsset: File? = null

        try {
            val repository = repositoryFor(channel) ?: throw IOException("unsupported yt-dlp update channel")
            val releaseUrl = "https://api.github.com/repos/$repository/releases/latest"
            val release = json.decodeFromString<Release>(responseText(releaseUrl, "application/vnd.github+json"))
            if (release.tagName.isNullOrBlank()) throw IOException("release has no tag")

            val binaryUrl = releaseAssetUrl(release, BINARY_NAME, repository)
            val checksumsUrl = releaseAssetUrl(release, CHECKSUMS_NAME, repository)
            val manifest = responseText(checksumsUrl, "text/plain")
            val expectedSha256 = YtDlpChecksum.parseManifest(manifest, BINARY_NAME)
                ?: throw IOException("release checksum manifest has no unique yt-dlp digest")

            if (hadWorkingEngine && YtDlpChecksum.matches(previousBinary!!, expectedSha256)) {
                return Result.ALREADY_UP_TO_DATE
            }

            if (hadWorkingEngine && !backup(context, previousBinary!!)) {
                Log.w(TAG, "skipping yt-dlp update: could not create a verified safety backup")
                return Result.FAILED_ROLLED_BACK
            }

            downloadedAsset = File.createTempFile("yt-dlp-", ".download", context.cacheDir)
            downloadAsset(binaryUrl, downloadedAsset)
            if (!YtDlpChecksum.matches(downloadedAsset, expectedSha256)) {
                Log.w(TAG, "downloaded yt-dlp SHA-256 does not match the release manifest")
                return failureResult(hadWorkingEngine)
            }
            if (!verifyInstalledBinary(downloadedAsset)) {
                Log.w(TAG, "verified yt-dlp asset is not a usable zipapp")
                return failureResult(hadWorkingEngine)
            }

            if (!installVerifiedAsset(context, downloadedAsset)) {
                Log.w(TAG, "yt-dlp install failed; restoring the previous engine")
                return if (hadWorkingEngine && rollback(context)) Result.FAILED_ROLLED_BACK else Result.FAILED
            }

            Log.i(TAG, "yt-dlp updated and SHA-256 verified (release ${release.tagName})")
            return Result.UPDATED
        } catch (exception: Exception) {
            Log.w(TAG, "yt-dlp update failed", exception)
            return failureResult(hadWorkingEngine)
        } finally {
            downloadedAsset?.delete()
        }
    }

    private fun failureResult(hadWorkingEngine: Boolean): Result =
        if (hadWorkingEngine) Result.FAILED_ROLLED_BACK else Result.FAILED

    private fun installVerifiedAsset(context: Context, asset: File): Boolean {
        val target = locateBinary(context) ?: installedBinary(context)
        val directory = target.parentFile ?: return false
        if (!directory.exists() && !directory.mkdirs()) return false

        val staged = File(directory, "$BINARY_NAME.new")
        val previous = File(directory, "$BINARY_NAME.previous")
        if (staged.exists() && !staged.delete()) return false
        if (previous.exists() && !previous.delete()) return false
        asset.copyTo(staged, overwrite = true)
        if (!verifyInstalledBinary(staged)) {
            staged.delete()
            return false
        }

        val hadTarget = target.exists()
        if (hadTarget && !target.renameTo(previous)) {
            staged.delete()
            return false
        }
        if (!staged.renameTo(target)) {
            if (hadTarget) previous.renameTo(target)
            staged.delete()
            return false
        }
        if (!verifyInstalledBinary(target)) {
            target.delete()
            if (hadTarget) previous.renameTo(target)
            return false
        }
        if (previous.exists()) previous.delete()
        return true
    }
}
