# Google Drive Cloud Sync — Production & Setup Guide

Luma Reader includes native, privacy-first **Google Drive Synchronization** for reading positions, bookmarks, user shelves, annotations, and library catalog metadata across devices.

---

## 1. Security & Architecture Model

### Why Google Drive AppData Folder (`appDataFolder`)?
- **Hidden Application Sandbox**: App files (`reading_positions.json`, `library_catalog.json`) are stored exclusively in the private Application Data folder (`https://www.googleapis.com/auth/drive.appdata`).
- **Zero Drive Clutter**: Users will not see internal JSON files mixed into their personal Drive files, preventing accidental deletion, renaming, or corruption.
- **Privacy & Easy Verification**: Full Google Drive access (`https://www.googleapis.com/auth/drive`) requires expensive, multi-thousand-dollar CASA Tier 2 security audits for public Play Store release. By using `drive.appdata` and `drive.file`, Luma Reader qualifies for standard verification without prohibitive audit requirements.
- **Pure Android & KMP Architecture**: Built on Google Play Services Auth (`play-services-auth:21.3.0`) and lightweight, direct REST v3 calls (`AndroidGoogleDriveClient`), avoiding conflict-prone legacy client jars (`guava`, `jackson`).

---

## 2. Setting Up Google Cloud Console (Required for Real Sign-In)

Follow these steps once in Google Cloud Console to enable Google Sign-In and Drive sync on your testing and production builds.

### Step 1: Open Google Cloud Console
1. Go to [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project (e.g. `Luma Reader`) or select an existing project.

### Step 2: Enable the Google Drive API
1. In the search bar at the top, type **Google Drive API**.
2. Click on **Google Drive API** and click **Enable**.

### Step 3: Configure the OAuth Consent Screen
1. In the left navigation menu, go to **APIs & Services** > **OAuth consent screen**.
2. Choose **External** and click **Create**.
3. Fill in the App Information:
   - **App name**: `Luma Reader`
   - **User support email**: Your email address
   - **Developer contact information**: Your email address
4. On the **Scopes** page, click **Add or Remove Scopes** and add:
   - `.../auth/drive.appdata` (See, create, and delete its own configuration data in your Google Drive)
   - `.../auth/drive.file` (See, edit, create, and delete only the specific Google Drive files you use with this app)
5. On the **Test Users** page (while the app is in testing mode):
   - Add the Gmail addresses of any Google accounts you will use to test sign-in on your phone.
6. Click **Save and Continue**.

### Step 4: Create Android OAuth 2.0 Client ID
1. In the left menu, go to **APIs & Services** > **Credentials**.
2. Click **+ CREATE CREDENTIALS** at the top and select **OAuth client ID**.
3. Set **Application type** to **Android**.
4. Set the fields:
   - **Name**: `Luma Reader Android Debug`
   - **Package name**: `com.example.lumareader`
   - **SHA-1 certificate fingerprint**:
     ```
     13:70:78:CB:F8:9F:D8:47:FF:53:52:0D:3F:DD:3B:55:55:17:2B:45
     ```
5. Click **Create**.

> [!TIP]
> Google Play Services natively authenticates your Android app by matching the requesting app's package name and signature fingerprint. You do not need to download or embed any secret client secret keys in the app!

---

## 3. Releasing to Public Google Play Store

When you build a signed release APK or Android App Bundle (AAB) for public release:

1. **Add Production SHA-1 Fingerprint**:
   - In Google Cloud Console > **Credentials**, create a second Android OAuth Client ID:
     - **Package name**: Your production package name (e.g. `com.example.lumareader`).
     - **SHA-1**: The SHA-1 of your release keystore (or if using **Play App Signing**, copy the **App signing key certificate SHA-1 fingerprint** from the Google Play Console under *Setup > App integrity*).
2. **Publish OAuth Consent Screen**:
   - In Google Cloud Console > **OAuth consent screen**, submit your verification or click **Publish App**. Once published, any user with a Google account worldwide can sync without being explicitly added to a test user list.

---

## 4. Conflict Resolution & Sync Behavior

- **Reading Positions**: Last-Write-Wins (LWW) based on `lastReadTimestamp`. If a different device read further or updated position later, local progress updates smoothly. If local device is ahead, remote is updated.
- **Shelves & Collections**: Set union merge. Shelves created on any device are preserved.
- **Annotations**: Union merge by unique annotation UUID with timestamp resolution.
- **Auto-Sync**: Automatically pulls the latest progress on app open when `autoSyncOnOpen` is enabled.
