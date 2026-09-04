package com.mojdesoh.groupchallenge.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.TimeZone

/**
 * All reads/writes go through Firestore's free Spark-plan quota directly from the client —
 * there is no backend server. Scheduling and result computation happen on-device (see
 * work/ReminderScheduler.kt) rather than via Cloud Functions, which require a paid plan.
 */
class ChallengeRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun groupsRef() = db.collection("groups")
    private fun membersRef(groupId: String) = groupsRef().document(groupId).collection("members")
    private fun entriesRef(groupId: String) = groupsRef().document(groupId).collection("entries")

    /**
     * Per-user index of which groups they belong to (`users/{uid}/groups/{groupId}`), so the
     * Home screen can list every challenge a user is in without a Firestore collection-group
     * query. Written whenever a user creates or joins a group; a stale entry (group deleted by
     * its admin) is cleaned up lazily the next time [observeMyGroups] notices it's gone.
     */
    private fun myGroupsRef(userId: String) = db.collection("users").document(userId).collection("groups")

    suspend fun currentUserId(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return result.user!!.uid
    }

    /**
     * The group's unique code doubles as its Firestore document ID, so a transaction can
     * atomically check-and-claim it — two people racing to create the same code can't both
     * succeed.
     */
    suspend fun createGroup(name: String, adminDisplayName: String, code: String): Group {
        val uid = currentUserId()
        val normalizedCode = normalizeGroupCode(code)
        val docRef = groupsRef().document(normalizedCode)
        val group = Group(
            id = normalizedCode,
            name = name,
            inviteCode = normalizedCode,
            adminId = uid,
            adminDisplayName = adminDisplayName,
            locked = false,
            createdAtMillis = System.currentTimeMillis(),
            adminTimeZoneId = TimeZone.getDefault().id
        )
        db.runTransaction { transaction ->
            if (transaction.get(docRef).exists()) throw GroupCodeTakenException()
            transaction.set(docRef, group)
        }.await()
        membersRef(normalizedCode).document(uid)
            .set(Member(uid, adminDisplayName, System.currentTimeMillis()))
            .await()
        myGroupsRef(uid).document(normalizedCode)
            .set(mapOf("joinedAtMillis" to group.createdAtMillis))
            .await()
        return group
    }

    /** Returns null if no group has that code. */
    suspend fun joinGroup(code: String, displayName: String): Group? {
        val uid = currentUserId()
        val normalizedCode = normalizeGroupCode(code)
        val snapshot = groupsRef().document(normalizedCode).get().await()
        if (!snapshot.exists()) return null
        val group = snapshot.toObject(Group::class.java) ?: return null
        if (group.locked) return group
        val joinedAtMillis = System.currentTimeMillis()
        membersRef(normalizedCode).document(uid)
            .set(Member(uid, displayName, joinedAtMillis))
            .await()
        myGroupsRef(uid).document(normalizedCode)
            .set(mapOf("joinedAtMillis" to joinedAtMillis))
            .await()
        return group
    }

    /**
     * Every group the current user belongs to (as admin or member), newest membership first.
     * Fetches each group document fresh whenever the membership index changes; a membership
     * pointing at a group that no longer exists (deleted by its admin) is removed as it's found.
     */
    fun observeMyGroups(userId: String): Flow<List<Group>> = callbackFlow {
        val scope = this
        val registration = myGroupsRef(userId)
            .orderBy("joinedAtMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val groupIds = snapshot?.documents?.map { it.id } ?: emptyList()
                scope.launch {
                    val groups = groupIds.mapNotNull { id ->
                        val group = groupsRef().document(id).get().await().toObject(Group::class.java)
                        if (group == null) myGroupsRef(userId).document(id).delete()
                        group
                    }
                    trySend(groups)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Admin-only: deletes a group and all its members/entries for everyone. */
    suspend fun deleteGroup(groupId: String) {
        val uid = currentUserId()
        val members = getMembersOnce(groupId)
        val entries = getEntriesOnce(groupId)
        val batch = db.batch()
        members.forEach { batch.delete(membersRef(groupId).document(it.userId)) }
        entries.forEach { batch.delete(entriesRef(groupId).document(it.userId)) }
        batch.delete(groupsRef().document(groupId))
        batch.delete(myGroupsRef(uid).document(groupId))
        batch.commit().await()
    }

    suspend fun lockGroupAndSetChallenge(groupId: String, challenge: Challenge) {
        groupsRef().document(groupId)
            .set(mapOf("locked" to true, "challenge" to challenge), SetOptions.merge())
            .await()
    }

    suspend fun submitEntry(groupId: String, userId: String, displayName: String, value: Double) {
        entriesRef(groupId).document(userId).set(
            mapOf(
                "userId" to userId,
                "displayName" to displayName,
                "total" to FieldValue.increment(value),
                "lastEntryAtMillis" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun getGroupOnce(groupId: String): Group? =
        groupsRef().document(groupId).get().await().toObject(Group::class.java)

    suspend fun getMembersOnce(groupId: String): List<Member> =
        membersRef(groupId).get().await().documents.mapNotNull { it.toObject(Member::class.java) }

    suspend fun getEntriesOnce(groupId: String): List<Entry> =
        entriesRef(groupId).get().await().documents.mapNotNull { it.toObject(Entry::class.java) }

    fun observeGroup(groupId: String): Flow<Group?> = callbackFlow {
        val registration = groupsRef().document(groupId).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toObject(Group::class.java))
        }
        awaitClose { registration.remove() }
    }

    fun observeMembers(groupId: String): Flow<List<Member>> = callbackFlow {
        val registration = membersRef(groupId)
            .orderBy("joinedAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents?.mapNotNull { it.toObject(Member::class.java) } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    fun observeEntries(groupId: String): Flow<List<Entry>> = callbackFlow {
        val registration = entriesRef(groupId).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.documents?.mapNotNull { it.toObject(Entry::class.java) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }
}
