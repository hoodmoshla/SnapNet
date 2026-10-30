package com.snapnet.ui.page

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.snapnet.App
import com.snapnet.Downloader
import com.snapnet.util.PreferenceUtil
import com.snapnet.util.PreferenceUtil.getBoolean
import com.snapnet.util.PreferenceUtil.getLong
import com.snapnet.util.PreferenceUtil.getString
import com.snapnet.util.UpdateUtil
import com.snapnet.util.YT_DLP_AUTO_UPDATE
import com.snapnet.util.YT_DLP_STALE_REFRESH_VERSION
import com.snapnet.util.YT_DLP_UPDATE_ATTEMPT_TIME
import com.snapnet.util.YT_DLP_UPDATE_DIAGNOSTIC
import com.snapnet.util.YT_DLP_UPDATE_INTERVAL
import com.snapnet.util.YT_DLP_UPDATE_STATUS
import com.snapnet.util.YT_DLP_UPDATE_TIME
import com.snapnet.util.YT_DLP_VERSION
import com.snapnet.util.YtDlpVersion
import com.snapnet.util.YtDlpUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "YtdlpUpdater"
private const val NETWORK_RETRY_DELAY_MILLIS = 60_000L
private const val FAILED_RETRY_DELAY_MILLIS = 15 * 60_000L

private fun recordUpdateStatus(status: String, diagnostic: String = "") {
    PreferenceUtil.encodeString(YT_DLP_UPDATE_STATUS, status)
    PreferenceUtil.encodeString(YT_DLP_UPDATE_DIAGNOSTIC, diagnostic)
}

@Composable
fun YtdlpUpdater() {
    LaunchedEffect(Unit) {
        while (true) {
            if (Downloader.downloaderState.value !is Downloader.State.Idle) {
                recordUpdateStatus("DEFERRED_DOWNLOAD_BUSY")
                Log.i(TAG, "yt-dlp refresh is waiting for the downloader to become idle")
                Downloader.downloaderState.first { it is Downloader.State.Idle }
            }

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
                recordUpdateStatus("SKIPPED_AUTO_UPDATE_DISABLED")
                Log.i(TAG, "yt-dlp refresh skipped: automatic updates are disabled and the engine is not stale")
                return@LaunchedEffect
            }

            // An engine older than --js-runtimes cannot run a single request: yt-dlp rejects the option
            // the library passes on every invocation, so the interval is skipped for that engine.
            val engineSupportsJsRuntimes = YtDlpVersion.supportsJsRuntimes(effectiveVersion)
            val forceRefresh = forceStaleRefresh || !engineSupportsJsRuntimes
            if (!forceRefresh && currentTime < lastUpdateTime + updateInterval) {
                recordUpdateStatus("SKIPPED_UPDATE_INTERVAL")
                Log.i(TAG, "yt-dlp refresh skipped: the configured update interval has not elapsed")
                return@LaunchedEffect
            }

            if (!PreferenceUtil.isNetworkAvailableForYtDlpUpdate()) {
                recordUpdateStatus("DEFERRED_NO_VALIDATED_NETWORK", "no_validated_network_or_metered_disabled")
                Log.i(TAG, "yt-dlp refresh deferred until validated internet is available on an allowed network")
                delay(NETWORK_RETRY_DELAY_MILLIS)
                continue
            }

            val previousStatus = YT_DLP_UPDATE_STATUS.getString()
            val previousAttemptTime = YT_DLP_UPDATE_ATTEMPT_TIME.getLong()
            val retryAfter = previousAttemptTime + FAILED_RETRY_DELAY_MILLIS
            if (
                previousStatus.startsWith("FAILED") &&
                    previousAttemptTime > 0L &&
                    currentTime < retryAfter
            ) {
                delay((retryAfter - currentTime).coerceAtMost(NETWORK_RETRY_DELAY_MILLIS))
                continue
            }

            // A download may have started while the version or network checks were running.
            if (Downloader.downloaderState.value !is Downloader.State.Idle) continue

            if (forceStaleRefresh) {
                Log.w(
                    TAG,
                    "yt-dlp engine is older than ${YtDlpVersion.STALE_AFTER_DAYS} days; refreshing now",
                )
            } else if (!engineSupportsJsRuntimes) {
                Log.w(TAG, "yt-dlp engine lacks --js-runtimes support; refreshing now")
            }

            Downloader.updateState(state = Downloader.State.Updating)
            val result =
                try {
                    withContext(Dispatchers.IO) { UpdateUtil.updateYtDlp() }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    recordUpdateStatus("FAILED", "unexpected_update_exception")
                    Log.e(TAG, "yt-dlp update failed unexpectedly (${exception.javaClass.simpleName})")
                    null
                } finally {
                    Downloader.updateState(state = Downloader.State.Idle)
                }

            if (result == null) {
                delay(FAILED_RETRY_DELAY_MILLIS)
                continue
            }

            val installedVersion =
                withContext(Dispatchers.IO) {
                    YtDlpVersion.resolveEffectiveVersion(App.context)?.also { installed ->
                        PreferenceUtil.encodeString(YT_DLP_VERSION, installed)
                        Log.i(TAG, "engine after update: $installed")
                    }
                }

            if (
                forceStaleRefresh &&
                    result in setOf(YtDlpUpdater.Result.UPDATED, YtDlpUpdater.Result.ALREADY_UP_TO_DATE) &&
                    YtDlpVersion.isOlderThan90Days(installedVersion)
            ) {
                PreferenceUtil.encodeString(
                    YT_DLP_STALE_REFRESH_VERSION,
                    installedVersion ?: effectiveVersion ?: "unknown",
                )
            }

            when (result) {
                YtDlpUpdater.Result.UPDATED -> {
                    Log.i(TAG, "yt-dlp update succeeded")
                    return@LaunchedEffect
                }
                YtDlpUpdater.Result.ALREADY_UP_TO_DATE -> {
                    Log.i(TAG, "yt-dlp already matches the latest release")
                    return@LaunchedEffect
                }
                YtDlpUpdater.Result.FAILED_ROLLED_BACK,
                YtDlpUpdater.Result.FAILED -> {
                    val diagnostic = YtDlpUpdater.lastFailureDiagnostic ?: "update_failed"
                    recordUpdateStatus(result.name, diagnostic)
                    Log.e(TAG, "yt-dlp refresh failed (${diagnostic}); preserving the recovered engine")
                    delay(FAILED_RETRY_DELAY_MILLIS)
                }
            }
        }
    }
}
