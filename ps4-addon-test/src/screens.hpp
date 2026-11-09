#ifndef PKG_ADDON_TEST_SCREENS_HPP
#define PKG_ADDON_TEST_SCREENS_HPP

// ============================================================================
//  AddonStation PS4 — UI v5 "functional"
//  * Everything on screen comes from data set by main.cpp (Supabase). No fake numbers / names / prices.
//  * Guest mode by default; opening an addon/world asks for sign-in (code only — email sign-in removed).
//  * Rail: Home · Deals · Store · Plans  |  Profile (wallet inside) · Settings.   △ = Search.  No triangle menu.
//  * Dark / Light theme switchable at runtime (setThemeLight).
//  * Sale art is shown whole (contain fit) — never cropped.
//  See docs/DEVELOPER_GUIDE_AR.docx for the full integration guide.
// ============================================================================

#include "ui_engine.hpp"
#include <ctime>
#include <functional>
#include <map>
#include <string>
#include <vector>

namespace pkgtest {

// Dark palette reference values (the live palette switches with setThemeLight — use themeColors()).
namespace theme {
constexpr SDL_Color BG{11, 13, 16, 255};
constexpr SDL_Color SF{21, 24, 29, 255};
constexpr SDL_Color SF2{28, 32, 39, 255};
constexpr SDL_Color BAR{17, 19, 24, 255};
constexpr SDL_Color BD{255, 255, 255, 20};
constexpr SDL_Color TX{243, 245, 247, 255};
constexpr SDL_Color MU{152, 161, 172, 255};
constexpr SDL_Color CHIP{205, 211, 218, 255};
constexpr SDL_Color BL{59, 230, 139, 255};
constexpr SDL_Color BL2{29, 184, 102, 255};
constexpr SDL_Color GD{255, 201, 64, 255};
constexpr SDL_Color GDB{140, 100, 24, 255};
constexpr SDL_Color EP{139, 92, 246, 255};
constexpr SDL_Color EPT{186, 164, 255, 255};
constexpr SDL_Color OK{43, 214, 123, 255};
constexpr SDL_Color OKT{120, 236, 170, 255};
constexpr SDL_Color WR{251, 160, 60, 255};
constexpr SDL_Color ER{255, 90, 99, 255};
constexpr SDL_Color ERT{255, 128, 150, 255};
constexpr SDL_Color FOCUS{255, 255, 255, 255};
} // namespace theme

// ============================================================================
//  DATA MODEL  (fill from Supabase; every field is optional — empty / 0 is hidden, never faked)
// ============================================================================
enum class Tier { Free = 0, Epic = 1, Legendary = 2 };      // content_items.plan / profiles.plan

struct AddonItem {
    std::string id;              // content_items.id (uuid)
    std::string title;           // content_items.title
    std::string creator;         // profiles.display_name of created_by (empty = hidden)
    std::string description;     // content_items.description
    std::string category;        // content_items.category
    bool isWorld = false;        // content_type == "world"
    int ftype = 0;               // content_type kind: 0 addon, 1 skin, 2 world, 3 texture pack
    Tier tier = Tier::Free;      // content_items.plan
    int acPrice = 0;             // effective_item_ac_price(id)  (sale applied). 0 = no AC unlock offered
    int acPriceOld = 0;          // content_items.ac_price when a sale price is active (else 0)
    std::string cover;           // engine image name for the cover (e.g. "cv_" + id). Loaded lazily — see setCoverRequestHandler
    std::string coverUrl;        // source URL for the cover download (content_items.cover_url)
    std::string version;         // content_items.version
    long long fileSize = 0;      // bytes (content_items.file_size)
    int downloads = 0;           // content_items.downloads_count
    int views = 0;               // content_items.views_count (REAL — record_content_view)
    std::string ps4RawUrl;       // content_items.ps4_raw_url (public file URL)
    std::string packTargetsRaw;  // content_items.pack_targets (raw jsonb)
    float rating = 0.f;          // content_items.rating
    int ratingCount = 0;         // content_items.rating_count (0 = rating hidden)
    bool exclusive = false;      // is_exclusive
    int exclusiveSlot = 0;       // exclusive_slot (1..3, 0 = any)
    bool featured = false;       // is_featured (hero on Home)
    bool unlocked = false;       // user owns an AC unlock for this item (can_access_item)
    bool installed = false;      // installed on this console (local check)
    std::time_t createdAt = 0;   // created_at (UTC). Newest first everywhere
};

struct Account {
    bool signedIn = false;       // false = guest (AC 0, no avatar, sign-in CTA)
    std::string displayName;     // profiles.display_name
    std::string username;        // profiles.username
    std::string avatar;          // engine image name of profiles.avatar_url (empty = default icon)
    Tier plan = Tier::Free;      // effective_plan(user)
    std::time_t planExpiresAt = 0;
    int acBalance = 0;           // ac_wallets.balance
    int downloads = 0;           // count(content_downloads)
    int favorites = 0;           // count(content_favorites)
    int installed = 0;           // local count
    int downloadsUsed = 0;       // downloads in the current limit period
    int downloadLimit = 0;       // subscription_plans.download_limit (0 = unknown, -1 = unlimited)
};

struct SaleBanner {              // store_banners (enabled, ends_at in the future)
    bool active = false;
    std::string slug;
    std::string theme;           // "winter" | "spring" | "summer" | "autumn" | anything else
    std::map<std::string, std::string> kicker, title, subtitle;   // *_i18n jsonb: lang -> text ("en" fallback)
    int discountPercent = 0;     // discount_percent (0 = no badge)
    std::string art;             // engine image name loaded from cover_url ("" = bundled art bn_<theme>)
    bool artHasTitle = false;    // the artwork already contains the title -> no text drawn over it
    std::string stripArt;        // optional wide Store-strip image ("" = bundled bn_<theme>_strip, then art)
    std::time_t endsAt = 0;      // ends_at (UTC). 0 = no countdown
};

struct WalletTx {                // ac_transactions
    int amount = 0;              // + earned / - spent
    std::string kind;            // "purchase" | "topup" | "gift" | "unlock" | ...
    std::string label;           // reason / item title
    std::time_t at = 0;
    int balanceAfter = 0;
};

struct PlanInfo {                // subscription_plans
    Tier tier = Tier::Free;
    std::string priceLabel;      // already formatted, e.g. "$2.99" or "15 LYD" (empty = hidden)
    int downloadLimit = 0;       // -1 unlimited
    std::string limitPeriod;     // "day" | "week" | "month"
};

// ---- setters (call from main.cpp whenever data arrives; cheap, no drawing) -------------------
void setAccount(const Account& a);           // after sign-in / refresh. Account{} = guest
const Account& account();
void setCatalog(const std::vector<AddonItem>& items);   // full list (sorted newest first internally)
void upsertCatalogItem(const AddonItem& item);          // a new item was published -> appears at once
void removeCatalogItem(const std::string& id);
const std::vector<AddonItem>& catalog();
int findItem(const std::string& id);                    // index in catalog() or -1
void setSaleBanner(const SaleBanner& s);                // SaleBanner{} = no sale
const SaleBanner& saleBanner();
void setWalletHistory(const std::vector<WalletTx>& tx);
void setPlans(const std::vector<PlanInfo>& plans);
void setAppVersion(const std::string& v);               // shown in Settings > About
void setThemeLight(bool light);                         // Settings > Appearance
bool themeLight();
// Called (at most once per frame per item) when a visible card needs a cover that is not loaded yet.
// main.cpp downloads cover_url in the background and calls UiEngine::addImageRGBA / loadImageFile(item.cover, ...).
void setCoverRequestHandler(std::function<void(const AddonItem&)> fn);

// ---- helpers ------------------------------------------------------------------------------
std::string langFromSystem(int sceSystemParamLang);     // SCE_SYSTEM_PARAM_LANG_* -> "en","ar","fr","tr","es","ru","pt","zh"
enum class View { Home, Deals, Store, Search, Mine };
// Catalog indices shown by a screen for a filter (same order as drawn). Use it for focus limits and ✕.
//  Home filter 0..6: All Addons Worlds Free Epic Legendary New   |  Deals 0..4: All Addons Worlds Under100AC Biggest
//  Store filter 0..6: All Addons Worlds Free Epic Legendary New (+ storeSort) |  Search 0..2: All Addons Worlds (+query)
std::vector<int> viewItems(View v, int filter, const std::string& query = "");
std::vector<int> searchCatalog(const std::string& query, int filter);
void setScroll(View v, int firstVisible);               // first card shown (main.cpp scrolls when focus passes the last card)
int scrollOf(View v);
std::vector<int> homeHeroItems();                       // featured items (used by homeSlides)
std::vector<int> homeExclusiveItems();                  // exclusives ordered by exclusive_slot (used by homeSlides)
// v5.4 Home hero carousel (L2/R2): -1 = the live sale slide, then featured + exclusive items (no duplicates).
std::vector<int> homeSlides();
bool saleLive();                                        // a server sale is running now (banner enabled and not ended)
int homeSlideAt(int heroIndex);                         // what Home focus 0 opens: -1 sale, -2 empty catalog, else catalog index
// v5.4 Store sort pill (focus 16): 0 Popular (downloads), 1 Newest (created_at), 2 Top rated (rating) — applied by viewItems(Store)
void setStoreSort(int sort);
int storeSort();
enum class Access { NeedSignIn, Download, NeedPlan };
Access accessFor(const AddonItem& it);                  // same rule as can_access_item on the server
bool acUnlockOffered(const AddonItem& it);              // "Unlock for X AC" visible? (hidden for Legendary / price 0 / free)
std::string formatAC(int v);

// Row icons: 0 pixel, 1 filled, 2 outline (default — chosen by the user)
void setIconStyle(int style);
int iconStyle();

// ============================================================================
//  SCREENS
// ============================================================================
void drawLegalSplash(UiEngine& e, const std::string& langCode);   // static frame (used by the fade below)
// v5.5 start-up splash: appears gradually, stays, disappears gradually. Call every frame with the ms since
// app start; returns false when finished (then show the app and call releaseLegalSplash to free 8 MB).
constexpr int kSplashFadeInMs = 800, kSplashHoldMs = 3800, kSplashFadeOutMs = 1000;   // v0.29: longer hold so the user can read the splash
bool drawLegalSplashFade(UiEngine& e, const std::string& langCode, int elapsedMs);
float legalSplashOpacity(int elapsedMs);                // 0..1 (linear) for your own effects / sounds
// v0.30: animated overlay record/replay (smooth 30 fps motion without full redraws)
void animReset();
void animReplay(UiEngine& e);
bool animHas();
void animRects(std::vector<int>& out);
int legalSplashTotalMs();
bool drawLegalSplashOver(UiEngine& e, const std::string& langCode, int elapsedMs);
void releaseLegalSplash(UiEngine& e);
void drawToast(UiEngine& e, const std::string& text, const std::string& langCode, int kind = 0);
void drawLegendBar(UiEngine& e, const std::vector<std::pair<std::string, std::string>>& items, const std::string& langCode);

// Rail tabs: 0 Home, 1 Deals, 2 Store, 3 Settings, 4 Plans, 5 Profile (-1 none). AC / avatar come from account().
void drawTopNav(UiEngine& e, int activeTab, const std::string& langCode, int acBalance = -1, bool rail = true);

// ---- Sign-in by code (replaces the welcome page) ------------------------------------------
// status: 0 waiting for the phone, 1 linked (shows account), 2 code expired, 3 network error, 4 creating code
// Code view (qrView=false): focus 0 "New code", 1 "Don't have the app? Get it now" (-> set qrView=true).
// QR view   (qrView=true):  the code card shows the "qr_app" image + hint; focus 0 "Get a code" (-> qrView=false).
// Linked (status 1): focus 0 "Continue".  ○ = continue as guest (backLabel=false) / Back; in the QR view ○ = back to the code.
void drawSignIn(UiEngine& e, int focus, const std::string& code, int secondsLeft, int status,
                const std::string& langCode, bool backLabel = false, bool qrView = false);
// Small dialog when a guest opens an addon/world: focus 0 "Sign in with code", 1 "Not now". Draw the screen first.
void drawSignInRequired(UiEngine& e, int focus, const std::string& langCode);

// ---- main screens --------------------------------------------------------------------------
// Home focus: 0-1 hero buttons, 2-4 exclusives, 5-11 tabs, 12-16 shelf cards (viewItems(Home, filter) from scrollOf)
void animBlock(bool b);  // v0.38: suppress page anim-REPLAY under modals (focus rings over dialogs)
bool bakedFadeActive();   // v0.38: banner crossfade running — keep the frame loop ticking
void bakedFadeReset();
void drawHome(UiEngine& e, int focus, int heroIndex, int filterIndex, const std::string& langCode);
// Deals focus: 0-4 tabs, 5-9 cards (items with a sale price, or every paid item while a sale banner is active)
void drawDeals(UiEngine& e, int focus, int filterIndex, const std::string& langCode);
// Store focus: 0-7 cards (2 rows x 4), 8 banner, 9-11 categories
void drawStore(UiEngine& e, int focus, int filterIndex, const std::string& langCode, float panelT);
// Addon page. focus 0 main button, 1 "Unlock for X AC" (only when acUnlockOffered). notice = optional server message
enum class DemoState { Idle = 0, Downloading = 1, Installing = 2, Installed = 3, Queued = 4 };   // v0.41: staged, waiting for Minecraft
void drawAddonDetail(UiEngine& e, int focus, int catalogIndex, DemoState state, int percent,
                     const std::string& langCode, const std::string& notice = "");
// Search (△): focus 0 field, 1-3 tabs, 4-8 results. Empty query shows the most downloaded items.
void drawSearch(UiEngine& e, int focus, const std::string& query, int filterIndex, const std::string& langCode);
// Profile + AC wallet. Guest: focus 0 = "Sign in with code". Signed in: focus = history row 0..5 (-1 none)
void drawProfile(UiEngine& e, int focus, const std::string& langCode);
void drawMine(UiEngine& e, int focus, const std::string& langCode);
// Plans (view-only, subscribe from phone). focus 0..2
void drawPlans(UiEngine& e, int focus, const std::string& langCode);
// Settings rows: 0 Language, 1 Appearance, 2 Account, 3 Auto-launch, 4 About, 5 Sign out (signed in only)
// subFocus: -1 = focus on the row list; >=0 = focus inside the right panel (language 0..7, appearance 0..1)
void drawSettings(UiEngine& e, int focus, int subFocus, bool autoLaunchOn, const std::string& langCode);
void drawConfirmDialog(UiEngine& e, const std::string& title, const std::string& sub,
                       const std::string& yesLabel, const std::string& noLabel, int focus, const std::string& langCode);
void drawLanguageDialog(UiEngine& e, int selectedLang, const std::string& langCode);   // optional (not used by the v5 flow)

std::string tr(const std::string& key, const std::string& langCode);

// ============================================================================
//  DEPRECATED (kept so old main.cpp still compiles — they forward to the v5 screens; remove when migrated)
// ============================================================================
struct CardInfo { std::string title, creator, price, oldPrice, badge; };
struct HeroInfo { std::string title, subtitle, kicker, badge, price, creator; };
[[deprecated("use setCatalog")]] void setServerContent(const HeroInfo*, const CardInfo*, const CardInfo*);
void setDetailTitle(const std::string& title);                            // no-op (title comes from the item)
void setDetailItem(int catalogIndex);                                     // item used by drawInstallDemo
void drawWelcome(UiEngine& e, int focusIndex, const std::string& langCode);              // -> drawSignIn
void drawCodeLink(UiEngine& e, const std::string& code, int secondsLeft, bool linked, const std::string& langCode);
void drawEmailSignin(UiEngine& e, int focus, const std::string& email, const std::string& pass, bool showError,
                     const std::string& langCode);                                        // email removed -> drawSignIn
void drawInstallDemo(UiEngine& e, DemoState state, int percent, const std::string& langCode);   // -> drawAddonDetail
void drawSearch(UiEngine& e, int focus, const std::string& query, int filterIndex, bool noResults, const std::string& langCode);
void drawProfile(UiEngine& e, int focus, const std::string& name, int planIndex, int acBalance,
                 int downloads, int saved, int installed, const std::string& langCode);   // args ignored -> account()
void drawWallet(UiEngine& e, int focus, int acBalance, const std::string& langCode);     // -> drawProfile
void drawPlans(UiEngine& e, int focus, int currentPlan, const std::string& langCode);    // -> drawPlans(account)
void drawMenuOverlay(UiEngine& e, int focus, const std::string& langCode);               // removed: draws nothing
void drawSettings(UiEngine& e, int focus, bool autoLaunchOn, const std::string& accountValue, const std::string& langCode);
void drawSettings(UiEngine& e, int focus, bool notifOn, bool autoLaunchOn, const std::string& accountValue,
                  const std::string& langCode);

// Sales are server-driven now (setSaleBanner). These remain for testing / old code.
enum class Season { None, Winter, Spring, Summer, Autumn };
Season seasonForDate(int month, int day);                 // reference windows (not used automatically)
Season currentSeason();                                   // theme of the active SaleBanner (or override)
bool seasonCountdown(int& days, int& hours, int& minutes); // time left until SaleBanner::endsAt
void setServerTime(std::time_t serverUtc);                // Supabase time (UTC) for countdowns; 0 = device clock
void setSeasonOverride(int season);                       // testing only: -1 off | 0 none | 1..4 forces a demo sale

} // namespace pkgtest
#endif
