package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.LocalPrefs
import kotlinx.coroutines.launch

@Composable
fun EntryScreen(
    groupId: String,
    repository: ChallengeRepository,
    prefs: LocalPrefs,
    onBack: () -> Unit,
    onSubmitted: () -> Unit
) {
    val group by repository.observeGroup(groupId).collectAsState(initial = null)
    var value by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        currentUserId = repository.currentUserId()
    }

    val challenge = group?.challenge

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text("Log your progress", style = MaterialTheme.typography.headlineSmall)
        }
        challenge?.let {
            Text(it.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }

        OutlinedTextField(
            value = value,
            onValueChange = { value = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
            label = { Text("Number") },
            suffix = { Text(challenge?.unit ?: "") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = {
                val parsed = value.toDoubleOrNull()
                val uid = currentUserId
                if (parsed == null) {
                    error = "Enter a valid number."
                    return@Button
                }
                if (uid == null) {
                    error = "Still signing in, try again in a moment."
                    return@Button
                }
                error = null
                isSubmitting = true
                scope.launch {
                    try {
                        repository.submitEntry(groupId, uid, prefs.displayName ?: "", parsed)
                        onSubmitted()
                    } catch (t: Throwable) {
                        error = "Couldn't save your entry. Check your connection and try again."
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Submit")
        }
    }
}
