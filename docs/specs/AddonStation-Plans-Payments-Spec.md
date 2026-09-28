# AddonStation — Part 3: Paid Plans (Subscriptions), What Each Plan Unlocks, Payment Flow & Protection

(Independent document. Give it to the Android Studio AI agent together with the other spec files. Everything about plans is inside this file.)

Tags:
- **[ORIGINAL]** = found in the original app's code. Keep it.
- **[NEW]** = improvement added for this project. Build it.
- **[DECIDE]** = the app owner must choose a value. The agent must ask before building it, or leave it configurable from the admin panel.

Important honesty note: the server rules of the original app (database policies and functions) are **not inside the APK**. The APK only shows *which server functions the app calls* and *what the app screens do*. Where the exact server rule is not visible, this document says **"inferred"**, and describes the rule that the new backend must implement.

Build as a native Android app (Kotlin + Jetpack Compose), Arabic first (RTL), 8 languages (ar, en, pt, es, ru, zh, fr, tr).

---

## 1. Plans at a glance

There are 3 subscription levels. Internal ids never change: `free`, `epic`, `legendary`.

| | 🍃 Free | 💎 Epic | 👑 Legendary |
|---|---|---|---|
| Price shown on the plans screen | $0 | $2.99 / month | $5.99 / month |
| Price shown in the profile "My plan" tab | — | 25 د.ل / 30 days | 50 د.ل / 30 days |
| Tagline | ابدأ باكتشاف الإضافات والعوالم المجانية. | محتوى أكثر وتجربة لعب أوسع. | افتح المحتوى المميز وامتيازات VIP. |
| Feature lines (as written) | محتوى مجاني · وصول أساسي · بدون اشتراك | إضافات Epic 💎 · تنزيلات أكثر · وصول المشتركين | إضافات Legendary 👑 · عوالم Legendary 👑 · أولوية الوصول |
| Button on the plans screen | ابدأ الآن | اختيار الخطة | اختيار الخطة (home preview: "JOIN LEGENDARY 👑") |
| Badge color | gray `#AAB9C8` | purple `#B98CFF` (glow) | gold `#FFD479` (glow) |
| Profile "My plan" card text | الأساسيات المجانية | أفاتارات مميزة + كل الأساسيات | كل شيء + أداة ذهبية بجانب اسمك |

Notes:
- The two price lines above come from two different places in the original (dollars on the marketing screen, Libyan dinar in the profile and checkout). **Real prices are stored in the database and edited by the admin** (`price_lyd` and `duration_days` per plan). The app must always read the price from the server and never hard-code it. **[DECIDE]** which currency/prices to show in the new app.
- The plans screen intro text: **"تابع مغامرتك دون انقطاع واستفد من كامل الميزات الحصرية، عبر ترقية باقتك وفتح تحميل غير محدود للإضافات والعوالم"** with the title "الاشتراكات" and small label "CHOOSE YOUR EXPERIENCE".
- Hierarchy: `free < epic < legendary`. Higher plan includes everything below it (inferred from the text "Legendary: كل شيء").
- Special roles outside the plans: **Admin ("TheKing ✦")** has access to everything; **Moderator ("Manager ✦")** and **Creator** are roles, not paid plans. A creator can still have a plan.

---

## 2. What each plan unlocks

### 2.1 Content (addons and worlds)
Every content item has a `plan` value: `free`, `epic`, or `legendary` (shown as the corner badge 🍃 Free / 💎 Epic / 👑 Legendary; the web version also called them FREE / NORMAL / VIP). **[ORIGINAL]**

Access rule (inferred, must be enforced on the server):
| Content plan | Who can download |
|---|---|
| free | any logged-in user |
| epic | Epic, Legendary, Admin |
| legendary | Legendary, Admin |
| any, but with an AC price | anyone logged-in who pays the AC price once (see section 4) |

- The creator of an item and admins can always access it.
- Guests (not logged in) can browse, but cannot download: message **"سجل الدخول أولًا 🔐"** then open Login.
- **Exclusive content** (`is_exclusive`) and **Featured** (`is_featured`) are flags the admin sets when uploading. Exclusive items appear on the "Exclusives" screen. Their access rule is not visible in the app. **[DECIDE]** whether exclusives follow the normal plan rule (recommended) or need a specific plan.
- **Downloads limits**: the plans text says Epic has "تنزيلات أكثر" (more downloads) and the plans screen says upgrading opens "تحميل غير محدود" (unlimited downloads). The number of downloads allowed per plan is **not visible** in the app. **[DECIDE]** the limits, e.g. downloads per day/month for Free and Epic, unlimited for Legendary. Store them in `subscription_plans.download_limit` so the admin can change them, and count using the existing downloads table. Show a friendly message when the limit is reached with an "Upgrade" button.

