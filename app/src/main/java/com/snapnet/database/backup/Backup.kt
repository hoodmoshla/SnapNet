package com.snapnet.database.backup

import com.snapnet.database.objects.CommandTemplate
import com.snapnet.database.objects.DownloadedVideoInfo
import com.snapnet.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null,
)
