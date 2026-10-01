# NestKeep

Android app (Kotlin, Jetpack Compose) for landlords: buildings and flats, tenant registration with family and ID
documents, monthly rent + electricity bills, partial/full payments with dates, flat and tenant history, PDF reports.

## Cloud and login
- **Sign in with Google** (Credential Manager -> Firebase Auth).
- **Records** (buildings, tenants, bills, payments) sync through Cloud Firestore under `users/{uid}`; rules in
  `firebase/firestore.rules` allow each user only their own data. Works offline and syncs later.
- **Photos and ID documents** are stored in the owner's own Google Drive *app data* folder (`drive.appdata` scope:
  private to the app, free, not visible in the Drive UI).
- Aadhaar numbers are never stored. Only the last four digits and a keyed hash (for duplicate detection) are kept.
- Data from an older on-device install can be uploaded on first login.

Firebase project: `nestkeep-rentals-app` (Spark/free plan). Package: `com.nestkeep.app`.

## Build
```
./gradlew testDebugUnitTest lintDebug assembleDebug
```
Needs `app/google-services.json` (download from Firebase console or `firebase apps:sdkconfig ANDROID <appId>`).
It is git-ignored. Release signing reads `keystore.properties` or the env vars `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`; `VERSION_CODE` / `VERSION_NAME` come from CI.

## Workflows (`.github/workflows`)
| File | Trigger | What it does |
|---|---|---|
| `ci.yml` | PRs and non-main pushes | unit tests, lint, debug build |
| `play-console.yml` | manual (Run workflow) | tests + lint, signed AAB to Internal, promote to closed testing (`alpha`), optionally production; also deploys Firestore rules when `FIREBASE_SERVICE_ACCOUNT` is set |

### Required GitHub secrets
`GOOGLE_SERVICES_JSON` (base64), `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
`PLAY_PUBLISHER_SERVICE_ACCOUNT_JSON` (Play Console API service account; the same one used by spikkle-e works), optional `FIREBASE_SERVICE_ACCOUNT`.
Choose `draft` as release status until the Play listing is complete; Play rejects the first upload through the API,
so upload the first AAB by hand.

## One-time setup checklist
1. Firebase console -> Authentication -> enable the **Google** provider (support email), then re-download
   `google-services.json` (it then contains the web client ID) and update the `GOOGLE_SERVICES_JSON` secret.
2. Enable the **Google Drive API** for the project in Google Cloud.
3. Google Auth platform: finish the consent screen, add scope `.../auth/drive.appdata`, add testers.
4. After the first Play upload, add the **Play App Signing SHA-1** (Play Console -> App integrity) to the Firebase
   Android app, otherwise Google sign-in fails on Play-installed builds.
5. Host `docs/privacy.html` (GitHub Pages) and enter its URL in the Play listing.
