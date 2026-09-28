# AddonStation — Part 2: Chat Rooms, Discord-Style Chat & Profile, Admin-Only Control Panel, Turkish Language

(Independent document. Give it to the Android Studio AI agent. It works together with the main file `AddonStation-Design-Spec.md`, but everything needed for these features is repeated here.)

Tags used below:
- **[ORIGINAL]** = the feature exists in the original app and must be kept.
- **[NEW]** = new feature added for this project (inspired by Discord). Build it unless told otherwise.

Build as a **native Android app** (Kotlin + Jetpack Compose). Arabic is the main language and the layout is RTL when Arabic is selected.

---

## Part A — Add Turkish (Türkçe)

The original app has 7 languages. Add an 8th.

**Final language list** (dropdown in Settings, in this order):
| Flag | Name | Code | Direction |
|---|---|---|---|
| 🇸🇦 | العربية | ar | RTL |
| 🇬🇧 | English | en | LTR |
| 🇧🇷 | Português | pt | LTR |
| 🇪🇸 | Español | es | LTR |
| 🇷🇺 | Русский | ru | LTR |
| 🇨🇳 | 简体中文 | zh | LTR |
| 🇫🇷 | Français | fr | LTR |
| 🇹🇷 | **Türkçe** | **tr** | LTR |

Rules:
1. Every string in the app must exist in all 8 languages. Use Android string resources (`values-tr/strings.xml`) so switching language changes the whole app immediately.
2. Turkish is left-to-right, so the layout flips back to LTR when Turkish is selected.
3. **Pixel font must support Turkish letters**: `İ ı Ş ş Ğ ğ Ç ç Ö ö Ü ü`. If the Minecraft-style font does not include them, use a fallback font for those characters. Test it.
4. Upper-casing must use the Turkish locale (`uppercase(Locale("tr"))`) so `i` becomes `İ`, not `I`.
5. Time-based greeting for Turkish: 05:00–11:59 → **Günaydın**, otherwise → **İyi akşamlar**.

**Core Turkish strings** (translate all remaining strings in the same style):
| Meaning | Türkçe |
|---|---|
| Welcome (guest) | Hoş geldin |
| Welcome back, {name} | Tekrar hoş geldin, {name} |
| Sign in | Giriş Yap |
| New account | Yeni Hesap |
| Create account | Hesap Oluştur |
| Email / Password | E-posta / Şifre |
| Forgot password? | Şifreni mi unuttun? |
| Search addons and worlds... | Eklenti ve dünya ara... |
| Quick actions | Hızlı İşlemler |
| My favorites / My downloads | Favorilerim / İndirmelerim |
| Upgrade | Yükselt |
| Device | Cihaz |
| Featured | Öne Çıkanlar |
| Latest addons | En Yeni Eklentiler |
| Recommended for you | Senin İçin Önerilenler |
| View all | Tümünü Gör |
| Nav: Settings / Community / Home / Store / Profile | Ayarlar / Topluluk / Ana Sayfa / Mağaza / Profil |
| Download | İndir |
| Save / Saved | Kaydet / Kaydedildi |
| Language | Uygulama Dili |
| Dark / Light theme | Koyu / Açık Tema |
| Log out | Çıkış Yap |
| Message | Mesaj yaz |
| Send | Gönder |
| Stickers | Çıkartmalar |
| Add photo | Fotoğraf ekle |
| Reply | Yanıtla |
| Report | Bildir |
| Delete | Sil |
| Members | Üyeler |
| Online / Offline | Çevrimiçi / Çevrimdışı |
| About me | Hakkımda |
| Member since | Üyelik tarihi |
| Level & reputation | Seviye ve itibar |
| Statistics | İstatistikler |

**Chat rules in Turkish** (shown before first entry to chat):
- 🤝 Herkese saygı göster
- 🚫 Hakaret ve taciz yok
- 📵 Irkçılık ve ayrımcılık yok
- 🔗 Şüpheli bağlantı ve izinsiz reklam yok
- 🔒 Kişisel bilgi paylaşma
- 🛑 Uygunsuz içerik yok
- 🏛️ Siyasi tartışma yok
- ⚠️ İhlaller susturma veya yasaklanmayla sonuçlanabilir
- Buttons: **Kabul ediyorum ve Sohbete giriyorum** / **Reddet ve Ana Sayfaya dön**

