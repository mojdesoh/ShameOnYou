package com.mojdesoh.groupchallenge.data

enum class GoalType { MIN, MAX }

enum class ScopeUnit(val label: String, val days: Int) {
    DAY("day", 1),
    WEEK("week", 7),
    MONTH("month", 30)
}

data class Challenge(
    val title: String = "",
    val goalType: GoalType = GoalType.MIN,
    val scopeValue: Int = 1,
    val scopeUnit: ScopeUnit = ScopeUnit.MONTH,
    val goalNumber: Double = 0.0,
    val unit: String = "",
    val startAtMillis: Long = 0L,
    val endAtMillis: Long = 0L
)

data class Group(
    val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val adminId: String = "",
    val adminDisplayName: String = "",
    val locked: Boolean = false,
    val createdAtMillis: Long = 0L,
    val adminTimeZoneId: String = "",
    val challenge: Challenge? = null
)

data class Member(
    val userId: String = "",
    val displayName: String = "",
    val joinedAtMillis: Long = 0L
)

data class Entry(
    val userId: String = "",
    val displayName: String = "",
    val total: Double = 0.0,
    val lastEntryAtMillis: Long = 0L
)
