package com.snapnet.database.backup

import android.content.Context
import com.snapnet.App
import com.snapnet.R
import com.snapnet.database.objects.CommandTemplate
import com.snapnet.database.objects.DownloadedVideoInfo
import com.snapnet.database.objects.OptionShortcut
import com.snapnet.util.DatabaseUtil
import java.util.Date
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BackupUtil {
    private val format = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportTemplatesToJson() =
        exportTemplatesToJson(
            templates = DatabaseUtil.getTemplateList(),
            shortcuts = DatabaseUtil.getShortcutList(),
        )

    fun exportTemplatesToJson(
        templates: List<CommandTemplate>,
        shortcuts: List<OptionShortcut>,
    ): String {
        return format.encodeToString(Backup(templates = templates, shortcuts = shortcuts))
    }

    fun List<DownloadedVideoInfo>.toJsonString(): String {
        return format.encodeToString(Backup(downloadHistory = this))
    }

    fun List<DownloadedVideoInfo>.toURLListString(): String {
        return this.map { it.videoUrl }.joinToString(separator = "\n") { it }
    }

    fun String.decodeToBackup(): Result<Backup> {
        return format.runCatching { decodeFromString<Backup>(this@decodeToBackup) }
    }

    fun getDownloadHistoryExportFilename(context: Context): String {
        return listOf(
                context.getString(R.string.app_name),
                App.packageInfo.versionName.toString(),
                Date().toString(),
            )
            .joinToString(separator = "-") { it }
    }

    enum class BackupDestination {
        File,
        Clipboard,
    }

    enum class BackupType {
        DownloadHistory,
        URLList,
        CommandTemplate,
        CommandShortcut,
    }
}
