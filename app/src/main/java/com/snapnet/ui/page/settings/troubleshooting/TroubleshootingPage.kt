package com.snapnet.ui.page.settings.troubleshooting

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snapnet.R
import com.snapnet.ui.common.Route
import com.snapnet.ui.common.booleanState
import com.snapnet.ui.component.PreferenceInfo
import com.snapnet.ui.component.PreferenceItem
import com.snapnet.ui.component.PreferenceSubtitle
import com.snapnet.ui.component.PreferenceSwitch
import com.snapnet.ui.component.YtDlpUpdateNetworkDialog
import com.snapnet.ui.page.settings.BasePreferencePage
import com.snapnet.ui.page.settings.general.YtdlpUpdateChannelDialog
import com.snapnet.util.PreferenceUtil
import com.snapnet.util.PreferenceUtil.getString
import com.snapnet.util.PreferenceUtil.updateBoolean
import com.snapnet.util.RESTRICT_FILENAMES
import com.snapnet.util.UpdateUtil
import com.snapnet.util.YT_DLP_VERSION
import com.snapnet.util.YtDlpUpdateNetworkState
import com.snapnet.util.makeToast
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.launch

@Composable
fun TroubleShootingPage(
    modifier: Modifier = Modifier,
    onNavigateTo: (String) -> Unit,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BasePreferencePage(
        modifier = modifier,
        title = stringResource(R.string.trouble_shooting),
        onBack = onBack,
    ) {
        LazyColumn(contentPadding = it) {
            item {
                OutlinedCard(modifier = Modifier.padding(16.dp)) {
                    PreferenceInfo(
                        modifier = Modifier,
                        text = stringResource(R.string.issue_tracker_hint),
                    )
                    val knownIssueUrlSnapNet = "https://github.com/hoodmoshla/SnapNet/issues"
                    PreferenceItem(
                        title = "SnapNet Issue Tracker",
                        description = null,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        onClick = { uriHandler.openUri(knownIssueUrlSnapNet) },
                    )

                    val knownIssueUrlYtdlp = "https://github.com/yt-dlp/yt-dlp/issues/3766"
                    PreferenceItem(
                        title = "yt-dlp Issue Tracker",
                        description = null,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        onClick = { uriHandler.openUri(knownIssueUrlYtdlp) },
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
            item { PreferenceSubtitle(text = stringResource(R.string.update)) }
            item {
                var isUpdating by remember { mutableStateOf(false) }
                var showYtdlpDialog by remember { mutableStateOf(false) }
                var ytdlpVersion by remember {
                    mutableStateOf(
                        YT_DLP_VERSION.getString().ifBlank {
                            context.getString(R.string.yt_dlp_version_unknown)
                        }
                    )
                }
                var lastReportedLatestVersion by remember {
                    mutableStateOf(
                        YoutubeDL.getInstance().version(context.applicationContext).orEmpty()
                    )
                }
                var pendingNetworkConfirmation by
                    remember { mutableStateOf<YtDlpUpdateNetworkState?>(null) }
                val versionDescription =
                    if (
                        lastReportedLatestVersion.isNotBlank() &&
                            lastReportedLatestVersion != ytdlpVersion
                    ) {
                        context.getString(
                            R.string.yt_dlp_versions_summary,
                            ytdlpVersion,
                            lastReportedLatestVersion,
                        )
                    } else {
                        context.getString(R.string.yt_dlp_current_engine, ytdlpVersion)
                    }

                fun startYtdlpUpdate() {
                    isUpdating = true
                    scope.launch {
                        try {
                            val outcome = UpdateUtil.updateYtDlpFromSettings()
                            ytdlpVersion = outcome.effectiveVersion
                            lastReportedLatestVersion =
                                YoutubeDL.getInstance()
                                    .version(context.applicationContext)
                                    .orEmpty()
                            context.makeToast(UpdateUtil.manualUpdateMessage(context, outcome))
                        } catch (exception: kotlinx.coroutines.CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            UpdateUtil.recordYtDlpUpdateStatus(
                                "FAILED",
                                "manual_update_exception",
                            )
                            context.makeToast(
                                context.getString(
                                    R.string.yt_dlp_update_failed_with_reason,
                                    "manual_update_exception",
                                )
                            )
                        } finally {
                            isUpdating = false
                        }
                    }
                }

                fun requestYtdlpUpdate(confirmed: Boolean = false) {
                    if (isUpdating) return
                    val networkState = PreferenceUtil.getYtDlpUpdateNetworkState()
                    when (networkState) {
                        YtDlpUpdateNetworkState.NO_INTERNET -> {
                            UpdateUtil.recordYtDlpUpdateStatus(
                                "MANUAL_NO_NETWORK",
                                "no_active_internet_connection",
                            )
                            context.makeToast(context.getString(R.string.yt_dlp_no_network))
                        }
                        YtDlpUpdateNetworkState.UNVALIDATED,
                        YtDlpUpdateNetworkState.METERED_CONFIRMATION_REQUIRED -> {
                            if (!confirmed) {
                                pendingNetworkConfirmation = networkState
                                UpdateUtil.recordYtDlpUpdateStatus(
                                    "MANUAL_CONFIRMATION_REQUIRED",
                                    if (networkState == YtDlpUpdateNetworkState.UNVALIDATED) {
                                        "manual_unvalidated_network_confirmation_required"
                                    } else {
                                        "manual_metered_network_confirmation_required"
                                    },
                                )
                                return
                            }
                            startYtdlpUpdate()
                        }
                        YtDlpUpdateNetworkState.AVAILABLE -> startYtdlpUpdate()
                    }
                }

                fun cancelNetworkConfirmation(state: YtDlpUpdateNetworkState) {
                    pendingNetworkConfirmation = null
                    UpdateUtil.recordYtDlpUpdateStatus(
                        "MANUAL_CANCELLED",
                        if (state == YtDlpUpdateNetworkState.UNVALIDATED) {
                            "user_cancelled_unvalidated_network"
                        } else {
                            "user_cancelled_metered_network"
                        },
                    )
                }

                PreferenceItem(
                    title = stringResource(id = R.string.ytdlp_update_action),
                    description = versionDescription,
                    enabled = !isUpdating,
                    leadingIcon = {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.padding(start = 8.dp, end = 16.dp)
                                        .size(24.dp)
                                        .padding(2.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Update,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp, end = 16.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        requestYtdlpUpdate()
                    },
                    onClickLabel = stringResource(id = R.string.update),
                    trailingIcon = {
                        IconButton(onClick = { showYtdlpDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(id = R.string.open_settings),
                            )
                        }
                    },
                )
                if (showYtdlpDialog) {
                    YtdlpUpdateChannelDialog(onDismissRequest = { showYtdlpDialog = false })
                }
                pendingNetworkConfirmation?.let { networkState ->
                    YtDlpUpdateNetworkDialog(
                        state = networkState,
                        onConfirm = {
                            pendingNetworkConfirmation = null
                            requestYtdlpUpdate(confirmed = true)
                        },
                        onDismiss = { cancelNetworkConfirmation(networkState) },
                    )
                }
            }

            item { PreferenceSubtitle(text = stringResource(R.string.network)) }
            item {
                PreferenceItem(
                    title = stringResource(R.string.cookies),
                    description = stringResource(R.string.cookies_desc),
                    icon = Icons.Outlined.Cookie,
                    onClick = { onNavigateTo(Route.COOKIE_PROFILE) },
                )
            }
            item { PreferenceSubtitle(text = stringResource(R.string.download_directory)) }
            item {
                var restrictFilenames by RESTRICT_FILENAMES.booleanState
                PreferenceSwitch(
                    title = stringResource(id = R.string.restrict_filenames),
                    icon = Icons.Outlined.Spellcheck,
                    description = stringResource(id = R.string.restrict_filenames_desc),
                    isChecked = restrictFilenames,
                ) {
                    restrictFilenames = !restrictFilenames
                    RESTRICT_FILENAMES.updateBoolean(restrictFilenames)
                }
            }
        }
    }
}
