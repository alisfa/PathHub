# Path Hub

Path Hub is a cross-platform mobile application (**Android** + **iOS**) for exploring video websites with cloud sync and playlist management. On launch, it presents a customizable grid of your favorite video websites. Tapping any site opens a full-screen browser with native HTML5 video playback and controls intact (ensuring 100% compatibility with complex sites like Pornhub), coupled with a lightweight **[+ Save Video]** floating pill and native bottom bar action that saves videos directly to a cloud-synced playlist.

Both your website bookmarks and saved playlist items are backed by a Supabase Postgres database with Realtime WebSocket synchronization, anonymous guest auth with optional email upgrade, and an offline mutation queue.

---

## Deliverables & Binaries

| Platform | File | Description |
| :--- | :--- | :--- |
| **iOS** | [`PathHub-unsigned.ipa`](file:///c:/Users/phar2/Downloads/iosandroid/PathHub-unsigned.ipa) | Unsigned `.ipa` built via `xtool` in WSL with compliant Deflate compression and standard Unix permissions. |
| **Android** | [`PathHub-debug.apk`](file:///c:/Users/phar2/Downloads/iosandroid/PathHub-debug.apk) | Debug `.apk` built with Kotlin 2.0 + Jetpack Compose + Android WebView. |
| **Injected Layer** | [`player-web/dist/player.js`](file:///c:/Users/phar2/Downloads/iosandroid/player-web/dist/player.js) | Bundled video detection and bridge dispatch script injected into both iOS and Android shells. |
| **Backend** | [`supabase/schema.sql`](file:///c:/Users/phar2/Downloads/iosandroid/supabase/schema.sql) | Complete Postgres SQL schema, RLS policies, Realtime publication, and RPC functions. |

---

## Repository Architecture

```
├── player-web/                 # Single-bundle TypeScript video detection & bridge layer
│   ├── src/
│   │   ├── adapters/           # Per-site metadata extraction & URL resolvers (Pornhub, generic)
│   │   ├── bridge.ts           # Cross-platform JS <-> Native bridge (WKScriptMessageHandler & AndroidBridge)
│   │   ├── index.ts            # DOM MutationObserver, SPA route watcher, Floating Save Pill
│   │   └── styles/player.css   # Glassmorphic pill badge and toast notifications
│   ├── dist/                   # Bundled player.js & player.css
│   └── build.js                # Syncs bundled assets directly into iOS Resources and Android Assets
│
├── ios/                        # Pure SwiftPM iOS app (Swift 6 + SwiftUI)
│   ├── Package.swift           # Pure SwiftPM package (no Xcode project or storyboards required)
│   ├── xtool.yml               # xtool manifest (bundle ID, display name, permissions)
│   └── Sources/PathHub/
│       ├── Models/             # Site and PlaylistItem Swift Sendable structs
│       ├── Services/           # SupabaseClient, SupabaseRealtime, SiteStore, PlaylistStore
│       ├── Views/              # HomeView, BrowserView (WKWebView), PlaylistView, AccountSheet
│       └── Resources/          # Embedded player.js, player.css, InjectedScripts.swift, AppIcon.png
│
├── android/                    # Android app (Kotlin 2.0 + Jetpack Compose)
│   ├── app/
│   │   ├── src/main/java/com/pathhub/app/
│   │   │   ├── model/          # Site, Playlist, PlaylistItem data models
│   │   │   ├── data/           # SupabaseConfig, AuthService, RestClient, RealtimeClient, Stores
│   │   │   ├── ui/screens/     # HomeScreen, BrowserScreen (Android WebView), PlaylistScreen, AccountDialog
│   │   │   └── MainActivity.kt # Compose Navigation host
│   │   └── src/main/assets/    # player.js and player.css assets
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── supabase/                   # Cloud backend
│   └── schema.sql              # Idempotent DDL: tables, indexes, RLS, triggers, Realtime publication
│
├── package_ipa.py              # Generates ZIP_DEFLATED, Scarlet/AltStore-compliant IPAs
├── PathHub-unsigned.ipa        # Compiled iOS IPA
├── PathHub-debug.apk           # Compiled Android APK
└── README.md
```

---

## Backend Setup (Supabase)

The apps connect to your Supabase project:
* **URL**: `https://ejobnyceertuvwkjfaxk.supabase.co`
* **Publishable Key**: `sb_publishable_tzwP5svHwAtTr3AnZSMOqg_EmStv2be`

### Initial Database Deployment
To provision the tables, triggers, and Realtime replication:
1. Open the [Supabase SQL Editor](https://supabase.com/dashboard/project/ejobnyceertuvwkjfaxk/sql/new) in your browser.
2. Copy the entire contents of [`supabase/schema.sql`](file:///c:/Users/phar2/Downloads/iosandroid/supabase/schema.sql).
3. Paste into the SQL editor and click **Run**.
4. The script executes idempotently:
   - Tables: `public.sites`, `public.playlists`, `public.playlist_members`, `public.playlist_items`
   - Explicit Grants: `GRANT ALL ON ... TO anon, authenticated`
   - RLS Policies: Multi-tenant ownership and collaboration enforcement
   - Triggers: `on_auth_user_created` seeds default sites (`Pornhub`, `Big Buck Bunny`, `Internet Archive`) and auto-provisions a personal playlist with a unique 6-character invite code
   - Realtime: Adds tables to `supabase_realtime` publication with `REPLICA IDENTITY FULL`
   - RPC: `join_playlist_by_code(code)` function for joining shared playlists.

---

## iOS Setup & Build (via WSL + xtool)

The iOS application is built entirely as a pure SwiftPM project without needing Xcode on Windows.

### Prerequisites (inside WSL Ubuntu)
```bash
# 1. Update and install dependencies
sudo apt-get update && sudo apt-get install -y clang libxml2-dev libz3-dev curl git zip

# 2. Install Swift toolchain (if not already installed)
# swift --version should return Swift 6.0+

# 3. Install xtool (github.com/xtool-org/xtool)
curl -fsSL https://raw.githubusercontent.com/xtool-org/xtool/main/install.sh | bash
xtool sdk install 18.1
```

### Compiling the App
From Windows PowerShell:
```powershell
# Compile the .app bundle using xtool inside WSL
wsl -d Ubuntu-24.04 bash -c "cd /mnt/c/Users/phar2/Downloads/iosandroid/ios && /usr/local/bin/xtool dev build"

# Package into a compliant unsigned IPA (handles Deflate compression and standard permissions)
python package_ipa.py
```
Output: [`PathHub-unsigned.ipa`](file:///c:/Users/phar2/Downloads/iosandroid/PathHub-unsigned.ipa).

### Sideloading & Signing on iOS
1. **Scarlet (Direct on device)**:
   - Transfer `PathHub-unsigned.ipa` to your iPhone (via AirDrop, iCloud Drive, or local HTTP).
   - In Scarlet, tap the top-right install button and select `PathHub-unsigned.ipa`.
2. **AltStore / Sideloadly (Free Apple ID 7-day signing)**:
   - Connect your iPhone to your Windows PC via USB.
   - Open Sideloadly or AltStore.
   - Drag `PathHub-unsigned.ipa` into Sideloadly.
   - Enter your Apple ID and password to sign with your free developer certificate.
   - On your iPhone, go to **Settings > General > VPN & Device Management**, tap your Apple ID, and tap **Trust**.

---

## Android Setup & Build (Gradle / Android Studio)

The Android application is written in Kotlin using Jetpack Compose, Android WebView, and Material 3.

### Prerequisites
* JDK 21 (bundled with Android Studio at `C:\Program Files\Android\Android Studio\jbr`)
* Android SDK (API 35/36 installed at `C:\Users\phar2\AppData\Local\Android\Sdk`)

### Compiling from Command Line
From the workspace root in PowerShell:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Set-Location -Path "android"
.\gradlew.bat assembleDebug
```
Output APK is created at: [`android/app/build/outputs/apk/debug/app-debug.apk`](file:///c:/Users/phar2/Downloads/iosandroid/android/app/build/outputs/apk/debug/app-debug.apk) and mirrored to [`PathHub-debug.apk`](file:///c:/Users/phar2/Downloads/iosandroid/PathHub-debug.apk).

### Installing to an Android Device / Emulator
```powershell
# Check connected devices
adb devices

# Install APK directly
adb install -r PathHub-debug.apk
```

---

## Shared Injected Player Layer (`player-web`)

### Architecture
Sites like Pornhub use internal shadow DOM elements, video click-capture listeners (`.mgp_clickCapture`), and custom overlays that break when third-party custom controls are superimposed. Path Hub uses a non-destructive injection approach:
1. **Native Controls Preserved**: The native HTML5 video player controls (`video.controls = true`), native fullscreen, PiP, and AirPlay/Cast are left 100% operational.
2. **DOM Watcher**: A `MutationObserver` and History API interceptor continuously monitors the page for video elements and route changes.
3. **Floating Glass Pill**: When a video is present, a floating `[+ Save Video]` button appears in the top-right corner with smooth safe-area awareness.
4. **Bridge Dispatch**: Tapping either the floating pill or the native bottom-bar Save button triggers `window.__pathHubSaveCurrentVideo()`, which extracts:
   - `videoUrl`: Direct MP4/HLS source if available, or the canonical page URL
   - `pageUrl`: Current window location
   - `title`: Video title extracted from page metadata / OpenGraph tags
   - `thumbnailUrl`: Poster image or OpenGraph preview image
   - `siteId`: Associated site ID

### Re-bundling the Player Layer
```powershell
cd player-web
npm run build
```
This automatically updates `dist/player.js` & `dist/player.css`, copies them to `ios/Sources/PathHub/Resources/` (updating `InjectedScripts.swift`), and writes them to `android/app/src/main/assets/`.

---

## Features Walkthrough

### 1. HOME Screen (Website Hub)
- **Website Grid/List**: Displays each site's high-resolution favicon, name, and domain.
- **Add Website**: Enter any URL (e.g. `pornhub.com`). The app normalizes it to `https://`, fetches the page title and Google favicon, and checks for duplicates.
- **Swipe-to-Delete**: Swipe left to delete a site with an immediate confirmation dialog and a 5-second **Undo** action.
- **Reorder**: Drag-and-drop or Move Up/Down buttons persist the `sort_order` across all devices.
- **Realtime Sync**: Inserting or deleting a site on Android instantly reflects on iOS in real time.

### 2. BROWSER Screen
- **Full-Screen WebView**: High-performance hardware-accelerated WebView.
- **Isolated Cookies**: Per-site session storage where supported.
- **Bottom Navigation**:
  - `Back` / `Forward`: Native web navigation with history state checks.
  - `Home`: Returns to the website grid.
  - `Save (+)`: Saves current video to the cloud playlist (turns red when saved).
  - `Playlist`: Direct shortcut to view saved videos.
- **Fullscreen Video**: Handles native orientation changes and HTML5 video fullscreen overlays without crashing or restarting the WebView.

### 3. PLAYLIST Screen
- **Cloud-Synced Videos**: List of saved videos with thumbnail, title, domain tag, and timestamp.
- **Domain Filter**: Quick filter chips to view videos saved from specific sites (e.g. "All", "pornhub.com", "blender.org").
- **Tap to Play**: Tapping any saved item opens the browser directly at that video.
- **Collaborative Sharing**:
  - **Share Invite Code**: Copies your playlist's unique 6-character code (e.g. `7K8N3Q`) or launches the system share sheet.
  - **Join by Code**: Enter another user's invite code to collaborate on a shared playlist.

### 4. ACCOUNT & OFFLINE SYNC
- **Anonymous Guest Auth**: On first launch, a persistent guest UUID is provisioned.
- **Email Account Upgrade**: Link your guest profile to an email and password in the Account Dialog to sign in across multiple devices under the same user ID.
- **Offline Mutation Queue**: If offline, site additions, deletions, reorders, and saved videos are queued in local cache and automatically synced to Supabase when connectivity returns.

---

## Known Limitations

1. **DRM & Encrypted Media**:
   - Streams protected with Widevine/FairPlay DRM or encrypted MediaSource blobs that enforce restricted domain playback cannot be extracted as standalone `.mp4` URLs; Path Hub saves the canonical page URL and launches the web player seamlessly.
2. **Third-Party Site Terms of Service**:
   - Extraction and bookmarking features are intended for personal streaming; users are responsible for complying with the terms of service of third-party video hosts.
3. **Free Apple ID 7-Day Signing Limit**:
   - IPAs signed with a free personal Apple ID expire every 7 days and must be refreshed through AltStore, Sideloadly, or Scarlet. Paid Apple Developer accounts support 365-day signing.
