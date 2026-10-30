package com.snapnet.util

import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpUpdateNetworkStateTest {
    @Test
    fun requiresValidatedInternetForAutomaticUpdates() {
        assertEquals(
            YtDlpUpdateNetworkState.NO_INTERNET,
            classifyYtDlpUpdateNetworkState(false, false, false, false, false),
        )
        assertEquals(
            YtDlpUpdateNetworkState.NO_INTERNET,
            classifyYtDlpUpdateNetworkState(true, false, false, false, false),
        )
        assertEquals(
            YtDlpUpdateNetworkState.UNVALIDATED,
            classifyYtDlpUpdateNetworkState(true, true, false, false, false),
        )
        assertEquals(
            YtDlpUpdateNetworkState.METERED_CONFIRMATION_REQUIRED,
            classifyYtDlpUpdateNetworkState(true, true, true, true, false),
        )
        assertEquals(
            YtDlpUpdateNetworkState.METERED_CONFIRMATION_REQUIRED,
            classifyYtDlpUpdateNetworkState(true, true, false, true, false),
        )
        assertEquals(
            YtDlpUpdateNetworkState.AVAILABLE,
            classifyYtDlpUpdateNetworkState(true, true, true, false, false),
        )
        assertEquals(
            YtDlpUpdateNetworkState.AVAILABLE,
            classifyYtDlpUpdateNetworkState(true, true, true, true, true),
        )
    }
}
