package com.mojdesoh.groupchallenge.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GroupStatus { NOT_LOCKED, ACTIVE, ENDED }

fun Long.toDateLabel(): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(this))

/** Where a group is in its lifecycle, used both for the Home screen label and for routing. */
fun Group.status(): GroupStatus {
    if (!locked) return GroupStatus.NOT_LOCKED
    val c = challenge ?: return GroupStatus.ACTIVE
    return if (System.currentTimeMillis() >= c.endAtMillis) GroupStatus.ENDED else GroupStatus.ACTIVE
}

fun Challenge.periodLabel(): String {
    val unitWord = scopeUnit.label + if (scopeValue == 1) "" else "s"
    return "$scopeValue $unitWord"
}

fun Challenge.durationMillis(): Long = scopeValue.toLong() * scopeUnit.days * 24L * 60 * 60 * 1000

fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** Builds the end-of-period title + body described in the product spec (success vs. shame). */
fun buildResultMessage(groupName: String, challenge: Challenge, result: ChallengeResult): Pair<String, String> {
    val period = challenge.periodLabel()
    if (result.groupSucceeded) {
        val title = "You did it!"
        val body = "Congratulations, you did it! You reached \"${challenge.title}\" in $period."
        return title to appendSummary(body, challenge, result)
    }

    val worst = result.worstStanding
    val worstPhrase = when (challenge.goalType) {
        GoalType.MIN -> "who contributed less"
        GoalType.MAX -> "who went over the most"
    }
    val title = "Goal missed"
    val body = if (worst != null) {
        "Shame on group $groupName, but more on ${worst.member.displayName}, $worstPhrase."
    } else {
        "Shame on group $groupName — the goal wasn't reached."
    }
    return title to appendSummary(body, challenge, result)
}

private fun appendSummary(headline: String, challenge: Challenge, result: ChallengeResult): String {
    val lines = result.standings
        .sortedByDescending { it.total }
        .joinToString("\n") { "${it.member.displayName}: ${formatNumber(it.total)} ${challenge.unit}" }
    return "$headline\n\n$lines"
}