### 2.2 Profile look
| Feature | Free | Epic | Legendary | Admin |
|---|---|---|---|---|
| Default avatar and default banner | ✅ | ✅ | ✅ | ✅ |
| Premium avatars (the original says "300+ avatars and tools") | 🔒 locked | ✅ | ✅ | ✅ |
| Banners marked `epic` | 🔒 | ✅ | ✅ | ✅ |
| Banners marked `legendary` | 🔒 | 🔒 | ✅ | ✅ |
| Assets marked "Admin only" (e.g. animated item Enchanted_Bedrock) | 🔒 | 🔒 | 🔒 | ✅ |
| Show/hide "online now" status | Epic+ label | ✅ | ✅ | ✅ |
| Rank badge + name color in chat and profile | gray | purple glow | gold glow | blue `#7FD4FF` |

Each asset (avatar, banner, sticker, item) has a `required_plan`: `free`, `epic`, `legendary`, or `admin`. Locked assets are shown with a 🔒 icon and disabled. Hints in the original: **"الأفاتارات المقفلة تفتح مع Epic 💎 أو Legendary 👑"** (free user) or **"كل الأفاتارات متاحة لحسابك."** (subscriber), and **"الأغلفة المقفلة تحتاج الخطة المناسبة."** (banners).

### 2.3 Chat item next to the name ("الأداة بجانب الاسم في الشات")
- 106 items exist: 1 "none" (بدون أداة), 104 Minecraft item icons (Diamond Sword, Netherite Pickaxe, Bell, Compass, etc.) tagged `legendary`, and 1 animated item "Enchanted Bedrock" tagged `admin`.
- The Legendary card promises: **"أداة ذهبية بجانب اسمك"**.
- ⚠️ **Inconsistency found in the original:** the screen only locks items for Free users, so **Epic users could also equip the items tagged `legendary`**, even though the items are tagged for Legendary and the Epic card does not promise them. **[DECIDE]** — recommended: follow the tag, meaning Legendary and Admin only (Epic sees them locked). The new app must apply the rule that the owner chooses, both in the UI and on the server.

### 2.4 Other plan-related things
- Profile → plan tab shows current plan, and "مفعّلة 🎉" with expiry date ("تنتهي في {date}") for paid plans, "الخطة المجانية 🍃" for free.
- Home screen status card: Free → gold card "خطتك: Free" + button "ترقية"; paid → green card "{plan} مفعّلة 🎉" + expiry.
- Quick action "👑 الترقية" on Home opens the plan tab.
- Store shows all content with its plan badge. **[NEW]** on the content details screen, if the user's plan is not enough, show the download button as **"🔒 يتطلب Epic 💎"** / **"🔒 يتطلب Legendary 👑"** with an "ترقية" button, instead of failing after the tap. (In the original the button always says "📥 تحميل" and the refusal appears afterwards as "لا تملك صلاحية تحميل هذا المحتوى".)

---

## 3. Download flow and how files are protected

### 3.1 Download flow **[ORIGINAL]** (what happens when the user taps download)
1. If the item has no uploaded file: toast **"ملف التحميل غير مرفوع بعد"**, stop.
2. If not logged in: toast **"سجل الدخول أولًا 🔐"**, then Login screen after ~1 second.
3. If the item has an AC price: charge it first (section 4). Toast `🪙 -{n} AC`. On failure show the error and stop.
4. Call the server function **`record_content_download(item_id)`**. It requires login; if the server refuses, show **"تعذر التحميل"**. This step counts the download and, on the server, is the place that must check the plan and the download limit.
5. Ask the private storage for a **temporary signed link valid for 60 seconds** for the file (bucket `content-files`). If the storage refuses, show **"لا تملك صلاحية تحميل هذا المحتوى"**.
6. Toast **"جارٍ التحميل... 📥"** and start the download with that link.

Also `record_content_view(item_id)` is called silently when the details screen opens.