---

## Part B — Chat Rooms

### B1. Room list (fixed rooms to create at first setup)

**💬 الغرف الأساسية / Main rooms** (category 1)
| Order | Icon + Name | Internal id |
|---|---|---|
| 1 | 🌐 General | general |
| 2 | ⛏️ Minecraft | minecraft |
| 3 | 🎨 Creator | creator |
| 4 | 🛠️ Support | support |
| 5 | 📱 Social Media Makers | social-media-makers |

**🌍 غرف اللغات / Language rooms** (category 2)
| Order | Icon + Name | Internal id |
|---|---|---|
| 1 | 🇱🇾 العربية | lang-ar |
| 2 | 🇷🇺 Русский | lang-ru |
| 3 | 🇵🇹 Português | lang-pt |
| 4 | 🇨🇳 中文 | lang-zh |
| 5 | 🇬🇧 English | lang-en |
| 6 | 🇪🇸 Español | lang-es |
| 7 | 🇫🇷 Français | lang-fr |
| 8 | 🇹🇷 Türkçe | lang-tr |

Total: 13 rooms in 2 categories. Names are shown exactly as above (they are not translated). The category titles are translated with the app language.

Room data: `id`, `name`, `icon`, `category`, `sort_order`, `description`, `is_locked` (read-only), `slowmode_seconds` (0 = off), `created_at`. Only the admin can create, rename, reorder, lock, or delete rooms (see Part E).

Behavior:
- Default room when opening Community: **General**.
- **[NEW]** The first time a user opens chat, highlight the language room that matches the app language (do not force them into it).
- **[ORIGINAL]** Before the first entry, the user must accept the community rules (Part A has the Turkish text). Save the acceptance per user. "Reject" returns to Home.
- Guests (not logged in) can read but cannot send; show "سجّل الدخول للمشاركة" with a login button in place of the input bar.

### B2. Room list screen (Discord style) **[NEW]**
- Dark screen, title "Community" with a members icon on the side.
- Two collapsible category headers with a small arrow (▾ / ▸): `الغرف الأساسية`, `غرف اللغات`. Header text: small, uppercase style, muted color, letter-spacing.
- Each room is one row (height 48dp, radius 10): icon, name, and on the far side an unread indicator:
  - unread messages: small white dot and the name in bold/white,
  - you were mentioned: red pill with the number.
  - Read rooms: name in muted color.
  - Selected room: elevated background with a green thin bar on the start edge.
- Locked rooms show a 🔒 icon.
- At the bottom, a **user panel** (like Discord): avatar with status dot, username, rank badge, and a gear icon opening Settings.

---

## Part C — Discord-Style Chat Screen

### C1. Colors (same theme as the main app)
bg `#0B0F14`, surface `#131A24`, elevated `#1A2330`, elevated2 `#212D3D`, border `#263240`, text `#E8EEF5`, muted `#8EA0B3`, dim `#5C6F82`, green `#3DDC84`, gold `#FFD479`, blue `#6FB8FF`, red `#FF5A5A`, purple `#B98CFF`.

**Name colors by rank** (like Discord role colors) **[NEW look, ORIGINAL ranks]**:
- Admin ("TheKing ✦"): `#7FD4FF`
- Moderator: `#3DDC84`
- Creator ("Creator ✦"): `#3DDC84`
- Legendary 👑: `#FFD479` (soft glow)
- Epic 💎: `#B98CFF` (soft glow)
- Free: `#E8EEF5` (normal text color)

### C2. Header bar
Back arrow, room icon + name (bold), the room description under it (small, muted), and on the far side: 👥 members button, 🔍 search in room **[NEW]**, 📌 pinned messages **[NEW]**.

