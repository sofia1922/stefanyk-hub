# Firebase setup

Firebase-backed sign-in and catalog sync require a Firebase project configuration.

1. Register the Android app in Firebase with application ID `com.example.stefanykhub`.
2. Download `google-services.json` into the `app/` directory.
3. Enable Email/Password and Google under Firebase Authentication. Add the app's SHA-1 fingerprint and download the updated config file after enabling Google sign-in. Email/password registration sends a verification link; signup details are stored in a restricted pending profile until the user verifies. If the message is lost, trying to sign in again sends a fresh verification email.
4. Create a Firestore database and a Firebase Storage bucket. Deploy `firestore.rules` and `storage.rules` with the Firebase CLI.
5. For the first administrator, register with the admin's university email, verify it, then sign in. In Firestore, change that user's `users/{uid}` document `role` field from `STUDENT` to `ADMIN`. Admin creation is intentionally not available in public registration.

After this one-time setup, the admin opens Profile → Base of teachers and adds each teacher's university email to the allowlist. The teacher then registers with that email; the app checks the invitation and creates a `TEACHER` profile automatically. Removing an invitation prevents a not-yet-registered invitee from receiving the teacher role. Students register normally and receive `STUDENT` automatically. Google sign-in uses the same allowlist rule for new accounts.

Email and password credentials are managed by Firebase Authentication. Firestore stores the user's profile and role only; it never stores the password. The role is enforced by Firestore and Storage rules, not only by which controls the app displays.

The Google Services Gradle plugin is applied automatically when `app/google-services.json` exists. Without that file, the project remains buildable, but Firebase operations report that Firebase is not configured.
