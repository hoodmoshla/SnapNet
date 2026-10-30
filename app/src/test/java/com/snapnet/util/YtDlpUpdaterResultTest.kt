package com.snapnet.util

import com.snapnet.R
import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpUpdaterResultTest {

    @Test
    fun failedUpdateReportsThatThePreviousEngineWasRetained() {
        assertEquals(
            YtDlpUpdater.Result.FAILED_ROLLED_BACK,
            YtDlpUpdater.failureResult(hadWorkingEngine = true),
        )
        assertEquals(
            YtDlpUpdater.Result.FAILED,
            YtDlpUpdater.failureResult(hadWorkingEngine = false),
        )
    }

    @Test
    fun manualUpdateUsesTheRollbackMessageOnlyWhenThePreviousEngineIsAvailable() {
        assertEquals(
            R.string.yt_dlp_update_failed_rolled_back,
            UpdateUtil.manualUpdateFailureMessageResource(YtDlpUpdater.Result.FAILED_ROLLED_BACK),
        )
        assertEquals(
            R.string.yt_dlp_update_failed_with_reason,
            UpdateUtil.manualUpdateFailureMessageResource(YtDlpUpdater.Result.FAILED),
        )
    }
}
