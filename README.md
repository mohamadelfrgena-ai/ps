# AddonStation — Native Android App (Kotlin + Jetpack Compose)

> **ملخص:** نسخة أندرويد أصلية (Native) كاملة من تطبيق AddonStation — Kotlin + Jetpack Compose،
> بدون WebView، نفس التصميم والشاشات والوظائف والتنقل وشاشة البداية والحركات مثل النسخة الأصلية.

A complete, fully-native Android port of the AddonStation app — Kotlin + Jetpack Compose,
no WebView. Same design, screens, features, navigation, splash screen and motion as the
original, backed by the same Supabase backend.

- Package: `com.addonstation.app` · minSdk 24 · target/compile 34
- Version: `1.22` (versionCode 23)
- Backend: Supabase REST + Auth + Storage + Realtime (phoenix v1 channels over WebSocket)
- Networking: Ktor · Persistence: DataStore · Images: Coil (incl. GIF stickers)

## Screen parity map (original `/m/*` → native route)

| Original page | Native route | File |
|---|---|---|
| Boot splash + welcome overlay | `splash`, `welcome` | `ui/screens/SplashScreen.kt` |
| Home | `home` | `ui/screens/HomeScreen.kt` |
| Store + item detail | `store`, `item/{id}` | `ui/screens/StoreScreen.kt` |
| Community chat + thread | `community?channel&message`, `thread/{rootId}` | `ui/screens/ChatScreen.kt` |
| Notifications | `notifications` | `ui/screens/NotificationsScreen.kt` |
| Profile + settings + public user | `profile`, `profile_settings`, `user/{id}` | `ui/screens/ProfileScreens.kt` |
| Login / register / forgot / reset | `login`, `register`, `forgot`, `reset_password` | `ui/screens/AuthScreens.kt` |
| App settings | `settings` | `ui/screens/SettingsScreen.kt` |
| Support + IP guide | `support`, `ip_guide` | `ui/screens/SupportScreens.kt` |
| AC wallet | `wallet` | `ui/screens/WalletScreen.kt` |
| Checkout + orders (+ plans sheet) | `checkout/{planId}`, `orders` | `ui/screens/CheckoutScreens.kt` |
| Exclusives | `exclusives` | `ui/screens/MiscScreens.kt` |
| Creators + profile + studio + application + tip | `creators`, `creator/{id}`, `creator_studio`, `creator_application`, `tip/{creatorId}` | `ui/screens/CreatorScreens.kt` |
| Admin panel (staff only) | `admin` | `ui/screens/AdminScreens.kt` |

Bottom tabs (like the original): Settings · Community · Home · Store · Profile.

## Project structure

```
app/src/main/
├── AndroidManifest.xml                 # launcher + addonstation://recovery deep link
├── assets/                             # bundled: stickers/*, items/*, img/*, img/ip-guide/*
├── res/                                # locales (en/ar full, es/pt/fr partial→fallback),
│                                       # 9 stroke icons, Monocraft font, launcher icons, themes
└── java/com/addonstation/app/
    ├── AddonStationApp.kt               # manual-DI container (session/backend/repos/Coil)
    ├── MainActivity.kt                 # system splash, locale/theme apply, recovery links
    ├── core/                           # data layer (no Android UI deps)
    │   ├── Constants.kt                # backend URL/key, buckets, limits, asset paths
    │   ├── Models.kt                   # @Serializable DTOs for every table/RPC
    │   ├── Catalogs.kt                 # 106 chat-item defs + 5 sticker tabs (181 files)
    │   ├── SupabaseClient.kt           # REST/Auth/Storage/RPC over Ktor
    │   ├── SessionManager.kt           # DataStore: session, IP, theme, locale, flags
    │   ├── RealtimeClient.kt           # phoenix v1 join + heartbeat + backoff retry
    │   ├── Utils.kt                    # Formats, Plans, TimeAgo, mentions, validators…
    │   ├── AuthRepository.kt           # sign in/up/out, recovery, AUTH_* error codes
    │   ├── Repositories.kt             # Profile/Content/Wallet/Payments/Assets/Creators
    │   └── ChatRepository.kt           # channels/messages/media/reactions/mutes/reports
    └── ui/
        ├── theme/Theme.kt              # Monocraft display font, dark/light schemes
        ├── navigation/NavGraph.kt      # Routes + NavHost + AppViewModel + MainScaffold
        ├── components/Components.kt    # TopBar, bottom bar, cards, MentionText (dir=auto)…
        ├── components/Dialogs.kt       # Connect/IP, language, mute, receipt viewer…
        └── screens/*.kt                # one file per area (see parity map)
legacy/                                 # reference APK (reassembled), not part of the build
```

## Features

- Auth incl. Google/Facebook/Discord OAuth (Custom Tabs), recovery deep link
- Device-IP binding gate for downloads (PS4/PS5 guide with pictures)
- Store with search/type/plan/sort, ratings, favorites, AC unlock, DownloadManager
- Realtime chat: 6 channels, stickers/GIF, images, replies, threads, reactions, @mentions
  with autocomplete, moderation (mute/delete/reports), members sheet, rules banner
- AC wallet: balance, tasks, events, history, login-reward auto-claim
- Subscriptions: plans, payment methods, receipt upload, orders, admin approve/reject
- Creators: directory, profiles, follow, studio uploads, application + review chat,
  tipping with receipts, exclusives
- Staff admin: reports, content, users, payments, applications, submissions, assets,
  tasks, methods, plans, AC adjustments
- 5 locales (ar/en full, es/pt/fr fallback), dark/light theme, RTL support

## Backend configuration

Defaults live in `core/Constants.kt` (`DEFAULT_SUPABASE_URL`, `DEFAULT_SUPABASE_ANON_KEY`)
and can be overridden at runtime from Settings → Backend (stored in DataStore).
Buckets used: `payment-receipts`, `content-files`, `creator-media`, `chat-images`,
`site-assets`, `content-media`, `chat-media`.

## Build (run it yourself — no APK is committed)

```bash
# requires Android SDK 34 + JDK 17 + Gradle 8.7+
gradle :app:assembleDebug        # → app/build/outputs/apk/debug/
gradle :app:assembleRelease      # minified + shrunk (see proguard-rules.pro)
```

No wrapper JAR/scripts are committed: build with a local Gradle 8.7+
(`gradle wrapper` first if you want `./gradlew`), or just open the project
in Android Studio, which syncs and builds it directly.

## Notes

- `MainActivity` keeps the AndroidX splash on screen until the container finishes
  `init()` (backend ping + profile/plan refresh), then the animated Compose splash runs.
- Unread badge: realtime `chat_notifications` INSERT events + 30 s polling fallback.
- All user-visible text (449 keys) ships in English + Arabic; es/pt/fr fall back
  to English where a translation is missing.
