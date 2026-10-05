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
import com.snapnet.util.YT_DLP_AUTO_UPDATE
import com.snapnet.util.YT_DLP_UPDATE_INTERVAL
import com.snapnet.util.YT_DLP_UPDATE_TIME
import com.snapnet.util.YT_DLP_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "YtdlpUpdater"

@Composable
fun YtdlpUpdater() {

    val downloaderState by Downloader.downloaderState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (downloaderState !is Downloader.State.Idle) return@LaunchedEffect

        if (!YT_DLP_AUTO_UPDATE.getBoolean() && YT_DLP_VERSION.getString().isNotEmpty())
            return@LaunchedEffect

        if (!PreferenceUtil.isNetworkAvailableForDownload()) {
            return@LaunchedEffect
        }

        // An engine older than --js-runtimes cannot run a single request: yt-dlp rejects the option
        // the library passes on every invocation, which aborts even a plain YouTube extraction. That
        // cannot wait for the configured interval, so the interval is skipped in that case.
        val engineSupportsJsRuntimes = YtDlpVersion.effectiveEngineSupportsJsRuntimes(App.context)
        if (!engineSupportsJsRuntimes) {
            Log.w(
                TAG,
                "yt-dlp engine is older than --js-runtimes; updating now, ignoring the update interval",
            )
        } else {
            val lastUpdateTime = YT_DLP_UPDATE_TIME.getLong()
            val currentTime = System.currentTimeMillis()
            if (currentTime < lastUpdateTime + YT_DLP_UPDATE_INTERVAL.getLong()) {
                return@LaunchedEffect
            }
        }

        runCatching {
                Downloader.updateState(state = Downloader.State.Updating)
                withContext(Dispatchers.IO) { UpdateUtil.updateYtDlp() }
                // Record whichever engine ended up installed, so the app reports the truth even
                // after a failed update that was rolled back.
                withContext(Dispatchers.IO) {
                    YtDlpVersion.resolveEffectiveVersion(App.context)?.let { installed ->
                        PreferenceUtil.encodeString(YT_DLP_VERSION, installed)
                        Log.i(TAG, "engine after update: $installed")
                    }
                }
            }
            .onFailure { Log.e(TAG, "yt-dlp update failed", it) }
        Downloader.updateState(state = Downloader.State.Idle)
    }
}
