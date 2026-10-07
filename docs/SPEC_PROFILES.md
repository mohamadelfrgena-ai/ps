# AddonStation — UI Spec: Profile screens (3)

Native Android app (Kotlin, Jetpack Compose, Material 3). Portrait, dark theme. Design width **390 dp**; sizes are dp/sp. **No bottom navigation bar** on these screens.
Placeholders in brackets (`[Display name]`, `[N]`, `[Date]`) are data-bound values.

The three screens share one layout and most components, so build the shared components once (section 2) and compose each screen from them.

| # | Screen | When it opens |
|---|---|---|
| A | **My profile** (own) | Profile tab |
| B | **Member profile** | I tap another user's name/avatar in chat |
| C | **My profile as others see it** | Preview of what visitors see when they open my profile |

---

## 1. Tokens
Colors: bg `#0A0F1C` · surface `#121A2B` · surfaceHigh `#1A2438` · barSurface `#0E1526` · border `#223049` · text `#EAF0FA` · textMuted `#9DB0CF` (labels `#C4D1E8`) · blue `#2E7BEF` · blueLight `#6FA8FF` · gold `#F0C25E` · goldDark `#1B1405` · online green `#3FBF6A` / text `#7FD99A` · danger text `#FF8791` · danger border `#B3414B`.
(Approximated from the app icon; keep all colors in one `AppColors` object.)
Fonts: pixel font `assets/fonts/pixel_regular.ttf` for small uppercase section labels (11sp, gold, letter spacing 1sp). Body: Nunito 500/700/800 (or system sans). Numbers may use `roboto_medium_numbers.ttf`.
Shapes: cards radius 16, buttons radius 12, badges radius 8. Min touch target 44dp. Horizontal screen margin 16dp. Card = bg `surface`, 1dp `border`, padding 14, top margin 12.

---

## 2. Shared components

### 2.1 Banner
Height 150, full width, image fills (crop). If the user has no banner use `profile_banner_default.jpg`. Dark gradient overlay: transparent at 40% → `bg` @90% alpha at bottom.
Overlay icon buttons (44×44 circle, bg `bg` @60% alpha, white icon 22dp, 10dp from the edges): **Back** (top-left, all screens); **More** (top-right, screen B only).

### 2.2 Avatar with status dot
Avatar 96×96 circle, 5dp border in `bg`, overlaps the banner by 46dp, 16dp from the left. Default avatar: `ic_default_profile.jpg`.
Status dot at the bottom-right corner (flush, offset 0): 30×30 circle, 5dp border in `bg`, fill = green radial gradient (`#8CF2AD` → `#2FB35C`, light from top-left), soft green glow (2dp ring @35% alpha + 14dp blur @65%).
Dot colors by status: online green · away/idle amber · offline grey `#6B7A94` (no glow). If the user hides their online status (a Legendary-only setting) show no dot and no "Online" text.

### 2.3 Identity card
- Display name 22sp/800.
- Line: `@[username]` 13sp/700 muted + ` · Online` in `#7FD99A` (text follows the status; hidden when status is hidden).
- Badge chips (wrap, gap 6, margin top 10): 11sp/800, radius 8, padding 3×9.
  `Premium member` gold bg / goldDark text · `Creator` blue bg / white · `Level [N]` surfaceHigh bg / `#C4D1E8`. Show only badges the user has (Admin: gold "ADMIN"; Moderator: blue "MOD").
- Custom status box: bg `barSurface`, radius 12, padding 10×12, 13sp `#C4D1E8`. Hide when empty.

### 2.4 Stats strip
Card with three equal columns separated by 1dp `border` dividers, padding 12 vertical. Number 20sp/800, label 11sp/700 muted. Labels: Friends · Downloads · Favorites.

### 2.5 Level card
Label "LEVEL & REPUTATION" (pixel). Text "Level [N] · [x]/100 to next level" 13sp/800. Progress bar: height 8, radius 4, track surfaceHigh, fill horizontal gradient blue → blueLight.

### 2.6 About card
Label "ABOUT ME" + bio text 14sp (line height 1.4, `#C4D1E8`); empty → "This member has not added a bio yet." 1dp divider (margin 12). Label "MEMBER SINCE" + date 14sp/700.

### 2.7 Wallet card (own profile only)
Card row, gap 12: `coin_ac.png` 44×44 · column ["AC wallet · Current balance" 12sp/700 muted; balance 22sp/800 gold; "[N] day streak" 12sp muted] weight 1 · button "Daily reward" (44h, radius 12, bg gold, goldDark text 13sp/800, padding 16). Disable the button when already claimed today.

---

## 3. Screen A — My profile
Top to bottom:
1. Banner (Back button only, no More).
2. Avatar row: avatar (left) + **Edit profile** button on the right, aligned to the avatar bottom (44h, radius 12, bg blue, white 14sp/800, padding 20).
3. Identity card.
4. **Wallet card** — directly under the identity card.
5. Stats strip.
6. Level card.
7. About card.
8. 2×2 grid, gap 10, margin top 12: **My favorites · My downloads · My orders · My content** (56h, radius 14, bg surface, 13sp/800).
9. **Log out** button: full width minus margins, 48h, radius 12, transparent, 1dp `danger border`, text `danger text` 14sp/800, margin 16.
Settings are a separate screen — not shown here.

## 4. Screen B — Member profile (opened from chat)
1. Banner with **Back** (left) and **More** (right; opens the menu below).
2. Avatar (no button next to it).
3. Identity card.
4. **Action row** (gap 10, margin top 12), two equal buttons, 48h, radius 12:
   - **Message [Name]** — filled blue, white text. Opens a private conversation.
   - **Friend request** — outlined 1dp blue, blue-light text. States: *Friend request* → *Request sent* (disabled, muted) → *Friends ✓* (tap offers unfriend). If they sent me a request: show "Accept" / "Reject".
5. Stats strip, Level card, About card.
6. **Safety card** (rows 52h, left-aligned 14sp/800, 1dp dividers): "Mute this user" (text color), "Block user" (danger), "Report" (danger).
   - Block shows a confirm dialog: "Block this user?" with explanation; success toast "User blocked — their messages are now hidden."
   - Report opens a reason picker (Inappropriate content, Insult, Other…).
   - The More menu in the banner contains the same three actions.
7. **Admin/moderator only:** extra card "Moderation" with Mute, Ban, Delete message tools (hidden for regular members).
- Do **not** show wallet, orders, favorites list or settings on this screen. If the user blocked me or I blocked them, replace the action row with a single disabled state/"Unblock".

## 5. Screen C — My profile as others see it
Same as screen B but from my side, so it shows what visitors can see:
1. Banner with Back only.
2. Avatar row with **Edit profile** button (as in A).
3. Identity card, Stats strip, Level card, About card (exactly as visitors see them).
4. A dashed-border card instead of the wallet: coin icon at 50% opacity 36×36, title "AC wallet" 14sp/800 `#C4D1E8`, caption "Hidden from other users" 12sp muted. Border: 1dp dashed `#2E3C58`, radius 16.
5. No action row, no safety card, no Log out.
Entry: a "View as others" action on screen A (menu) or the Edit profile screen.

---

## 6. Assets
```
assets/images/coin_ac.png                 AC icon
assets/images/ic_default_profile.jpg      default avatar
assets/images/profile_banner_default.jpg  default banner
assets/images/app_icon.png                app icon
assets/fonts/pixel_regular.ttf, roboto_medium_numbers.ttf
```
Copy images to `res/drawable-nodpi/` and fonts to `res/font/`. Icons: Material Icons Outlined — ArrowBack, MoreVert, Message/ChatBubble.
