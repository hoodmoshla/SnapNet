package com.snapnet.ui.page

import androidx.compose.runtime.Composable
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapnet.Downloader
import com.snapnet.App
import com.snapnet.util.PreferenceUtil
import com.snapnet.util.PreferenceUtil.getBoolean
import com.snapnet.util.PreferenceUtil.getLong
import com.snapnet.util.PreferenceUtil.getString
import com.snapnet.util.UpdateUtil
import com.snapnet.util.YtDlpVersion
import com.snapnet.util.YtDlpUpdater
import com.snapnet.util.YT_DLP_AUTO_UPDATE
import com.snapnet.util.YT_DLP_UPDATE_INTERVAL
import com.snapnet.util.YT_DLP_UPDATE_TIME
import com.snapnet.util.YT_DLP_STALE_REFRESH_VERSION
import com.snapnet.util.YT_DLP_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "YtdlpUpdater"

@Composable
fun YtdlpUpdater() {

    val downloaderState by Downloader.downloaderState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (downloaderState !is Downloader.State.Idle) return@LaunchedEffect

        val effectiveVersion =
            withContext(Dispatchers.IO) { YtDlpVersion.resolveEffectiveVersion(App.context) }
        val lastUpdateTime = YT_DLP_UPDATE_TIME.getLong()
        val updateInterval = YT_DLP_UPDATE_INTERVAL.getLong()
        val currentTime = System.currentTimeMillis()
        val forceStaleRefresh =
            YtDlpVersion.shouldForceStaleRefresh(
                effectiveVersion,
                YT_DLP_STALE_REFRESH_VERSION.getString(),
                lastUpdateTime,
                updateInterval,
                currentTime,
            )

        if (
            !YT_DLP_AUTO_UPDATE.getBoolean() &&
                !forceStaleRefresh &&
                YT_DLP_VERSION.getString().isNotEmpty()
        ) {
            return@LaunchedEffect
        }

        if (!PreferenceUtil.isNetworkAvailableForDownload()) {
            return@LaunchedEffect
        }

        // An engine older than --js-runtimes cannot run a single request: yt-dlp rejects the option
        // the library passes on every invocation, which aborts even a plain YouTube extraction. That
        // cannot wait for the configured interval, so the interval is skipped in that case.
        val engineSupportsJsRuntimes = YtDlpVersion.supportsJsRuntimes(effectiveVersion)
        val forceRefresh = forceStaleRefresh || !engineSupportsJsRuntimes
        if (forceStaleRefresh) {
            Log.w(
                TAG,
                "yt-dlp engine is older than ${YtDlpVersion.STALE_AFTER_DAYS} days; refreshing now",
            )
        } else if (!engineSupportsJsRuntimes) {
            Log.w(
                TAG,
                "yt-dlp engine is older than --js-runtimes; updating now, ignoring the update interval",
            )
        } else {
            if (!forceRefresh && currentTime < lastUpdateTime + updateInterval) {
                return@LaunchedEffect
            }
        }

        runCatching {
                Downloader.updateState(state = Downloader.State.Updating)
                val result = withContext(Dispatchers.IO) { UpdateUtil.updateYtDlp() }
                // Record whichever engine ended up installed, so the app reports the truth even
                // after a failed update that was rolled back.
                var installedVersion: String? = null
                withContext(Dispatchers.IO) {
                    YtDlpVersion.resolveEffectiveVersion(App.context)?.let { installed ->
                        installedVersion = installed
                        PreferenceUtil.encodeString(YT_DLP_VERSION, installed)
                        Log.i(TAG, "engine after update: $installed")
                    }
                }
                if (
                    forceStaleRefresh &&
                        result in
                            setOf(
                                YtDlpUpdater.Result.UPDATED,
                                YtDlpUpdater.Result.ALREADY_UP_TO_DATE,
                            ) &&
                        YtDlpVersion.isOlderThan90Days(installedVersion)
                ) {
                    PreferenceUtil.encodeString(
                        YT_DLP_STALE_REFRESH_VERSION,
                        installedVersion ?: effectiveVersion ?: "unknown",
                    )
                }
                when (result) {
                    YtDlpUpdater.Result.UPDATED ->
                        Log.i(TAG, "yt-dlp update succeeded")
                    YtDlpUpdater.Result.ALREADY_UP_TO_DATE ->
                        Log.i(TAG, "yt-dlp already matches the latest release")
                    YtDlpUpdater.Result.FAILED_ROLLED_BACK,
                    YtDlpUpdater.Result.FAILED ->
                        Log.e(TAG, "yt-dlp refresh failed; keeping the previous engine")
                }
            }
            .onFailure { Log.e(TAG, "yt-dlp update failed", it) }
        Downloader.updateState(state = Downloader.State.Idle)
    }
}
