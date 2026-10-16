# AddonStation for PS4: complete design specification

**PS4 only.** One TV app, 1920 × 1080, controlled with the DualShock 4. This document describes all 19 screens and their 43 states shown in `AddonStation_PS4_design.html` (open it next to this file) and the captures in `captures/`.

## 0. Contents
1. App map  2. Global rules  3. Components  4. Welcome flow  5. Home  6. Deals  7. Store and Search  8. Addon page  9. Community  10. Account  11. System states  12. Languages and RTL  13. What the PS4 app does not do  14. Data it reads  15. Open points  16. Assets

## 1. App map
```
Splash ─► Welcome ─┬─ Sign in with a code ─► (linked) ─┐
                   ├─ Sign in (email)  ───────────────┤
                   ├─ Browse as a guest ──────────────┤
                   └─ Language (□ from the welcome)   │
                                                       ▼
Top tabs (L1/R1):  Home · Deals · Store · Chat · Settings
Top-right shortcuts: Notifications (bell) · AC wallet (coin pill) · Profile (avatar)

Home ─► Addon page ─► (Unlock / Download / See plans / Save)
Deals ─► Addon page            Store ─► Search ─► Addon page
Chat ─► Room (members side panel) ─► (Write □)      Chat ─► Creators ─► Addon page
Profile ─► About · Favorites · My plan              Wallet ─► (Claim, tasks, history, code)
Settings ─► Language · Account · Notifications · Link a console · About · Privacy · Sign out
```

## 2. Global rules
- **Canvas** 1920 × 1080 px, dark only. **Title-safe area 96 px** left/right, 54 px top/bottom (art may bleed).
- **Colors:** bg `#0A0F1C`, surface `#121A2B`, surface-high `#1A2438`, bar `#0E1526`, border `#223049`, text `#EAF0FA`, muted `#9DB0CF`, chip text `#C4D1E8`, blue `#2E7BEF → #4C9AFF`, blue-light `#6FA8FF`, **focus ring `#9CC1FF`**, gold `#F0C25E`, ok `#3FBF6A` (text `#7FD99A`), error `#FF6B78` (text `#FF8791`), warning `#F0A04B`, discount red `#FF5C7A → #FF3D6B`, Epic purple `#9B6BFF`.
- **Fonts:** **F77 Minecraft** for the *main names* (brand, banner title, section titles, screen titles, card and item names, countdown digits, big numbers); **Nunito** for all other text; **Tajawal** ExtraBold for Arabic (F77 is not used in Arabic layouts); a system CJK font for Chinese. Font license: see `assets/fonts/FONT_LICENSE.txt` (CC BY-ND: show the credit in Settings → About, never modify the file).
- **Type sizes (px):** body 28, small 22 (the minimum anywhere), tabs 30, section labels 21 (uppercase, +1.4 spacing for Latin scripts), card title 26–28, screen title 44–62, banner title up to 100, big numbers 64–128.
- **Radius:** panels 30–36, banner 34, tiles 28, cards 22, buttons 22–28, chips full-round.
- **Controller map (same everywhere):** D-pad / left stick = move focus · ✕ select · ○ back · □ search / write / language / type · △ menu or options · L1/R1 previous/next tab · L2/R2 previous/next offer (Home) or previous/next tab inside Profile · OPTIONS help.
- **Focus (the most important rule):** 5 px ring `#9CC1FF` (offset 4) + 14 px glow `#2E7BEF`@35% + shadow 0 28 60 black@60%; cards scale 1.05 and are drawn above neighbours. Never lose focus: on entering a screen focus goes to the primary element. 120 ms move animation (none with reduced motion).
- **Legend bar** (bottom, 96 high, gradient to `#050810`): glyph circle 44 (✕ blue, ○ red, □ pink, △ green, L1/R1/L2/R2/OPT grey) + label 24px; the hints change with the screen.
- **60 fps**, decoded images small (cards ≤ 660 × 360), no hover-only information, every action reachable with the pad.

