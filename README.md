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
(collection `groups`, with subcollections `members` and `entries`), a starting point that
matches what the client actually needs — anyone signed in can read a group and its
subcollections, only the admin can lock it or set the challenge, and each member can only
write their own member/entry doc:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /groups/{groupId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update: if request.auth != null && resource.data.adminId == request.auth.uid;

      match /members/{userId} {
        allow read: if request.auth != null;
        allow write: if request.auth != null && request.auth.uid == userId;
      }
      match /entries/{userId} {
        allow read: if request.auth != null;
        allow write: if request.auth != null && request.auth.uid == userId;
      }
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