### 3.2 The protection layers in the original (this is how it protects paid content)
1. **Private storage**: the files are never public. Buckets: `content-files` (addons/worlds), `payment-receipts` (transfer receipts), `chat-images` and `chat-media` (chat uploads), `creator-media`, `content-media`, and `site-assets` (public art like avatars).
2. **Short-lived links**: 60 seconds for content files and receipts; 60 seconds for chat images; 300 seconds for chat audio/video.
3. **Server rules on the database and storage** (row-level security) decide who can read what; the app only holds the public "publishable" key, never an admin key.
4. **Server functions for sensitive actions** — the app never edits these directly: change a user's plan/role (`admin_update_profile`), approve a payment (`approve_payment_order`), unlock with AC (`ac_unlock_content`), give/remove AC (`ac_admin_adjust`), claim rewards (`ac_claim_*`), review creators (`review_creator_application`), approve tips (`creator_approve_tip`).
5. **Roles**: admin pages check the caller's role and show "غير مسموح" otherwise.
6. **Duplicate protection**: admin coin operations carry a unique `request_key`, and claims answer "Already claimed".
7. **Chat**: messages go through a server function (`send_chat_message`) that enforces the 500-character limit, blocks links/personal data, and applies mutes.

### 3.3 Weak points found in the original (fix them in the new app) **[NEW]**
1. **UI-only locks**: avatars, banners and chat items are locked only by disabling buttons in the screen. Anyone who edits their own profile record could set a locked avatar/banner/item. → The server must validate `avatar_url`, `banner_url`, `equipped_item_id` against the asset's `required_plan` and the user's real plan/role before saving.
2. **Payment amount comes from the phone**: the checkout screen sends the price it read when creating the order. → The server must set `amount_lyd` itself from `subscription_plans` when creating the order (ignore any amount sent by the app).
3. **Order status can be edited by the app**: the app moves the order to `submitted` itself. → Server rule: a user may only move **their own** order from `pending` to `submitted`, only once, and can never set `approved` or `rejected`.
4. **Plan and role**: a user must never be able to update their own `plan`, `plan_expires_at`, `role`, `user_level`, or AC balance. Only server functions run by an admin may.
5. **The payment page didn't show where to send the money**: the original instructions only say "حوّل X د.ل عبر {method} ثم ارفع الإيصال", while the admin panel stores beneficiary name, account number and instructions for each method. → Show those stored details on the payment screen.
6. **Moderators could open admin Settings** (plan prices and payment methods) in the original. → In the new app only **Admin** changes prices, payment methods and user plans.
7. **No visible expiry enforcement**: the app only displays the expiry date. → The server must treat an expired plan as `free` (see section 6).

### 3.4 Protection checklist for the Android app **[NEW]**
- Never put a service/admin key in the app. Only the public client key.
- Keep the login session in encrypted storage (Android Keystore / EncryptedSharedPreferences).
- Never trust a plan stored on the phone; always read the plan from the server when opening the app and before every download.
- Do not save downloaded paid files in a public folder where the app can't control them; save in app storage and export to the game folder only when the user asks.
- Turn on code shrinking/obfuscation (R8) for release builds; use Play Integrity API to reduce fake clients **(optional)**.
- Rate-limit downloads and payment orders per user on the server (e.g. max open orders per user).
- Log every plan change, payment approval and coin adjustment in the admin audit log (see the chat/admin document).
- Screens with receipts: block screenshots (`FLAG_SECURE`) for admin payment screens **(optional)**.

---

## 4. Unlocking content with AddonCoins (AC) **[ORIGINAL]**

AC is an internal reward currency, not money: **"AC is an internal reward currency. Earn it through activity and use it to unlock eligible addons and worlds. There is no AC purchase or cash-out."**

- The details screen asks the server for the item's AC price (`ac_content_price`). If the price is greater than 0, the download button becomes **"🪙 {price} AC · Unlock with AC"**.
- Tap → server function `ac_unlock_content(item)` charges the wallet (it returns how much was charged; nothing is charged twice for the same item) → toast `🪙 -{n} AC` → normal download flow continues.
- Errors: "Not enough AC", "Sign in to use AddonCoins", "You do not have permission for this action".
- AC lets a Free user unlock *eligible* items that are above their plan. Which items are eligible and their price is set by the admin per item. **[DECIDE]** whether Legendary-only items can be AC-unlocked (recommended: only if the admin marks them).
- Ways to earn AC (tasks): Welcome Reward, Daily Login with streak bonuses (3/7/14/30 days), Complete Profile, Community Participation, First Addon Download, First World Download, First Useful Post, plus admin events and gifts. Admin can give or remove AC and edit tasks.
- Sign-in toolbar chip shows the balance.

