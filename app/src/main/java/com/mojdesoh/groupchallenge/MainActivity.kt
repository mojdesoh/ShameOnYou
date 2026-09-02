package com.mojdesoh.groupchallenge

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mojdesoh.groupchallenge.data.LocalPrefs
import com.mojdesoh.groupchallenge.notification.DESTINATION_ENTRY
import com.mojdesoh.groupchallenge.notification.DESTINATION_RESULT
import com.mojdesoh.groupchallenge.notification.EXTRA_DESTINATION
import com.mojdesoh.groupchallenge.notification.EXTRA_GROUP_ID
import com.mojdesoh.groupchallenge.notification.ensureNotificationChannels
import com.mojdesoh.groupchallenge.ui.theme.GroupChallengeTheme

class MainActivity : ComponentActivity() {

    private var pendingNav by mutableStateOf<PendingNav?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val prefs = LocalPrefs(this)
        pendingNav = pendingNavFrom(intent)

        setContent {
            GroupChallengeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GroupChallengeApp(
                        prefs = prefs,
                        pendingNav = pendingNav,
                        onPendingNavConsumed = { pendingNav = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNav = pendingNavFrom(intent)
    }

    private fun pendingNavFrom(intent: Intent?): PendingNav? {
        intent ?: return null

        if (intent.action == Intent.ACTION_VIEW) {
            val code = intent.data?.getQueryParameter("code")
            if (code != null) return PendingNav.JoinWithCode(code)
        }

        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: return null
        return when (intent.getStringExtra(EXTRA_DESTINATION)) {
            DESTINATION_ENTRY -> PendingNav.OpenEntry(groupId)
            DESTINATION_RESULT -> PendingNav.OpenResult(groupId)
            else -> null
        }
    }
}