## 3. Components
- **Top bar** (120 high): brand tile 60 (3 px gold border, glow) + name (F77); tabs (30px) with L1/R1 glyphs, active = white + 5 px blue-light underline, **Deals** tinted ice-blue with a snowflake (follows the campaign theme); right: **bell** 58 (red dot when unread) · **AC pill** (58 high, glass, coin 40 + balance 28px gold) · **avatar** 58 (gold ring). Over art (Deals header, Welcome) the bar is transparent with a top gradient; elsewhere solid.
- **Item card** 318 × ~330: cover 178 (16:9) + discount tag (top-start; red gradient, 26px/800) or tier badge (EPIC purple / LEGENDARY gold) + title 26px (F77) + "By [Creator]" 21px + **price row** (coin 26 + price 30px gold; old price struck 22px muted); free items show no price row.
- **Buttons** (height 76–104, radius 22–28): primary (blue gradient), gold (unlock/plans), ghost (glass + 3 px white@28% border), text (underlined). Full-width variants 92–96 high.
- **Chips** (62 high, radius 31, 24px/700): selected = blue@30% + blue-light border. **Segmented control** 62 high.
- **Dialog** (760 wide, radius 36, scrim `#03060E`@78% + blur): icon circle 110, title 40px (F77), text 26px muted, full-width buttons (focus starts on the safe choice).
- **Toast:** pill 84 high, 130 above the bottom, 3 s, ok-green border for success.
- **Skeleton:** shimmer 1.4 s (static with reduced motion).

## 4. Welcome flow (first launch or signed out)
1. **Splash (8 s loop, plays once on first launch, ~1.5 s afterwards):** scene art full-screen (slow zoom 1.18 → 1.02) + dark vignette; app icon 150 (gold border, pop-in); name (F77 64px); slogan lines **DREAM IT. / BUILD IT. / CREATE IT.** (F77 96px; white, blue, gold with glow; enter from the sides then scale); tagline 30px; **loader** 10 gold segments 44 × 20 that fill one by one. The splash waits for the data but never blocks more than needed; ✕ skips after 1 s.
2. **Welcome:** art + left-to-right dark gradient; top bar (brand + **language chip** with flag and □ hint); kicker chip "WELCOME"; headline "Welcome to the family" (F77 62px); the app's description text; **three actions** (width 700): ① **Sign in with a code** (primary, initial focus: "Type nothing: link with your phone or PC") ② **Sign in** (email and password) ③ **Browse as a guest**; two floating deal cards on the right; terms line at the bottom. Legend: ✕ Select · □ Language · OPT Help.
3. **Language:** centered dialog (820 wide) with the 8 languages (rows 80 high: flag 56, native name 30px, English name 20px, radio); the current one is selected, focus ring on the highlighted row. Applies immediately (Arabic mirrors the whole app).
4. **Sign in with a code:** left: title (F77 64px) + 3 numbered step cards (open the app on phone/PC → Settings → "Link a console" → enter this code); right: **code card** (radius 40): label, the code in F77 104px (e.g. `K7P 4QX`), expiry ring + "Expires in 9:42", "Waiting for you…" with 3 pulsing dots. States: **waiting**, **linked** (green check, avatar, "Welcome back, [Name]", Continue with focus), **expired** (code dimmed, warning, **New code** button with focus). The code is valid 10 minutes; the console polls every 3 s.
5. **Sign in (email):** centered card (880 wide): title, optional error banner ("Wrong email or password. Try again."), two fields (height 96: icon + value + "Press ✕ to type" which opens the system keyboard), primary Sign in, hint to reset the password on the phone. Wrong credentials: red border on both fields, focus returns to the password.

## 5. Home (Microsoft-Store-style layout)
Block at y = 126, height 520, two columns, gap 28.
- **Offer banner** (left, 1040 × 520, radius 34): full-bleed art + left-to-right gradient + themed glow (winter ice-blue, sunset amber, night violet). Content (x 56, y 44, max width 760): label chip (40 high, glass, snowflake for winter) → **title** (F77, one line, size = min(100, 800 ÷ (characters × 0.7)); winter = white → ice-blue gradient) → subtitle 30px → row: **discount badge** (78 high, red gradient, 46px) + optional **countdown** (3 glass tiles 88 × 78: F77 digits 34px + DAYS/HRS/MIN) → buttons (primary + glass). No floating card. **Pager** under it: 5 dots; L2/R2 change the slide, auto-advance every 7 s, paused while the banner has focus. Content comes from the database (title, subtitle, badge, buttons per language, art, target, dates).
- **Exclusives** (right, 660 wide): big tile 660 × 300 + two small 316 × 192; each: EXCLUSIVE chip (gold, star) joined to the AC price chip, title (F77 24–34px), creator on the big tile.
- **Filter chips** (y 700): All · Addons · Worlds · Free · Epic · Legendary · New (Free/Epic/Legendary = required plan; New = last 14 days; one active at a time).
- **Recommended for you** row (+ prev/next circle buttons); the top of the cards is visible, the rest fades under the legend and scrolls up when focus moves down.
- **Focus order:** banner primary → banner secondary → (Right) exclusives big → small 1 → small 2 → (Down) chips → row. Up/Left return.
- **States:** signed out (AC pill shows "Sign in"), loading skeleton (see 11), no active banner (the first exclusive takes the left card).

