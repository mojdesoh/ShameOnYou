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

    /** Per-user inbox of pending "you were removed" notices — see [RemovalNotice]. */
    private fun removalNoticesRef(userId: String) =
        db.collection("users").document(userId).collection("removalNotices")

    /** Pending requests to join this group, awaiting the admin's confirm/reject. */
    private fun joinRequestsRef(groupId: String) = groupsRef().document(groupId).collection("joinRequests")

    /** Mirror of a user's own pending requests, so their Home screen can show them without a
     * collection-group query — same pattern as [myGroupsRef]. */
    private fun myJoinRequestsRef(userId: String) =
        db.collection("users").document(userId).collection("joinRequests")

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

    /**
     * Returns null if no group has that code. Requesting works before the challenge locks and
     * while it's ongoing (e.g. via the Details-screen QR code) — only once it's actually ended
     * does a code/QR stop accepting requests. Joining isn't immediate: this creates a pending
     * [JoinRequest] that the admin must confirm (see [confirmJoinRequest]) before the requester
     * becomes a member.
     */
    suspend fun requestToJoin(code: String, displayName: String): Group? {
        val uid = currentUserId()
        val normalizedCode = normalizeGroupCode(code)
        val snapshot = groupsRef().document(normalizedCode).get().await()
        if (!snapshot.exists()) return null
        val group = snapshot.toObject(Group::class.java) ?: return null
        if (group.status() == GroupStatus.ENDED) return group
        val request = JoinRequest(
            groupId = normalizedCode,
            groupName = group.name,
            userId = uid,
            displayName = displayName,
            requestedAtMillis = System.currentTimeMillis()
        )
        val batch = db.batch()
        batch.set(joinRequestsRef(normalizedCode).document(uid), request)
        batch.set(myJoinRequestsRef(uid).document(normalizedCode), request)
        batch.commit().await()
        return group
    }

    /** Every pending request to join this group — for the admin's review. */
    suspend fun getPendingJoinRequests(groupId: String): List<JoinRequest> =
        joinRequestsRef(groupId).get().await().documents.mapNotNull { it.toObject(JoinRequest::class.java) }

    /** Every challenge the current user has an outstanding request for, so Home can show it
     * alongside their confirmed memberships with a "Requested" status. */
    suspend fun getMyJoinRequests(userId: String): List<JoinRequest> =
        myJoinRequestsRef(userId).get().await().documents.mapNotNull { it.toObject(JoinRequest::class.java) }

    /**
     * Admin-only: accepts [request], turning it into real membership — creates the member doc
     * and the requester's own membership index entry (a cross-user write, hence the admin-write
     * rule on `users/{uid}/groups`), then clears the request from both sides.
     */
    suspend fun confirmJoinRequest(request: JoinRequest) {
        val joinedAtMillis = System.currentTimeMillis()
        val batch = db.batch()
        batch.set(membersRef(request.groupId).document(request.userId), Member(request.userId, request.displayName, joinedAtMillis))
        batch.set(myGroupsRef(request.userId).document(request.groupId), mapOf("joinedAtMillis" to joinedAtMillis))
        batch.delete(joinRequestsRef(request.groupId).document(request.userId))
        batch.delete(myJoinRequestsRef(request.userId).document(request.groupId))
        batch.commit().await()
    }

    /** Admin-only reject, or the requester cancelling their own pending request — same effect. */
    suspend fun removeJoinRequest(groupId: String, userId: String) {
        val batch = db.batch()
        batch.delete(joinRequestsRef(groupId).document(userId))
        batch.delete(myJoinRequestsRef(userId).document(groupId))
        batch.commit().await()
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

    /**
     * Every open or not-yet-started challenge in the whole app — not just the current user's
     * own — newest first, for the "Discover existing challenges" browse list. Filters out
     * ended and archived challenges, and ones the current user already belongs to, client-side
     * (there's no stored "ended" field to query on; status is always computed from the current
     * time, same as everywhere else in the app).
     */
    suspend fun discoverJoinableGroups(userId: String): List<Group> {
        val myGroupIds = myGroupsRef(userId).get().await().documents.map { it.id }.toSet()
        // Sorted client-side rather than via Firestore orderBy(): an orderBy on a field would
        // silently drop any document missing that field, and this collection has accumulated
        // data from well before some fields existed.
        return groupsRef()
            .get().await().documents
            .mapNotNull { it.toObject(Group::class.java) }
            .filter { !it.archived && it.status() != GroupStatus.ENDED && it.id !in myGroupIds }
            .sortedByDescending { it.createdAtMillis }
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

    /** Admin-only. The unique code (Firestore document ID) can't change, only the display name. */
    suspend fun updateGroupName(groupId: String, newName: String) {
        groupsRef().document(groupId).update("name", newName).await()
    }

    suspend fun archiveGroup(groupId: String, archived: Boolean) {
        groupsRef().document(groupId).update("archived", archived).await()
    }

    /**
     * Admin-only: removes a member and their entry from the group, drops the group from their
     * own membership index, and leaves them a one-time [RemovalNotice] to see next time they
     * open the app.
     */
    suspend fun removeMember(groupId: String, memberUserId: String, groupName: String, adminDisplayName: String) {
        val notice = RemovalNotice(
            groupId = groupId,
            groupName = groupName,
            removedByName = adminDisplayName,
            removedAtMillis = System.currentTimeMillis()
        )
        val batch = db.batch()
        batch.delete(membersRef(groupId).document(memberUserId))
        batch.delete(entriesRef(groupId).document(memberUserId))
        batch.delete(myGroupsRef(memberUserId).document(groupId))
        batch.set(removalNoticesRef(memberUserId).document(groupId), notice)
        batch.commit().await()
    }

    /** A member leaving removes only their own data, so this needs no admin/cross-user rule. */
    suspend fun leaveGroup(groupId: String) {
        val uid = currentUserId()
        val batch = db.batch()
        batch.delete(membersRef(groupId).document(uid))
        batch.delete(entriesRef(groupId).document(uid))
        batch.delete(myGroupsRef(uid).document(groupId))
        batch.commit().await()
    }

    suspend fun getRemovalNotices(userId: String): List<RemovalNotice> =
        removalNoticesRef(userId).get().await().documents.mapNotNull { it.toObject(RemovalNotice::class.java) }

    /** Dismissing a notice deletes it, so it's never shown again. */
    suspend fun dismissRemovalNotice(userId: String, groupId: String) {
        removalNoticesRef(userId).document(groupId).delete().await()
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
