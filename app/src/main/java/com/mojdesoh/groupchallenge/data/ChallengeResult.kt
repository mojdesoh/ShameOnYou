package com.mojdesoh.groupchallenge.data

/**
 * Per-member standing against the goal.
 *
 * `distance` is how far short of success this member is: for a MIN goal it's
 * (goal - total), for a MAX goal it's (total - goal). Zero or negative means they
 * met the goal individually. The group succeeds only if every member met it.
 */
data class MemberStanding(
    val member: Member,
    val total: Double,
    val distance: Double
) {
    val succeeded: Boolean get() = distance <= 0.0
}

data class ChallengeResult(
    val groupSucceeded: Boolean,
    val standings: List<MemberStanding>,
    val worstStanding: MemberStanding?
)

object ChallengeResultEvaluator {
    fun evaluate(challenge: Challenge, members: List<Member>, entries: List<Entry>): ChallengeResult {
        val totalsByUser = entries.associateBy { it.userId }
        val standings = members.map { member ->
            val total = totalsByUser[member.userId]?.total ?: 0.0
            val distance = when (challenge.goalType) {
                GoalType.MIN -> challenge.goalNumber - total
                GoalType.MAX -> total - challenge.goalNumber
            }
            MemberStanding(member, total, distance)
        }
        val groupSucceeded = standings.all { it.succeeded }
        val worst = standings.maxByOrNull { it.distance }
        return ChallengeResult(groupSucceeded, standings, worst)
    }
}
