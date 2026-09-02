package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mojdesoh.groupchallenge.data.Challenge
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.GoalType
import com.mojdesoh.groupchallenge.data.ScopeUnit
import com.mojdesoh.groupchallenge.work.ReminderScheduler
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CreateChallengeScreen(
    groupId: String,
    repository: ChallengeRepository,
    onChallengeStarted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var goalType by remember { mutableStateOf(GoalType.MIN) }
    var scopeValue by remember { mutableStateOf("1") }
    var scopeUnit by remember { mutableStateOf(ScopeUnit.MONTH) }
    var scopeUnitMenuExpanded by remember { mutableStateOf(false) }
    var goalNumber by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Set the challenge", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            placeholder = { Text("e.g. Losing weight") },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        )

        Text("Goal type", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.padding(top = 8.dp)) {
            FilterChip(
                selected = goalType == GoalType.MIN,
                onClick = { goalType = GoalType.MIN },
                label = { Text("At least (min)") }
            )
            Spacer(Modifier.padding(start = 8.dp))
            FilterChip(
                selected = goalType == GoalType.MAX,
                onClick = { goalType = GoalType.MAX },
                label = { Text("At most (max)") }
            )
        }

        Text("Time scope", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = scopeValue,
                onValueChange = { scopeValue = it.filter { c -> c.isDigit() } },
                label = { Text("Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.padding(start = 8.dp))
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { scopeUnitMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(scopeUnit.label + if (scopeValue == "1") "" else "s")
                }
                DropdownMenu(
                    expanded = scopeUnitMenuExpanded,
                    onDismissRequest = { scopeUnitMenuExpanded = false }
                ) {
                    ScopeUnit.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.label + "s") },
                            onClick = {
                                scopeUnit = candidate
                                scopeUnitMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(top = 16.dp)) {
            OutlinedTextField(
                value = goalNumber,
                onValueChange = { goalNumber = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Number") },
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
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                val scopeValueInt = scopeValue.toIntOrNull()
                val goalNumberDouble = goalNumber.toDoubleOrNull()
                if (title.isBlank() || scopeValueInt == null || scopeValueInt <= 0 ||
                    goalNumberDouble == null || unit.isBlank()
                ) {
                    error = "Fill in every field with a valid title, number, and unit."
                    return@Button
                }
                error = null
                isSubmitting = true
                val startAt = System.currentTimeMillis()
                val durationMillis = scopeValueInt.toLong() * scopeUnit.days * 24L * 60 * 60 * 1000
                val challenge = Challenge(
                    title = title.trim(),
                    goalType = goalType,
                    scopeValue = scopeValueInt,
                    scopeUnit = scopeUnit,
                    goalNumber = goalNumberDouble,
                    unit = unit.trim(),
                    startAtMillis = startAt,
                    endAtMillis = startAt + durationMillis
                )
                scope.launch {
                    try {
                        repository.lockGroupAndSetChallenge(groupId, challenge)
                        ReminderScheduler.scheduleForNewChallenge(
                            context, groupId, challenge.title, challenge.unit, challenge.endAtMillis
                        )
                        onChallengeStarted()
                    } catch (t: Throwable) {
                        error = "Couldn't start the challenge. Check your connection and try again."
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Start challenge")
        }
    }
}
