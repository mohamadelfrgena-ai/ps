# Windows Bedrock test checklist

**All rows are NOT RUN.** The agent did not launch Minecraft. Fill this in on your Windows PC.

Rule: mark **PASS** only for what you actually observed on screen. Anything you did not see stays **NOT RUN**. Anything you saw fail is **FAIL** with the exact error text.

---

## Which pack are you testing?

Tick one or both. See `INSTALL-WINDOWS.md` section 0 for why there are two.

- [ ] **Track A** — `windows-skin-test-azure.mcpack` → equip **Azure Mechanic** (matches your stated step 3)
- [ ] **Track B** — `windows-skin-test.mcpack` → equip **C1 Signal Red** (the actual Tier C pack)

Pack tested: ______________________
Minecraft version: ______________________
Windows version: ______________________
Signed in to Microsoft/Xbox? ☐ Yes ☐ No
Internet connected during test? ☐ Yes ☐ No
Date/time: ______________________

---

## Pre-flight

| # | Step | Expected | Result | Notes |
|---|---|---|---|---|
| P1 | Backed up `com.mojang` | Copy completes, no errors | ☐ PASS ☐ FAIL ☐ NOT RUN | |
| P2 | Identified correct `com.mojang` path | Path exists for your build | ☐ PASS ☐ FAIL ☐ NOT RUN | |
| P3 | "Only Allow Trusted Skins" turned OFF | Setting is off | ☐ PASS ☐ FAIL ☐ NOT RUN | Original state: ______ |

---

## The five required conditions

### W1 — Import into Minecraft Bedrock for Windows

**Do:** double-click the `.mcpack` (Method A), or copy the unpacked folder into `com.mojang\skin_packs\` (Method B).

**Expected:** "Import started" then "Successfully imported".

| Field | Record |
|---|---|
| Method used | ☐ A (double-click) ☐ B (manual folder) |
| Exact message shown | |
| Error text, if any | |
| **Result** | ☐ PASS ☐ FAIL ☐ NOT RUN |

> Importing successfully is **not** the main success criterion. Keep going.

---

### W2 — Four skins visible in the real Dressing Room

**Do:** Dressing Room → Classic Skins → find the pack tile → expand it.

**Expected:** the pack appears by name and lists **four** skins.

| Field | Record |
|---|---|
| Pack tile name shown | |
| Where you found it | ☐ Owned ☐ Featured/hanger button ☐ Other: ______ |
| Skin names visible (list all four) | 1. ______ 2. ______ 3. ______ 4. ______ |
| Screenshot captured? | ☐ Yes ☐ No |
| **Result** | ☐ PASS ☐ FAIL ☐ NOT RUN |

If the pack does not appear: confirm "Only Allow Trusted Skins" is off, fully restart Minecraft, and check the folder location. Record what you tried.

---

### W3 — Select and equip the target skin

**Do:** select **Azure Mechanic** (Track A) or **C1 Signal Red** (Track B), then click **Equip**.

| Field | Record |
|---|---|
| Skin selected | |
| Equip button present and clickable? | ☐ Yes ☐ No |
| Any sign-in / purchase prompt? | ☐ No ☐ Yes → exact text: ______ |
| Dressing Room model changed? | ☐ Yes ☐ No |
| **Result** | ☐ PASS ☐ FAIL ☐ NOT RUN |

> A Dressing Room preview alone does **not** satisfy the main criterion. W4 is the real test.

---

### W4 — Real local player model changed in a world  ← MAIN CRITERION

**Do:** enter a local world, press **F5** for third person, look at your own character.

**Expected:** your actual player character wears the skin. Azure Mechanic = blue/teal. C1 Signal Red = red with a bright chest square and one white scan line.

| Field | Record |
|---|---|
| World used | ☐ Existing ☐ New test world |
| View mode | ☐ Third person back ☐ Third person front ☐ Inventory model |
| Does the real player show the skin? | ☐ Yes ☐ No — still default Steve/Alex ☐ No — other: ______ |
| Chest marker matches the expected skin? | ☐ Yes ☐ No ☐ N/A |
| Screenshot of the real player captured? | ☐ Yes ☐ No |
| **Result** | ☐ PASS ☐ FAIL ☐ NOT RUN |

**This row decides overall success.** A mannequin, a menu thumbnail, or the Dressing Room model do not count.

---

### W5 — Persists after restarting Minecraft

**Do:** fully close Minecraft, relaunch with the same account/network state, do **not** re-select the skin, enter the same world, check third person.

| Field | Record |
|---|---|
| Game fully closed (not minimised)? | ☐ Yes ☐ No |
| Same account state after relaunch? | ☐ Yes ☐ No |
| Skin still equipped without re-selecting? | ☐ Yes ☐ No |
| Real player still shows it in world? | ☐ Yes ☐ No |
| Screenshot after restart? | ☐ Yes ☐ No |
| **Result** | ☐ PASS ☐ FAIL ☐ NOT RUN |

---

## W6 — Offline / no Microsoft sign-in (separate investigation)

Run W1–W5 again while **signed out and disconnected from the internet**.

| # | Question | Result | Notes |
|---|---|---|---|
| W6a | Minecraft launches with no internet | ☐ PASS ☐ FAIL ☐ NOT RUN | Blocker text: |
| W6b | Pack imports while signed out | ☐ PASS ☐ FAIL ☐ NOT RUN | |
| W6c | Pack visible in Dressing Room while signed out | ☐ PASS ☐ FAIL ☐ NOT RUN | |
| W6d | Skin equips with no sign-in prompt | ☐ PASS ☐ FAIL ☐ NOT RUN | |
| W6e | Real player shows skin in a world, offline | ☐ PASS ☐ FAIL ☐ NOT RUN | |
| W6f | Persists after restart, still offline | ☐ PASS ☐ FAIL ☐ NOT RUN | |

On GDK builds, signed-out players use `%appdata%\Minecraft Bedrock\Users\Shared\games\com.mojang`. Record which folder the pack landed in: ______________________

If the game refuses to launch offline because of a Store licence check, record it as a blocker and stop. **Do not attempt any bypass.**

---

## Overall verdict

Overall PASS requires **W1 AND W2 AND W3 AND W4 AND W5** all PASS.

| Condition | Result |
|---|---|
| W1 Import | ☐ PASS ☐ FAIL ☐ NOT RUN |
| W2 Four skins in Dressing Room | ☐ PASS ☐ FAIL ☐ NOT RUN |
| W3 Select + equip | ☐ PASS ☐ FAIL ☐ NOT RUN |
| W4 **Real player changed** | ☐ PASS ☐ FAIL ☐ NOT RUN |
| W5 Restart persistence | ☐ PASS ☐ FAIL ☐ NOT RUN |
| **Overall** | ☐ PASS ☐ FAIL ☐ NOT RUN |
| Offline (W6) overall | ☐ PASS ☐ FAIL ☐ NOT RUN |

Evidence files attached: ______________________

Notes / anything unexpected: ______________________