## 6. Deals (campaign landing page)
Header band 430 high (campaign art, label, F77 title 80px, subtitle, countdown) under a transparent top bar; chips: All · Addons · Worlds · Under 100 AC · Biggest discount; **5-column grid** with discount tags and price rows, two rows visible, fades under the legend; □ Filter, △ Sort. Shows the items of the collection chosen by the banner (or the collection that ends first).

## 7. Store and Search
- **Store:** optional **promo strip** (y 130, 210 high, radius 30: label, F77 title 56px, subtitle, discount badge, "Shop the sale") → filter row (Addons | Worlds segmented, sort chips: Newest · Most downloaded · Most viewed · Top rated) → 5-column grid (infinite scroll, two rows visible).
- **Search (□):** big field (104 high, blue ring) with the query and a caret; the system keyboard opens on □; "Recent" chips; result count; the same card grid; empty result → "No results" state (11).

## 8. Addon page (one layout, six states)
Left: cover 1000 × 560 (radius 32) with tier badge (top-end) and the campaign ribbon when on sale; description below. Right (560 wide): title (F77 48px), creator, stats (downloads, rating); **price box** (only for paid items); platform check "PS4 ✓"; buttons 96 high: main + Save + Share.
| State | Price box | Main button (initial focus) |
|---|---|---|
| **Free** | none | **Download** (blue) |
| **Paid** | coin 44 + price 64px gold | **Unlock with AC** (gold) |
| **On sale** | discount tag 76 high, new price 64px, old price struck 34px, "Sale ends in 3d 12h" | **Unlock with AC** |
| **Owned** | green "Owned" mark | **Download** |
| **Needs a plan** | purple panel "Requires the Epic plan" + "Plans are managed in the app on your phone or PC" | **See plans** (gold) → Plans screen (view only) |
| **Not enough AC** | sale box + dialog "Not enough AC: You need 150 AC and you have 90 AC." | dialog: **Open wallet** (focus) / Cancel |
The price shown is informational: the **server decides the real charge** (sale price included). Unlock success → toast "Unlocked", the button becomes Download, the AC pill updates.

## 9. Community
- **Chat rooms:** left list (900 wide): title, rules chip (shield, warning tone), sections Main rooms / Language rooms; rows 100 high (icon 68, name 30px, "[Name]: last message" 22px, time, unread badge 34, "Locked (read only)" chip); right **preview** panel (760 wide) of the focused room (name, online count, pinned icon, 3 latest bubbles, "Press ✕ to open the room"). ✕ opens the room.
- **Room (redesigned):** main panel + members panel (440 wide) in one row. **Header** 116 high with a blue→green gradient wash: room icon 78 (soft ring), name (F77 42px), online count, members button (icon + count). **Pinned message strip** (58 high, gold tone, pin icon, text, count badge) under it. **Messages** (26px) grouped by sender within 5 minutes: name + role chip (e.g. Manager) above the first bubble, avatar 60 only beside the last bubble, bubbles radius 28 with an 8 px tail (mirrored in RTL); own = blue gradient with shadow and delivery tick; **reply quote** inside the bubble (4 px bar), **reaction chips** under the bubble (emoji + count, your own highlighted blue), **image bubbles** (150 high, radius 20) with caption, **hidden-message pill**, day chips. The **focused message** gets the focus ring; **△ Options** opens a floating menu: reaction pill (👍 ❤️ 😂 😮 😢 +) and a card with Reply · View profile · Report (staff also see Hide / Mute). **Composer** (92 high): □ Write hint + △ Options hint. States: **normal**, **message options**, **read-only** (lock bar), **signed out** (bar + Sign in). Members panel: sections Admin / Moderators / Online / Offline with presence dots and role chips. Messages refresh every 3 s.
- **Creators:** subtitle, sections Featured / Popular, 5-column grid of creator cards (cover strip, avatar 116, name, Creator chip, addons count) → creator's addons (same cards).

