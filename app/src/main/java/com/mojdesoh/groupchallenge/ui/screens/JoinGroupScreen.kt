package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.LocalPrefs
import kotlinx.coroutines.launch

@Composable
fun JoinGroupScreen(
    repository: ChallengeRepository,
    prefs: LocalPrefs,
    prefilledCode: String?,
    onJoined: (groupId: String) -> Unit
) {
    var yourName by remember { mutableStateOf("") }
    var groupCode by remember { mutableStateOf(prefilledCode ?: "") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Join a group", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = yourName,
            onValueChange = { yourName = it },
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        )
        OutlinedTextField(
            value = groupCode,
            onValueChange = { groupCode = it },
            label = { Text("Group unique code") },
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
                        val group = repository.joinGroup(groupCode.trim(), yourName.trim())
                        when {
                            group == null -> error = "No group found for that code."
                            group.locked -> error = "This group is already locked and isn't accepting new members."
                            else -> {
                                prefs.displayName = yourName.trim()
                                onJoined(group.id)
                            }
                        }
                    } catch (t: Throwable) {
                        error = "Couldn't join the group. Check your connection and try again."
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
            Text("Join")
        }
    }
}