### C3. Message list
Rendering rules (Discord-like):
- **Round avatar 40dp** (circle) at the start side, small status dot (green online / gray offline) at its bottom corner. Respect the user's "show online status" setting.
- Message header: **username** (rank color) → rank badge → equipped item icon (small) → time (small, dim).
- **Grouping**: consecutive messages from the same person within 5 minutes are stacked under one header; the later ones show only the text and, when touched, the time on the side.
- **Date divider**: a thin line with the date in the middle ("Today", "Yesterday", or the date).
- **New messages divider** **[NEW]**: red thin line with a small "NEW" tag where unread messages start.
- **Reply**: a small preview line above the message: curved connector line, small avatar, "@name" and first 60 characters of the original, muted. Tapping it scrolls to the original and flashes it. **[ORIGINAL: reply with quote of 60 chars]**
- **Mentions**: `@username` shown as a blue chip. If the message mentions me, give the whole message a light blue tint with a blue bar on the start edge. **[ORIGINAL: mention system]**
- **Reactions**: chips under the message (emoji + count). A chip is highlighted (green border) when I reacted. Tap toggles my reaction. A small "+" chip opens the emoji picker. Quick reactions shown first: 👍 ❤️. **[ORIGINAL: 👍 ❤️ reactions]**
- **Stickers**: shown large (about 150dp) without a bubble. **[ORIGINAL]**
- **Images**: rounded 12dp, max width 260dp, tap to open full screen with zoom and a close button. **[ORIGINAL: image attachments]**
- **Audio / video files**: inline player card (🎙️ Audio, 🎬 Video) with duration and file size. **[ORIGINAL]**
- **Admin announcement** **[NEW]**: a message sent by admin can be marked "Announcement": gold left bar, 📢 icon, tinted background.
- **System messages** **[NEW]**: centered small muted line, e.g. "Ahmed joined", "Message deleted by moderator".
- **Deleted/hidden message by moderator**: replaced with "🚫 تم إخفاء هذه الرسالة" (visible to admin with a "show" option).
- Empty room text: "💬 لا توجد رسائل — كن أول من يرسل في هذه القناة!" **[ORIGINAL]**
- Loading text: "جارٍ تحميل الرسائل..." and error text with a retry button. **[ORIGINAL]**
- Auto-scroll to bottom for new messages only if I was already near the bottom (within ~72dp); otherwise show a floating "↓ New messages" button. **[ORIGINAL logic]**
- Load the last **100 messages**, and load older ones when scrolling up (paging). Live updates in real time (the original also refreshed every ~2 seconds as a backup).
- **Typing indicator** **[NEW]**: "Ahmed is typing…" above the input bar.

### C4. Message actions (long-press on a message → bottom sheet)
For everyone: React, Reply, Copy text, Report.
For the message owner **[NEW]**: Delete my message.
For Moderator/Admin: Delete message (hide), Pin/Unpin **[NEW]**, Mute this user, View profile.
Report opens a small form with reasons (spam, insult, inappropriate, other) **[ORIGINAL: reports exist with a reason field]**.

### C5. Input bar (bottom) — must include: send button, stickers, add image
Layout, from start to end:
1. **➕ button** (round, elevated2): opens a sheet with **Photo** (image from gallery) and **Audio / Video file**. **[ORIGINAL]**
2. **Text field** (rounded pill, elevated, max 4 lines, placeholder "Message 🌐 General" translated).
3. **😊 button**: opens the **Emoji + Sticker picker** (see C6).
4. **Send button**: round green gradient button with an arrow icon. Disabled (dim) when the field is empty and nothing is attached. Sending an empty message is not allowed.

Above the bar (only when needed):
- Reply banner: "↩ الرد على {name} ✕". **[ORIGINAL]**
- Attachment preview chip: thumbnail/name with ✕ to remove. Image rules below.
- @mention suggestions list: typing `@` shows members (max 40, filtered as you type). **[ORIGINAL]**

Limits (**[ORIGINAL]**):
- Text message: max **500 characters** (show a counter when under 50 left).
- Links and personal data (phone numbers, emails) are **not allowed** in messages; block on the client and also on the server.
- Images: JPG / PNG / WEBP only, max **4 MB**. Messages: "يسمح فقط بـ JPG / PNG / WEBP" and "الحد الأقصى 4MB".
- Audio/video: max **100 MB** ("The maximum media size is 100 MB."). Show upload progress; "Audio sent." / "Video sent." on success.
- A muted user sees the input bar disabled with "أنت مكتوم حتى {time}".
- Slowmode **[NEW]**: if the room has slowmode, show a countdown on the send button after each message.