## 10. Account
- **Profile (redesigned)** (avatar shortcut): a **cover header** (1728 × 330, radius 36) with the member's cover art + dark gradient: avatar 236 (gold ring, glow, status dot), name (F77 64px) with the equipped **chat item** badge, @username, chips (LV, Epic, Creator) and the custom status pill; on the right four **glass stat tiles** (Published, Downloads, Favorites, Friends: icon, F77 number, label). Under it **tabs About · Favorites · My plan** (L2/R2; pill 70 high, selected = blue with glow). **About:** three cards — **Level** (progress ring 200 with the level number in F77, XP text, bar, next reward), **About me** (bio + email, member since, roles), **Friends** (avatar stack + count). **Favorites:** a row of item cards. **My plan:** a plan card (icon, name F77 48px, expiry, "Active", feature chips) + the notice that plans are managed in the app on the phone or PC.
- **AC wallet (redesigned)** (coin pill): top row — **balance card** (gold gradient, big coin art, balance in F77 100px gold, "AC gift code" button with □ hint) and the **daily reward** panel (title, streak, **Claim** with focus, and **7 day tiles**: done ✓ in green, today with a gold glow and "Claim", upcoming dimmed, day 7 as the purple bonus tile with a gift). Bottom row — **How to earn AC** task cards (icon, name, frequency chip, reward, progress bar, Claim / Claimed / locked) and **Transaction history** grouped by Today / Yesterday (kind icon, date, ± amount in green/red; fades under the legend). States: **wallet**, **Redeem a code** (dialog with 8 slots, detected tier "Code tier: 500 AC", Redeem code with focus, Cancel), **after claiming** (tile becomes ✓, balance counts up, gold toast "+20 AC added to your wallet").
- **Notifications** (bell): New / Earlier sections; rows 124 high (unread dot, icon tile 72, title 30px, text 24px, time); friend requests have **Accept / Decline** buttons (focus on Accept). △ marks all as read.
- **Plans (view only):** 3 cards Free / Epic / Legendary (name F77, tagline, 3 features, price, "Active" badge on the current one) + a dashed panel "Subscribe in the AddonStation app on your phone or PC. Your plan appears here automatically after it is approved."
- **Settings:** left list (rows 100 high): Language · Account · Notifications (switch) · Link a console · About the app · Privacy policy · Sign out; right **detail panel** of the focused row (for Language: explanation + the 8 languages). Sign out opens a confirmation dialog (focus on Cancel).

## 11. System states
Loading (skeleton of the Home layout, shimmer) · Offline ("Network error" + Retry, focus on Retry, never a dead end) · No results ("No results" + Clear filters) · Sign-out dialog · Toast (e.g. "Saved to your favorites"). A failed banner image shows the themed gradient with the text only.

## 12. Languages and RTL
8 languages: English, العربية, Français, Türkçe, Español, Русский, Português, 中文 (the app's own strings; PS4-only texts are in `new_texts.md`). **Arabic mirrors the entire layout:** banner left ↔ right, exclusives on the start side, tabs, rows, grid fill direction, bubbles and tails, legend order, chips; the title font switches to Tajawal. Allow 30–40% longer text in French, Spanish, Portuguese and Russian; titles shrink to fit one line, long names ellipsize.

## 13. What the PS4 app does not do (by design)
Buying a plan or paying, uploading or editing content, creator and admin tools, typing without the system keyboard, push notifications, photo uploads. Where an action needs one of these, the screen says it is done in the AddonStation app on the phone or PC.

## 14. Data it reads
Published content (title, creator, cover, price, plan, dates), offer banners and their language texts, the three exclusive slots, the active collection and its sale prices, chat rooms and messages, the signed-in user's profile, plan, favorites, AC balance, daily reward, tasks, history and notifications. Sign-in by code needs a small server feature (code creation and claiming) and a "Link a console" screen in the phone/PC app, which are outside this PS4 design. Offers and exclusives are entered by the owner in the admin of the AddonStation app.

## 15. Open points
1. What exactly **Download** does on a PS4 (the design shows the button and a toast; the real behavior must be decided).
2. The "Link a console" server feature and the phone/PC screen to enter the code.
3. QR codes were left out of the code screen (add one next to the code if wanted).
4. Real names of the packs, rooms (the new "Cash" room) and campaign texts are placeholders.

## 16. Assets
`assets/fonts/F77-Minecraft.ttf` (+ license note). Art (Minecraft scenes), room icons, flags and the coin come from the app itself. All images in the preview are placeholders from the app's own artwork.
