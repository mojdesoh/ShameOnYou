package com.mojdesoh.groupchallenge.data

/**
 * Group codes are user-chosen and double as the Firestore document ID for the group
 * (see ChallengeRepository.createGroup), so they're normalized to a small safe character
 * set that's also friendly for the groupchallenge://join?code= deep link.
 */
fun normalizeGroupCode(raw: String): String =
    raw.trim().uppercase().replace(Regex("[^A-Z0-9]+"), "-").trim('-')

/** Thrown by ChallengeRepository.createGroup when the chosen code is already in use. */
class GroupCodeTakenException : Exception()