### C6. Emoji + Sticker picker (bottom sheet)
- Two tabs: **Emoji** and **Stickers**.
- Emoji tab: grid of emojis (the original list of 48: 😀 😃 😄 😍 😂 🥰 😎 🤣 😅 😇 😉 🤔 😴 🤯 🥳 😢 😭 😡 🤩 👍 👎 👏 🙏 💪 🔥 ❤️ 💔 💎 👑 🍃 ⭐ 🎮 🕹 🧊 🏆 ⚽ 🚀 ✨ 🎉 💬 ✅ ❌ 😊 😁 🤝 🧡 💛 💜). Add a search field and "recent" row **[NEW]**.
- Stickers tab: pack icons row at the bottom (each pack shows its first sticker as icon), grid of stickers of the selected pack above (4 columns). Tap a sticker = send immediately. **[ORIGINAL]**
- The 5 original packs (names and counts): `meme` (47), `Minecraft Memes1` (21), `Minecraft Memes2` (87), `Minecraft Memes3` (20), `Minecraft Movie` (6). Stickers are 128–256 px WEBP/PNG/GIF. Replace with your own art before publishing.
- New packs are added by the admin from the panel (Assets → Sticker), not by editing the app.

### C7. Members sheet **[ORIGINAL list + NEW Discord grouping]**
Opens from 👥. Grouped by role with counts, like Discord:
`Admin — 1`, `Moderators — 2`, `Creators — 5`, `Legendary 👑 — 8`, `Epic 💎 — 10`, `Online — 30`, `Offline — 120`.
Each row: round avatar with status dot, name in rank color, rank badge. Tap → profile card (Part D). Empty: "لا أحد متصل الآن".

---

## Part D — Discord-Style Profile

### D1. Profile card (bottom sheet when tapping a user in chat) **[ORIGINAL data, NEW look]**
Top to bottom:
1. **Banner** image (height 100dp, full width, dark gradient at the bottom). Default banner if none.
2. **Avatar**: circle 80dp overlapping the banner's bottom edge on the start side, with a 6dp border in the card color, and the status dot (green/gray) on its corner.
3. **Name row**: display name (bold 20), under it `@username` (muted), then a **badges row** of small rounded chips: rank (Free/Epic/Legendary/Admin/Creator), Level N, equipped item (icon + name).
4. **Action buttons** row: green **💬 Message** (adds `@name` to the chat input), blue **🤝 Add Friend** (hidden on my own card; becomes "Request sent" / "Friends ✓"), ghost **Open Profile ↗**, and a ⋯ menu (Report user, Block) **[NEW]**.
5. **Info panel** (one rounded darker card, sections separated by thin lines, like Discord):
   - **About Me** (bio; default text "This member has not added a bio yet.")
   - **Member Since** (date, e.g. "Sep 28, 2026")
   - **Level & Reputation** (progress bar + "Level N · Active member")
   - **Roles** **[NEW]**: colored pills with a small round dot, e.g. ● Legendary, ● Creator, ● Admin
   - **Statistics**: Downloads (sum of downloads of their content), Worlds count, Published count; up to 3 latest published items shown as small cards.
6. Close: handle bar on top and ✕.

### D2. Full profile screen
Same visual language as D1 with a larger banner (140dp). Own profile keeps the main app's tabs (نبذة, محتواي, مفضلتي, خطتي) and buttons (✏️ تعديل الملف, 👑 ترقية). Another user's profile shows About + their published content.
- Avatar and banner are picked from the assets unlocked by the user's plan (Free / Epic / Legendary / Admin only). **[ORIGINAL]** The original ships ~29 default profile/banner images.
- **[NEW]** Optional custom status text (max 60 chars) shown under the name, and the online status toggle stays in Settings.

---

## Part E — Admin-Only Control Panel

### E1. Access rule (very important)
- The control panel is for **Admin role only**. Not moderators, not creators, not Epic/Legendary users.
- In the original, the profile shows a "⚙️ لوحة" button to some roles. Change it: **only `role == admin` sees the button/entry**. Everyone else must not see any trace of it (no button, no menu item, no deep link that opens it).
- Creators keep their own separate screen, **Creator Studio** (not part of this panel).
- **Security must be enforced on the server, not only in the app.** Hiding a button is not protection. Every admin action (change role, approve payment, delete content, give coins, etc.) must be checked by the backend rules/functions to confirm the caller is an admin, and rejected otherwise. If a non-admin somehow opens an admin screen, show "غير مسموح — حسابك ليس Admin" and close it. **[ORIGINAL message: "غير مسموح حسابك ليس Admin بعد."]**
- Roles in the system: `user`, `creator`, `moderator`, `admin`. Only an admin can change anyone's role. Never allow a user to change their own role. Admin cannot remove the last remaining admin.
- **Moderator**: no access to the panel. Moderators only get the in-chat tools (delete/hide message, mute user, review reports from the message sheet). Keep this setting easy to change later.
- Ask for re-login/biometric before opening the panel **[NEW, optional]**.

