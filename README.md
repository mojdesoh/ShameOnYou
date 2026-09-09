# Group Challenge

Android app (Kotlin + Jetpack Compose) for group accountability challenges: invite people to
a group, lock it, set a shared goal, and everyone logs a weekly number until the deadline —
then the group finds out whether it succeeded, and if not, who fell shortest.

## How it's built, and why

- **Android only**, native Kotlin + Jetpack Compose. No cross-platform framework.
- **No backend server.** The app talks to Firebase directly from the client on the free
  **Spark plan** — Firestore (data) + Auth (anonymous sign-in, one identity per install).
  There are no Cloud Functions and no Cloud Scheduler, because those require the paid
  Blaze plan even when actual usage is free. Everything Cloud Functions would normally do
  (weekly reminders, computing the end-of-challenge result) instead runs **on-device** via
  Android's `WorkManager` — see `work/ReminderScheduler.kt`.
- **Distribution is a sideloaded APK** for now, not the Play Store.
- **Invite links assume the app is already installed.** Sharing a group sends both a
  6-character invite code and a `groupchallenge://join?code=XXXXXX` deep link; there's no
  web fallback page for someone without the app yet.
- **Reminders run on the group creator's clock**, not each member's local timezone — one
  weekly cadence, anchored to the moment the challenge was created, fires at the same
  instant for everyone.

## Required setup: your own Firebase project

`app/google-services.json` currently contains a **placeholder** with fake credentials, just
so the project builds out of the box. The app runs and every screen works, but any Firebase
call (creating a group, joining one, logging a number) will fail until you swap in a real
config:

