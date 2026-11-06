package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.mojdesoh.groupchallenge.data.GroupCodeTakenException
import com.mojdesoh.groupchallenge.data.LocalPrefs
import com.mojdesoh.groupchallenge.data.normalizeGroupCode
import kotlinx.coroutines.launch

@Composable
fun CreateGroupScreen(
    repository: ChallengeRepository,
    prefs: LocalPrefs,
    onBack: () -> Unit,
    onCreated: (groupId: String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var yourName by remember { mutableStateOf("") }
    var groupCode by remember { mutableStateOf("") }
    var codeManuallyEdited by remember { mutableStateOf(false) }
    var goal by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Follows the group name until the user edits the code field themselves.
    LaunchedEffect(groupName, codeManuallyEdited) {
        if (!codeManuallyEdited) {
            groupCode = normalizeGroupCode(groupName)
        }
    }

    val normalizedCode = normalizeGroupCode(groupCode)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text("Create a challenge", style = MaterialTheme.typography.headlineSmall)
        }

        OutlinedTextField(
            value = groupName,
            onValueChange = { groupName = it },
            label = { Text("Challenge name") },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        )
        OutlinedTextField(
            value = yourName,
            onValueChange = { yourName = it },
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        OutlinedTextField(
            value = groupCode,
            onValueChange = {
                codeManuallyEdited = true
                groupCode = it
            },
            label = { Text("Challenge unique code") },
            supportingText = {
                Text(
                    if (normalizedCode.isNotEmpty()) "People will use \"$normalizedCode\" to join"
                    else "Letters, numbers, and hyphens only"
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        Text(
            "Goal (optional — you can set this later, but you'll need it to start the challenge)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        OutlinedTextField(
            value = goal,
            onValueChange = { goal = it },
            label = { Text("Goal") },
            placeholder = { Text("e.g. Losing weight") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(modifier = Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Target") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.padding(start = 8.dp))
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Unit") },
                placeholder = { Text("e.g. kg") },
                modifier = Modifier.weight(1f)
            )
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = {
                if (normalizedCode.isEmpty()) {
                    error = "Enter a challenge code with at least one letter or number."
                    return@Button
                }
                error = null
                isSubmitting = true
                scope.launch {
                    try {
                        val group = repository.createGroup(
                            name = groupName.trim(),
                            adminDisplayName = yourName.trim(),
                            code = normalizedCode,
                            draftGoalTitle = goal.trim(),
                            draftGoalTarget = target.toDoubleOrNull(),
                            draftGoalUnit = unit.trim()
                        )
                        prefs.displayName = yourName.trim()
                        onCreated(group.id)
                    } catch (e: GroupCodeTakenException) {
                        error = "That challenge code is already taken. Try a different one."
                    } catch (t: Throwable) {
                        error = "Couldn't create the challenge. Check your connection and try again."
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = !isSubmitting && groupName.isNotBlank() && yourName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Create")
        }
    }
}