### E2. Panel layout **[ORIGINAL structure]**
Bottom bar with 5 items: **الرئيسية (Overview)** · **المحتوى (Content)** · **المدفوعات (Payments)** · **الإشراف (Moderation)** · **المزيد (More)**.
"المزيد" opens a sheet titled "أدوات الإدارة" with: 🖼️ الأصول · 🎬 المبدعون (مراجعة طلبات المحتوى) · ⚙️ الإعدادات (الخطط وطرق الدفع) · 🪙 AC Management · 💛 Creator Tips · 🧑‍🎨 Creator Applications · ↩ تطبيق AddonStation (back to the app).
Header shows the page title and the AC chip like the rest of the app.

### E3. Screens and what each one does

**1) Overview — "ADMIN DASHBOARD / لوحة الإدارة"** **[ORIGINAL]**
- Counters: المستخدمون (users), المحتوى المنشور (published content), الإضافات (addons), العوالم (worlds).
- Quick buttons: ＋ إضافة محتوى, 💳 المدفوعات, ⚑ بلاغات الشات, 🧪 طلبات المبدعين, ⚙ الخطط والدفع, 🖼 إدارة الأفاتارات والملصقات.
- Content list: cover, title, buttons Edit (title, description, category, version) and Delete (with confirmation).
- **Users & subscriptions list**: for each user, a plan selector (Free / Epic / Legendary), a role selector (User / Creator / Moderator / Admin), and **حفظ** button. **[NEW]** add search by name/email and a filter by role.

**2) Content — "CONTENT ADMINISTRATION / إضافة محتوى جديد"** **[ORIGINAL]**
Form fields: content type (addon / world / Resource Pack / Map), name, short link (slug, auto-generated from the name), description, category (Adventure, Survival, PvP, Building, Skins, Other), version, plan (Free 🍃 / Epic 💎 / Legendary 👑), cover image, content file (addon/world), checkboxes: "محتوى مميز" (featured) and "محتوى حصري" (exclusive). Button: **رفع ونشر المحتوى**. Also an "import from an official link" option that fills title, description and cover for preview. Show progress and errors.

**3) Payments — "PAYMENTS / طلبات الدفع"** **[ORIGINAL]**
List of payment orders (user, plan, amount, method, reference, status). Buttons: **عرض الإيصال** (open receipt image), **تأكيد الدفع** (approve → activates the plan), **رفض** (reject).

**4) Moderation — "CHAT MODERATION / بلاغات الشات"** **[ORIGINAL]**
List of open reports: reason, status, reported message text, message id, author id. Actions: **hide the message**, **mute the user for N minutes**, **close the report**. Empty: "لا توجد بلاغات مفتوحة 🎉".

**5) Assets — "ASSET MANAGEMENT / الأفاتارات والملصقات والـItems"** **[ORIGINAL]**
Form: file type (أفاتار / Banner للملف الشخصي / ملصق / Item للشات), file name, unlocked with (Free 🍃 / Epic 💎 / Legendary 👑 / Admin فقط), image or GIF file, button **رفع وحفظ**. Below: grid of all assets with delete button. **[NEW]** For stickers also choose the sticker pack (create a new pack from here).

**6) Settings — "ADMIN SETTINGS / الخطط وبيانات الدفع"** **[ORIGINAL]**
Subscription plans: price (in LYD) and duration (days) per plan, **حفظ**. Local payment methods: add (name, account identifier, extra fields, instructions) and delete.

**7) AC Management** **[ORIGINAL]**
Stats: Total AC earned, spent, held, users with AC, given by admin, top tasks by claims, top earners. User search with **+ Give** / **− Remove** (asks amount, records transaction "Admin Reward" / "Admin Adjustment"). Global reward to all eligible users with a preview (amount × number of eligible users). Transactions list with search and type filter. Tasks editor: title, reward, frequency (Once / Daily / Streak / Manual), max claims, active toggle, Save / Delete.

