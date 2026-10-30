package com.snapnet.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.snapnet.R
import com.snapnet.util.YtDlpUpdateNetworkState

@Composable
internal fun YtDlpUpdateNetworkDialog(
    state: YtDlpUpdateNetworkState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message =
        when (state) {
            YtDlpUpdateNetworkState.METERED_CONFIRMATION_REQUIRED ->
                R.string.yt_dlp_metered_network_prompt
            YtDlpUpdateNetworkState.UNVALIDATED -> R.string.yt_dlp_unvalidated_network_prompt
            else -> return
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.yt_dlp_network_confirmation_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.yt_dlp_continue_once))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