---

## 5. How a user buys a plan (manual payment with receipt) **[ORIGINAL]**

There is **no online payment gateway**. The user transfers money with a local method and uploads the receipt; an admin checks it and activates the plan.

### 5.1 Screens
**Screen 1 — Plans ("الاشتراكات")**: three cards (Free, Epic, Legendary) as in section 1. Tapping Epic/Legendary opens Checkout with `plan=epic|legendary`. Free just closes/starts.

**Screen 2 — Checkout ("CHECKOUT")** (requires login; guests are sent to Login):
- Shows plan name and `{price} د.ل / {days} يوم` (from the server; if the plan is inactive: **"الخطة غير متاحة."**).
- **طريقة الدفع** (payment method) dropdown with these options: OnePay, LPay, Edfa3li, MobiCash, Sadad, Tadawul, تحويل مصرفي (bank transfer). The list should come from the admin's payment-methods table (only enabled ones). **[DECIDE]** which methods to offer.
- Button **إنشاء طلب الدفع** (create payment order).

**Screen 3 — Order ready ("طلبك جاهز")**:
- **رقم الطلب**: reference code `AS-XXXXXXXX` (8 random letters/digits, uppercase).
- Text: transfer the amount using the chosen method, then upload the receipt.
- **[NEW]** Show the beneficiary name, account/wallet number and the admin's instructions for that method, with a copy button.
- Fields: receipt image (PNG / JPEG / WEBP), **رقم العملية أو التحويل** (transaction number), button **إرسال الإيصال**. Error if no image: **"اختر صورة الإيصال أولًا"**.
- Success message: **"تم إرسال طلبك للمراجعة ✅"**.
- **[NEW]** A "My orders" list in the profile (reference, plan, amount, method, status chip: قيد الانتظار / قيد المراجعة / مفعّل / مرفوض, with the rejection reason).

### 5.2 Order data and statuses
`payment_orders`: `id`, `user_id`, `plan_id`, `amount_lyd`, `payment_method`, `reference_code`, `receipt_url` (private file path `{userId}/{orderId}-{filename}`), `transaction_reference`, `status`, `created_at`, **[NEW]** `reviewed_by`, `reviewed_at`, `reject_reason`.

Status flow:
`pending` (order created) → `submitted` (receipt uploaded) → `approved` (plan activated) **or** `rejected`.

### 5.3 Admin side (admin only) **[ORIGINAL]**
- **Payments screen "PAYMENTS / طلبات الدفع"**: list of orders with user name/email, plan, amount, method, reference, transaction number and status. Buttons: **عرض الإيصال** (opens the receipt through a 60-second link), **تأكيد الدفع** (calls `approve_payment_order(order_id)`), **رفض**.
- Approve on the server must, in one step: check the order is `submitted` and not already approved, set the user's `plan`, set `plan_expires_at = now + duration_days` (or extend from the current expiry if the user renews early — **[NEW]**), mark the order `approved`, write the audit log, and send the user a notification **[NEW]**.
- **Users list** (Overview): each user has a plan selector (Free / Epic / Legendary) and a role selector; **حفظ** calls `admin_update_profile(target_user, target_plan, target_role, target_expires)`. The original sends `target_expires = null`; **[NEW]** let the admin pick an expiry date and add "grant 30 days" as a shortcut for gifts/support cases.
- **Settings screen "الخطط وبيانات الدفع"**: edit each plan's price and duration in days, and add/delete payment methods with: method code (e.g. onepay), beneficiary name, account/wallet number, instructions text.

---

## 6. Renewal and expiry **[mostly NEW, original only displays the date]**
- Each paid user has `plan_expires_at`. The plan card shows **"تنتهي في {date}"**. When there is no date on a paid plan the original shows "مزايا كاملة حتى الآن".
- **Server rule**: if `plan_expires_at` is in the past, the effective plan is `free` (everything in section 2 locks again) — computed on every request, not only by a nightly job. Keep the user's chosen avatar/banner/item stored, but they are shown locked and can't be re-selected; use the default until renewal. **[DECIDE]** whether to keep or reset them.
- Renewal: the same checkout flow ("ترقية أو تجديد"). Renewing before expiry adds days to the current expiry.
- **[NEW]** Notification 3 days before expiry and on expiry day: "اشتراكك ينتهي قريبًا" with a Renew button.
- **[NEW]** Downgrade rule: never delete the user's content or favorites when the plan expires.