**8) Creator Tips** **[ORIGINAL]**: list of tip requests with receipt; **Approve & credit** (credits the creator wallet) or **Reject**. Message: "Verify the receipt before approving."

**9) Creator Applications** **[ORIGINAL]**: statuses 🟡 Pending, 🟢 Approved, 🔴 Rejected, 🔵 More Information Required. Each application expands to show experience, what they want to publish, previous work, portfolio, motivation, bio. Actions: ✅ Approve Creator (notification "Welcome to AddonStation Creators!"), 📝 Request More Information (asks for a note), ❌ Reject (asks for a reason), ⭐ Toggle Featured Creator.

**10) Creator content review — "CREATOR REVIEW"** **[ORIGINAL]**: submissions from creators; Approve = publish to the store, Reject = with admin note.

### E4. Chat administration inside the panel **[NEW]** (Discord-inspired)
Add a new page **الدردشة (Chat)** under "المزيد":
- **Rooms manager**: create, rename, change icon, reorder (drag), move between categories, lock (read-only), delete room, set slowmode (off / 5s / 10s / 30s / 1m / 5m).
- **Room permissions**: per room choose who can post: everyone / logged-in users / creators+admin / admin only (useful for an announcements room or the Creator room).
- **Announcements**: send a highlighted message to one or all rooms.
- **User discipline**: search a user → Mute (5 min, 1 hour, 1 day, 1 week, custom) / Unmute / Ban from chat / Unban, with a reason. Muted users cannot send but can read.
- **Reports queue**: same as Moderation (E3-4), plus bulk actions.
- **Audit log**: every admin/moderator action saved with who, what, when (delete message, mute, role change, payment approval, coin changes). Read-only list with filters.
- **Auto-moderation settings**: blocked words list, block links (on by default), block phone numbers/emails (on by default), rate limit (max messages per 10 seconds).
- **Chat rules editor**: edit the rules text in every language.

### E5. What admins can do inside the chat itself
Admin sees extra options everywhere in chat (moderators get the first four only):
1. Delete/hide any message.
2. Mute a user from the message sheet (asks duration).
3. Pin/unpin messages (max 50 per room).
4. Review reports.
5. Send announcements.
6. Change slowmode and lock the room from the room header menu.
7. Admin name is shown in `#7FD4FF` with the "TheKing ✦" badge and cannot be muted by others.

---

## Part F — Data the backend needs (own backend, not the original one)

- `chat_channels`: id, name, icon, category, sort_order, description, is_locked, slowmode_seconds, post_permission.
- `chat_messages`: id, channel_id, user_id, content (≤500), sticker_url, reply_to_id, thread_root_id, message_type (text/audio/video), media_path, media_mime_type, media_duration, media_size, status (published/hidden), is_announcement, is_pinned, created_at.
- `chat_images` (message_id, image_path), `chat_reactions` (message_id, user_id, emoji; unique per user+emoji).
- `chat_mutes` (user_id, reason, expires_at, created_by), `chat_bans` (user_id, reason, created_by).
- `chat_reports` (id, message_id, reporter_id, reason, status open/closed, created_at).
- `chat_rule_acceptances` (user_id, accepted_at), `chat_notifications` (recipient_id, actor_id, type: friend request / mention / reply / thread), `user_friends` (requester_id, receiver_id, status).
- `admin_audit_log` (id, actor_id, action, target, details, created_at).
- `profiles`: add `custom_status`, keep `role`, `plan`, `plan_expires_at`, `user_level`, `bio`, `banner_url`, `avatar_url`, `equipped_item_id`, `show_online_status`.
- Files stored in private storage with short-lived links for chat images/media and payment receipts.

## Part G — Build order for the agent
1. Add Turkish (Part A) and the 8-language switcher.
2. Create the 13 rooms and the room list screen (Part B).
3. Chat screen: message list, input bar with send, stickers, image attach (Part C).
4. Reactions, replies, mentions, members sheet.
5. Discord-style profile card and profile screen (Part D).
6. Admin-only access rule and panel shell (E1–E2), then the panel screens one by one (E3).
7. Chat administration and moderation tools (E4–E5).