1. Go to the [Firebase console](https://console.firebase.google.com/) and create a new
   project — the free **Spark plan**, no billing needed.
2. Add an Android app to it with package name `com.mojdesoh.groupchallenge`.
3. Download the `google-services.json` it gives you and replace
   `app/google-services.json` with it.
4. In the Firebase console, enable **Authentication → Sign-in method → Anonymous**.
5. In **Firestore Database**, create a database (production mode is fine — see the security
   rules note below).
6. Rebuild: `./gradlew assembleDebug`.

`app/google-services.json` is gitignored (it's specific to your Firebase project), so this
file stays local to your machine — you won't need to redo this after a `git pull`.

### Firestore security rules

The default "production mode" rules deny all reads/writes. For this app's data model
(collection `groups`, with subcollections `members` and `entries`, plus a top-level `users`
collection that indexes which groups each user belongs to and holds each user's pending
removal notices — see "Home screen and multiple challenges" below), a starting point that
matches what the client actually needs — anyone signed in can read a group and its
subcollections, only the admin can lock it, rename it, archive it, set the challenge, or
delete it (which also lets the admin delete other members' member/entry docs, drop a removed
member's own membership-index entry, and leave that member a removal notice), each member
can otherwise only write their own member/entry doc, and each user can only read/write their
own membership index and their own removal notices:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /groups/{groupId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && resource.data.adminId == request.auth.uid;

      match /members/{userId} {
        allow read: if request.auth != null;
        allow write: if request.auth != null && request.auth.uid == userId;
        allow delete: if request.auth != null &&
          get(/databases/$(database)/documents/groups/$(groupId)).data.adminId == request.auth.uid;
      }
      match /entries/{userId} {
        allow read: if request.auth != null;
        allow write: if request.auth != null && request.auth.uid == userId;
        allow delete: if request.auth != null &&
          get(/databases/$(database)/documents/groups/$(groupId)).data.adminId == request.auth.uid;
      }
    }

    match /users/{userId}/groups/{groupId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      allow delete: if request.auth != null &&
        get(/databases/$(database)/documents/groups/$(groupId)).data.adminId == request.auth.uid;
    }

    match /users/{userId}/removalNotices/{groupId} {
      allow read, delete: if request.auth != null && request.auth.uid == userId;
      allow create: if request.auth != null &&
        get(/databases/$(database)/documents/groups/$(groupId)).data.adminId == request.auth.uid;
    }
  }
}
```

## Running it

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project folder in Android Studio and run it from there.

## Home screen and multiple challenges

A user can belong to any number of challenges at once (as admin of some, a member of
others). The Home screen lists all of them, newest on top, and is the app's permanent hub —
every other screen is pushed on top of it and returns to it on back. Since there's no
backend, "which groups does this user belong to" is answered by a small per-user index in
Firestore: `users/{uid}/groups/{groupId}`, one doc per membership, written alongside the
existing `groups/{groupId}/members/{uid}` doc whenever someone creates or joins a group. The
Home screen just reads that index for the signed-in user and fetches each referenced group.

- Tapping a card opens it at whatever stage it's in: the Lobby if not locked yet, or a
  Details screen (name, code, your own display name, member list, dates, and status) once
  it's locked — Details links onward to live Progress or the final Result.
- Edit, Delete, and Archive only show for challenges where the current user is admin.
  - Edit is disabled once a challenge has ended. Before locking it opens the Lobby
    (today's pre-lock management screen); once locked and ongoing it opens a dedicated Edit
    Challenge screen where the admin can rename the challenge (the unique code/document ID
    never changes) and remove members. A name change is a plain field update — every
    member's UI picks it up live via the existing group listener, with no separate
    notification. Removing a member deletes their member/entry docs, drops the group from
    their own membership index, and leaves them a [`RemovalNotice`](#firestore-security-rules)
    they'll see next time they open the app (Home shows it as a dialog naming the challenge
    and the admin, and deletes the notice on dismissal so it's shown exactly once).
  - Delete works at any stage, with a confirmation dialog, and removes the group for every
    member. It cascades: the group doc, every member doc, and every entry doc are removed in
    one batch, along with the admin's own membership index entry. Other members' index
    entries would otherwise go stale (Firestore doesn't cascade-delete across a different
    user's data from a client-only app); each is cleaned up lazily the next time that
    member's Home screen notices the group it points to no longer exists.
  - Archive (ongoing or ended challenges only) asks "Are you sure to archive this
    challenge?" before setting `archived = true` on the group; archived challenges are
    filtered out of the Home list (still in Firestore, just not shown — no dedicated
    archived-challenges view yet) and their reminders are cancelled.

## Known v1 limitations

- **Reminder timing isn't exact.** `WorkManager` periodic scheduling can drift by a while
  under Doze/battery optimization — fine for a weekly nudge, but worth revisiting with
  exact alarms if tighter timing ever matters.
- **No admin confirmation step for new members yet** — anyone with the group code joins
  immediately. The product spec calls for the group creator to be notified when someone
  joins (and, later, to approve them) — that notification isn't built yet, only the plain
  join flow.
- **The "Your name" field on Create/Join is temporary.** Once the app has a profile
  screen, display names should come from there instead of being typed in on every group.
- **A member can log progress more than once between reminders** — there's no enforcement
  of "once per week"; every submission just adds to their running total.
- **No launcher icon** — the app uses the system default until a real one is designed.
- **Goal semantics (v1 assumption):** each member individually must reach the goal (their
  own running total vs. the target). The group succeeds only if *everyone* reaches it. If
  not, the "shame" message calls out whichever member is furthest from their target. This
  wasn't fully spelled out in the original spec — worth confirming this is the intended
  rule as more challenge types get added.

## Group codes

Group identity is a code the creator chooses (not a randomly generated invite code). It's
prefilled from the group name as they type but fully editable, and it doubles as the
group's Firestore document ID — so uniqueness is enforced atomically by a Firestore
transaction at creation time (claim-or-fail on the same document, no separate check step).
Codes are normalized to uppercase letters, digits, and hyphens (e.g. "Fat Rats!" →
`FAT-RATS`) so they're also safe to use in the `groupchallenge://join?code=` deep link.