---

## 7. Screens and texts to build (Arabic; other languages via resources)

| Where | Text |
|---|---|
| Plans screen title | الاشتراكات |
| Plans screen small label | CHOOSE YOUR EXPERIENCE |
| Free button | ابدأ الآن |
| Paid buttons | اختيار الخطة |
| Profile plan tab (free) | الخطة المجانية 🍃 |
| Profile plan tab (paid) | {الخطة} مفعّلة 🎉 · تنتهي في {date} |
| Buy button | شراء اشتراك 💳 |
| Home card free | خطتك: Free · افتح الأفاتارات والـ Items المميزة · [ترقية] |
| Home card paid | {plan} مفعّلة 🎉 |
| Locked avatar hint | الأفاتارات المقفلة تفتح مع Epic 💎 أو Legendary 👑. |
| Locked banner hint | الأغلفة المقفلة تحتاج الخطة المناسبة. |
| Item section title | الأداة بجانب الاسم في الشات |
| Download refused | لا تملك صلاحية تحميل هذا المحتوى |
| Login needed | سجل الدخول أولًا 🔐 |
| Order ready | طلبك جاهز |
| Order sent | تم إرسال طلبك للمراجعة ✅ |
| Plan unavailable | الخطة غير متاحة. |
| Turkish (key ones) | Abonelikler · Plan Seç · Şimdi Başla · Satın Al · Ödeme Yöntemi · Sipariş Oluştur · Dekontu Gönder · Siparişiniz hazır · Talebiniz incelemeye gönderildi ✅ · Bu içeriği indirme yetkiniz yok · Önce giriş yapın 🔐 |

---

## 8. Backend needed (own backend, not the original one)
Tables: `profiles` (plan, plan_expires_at, role, avatar_url, banner_url, equipped_item_id, show_online_status), `subscription_plans` (id, name, price_lyd, duration_days, active, **[NEW]** download_limit, limit_period), `payment_orders`, `payment_method_settings` (method, account_name, account_identifier, instructions, **[NEW]** enabled), `content_items` (plan, is_featured, is_exclusive, file_path, cover_url), `content_downloads`, `site_assets` (asset_type: avatar/banner/sticker/item, name, file_url, required_plan, enabled), `ac_wallets`, `ac_transactions`, `ac_tasks`, `ac_events`, `admin_audit_log`.
Server functions: record_content_view, record_content_download (checks login, plan, limit), unlock with AC (+price lookup), approve_payment_order, admin_update_profile, create_payment_order (sets amount itself), submit_receipt, set_avatar/banner/item (validates plan).
Storage: private buckets `content-files`, `payment-receipts`, `chat-images`, `chat-media`; public bucket for site assets. Receipts: a user can upload only into their own folder and read only their own files; admins read all.

## 9. Google Play warning (check before publishing)
This app sells digital access (subscriptions that unlock content) using manual local transfers. **Google Play usually requires Google Play Billing for digital goods and subscriptions sold inside apps**, with exceptions depending on the country and the program. Read the current Play Payments policy before publishing on Google Play. If the app is distributed as an APK outside Play, this does not apply.

## 10. Questions the owner must answer ([DECIDE] list)
1. Final prices and currency (LYD vs USD) and plan duration (30 days).
2. Download limits for Free and Epic; unlimited for Legendary?
3. Chat items: Legendary only (recommended) or Epic too?
4. Do exclusive items follow the normal plan rule?
5. Can Legendary-only items be unlocked with AC?
6. Payment methods to offer (OnePay, LPay, Edfa3li, MobiCash, Sadad, Tadawul, bank transfer) and the account details for each.
7. After expiry: keep or reset the chosen avatar/banner/item?
8. Will the app be published on Google Play (see section 9)?

## 11. Build order for the agent
1. Data model and plan rule function (effective plan with expiry).
2. Plans screen and profile plan tab; Home status card.
3. Content access check + download flow with short-lived links.
4. Locks for avatars/banners/items (UI and server validation).
5. Checkout, payment order, receipt upload, "My orders".
6. Admin payments screen, users/plans screen, settings (prices, methods).
7. AC unlock on the content screen.
8. Expiry, renewal notifications, audit log.
