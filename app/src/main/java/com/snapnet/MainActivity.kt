package com.snapnet

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.snapnet.App.Companion.context
import com.snapnet.ui.common.LocalDarkTheme
import com.snapnet.ui.common.SettingsProvider
import com.snapnet.ui.page.AppEntry
import com.snapnet.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.snapnet.ui.theme.SealTheme
import com.snapnet.util.PreferenceUtil
import com.snapnet.util.matchUrlFromSharedText
import com.snapnet.util.setLanguage
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext

class MainActivity : AppCompatActivity() {
    private val dialogViewModel: DownloadDialogViewModel by viewModel()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }
        enableEdgeToEdge()

        context = this.baseContext

        // Handle the intent that launched this activity.
        //
        // Only onNewIntent() used to be handled, so choosing "SnapNet" from the system share sheet
        // with the app closed opened the app and did nothing at all with the link - the action was
        // never dispatched. onNewIntent() alone is only correct for an activity that is already
        // running (launchMode is singleTask, so a second share arrives there).
        //
        // Guarded by savedInstanceState so a configuration change does not re-raise the dialog for
        // the same intent.
        if (savedInstanceState == null) {
            handleShareIntent(intent)
        }

        setContent {
            KoinContext {
                val windowSizeClass = calculateWindowSizeClass(this)
                SettingsProvider(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                    SealTheme(
                        darkTheme = LocalDarkTheme.current.isDarkTheme(),
                        isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                    ) {
                        AppEntry(dialogViewModel = dialogViewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep the activity's current intent in sync with the one that just arrived, so later code
        // (and a recreated activity) sees the new share rather than the original launch intent.
        setIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * Extracts a link from a share/view intent and opens the same download dialog that the quick
     * download activity uses. Does nothing when there is no usable link, so the app never opens an
     * empty sheet.
     */
    private fun handleShareIntent(intent: Intent?) {
        val url = intent?.getSharedURL() ?: return
        dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet(listOf(url)))
    }

    /**
     * @return the first URL found in the intent, or null.
     *
     * `EXTRA_TEXT` is deliberately left untouched: removing it before the link has been captured
     * risks losing the share if this activity is recreated before the dialog is shown.
     */
    private fun Intent.getSharedURL(): String? =
        when (action) {
            Intent.ACTION_VIEW -> dataString
            Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)?.let(::matchUrlFromSharedText)
            else -> null
        }?.takeIf { it.isNotBlank() }
}
