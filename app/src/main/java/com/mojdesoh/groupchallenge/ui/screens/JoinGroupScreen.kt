package com.mojdesoh.groupchallenge.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.Group
import com.mojdesoh.groupchallenge.data.GroupStatus
import com.mojdesoh.groupchallenge.data.LocalPrefs
import com.mojdesoh.groupchallenge.data.status
import kotlinx.coroutines.launch

@Composable
fun JoinGroupScreen(
    repository: ChallengeRepository,
    prefs: LocalPrefs,
    prefilledCode: String?,
    onBack: () -> Unit,
    onRequested: () -> Unit
) {
    var yourName by remember { mutableStateOf(prefs.displayName ?: "") }
    var groupCode by remember { mutableStateOf(prefilledCode ?: "") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var scannedGroup by remember { mutableStateOf<Group?>(null) }
    var scanFailed by remember { mutableStateOf(false) }
    var isLookingUpScan by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@rememberLauncherForActivityResult
        val code = extractJoinCode(raw)
        if (code == null) {
            scanFailed = true
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isLookingUpScan = true
            try {
                val g = repository.getGroupOnce(code)
                if (g == null) scanFailed = true else scannedGroup = g
            } catch (t: Throwable) {
                scanFailed = true
            } finally {
                isLookingUpScan = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text("Enter the challenge name", style = MaterialTheme.typography.headlineSmall)
        }

        OutlinedButton(
            onClick = {
                scanLauncher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setBeepEnabled(true)
                        .setOrientationLocked(true)
                        .setPrompt("Scan a Group Challenge QR code")
                )
            },
            enabled = !isLookingUpScan,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            if (isLookingUpScan) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Scan QR code")
        }

        Spacer(Modifier.height(12.dp))
        Text("Or enter the code manually", style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = yourName,
            onValueChange = { yourName = it },
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        OutlinedTextField(
            value = groupCode,
            onValueChange = { groupCode = it },
            label = { Text("Challenge unique code") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = {
                error = null
                isSubmitting = true
                scope.launch {
                    try {
                        val group = repository.requestToJoin(groupCode.trim(), yourName.trim())
                        when {
                            group == null -> error = "No challenge found for that code."
                            group.status() == GroupStatus.ENDED ->
                                error = "This challenge has already ended and isn't accepting new members."
                            else -> {
                                prefs.displayName = yourName.trim()
                                onRequested()
                            }
                        }
                    } catch (t: Throwable) {
                        error = "Couldn't send the request. Check your connection and try again."
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = !isSubmitting && groupCode.isNotBlank() && yourName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Request to join")
        }
    }

    scannedGroup?.let { g ->
        val joinable = g.status() != GroupStatus.ENDED
        val statusLabel = when {
            g.archived -> "Archived"
            g.status() == GroupStatus.NOT_LOCKED -> "Not started"
            g.status() == GroupStatus.ACTIVE -> "In progress"
            else -> "Finished"
        }
        AlertDialog(
            onDismissRequest = { scannedGroup = null },
            title = { Text(g.name) },
            text = {
                Column {
                    Text("Unique code: ${g.inviteCode}")
                    Text("Status: $statusLabel")
                    if (!joinable) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This challenge has already ended and isn't accepting new members.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (joinable) {
                    TextButton(onClick = {
                        groupCode = g.inviteCode
                        scannedGroup = null
                    }) { Text("Use this challenge") }
                } else {
                    TextButton(onClick = { scannedGroup = null }) { Text("OK") }
                }
            },
            dismissButton = {
                if (joinable) {
                    TextButton(onClick = { scannedGroup = null }) { Text("Cancel") }
                }
            }
        )
    }

    if (scanFailed) {
        AlertDialog(
            onDismissRequest = { scanFailed = false },
            title = { Text("The QR code isn't correct.") },
            confirmButton = {
                TextButton(onClick = { scanFailed = false }) { Text("OK") }
            }
        )
    }
}

/** Returns the invite code if [raw] is one of this app's join links, null otherwise. */
private fun extractJoinCode(raw: String): String? {
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    if (uri.scheme != "groupchallenge" || uri.host != "join") return null
    return uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }
}
