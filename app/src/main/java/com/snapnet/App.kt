package com.snapnet

import android.annotation.SuppressLint
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.app.Activity
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.google.android.material.color.DynamicColors
import com.snapnet.download.DownloaderV2
import com.snapnet.download.DownloaderV2Impl
import com.snapnet.ui.page.download.HomePageViewModel
import com.snapnet.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.snapnet.ui.page.settings.directory.Directory
import com.snapnet.ui.page.settings.network.CookiesViewModel
import com.snapnet.ui.page.videolist.VideoListViewModel
import com.snapnet.util.AUDIO_DIRECTORY
import com.snapnet.util.COMMAND_DIRECTORY
import com.snapnet.util.DownloadUtil
import com.snapnet.util.FileUtil
import com.snapnet.util.FileUtil.createEmptyFile
import com.snapnet.util.FileUtil.getCookiesFile
import com.snapnet.util.FileUtil.getExternalDownloadDirectory
import com.snapnet.util.FileUtil.getExternalPrivateDownloadDirectory
import com.snapnet.util.NotificationUtil
import com.snapnet.util.PreferenceUtil
import com.snapnet.util.PreferenceUtil.getString
import com.snapnet.util.PreferenceUtil.updateString
import com.snapnet.util.SDCARD_URI
import com.snapnet.util.UpdateUtil
import com.snapnet.util.VIDEO_DIRECTORY
import com.snapnet.util.YT_DLP_VERSION
import com.snapnet.util.YtDlpVersion
import com.tencent.mmkv.MMKV
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                module {
                    single<DownloaderV2> { DownloaderV2Impl(androidContext()) }
                    viewModel { DownloadDialogViewModel(downloader = get()) }
                    viewModel { HomePageViewModel() }
                    viewModel { CookiesViewModel() }
                    viewModel { VideoListViewModel() }
                }
            )
        }

        context = applicationContext
        packageInfo =
            packageManager.run {
                if (Build.VERSION.SDK_INT >= 33)
                    getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                else getPackageInfo(packageName, 0)
            }
        applicationScope = CoroutineScope(SupervisorJob())
        engineReady = CompletableDeferred()

        // Retry a refused foreground-service start as soon as any activity becomes visible, which is
        // the only moment Android allows the service to be started.
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    if (wantsService && !isServiceRunning) {
                        Log.i(TAG, "retrying the download foreground service now that the app is visible")
                        startService()
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
        )
        DynamicColors.applyToActivitiesIfAvailable(this)

        clipboard = getSystemService()!!
        connectivityManager = getSystemService()!!

        applicationScope.launch((Dispatchers.IO)) {
            runCatching {
                YoutubeDL.init(this@App)
                FFmpeg.init(this@App)
                Aria2c.init(this@App)

                // Always publish a concrete engine version. The library only stores one after a
                // runtime update, which left a freshly installed app reporting an empty version and
                // made it impossible to tell which yt-dlp was actually in use.
                // The value reported to the user must be the engine that will actually run, not
                // whatever the library happened to remember. Refresh it on every launch so an
                // out-of-band update, or a failed one, is reflected immediately.
                ensureYtDlpReady(this@App)
                DownloadUtil.getCookiesContentFromDatabase().getOrNull()?.let {
                    FileUtil.writeContentToFile(it, getCookiesFile())
                }
                UpdateUtil.deleteOutdatedApk()
            }.onSuccess { engineReady.complete(Result.success(Unit)) }
                .onFailure { th ->
                    Log.e(TAG, "yt-dlp engine initialization failed; downloads are blocked", th)
                    engineReady.complete(Result.failure(th))
                }
        }

        videoDownloadDir = VIDEO_DIRECTORY.getString(getExternalDownloadDirectory().absolutePath)

        audioDownloadDir = AUDIO_DIRECTORY.getString(File(videoDownloadDir, "Audio").absolutePath)
        if (!PreferenceUtil.containsKey(COMMAND_DIRECTORY)) {
            COMMAND_DIRECTORY.updateString(videoDownloadDir)
        }
        if (Build.VERSION.SDK_INT >= 26) NotificationUtil.createNotificationChannel()

        Thread.setDefaultUncaughtExceptionHandler { _, e -> startCrashReportActivity(e) }
    }

    private fun startCrashReportActivity(th: Throwable) {
        th.printStackTrace()
        startActivity(
            Intent(this, CrashReportActivity::class.java)
                .setAction("$packageName.error_report")
                .apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("error_report", getVersionReport() + "\n" + th.stackTraceToString())
                }
        )
    }

    companion object {
        lateinit var clipboard: ClipboardManager
        lateinit var videoDownloadDir: String
        lateinit var audioDownloadDir: String
        lateinit var applicationScope: CoroutineScope
        lateinit var connectivityManager: ConnectivityManager
        lateinit var packageInfo: PackageInfo
        private lateinit var engineReady: CompletableDeferred<Result<Unit>>

        var isServiceRunning = false

        private const val TAG = "App"

        /** Binder handle to the running service, used to tear it down cleanly. */
        private var boundService: DownloadService? = null
        @Volatile private var foregroundReady: CompletableDeferred<Unit>? = null

        /**
         * True while a download is in flight and the service therefore ought to be running.
         *
         * A foreground service may only be started while the app is visible, so an attempt made from
         * the background can be refused. The flag lets the app retry the moment it is visible again
         * instead of silently losing background execution for the rest of the download.
         */
        @Volatile private var wantsService = false

        /** Non-null when the last attempt to start the service was refused; surfaced in diagnostics. */
        @Volatile var foregroundServiceError: String? = null
            private set

        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    val binder = service as DownloadService.DownloadServiceBinder
                    boundService = binder.getService()
                    if (boundService?.isForegroundReady() == true) {
                        isServiceRunning = true
                        foregroundReady?.complete(Unit)
                    } else {
                        isServiceRunning = false
                        foregroundReady?.completeExceptionally(
                            IllegalStateException("DownloadService did not enter foreground mode")
                        )
                    }
                }

                override fun onServiceDisconnected(arg0: ComponentName) {
                    boundService = null
                    isServiceRunning = false
                    foregroundReady?.completeExceptionally(
                        IllegalStateException("DownloadService disconnected")
                    )
                }
            }

        /**
         * Starts [DownloadService] as a foreground service **and** binds to it.
         *
         * Starting it (rather than only binding) is what lets downloads survive the app being
         * backgrounded. The bind is kept because the service is also used as a liveness handle.
         *
         * On Android 12+ a foreground service may not be started from the background; that throws
         * [android.app.ForegroundServiceStartNotAllowedException], which must not crash the app, so
         * the call is guarded and the failure is logged.
         */
        fun startService() {
            wantsService = true
            if (isServiceRunning) return
            if (foregroundReady?.isActive == true) return
            val app = context.applicationContext
            val intent = Intent(app, DownloadService::class.java)
            foregroundReady = CompletableDeferred()
            try {
                ContextCompat.startForegroundService(app, intent)
                app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                foregroundServiceError = null
            } catch (e: Exception) {
                // Android 12+ refuses a foreground service start from the background. Failing here
                // means the download would be killed if the user leaves the app, so record it loudly
                // instead of swallowing it; the activity lifecycle callback below retries as soon as
                // the app is visible again.
                foregroundServiceError = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                foregroundReady?.completeExceptionally(e)
                foregroundReady = null
                Log.e(
                    TAG,
                    "download foreground service could not be started; the running download will not survive being backgrounded until the app is shown again",
                    e,
                )
            }
        }

        /** Starts the service and waits until Android has accepted its foreground notification. */
        suspend fun ensureForegroundServiceReady() {
            startService()
            foregroundReady?.await() ?: error("DownloadService was not started")
        }

        fun stopService() {
            wantsService = false
            foregroundServiceError = null
            val app = context.applicationContext
            try {
                if (isServiceRunning) app.unbindService(connection)
            } catch (e: Exception) {
                Log.w(TAG, "could not unbind the download service", e)
            }
            isServiceRunning = false
            foregroundReady = null
            boundService?.stopForegroundCompat()
            boundService = null
            try {
                app.stopService(Intent(app, DownloadService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "could not stop the download service", e)
            }
        }

        /** Blocks all yt-dlp work until the exact runtime engine is initialized and compatible. */
        suspend fun awaitYtDlpReady() {
            engineReady.await().getOrThrow()
        }

        private suspend fun ensureYtDlpReady(app: Context) {
            fun logEngine() {
                val installed = YtDlpVersion.resolveInstalledVersion(app)
                val bundled = YtDlpVersion.resolveBundledVersion(app)
                val effective = installed ?: bundled
                Log.i(
                    TAG,
                    "yt-dlp engine: version=${effective ?: "unknown"}, " +
                        "path=${YtDlpVersion.effectiveBinaryPath(app)}, " +
                        "installed=${installed != null}, bundled=${bundled ?: "unknown"}, " +
                        "quickjs=${File(app.applicationInfo.nativeLibraryDir, "libqjs.so").absolutePath}",
                )
                effective?.let { PreferenceUtil.encodeString(YT_DLP_VERSION, it) }
            }

            logEngine()
            if (!YtDlpVersion.effectiveEngineSupportsJsRuntimes(app)) {
                check(PreferenceUtil.isNetworkAvailableForDownload()) {
                    "yt-dlp engine is too old for --js-runtimes and no network is available"
                }
                Log.w(TAG, "stale yt-dlp detected; forcing an update before any download")
                UpdateUtil.updateYtDlp()
                logEngine()
            }
            check(YtDlpVersion.effectiveEngineSupportsJsRuntimes(app)) {
                "yt-dlp engine does not support --js-runtimes after update"
            }
        }

        val privateDownloadDir: String
            get() =
                getExternalPrivateDownloadDirectory().run {
                    createEmptyFile(".nomedia")
                    absolutePath
                }

        fun updateDownloadDir(uri: Uri, directoryType: Directory) {
            when (directoryType) {
                Directory.AUDIO -> {
                    val path = FileUtil.getRealPath(uri)
                    audioDownloadDir = path
                    PreferenceUtil.encodeString(AUDIO_DIRECTORY, path)
                }

                Directory.VIDEO -> {
                    val path = FileUtil.getRealPath(uri)
                    videoDownloadDir = path
                    PreferenceUtil.encodeString(VIDEO_DIRECTORY, path)
                }

                Directory.CUSTOM_COMMAND -> {
                    val path = FileUtil.getRealPath(uri)
                }

                Directory.SDCARD -> {
                    context.contentResolver?.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    PreferenceUtil.encodeString(SDCARD_URI, uri.toString())
                }
            }
        }

        fun getVersionReport(): String {
            val versionName = packageInfo.versionName
            val page = packageInfo
            val versionCode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
            val release =
                if (Build.VERSION.SDK_INT >= 30) {
                    Build.VERSION.RELEASE_OR_CODENAME
                } else {
                    Build.VERSION.RELEASE
                }
            return StringBuilder()
                .append("App version: $versionName ($versionCode)\n")
                .append("Device information: Android $release (API ${Build.VERSION.SDK_INT})\n")
                .append("Supported ABIs: ${Build.SUPPORTED_ABIS.contentToString()}\n")
                .append("Yt-dlp version: ")
                .append(
                    YtDlpVersion.resolveEffectiveVersion(context)
                        ?: YT_DLP_VERSION.getString().ifEmpty { "unknown" }.let { it }
                )
                .append("\n")
                .toString()
        }

        fun isFDroidBuild(): Boolean = BuildConfig.FLAVOR == "fdroid"

        fun isDebugBuild(): Boolean = BuildConfig.DEBUG

        @SuppressLint("StaticFieldLeak") lateinit var context: Context
    }
}
