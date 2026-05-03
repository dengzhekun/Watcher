package com.example.watcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.example.watcher.ui.screens.HubPage
import com.example.watcher.ui.screens.MainScreen
import com.example.watcher.ui.theme.WatcherTheme

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startPage = intent.getStringExtra(EXTRA_START_PAGE)
            ?.let(StartPage::fromRaw)
            ?: StartPage.Hub
        setContent {
            WatcherTheme {
                MainScreen(
                    initialPage = when (startPage) {
                        StartPage.Hub -> HubPage.Hub
                        StartPage.Templates -> HubPage.Templates
                    }
                )
            }
        }
        requestNotificationPermissionIfNeeded()
    }

    companion object {
        private const val EXTRA_START_PAGE = "com.example.watcher.extra.START_PAGE"

        fun createIntent(
            context: Context,
            startPage: StartPage = StartPage.Hub
        ): Intent {
            return Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_START_PAGE, startPage.rawValue)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    enum class StartPage(val rawValue: String) {
        Hub("hub"),
        Templates("templates");

        companion object {
            fun fromRaw(raw: String): StartPage? {
                return entries.firstOrNull { it.rawValue == raw }
            }
        }
    }
}
