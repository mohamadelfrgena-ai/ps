// ============================================================================
//  AddonStation PS4 — main.cpp (UI v7.3 final + real Supabase / installer)
//  Guest-first, code sign-in only, sidebar rail, △ = search, HUD sounds,
//  language auto-detected from the PS4 system language (8 languages).
//  Keeps the proven pkg properties: dynamic dual-mode installer, staged .raw
//  auto-delete + syncWatcher, CE-34878-0-safe IME, natural wording, toasts.
// ============================================================================
#include "screens.hpp"
#include "ui_audio.hpp"

#include "http_download.hpp"
#include "ime_dialog.hpp"
#include "installer.hpp"
#include "supabase_client.hpp"

#include <SDL2/SDL.h>
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <deque>
#include <mutex>
#include <string>
#include <atomic>
#include <thread>
#include <vector>

#if defined(__ORBIS__) || defined(__PROSPERO__)
// libSceSystemService: sceSystemServiceHideSplashScreen + sceSystemServiceParamGetInt.
// create-fself maps these through the toolchain's libSceSystemService.prx stub (module
// table entry "libSceSystemService"); link with -lSceSystemService.
#include <orbis/SystemService.h>
#endif

using namespace pkgtest;

// ---------------------------------------------------------------- config ----
static const char* kStageDir = "/data/AddonStation/installed";
static const char* kUiCfgPath = "/data/AddonStation/ui.cfg";
static const char* kAppVersion = "0.33";
static const char* kBrandSuffix = " by sliver fox";

static const char* kLangs[8] = {"en", "ar", "fr", "tr", "es", "ru", "pt", "zh"};

enum class Btn { None, Up, Down, Left, Right, Cross, Circle, Triangle, Square, L1, R1, L2, R2, Options };

// ---------------------------------------------------------------- helpers ---
static std::string brandTitle(const std::string& t) {
    if (t.empty()) return t;
    if (t.find("sliver fox") != std::string::npos) return t;
    return t + kBrandSuffix;
}

static std::time_t isoToTime(const std::string& iso) {
    int y = 0, mo = 0, d = 0, h = 0, mi = 0, s = 0;
    if (std::sscanf(iso.c_str(), "%d-%d-%dT%d:%d:%d", &y, &mo, &d, &h, &mi, &s) < 3) return 0;
    std::tm tm{};
    tm.tm_year = y - 1900;
    tm.tm_mon = mo - 1;
    tm.tm_mday = d;
    tm.tm_hour = h;
    tm.tm_min = mi;
    tm.tm_sec = s;
    return std::mktime(&tm);
}

static Tier tierFromPlan(const std::string& p) {
    if (p == "legendary") return Tier::Legendary;
    if (p == "epic") return Tier::Epic;
    return Tier::Free;
}

static std::map<std::string, std::string> i18nMap(const std::string& raw) {
    std::map<std::string, std::string> m;
    static const char* langs[] = {"en", "ar", "fr", "tr", "es", "ru", "pt", "zh"};
    for (const char* l : langs) {
        const std::string v = jsonGetString(raw, l);
        if (!v.empty() && v != "null") m[l] = v;
    }
    return m;
}

static std::string basenameOf(const std::string& url) {
    const std::size_t q = url.find('?');
    const std::string clean = (q == std::string::npos) ? url : url.substr(0, q);
    const std::size_t s = clean.find_last_of('/');
    return (s == std::string::npos) ? clean : clean.substr(s + 1);
}

// ---------------------------------------------------------------- app state -
enum class Scr { SignIn, Home, Deals, Store, Plans, Profile, Settings, Search, Detail };

struct App {
    Scr scr = Scr::Home;            // v5: guest Home (no welcome page)
    Scr back = Scr::Home;
    std::string lang = "en";
    int focus = 0, filter = 0, hero = 0, subFocus = -1;
    int detailIdx = -1;
    bool askSignIn = false;
    int askFocus = 0;
    bool confirmSignOut = false;
    int confirmFocus = 1;
    std::string query, notice, code, pollSecret, exchangeId, linkedConsoleId, sessionRef, sessionRefresh;
    int codeSecs = 0, codeStatus = 4;
    bool qrView = false;
    std::atomic<int> dl{0};          // DemoState
    std::atomic<int> dlPct{0};
    std::atomic<bool> busyDownload{false};
    bool autoLaunch = false;
    std::string toastText;
    int toastUntil = 0;              // ms timestamp
    std::atomic<bool> dirty{true};
};
static App A;

static SupabaseConfig gCfg;
static LinkSession gSession;
static std::mutex gSessionMx;
static std::deque<Btn> gBtnQ;
static std::mutex gBtnMx;

// cover queue: worker downloads -> UI thread decodes (engine is not thread safe)
struct CoverBlob { std::string name; std::vector<unsigned char> data; };
static std::deque<CoverBlob> gCoverQ;
static std::mutex gCoverMx;
static std::map<std::string, bool> gCoverRequested;

// ---- v7.3 live-publish sync: a phone publish appears on PS4/PS5 at once ----
static std::mutex gSyncMx;
static std::map<std::string, std::string> gTouchSeen;   // id -> updated_at/created_at
static std::string gDetailId;

static UiEngine* gEngine = nullptr;

static void toast(const std::string& text, int ms = 4200) {
    A.toastText = text;
    A.toastUntil = (int)(std::chrono::duration_cast<std::chrono::milliseconds>(
                             std::chrono::steady_clock::now().time_since_epoch()).count()) + ms;
    A.dirty = true;
}

// ---------------------------------------------------------------- settings --
static void saveUiSettings() {
    FILE* f = std::fopen(kUiCfgPath, "w");
    if (!f) return;
    std::fprintf(f, "lang=%s\ntheme=%s\n", A.lang.c_str(), themeLight() ? "light" : "dark");
    std::fclose(f);
}

static void loadUiSettings() {
    FILE* f = std::fopen(kUiCfgPath, "r");
    if (!f) return;
    char line[128];
    while (std::fgets(line, sizeof(line), f)) {
        std::string s(line);
        while (!s.empty() && (s.back() == '\n' || s.back() == '\r')) s.pop_back();
        const std::size_t eq = s.find('=');
        if (eq == std::string::npos) continue;
        const std::string k = s.substr(0, eq), v = s.substr(eq + 1);
        if (k == "lang") {
            for (const char* l : kLangs) if (v == l) A.lang = v;
        } else if (k == "theme") {
            setThemeLight(v == "light");
        }
    }
    std::fclose(f);
}

static int hookSystemLanguage() {
#if defined(__ORBIS__) || defined(__PROSPERO__)
    int32_t lang = 1;
    sceSystemServiceParamGetInt(1 /* SCE_SYSTEM_SERVICE_PARAM_ID_LANG */, &lang);
    return static_cast<int>(lang);
#else
    const char* env = std::getenv("PS4_LANG");
    std::string v = env ? env : "";
    if (v.rfind("ar", 0) == 0) return 21;
    if (v.rfind("fr", 0) == 0) return 2;
    if (v.rfind("tr", 0) == 0) return 19;
    if (v.rfind("es", 0) == 0) return 3;
    if (v.rfind("ru", 0) == 0) return 8;
    if (v.rfind("pt", 0) == 0) return 7;
    if (v.rfind("zh", 0) == 0) return 10;
    return 1;
#endif
}

// ---------------------------------------------------------------- data ------
// v0.31: JWT الهاتف ينتهي بعد ساعة — نجدّده عبر refresh_token قبل كل استخدام،
// وبلا محاذاة صارمة (كل 40 دقيقة). فشل التحديث لا يُسقط الجلسة إطلاقاً.
static Uint32 gTokenAtMs = 0;
static void ensureFreshSession() {
    if (!gSession.valid || gSession.refreshToken.empty()) return;
    const Uint32 nowMs = SDL_GetTicks();
    if (gTokenAtMs != 0 && nowMs - gTokenAtMs < 40u * 60u * 1000u) return;
    AuthLogin r = callRefreshSession(gCfg, gSession.refreshToken);
    if (r.ok) {
        std::lock_guard<std::mutex> lk(gSessionMx);
        gSession.accessToken = r.accessToken;
        if (!r.refreshToken.empty()) gSession.refreshToken = r.refreshToken;
        saveLinkSession(gSession);
        gTokenAtMs = nowMs;
    }
    // فشل التحديث: نُبقي الجلسة كما هي (لا خروج قسري أبداً)
}

static void applyAccountFromSession() {
    ensureFreshSession();
    ProfileRow p = fetchProfile(gCfg, gSession.userId, gSession.accessToken);
    if (!p.ok && gSession.displayName.empty()) return;
    Account a;
    a.signedIn = true;
    a.displayName = p.ok ? p.displayName : gSession.displayName;
    if (a.displayName.empty()) a.displayName = gSession.username;
    a.username = p.ok ? p.username : gSession.username;
    a.plan = tierFromPlan(p.ok ? p.plan : gSession.plan);
    a.acBalance = p.ok ? p.acBalance : (int)gSession.acBalance;
    a.downloads = (int)p.downloads;
    a.favorites = (int)p.favorites;
    for (const AddonItem& it : catalog())
        if (it.installed) a.installed += 1;
    setAccount(a);
}

static void loadWalletHistory() {
    if (!gSession.valid || gSession.userId.empty()) return;
    std::vector<WalletTx> tx;
    for (const WalletRow& w : fetchWalletTx(gCfg, gSession.userId, gSession.accessToken)) {
        WalletTx t;
        t.amount = (int)w.amount;
        t.kind = w.kind;
        t.label = w.label;
        t.at = isoToTime(w.at);
        t.balanceAfter = (int)w.balanceAfter;
        tx.push_back(t);
    }
    setWalletHistory(tx);
}

static AddonItem toItem(const ItemData& d) {
    AddonItem it;
    it.id = d.id;
    it.title = brandTitle(d.title);
    it.creator = d.creatorName.empty() ? std::string("sliver fox") : d.creatorName;
    it.description = d.description;
    it.category = d.category.empty() ? d.contentType : d.category;
    it.isWorld = (d.contentType == "world");
    it.tier = tierFromPlan(d.plan);
    it.acPrice = (int)(d.saleAcPrice >= 0 ? d.saleAcPrice : d.acPrice);
    it.acPriceOld = (d.saleAcPrice >= 0) ? (int)d.acPrice : 0;
    it.cover = "cv_" + d.id;
    it.coverUrl = d.coverUrl;
    it.version = d.version;
    it.fileSize = d.fileSize;
    it.downloads = (int)d.downloads;
    it.rating = (float)d.rating;
    it.ratingCount = (int)d.ratingCount;
    it.exclusive = d.isExclusive;
    it.exclusiveSlot = (int)d.exclusiveSlot;
    it.featured = d.isFeatured;
    it.installed = isAddonMarkedInstalled(brandTitle(d.title), kStageDir);
    it.createdAt = isoToTime(d.createdAt);
    return it;
}

static void loadCatalogNow() {
    std::vector<ItemData> items = fetchContentItems(gCfg, 40);
    std::vector<AddonItem> out;
    out.reserve(items.size());
    {
        std::lock_guard<std::mutex> lk(gSyncMx);
        for (const ItemData& d : items) {
            out.push_back(toItem(d));
            gTouchSeen[d.id] = d.updatedAt.empty() ? d.createdAt : d.updatedAt;
        }
    }
    setCatalog(out);
    A.dirty = true;
}

// Live-publish sync (phone -> PS4 & PS5 at once): cheap poll every few seconds.
// New / changed rows are fetched by id and upserted; unpublished rows disappear.
static void syncCatalogLive() {
    const std::vector<ItemTouch> touches = fetchContentItemsTouch(gCfg);
    if (touches.empty()) return;
    std::vector<std::string> need;
    bool changed = false;
    {
        std::lock_guard<std::mutex> lk(gSyncMx);
        for (const ItemTouch& t : touches) {
            const int idx = findItem(t.id);
            if (!t.published) {
                if (idx >= 0) {
                    removeCatalogItem(t.id);
                    changed = true;
                }
                gTouchSeen.erase(t.id);
                continue;
            }
            const auto it = gTouchSeen.find(t.id);
            if (it == gTouchSeen.end() || it->second != t.touchAt || idx < 0) {
                need.push_back(t.id);
                gTouchSeen[t.id] = t.touchAt;
            }
        }
    }
    if (!need.empty()) {
        const std::vector<ItemData> rows = fetchContentItemsByIds(gCfg, need);
        for (const ItemData& d : rows) {
            if (!d.isPublished) continue;
            AddonItem u = toItem(d);
            const int old = findItem(u.id);
            if (old >= 0) {
                const AddonItem& o = catalog()[(std::size_t)old];
                u.unlocked = o.unlocked;          // keep AC unlocks across live updates
                if (o.installed) u.installed = true;
            }
            upsertCatalogItem(u);
            changed = true;
        }
    }
    if (changed) {
        if (A.scr == Scr::Detail && !gDetailId.empty()) {
            A.detailIdx = findItem(gDetailId);
            if (A.detailIdx < 0) { A.scr = A.back; A.focus = 0; }
        }
        A.dirty = true;
    }
}

static void loadBannerNow() {
    SaleBanner none;
    setSaleBanner(none);
    for (const BannerData& b : fetchStoreBanners(gCfg)) {
        SaleBanner s;
        s.active = true;
        s.slug = b.slug;
        s.theme = b.theme;
        s.kicker = i18nMap(b.kickerI18n);
        s.title = i18nMap(b.titleI18n);
        s.subtitle = i18nMap(b.subtitleI18n);
        s.discountPercent = (int)b.discountPercent;
        s.artHasTitle = (b.theme == "summer");
        s.endsAt = isoToTime(b.endsAt);
        setSaleBanner(s);
        break;
    }
    A.dirty = true;
}

static void loadPlansNow() {
    std::vector<PlanInfo> plans;
    for (const PlanRow& r : fetchSubscriptionPlans(gCfg)) {
        PlanInfo p;
        p.tier = tierFromPlan(r.plan);
        p.priceLabel = r.priceLabel;
        p.downloadLimit = (int)r.downloadLimit;
        p.limitPeriod = r.limitPeriod;
        plans.push_back(p);
    }
    setPlans(plans);
    A.dirty = true;
}

// covers: ONE serial worker downloads (never floods the HTTP pool), decode on the UI thread.
// The cover URL travels on the item — no network refetch per cover.
struct CoverJob { std::string name; std::string url; };
static std::deque<CoverJob> gCoverJobs;
static std::mutex gCoverJobMx;
static bool gCoverWorkerOn = false;

static void requestCoverAsync(const AddonItem& it) {
    if (it.cover.empty() || it.coverUrl.empty()) return;
    {
        std::lock_guard<std::mutex> lk(gCoverMx);
        if (gCoverRequested[it.cover]) return;
        gCoverRequested[it.cover] = true;
    }
    std::lock_guard<std::mutex> jk(gCoverJobMx);
    gCoverJobs.push_back(CoverJob{it.cover, it.coverUrl});
    if (gCoverWorkerOn) return;
    gCoverWorkerOn = true;
    std::thread([]() {
        for (;;) {
            CoverJob job;
            {
                std::lock_guard<std::mutex> lk(gCoverJobMx);
                if (gCoverJobs.empty()) { gCoverWorkerOn = false; return; }
                job = gCoverJobs.front();
                gCoverJobs.pop_front();
            }
            const std::string tmp = std::string(kStageDir) + "/.cover_tmp";
            DownloadResult r = downloadToFile(job.url, tmp, nullptr);
            if (!r.ok) continue;
            FILE* f = std::fopen(tmp.c_str(), "rb");
            if (!f) continue;
            std::fseek(f, 0, SEEK_END);
            long sz = std::ftell(f);
            std::fseek(f, 0, SEEK_SET);
            CoverBlob blob;
            blob.name = job.name;
            blob.data.resize((std::size_t)(sz > 0 ? sz : 0));
            if (!blob.data.empty()) std::fread(blob.data.data(), 1, blob.data.size(), f);
            std::fclose(f);
            std::remove(tmp.c_str());
            if (blob.data.empty()) continue;
            std::lock_guard<std::mutex> lk(gCoverMx);
            gCoverQ.push_back(std::move(blob));
            A.dirty = true;
        }
    }).detach();
}

static void pumpCovers() {
    std::lock_guard<std::mutex> lk(gCoverMx);
    while (!gCoverQ.empty()) {
        CoverBlob& b = gCoverQ.front();
        if (gEngine) gEngine->loadImageMemory(b.name, b.data.data(), b.data.size(), 640, 360);
        gCoverQ.pop_front();
    }
}

// ---------------------------------------------------------------- sign in ---
static bool parseCodeCreate(const RpcResult& r, std::string& code, std::string& secret, int& secs) {
    if (!r.ok) return false;
    code = jsonGetString(r.body, "code");
    if (code.empty()) code = jsonGetString(r.body, "p_code");
    secret = jsonGetString(r.body, "poll_secret");
    if (secret.empty()) secret = jsonGetString(r.body, "p_poll_secret");
    if (secret.empty()) secret = jsonGetString(r.body, "poll_token");
    secs = (int)jsonGetInt(r.body, "expires_in", 600);
    return !code.empty();
}

static std::atomic<bool> gCodeBusy{false};
static void newCode() {
    if (gCodeBusy.exchange(true)) return;      // one request at a time
    A.codeStatus = 4;
    A.dirty = true;
    std::thread([]() {
        std::string code, secret;
        int secs = 600;
        // legacy RPC first — 40_ps4_and_deals.sql defines create_console_link_code on the live DB
        RpcResult r = callCreateLinkCode(gCfg, "ps4", "PlayStation 4");
        if (!parseCodeCreate(r, code, secret, secs)) {
            r = callConsoleCodeCreate(gCfg, "PlayStation 4", kAppVersion, "ps4-console");
            parseCodeCreate(r, code, secret, secs);
        }
        if (code.empty()) {
            A.codeStatus = 3;
        } else {
            A.code = code;
            A.pollSecret = secret;
            A.codeSecs = secs;
            A.codeStatus = 0;
        }
        gCodeBusy = false;
        A.dirty = true;
    }).detach();
}

// returns 0 pending, 1 approved, 2 expired, 3 error
static int pollCodeOnce() {
    // legacy poll first — poll_console_link_code (40_ps4_and_deals.sql)
    RpcResult old = callPollLinkCode(gCfg, A.code, A.pollSecret);
    if (old.ok) {
        const std::string st = jsonGetString(old.body, "status");
        if (st == "linked" || st == "approved") {
            // one-shot response: carry the session + profile back to the app
            A.sessionRef = jsonGetString(old.body, "access_token");
            A.sessionRefresh = jsonGetString(old.body, "refresh_token");
            A.linkedConsoleId = jsonGetString(old.body, "user_id");
            A.exchangeId.clear();
            return 1;
        }
        if (st == "expired") return 2;
        if (st == "waiting" || st == "pending") return 0;   // "ok":true while waiting — NOT linked
        return 3;
    }
    // fallback: console_code_* RPCs (newer schema)
    RpcResult r = callConsoleCodeConsume(gCfg, A.code, A.pollSecret);
    if (r.ok) {
        const std::string st = jsonGetString(r.body, "status");
        A.exchangeId = jsonGetString(r.body, "exchange_id");
        A.linkedConsoleId = jsonGetString(r.body, "linked_console_id");
        A.sessionRef = jsonGetString(r.body, "session_ref");
        if (A.sessionRefresh.empty()) A.sessionRefresh = jsonGetString(r.body, "refresh_token");
        if (st == "approved" || jsonGetBool(r.body, "approved", false) || jsonGetBool(r.body, "claimed", false))
            return 1;
        if (st == "expired") return 2;
        if (st == "pending" || st == "waiting" || r.rpcOk) return 0;
        return 3;
    }
    return 3;
}

static void finishSignIn() {
    // complete the exchange (new RPC), then load the account
    if (!A.exchangeId.empty() || !A.sessionRef.empty())
        callConsoleCodeComplete(gCfg, A.exchangeId, A.linkedConsoleId, A.sessionRef);
    {
        std::lock_guard<std::mutex> lk(gSessionMx);
        gSession.valid = true;
        gSession.accessToken = A.sessionRef;
        gSession.refreshToken = A.sessionRefresh;
        gSession.plan = "free";
        gTokenAtMs = SDL_GetTicks();   // توكن جديد — لا حاجة لتجديد فوري
    }
    // the complete/consume response may carry the linked console/user id
    {
        std::lock_guard<std::mutex> lk(gSessionMx);
        if (!A.linkedConsoleId.empty()) gSession.userId = A.linkedConsoleId;
    }
    applyAccountFromSession();
    loadWalletHistory();
    {
        std::lock_guard<std::mutex> lk(gSessionMx);
        if (account().signedIn) {
            gSession.displayName = account().displayName;
            gSession.username = account().username;
            gSession.plan = account().plan == Tier::Legendary ? "legendary"
                            : account().plan == Tier::Epic ? "epic" : "free";
            gSession.acBalance = account().acBalance;
            saveLinkSession(gSession);
        }
    }
}

static void hookSignOut() {
    callConsoleUnlink(gCfg, gSession.accessToken);
    clearLinkSession();
    gSession = LinkSession{};
    setAccount(Account{});
    setWalletHistory({});
    A.dirty = true;
}

// ---------------------------------------------------------------- install ---
static void startDownloadFlow(int catIdx) {
    if (catIdx < 0 || catIdx >= (int)catalog().size()) return;
    if (A.busyDownload.exchange(true)) return;
    const AddonItem it = catalog()[(std::size_t)catIdx];
    A.dl = (int)DemoState::Downloading;
    A.dlPct = 0;
    A.notice.clear();
    A.dirty = true;
    const std::string lang = A.lang;
    const bool autoLaunch = A.autoLaunch;
    std::thread([it, lang, autoLaunch]() {
        DownloadGrant g = callRecordContentDownload(gCfg, it.id, gSession.accessToken);
        if (!g.ok || g.ps4RawUrl.empty()) {
            ui_sfx(Sfx::Error);
            toast(g.message.empty() ? tr("err_network", lang) : g.message);
            A.dl = (int)DemoState::Idle;
            A.busyDownload = false;
            A.dirty = true;
            return;
        }
        const std::string rawName = basenameOf(g.ps4RawUrl);
        const std::string rawPath = std::string(kStageDir) + "/" + rawName;
        DownloadResult dr = downloadToFile(g.ps4RawUrl, rawPath, [](int p) {
            A.dlPct = p;
            A.dirty = true;
        });
        if (!dr.ok) {
            ui_sfx(Sfx::Error);
            toast(tr("err_network", lang));
            A.dl = (int)DemoState::Idle;
            A.busyDownload = false;
            A.dirty = true;
            return;
        }
        A.dl = (int)DemoState::Installing;
        A.dlPct = 100;
        A.dirty = true;
        std::string category = "resource_packs";
        if (!g.packs.empty() && !g.packs[0].target.empty()) category = g.packs[0].target;
        else if (it.isWorld) category = "minecraftWorlds";
        const std::string displayName = it.title;
        CopyResult r = installOrQueueAddon(
            rawPath, displayName, kStageDir, "", category, lang,
            [](InstallPhase phase, int pct) {
                if (phase == InstallPhase::Installing) {
                    A.dl = (int)DemoState::Installing;
                    A.dlPct = pct;
                    A.dirty = true;
                }
            },
            autoLaunch);
        if (r.ok) {
            A.dl = (int)DemoState::Installed;
            ui_sfx(Sfx::DownloadDone);
            toast(formatEnjoyNotification(displayName, lang));
            AddonItem u = it;
            u.installed = true;
            upsertCatalogItem(u);
            A.detailIdx = findItem(u.id);
        } else {
            A.dl = (int)DemoState::Idle;
            ui_sfx(Sfx::Error);
            toast(r.message.empty() ? tr("err_network", lang) : r.message);
        }
        A.busyDownload = false;
        A.dirty = true;
    }).detach();
}

// ---------------------------------------------------------------- input -----
// v0.29: input hardening — PS-menu-style hold-repeat + logical held state (post-RTL).
static Btn swapRtl(Btn b) {
    // Arabic (RTL): the layout mirrors horizontally, so the horizontal controls mirror too
    if (A.lang == "ar") {
        if (b == Btn::Left) return Btn::Right;
        if (b == Btn::Right) return Btn::Left;
        if (b == Btn::L2) return Btn::R2;
        if (b == Btn::R2) return Btn::L2;
    }
    return b;
}
static void queueBtn(Btn b) {
    std::lock_guard<std::mutex> lk(gBtnMx);
    if (gBtnQ.size() < 32) gBtnQ.push_back(b);
}
// v0.30: animation frame-snapshot (restored between state changes; animated overlays replayed)
static std::vector<std::uint32_t> gFrameBase;
static Uint32 gLastAnimMs = 0;

static void pushBtn(Btn b) {
    queueBtn(swapRtl(b));
}
static bool gHeld[static_cast<std::size_t>(Btn::Options) + 1] = {};
static std::uint32_t gRepeatAt[static_cast<std::size_t>(Btn::Options) + 1] = {};
static void markHeld(Btn phys, bool down) {
    const Btn b = swapRtl(phys);
    const std::size_t i = static_cast<std::size_t>(b);
    if (down) {
        if (!gHeld[i]) {
            gHeld[i] = true;
            gRepeatAt[i] = SDL_GetTicks() + 380;   // first repeat after 380 ms
            queueBtn(b);
        }
    } else {
        gHeld[i] = false;
    }
}
static void pumpRepeats() {
    const std::uint32_t now = SDL_GetTicks();
    static const Btn kRepeatable[] = {Btn::Up, Btn::Down, Btn::Left, Btn::Right,
                                      Btn::L1, Btn::R1, Btn::L2, Btn::R2};
    for (Btn b : kRepeatable) {
        const std::size_t i = static_cast<std::size_t>(b);
        if (gHeld[i] && static_cast<int>(now - gRepeatAt[i]) >= 0) {
            gRepeatAt[i] = now + 120;              // then every 120 ms (PS-menu speed)
            queueBtn(b);
        }
    }
}

static std::vector<Scr> gBackStk;   // v0.29: real back stack (A.back alone could trap Search)
static void go(Scr s) {
    if (s != Scr::Detail && s != Scr::Search && s != Scr::SignIn) gBackStk.clear();
    A.scr = s;
    A.focus = 0;
    A.filter = 0;
    A.subFocus = -1;
    A.notice.clear();
    A.dirty = true;
}

static void pushScr(Scr s) {
    A.back = A.scr;
    gBackStk.push_back(A.scr);
    go(s);
}
static void goBack() {
    Scr s = gBackStk.empty() ? A.back : gBackStk.back();
    if (!gBackStk.empty()) gBackStk.pop_back();
    if (s == A.scr) s = Scr::Home;   // never "back" into the same screen (old Search trap)
    go(s);
}
static void openItem(int catIdx) {
    if (catIdx < 0) return;
    if (!account().signedIn) {
        A.askSignIn = true;
        A.askFocus = 0;
        A.detailIdx = catIdx;
        A.dirty = true;
        return;
    }
    A.detailIdx = catIdx;
    gDetailId = catalog()[(std::size_t)catIdx].id;
    A.dl = catalog()[(std::size_t)catIdx].installed ? (int)DemoState::Installed : (int)DemoState::Idle;
    pushScr(Scr::Detail);
}

static void startSignIn() {
    pushScr(Scr::SignIn);
    A.qrView = false;
    newCode();
}

static int focusedCard() {
    std::vector<int> v;
    int first = 0, idx = -1;
    switch (A.scr) {
        case Scr::Home:   v = viewItems(View::Home, A.filter);  first = scrollOf(View::Home);  idx = A.focus - 12; break;
        case Scr::Deals:  v = viewItems(View::Deals, A.filter); first = scrollOf(View::Deals); idx = A.focus - 5; break;
        case Scr::Store:  v = viewItems(View::Store, A.filter); first = scrollOf(View::Store); idx = A.focus; break;
        case Scr::Search: v = viewItems(View::Search, A.filter, A.query); first = scrollOf(View::Search); idx = A.focus - 4; break;
        default: return -1;
    }
    const int k = first + idx;
    return (idx >= 0 && k < (int)v.size()) ? v[(std::size_t)k] : -1;
}

static void clampScroll() {
    // keep the focused card visible; windows: Home 5, Deals 5, Store 8, Search 5
    int win = 5, idx = -1;
    View view = View::Home;
    if (A.scr == Scr::Home) { idx = A.focus - 12; view = View::Home; }
    else if (A.scr == Scr::Deals) { idx = A.focus - 5; view = View::Deals; }
    else if (A.scr == Scr::Store) { idx = A.focus; view = View::Store; win = 8; }
    else if (A.scr == Scr::Search) { idx = A.focus - 4; view = View::Search; }
    if (idx < 0) return;
    int first = scrollOf(view);
    if (idx < first) first = idx;
    if (idx > first + win - 1) first = idx - win + 1;
    if (first < 0) first = 0;
    if (first != scrollOf(view)) setScroll(view, first);
}

// focus zones per screen (linear stepping inside a zone; Up/Down crosses zones)
static void moveFocus(int dx, int dy) {
    auto clampInto = [](int v, int lo, int hi) { return std::max(lo, std::min(hi, v)); };
    switch (A.scr) {
        case Scr::SignIn:
            if (A.codeStatus == 1) { A.focus = 0; return; }
            if (A.qrView) { A.focus = 0; return; }
            A.focus = clampInto(A.focus + dx + dy, 0, 1);
            break;
        case Scr::Home: {
            // zones: hero 0-1 | tabs 5-11 | shelf 12-16
            const std::vector<int> v = viewItems(View::Home, A.filter);
            const int shelfN = (int)std::min<std::size_t>(5, v.size());
            if (A.focus <= 1) {
                if (dy > 0) A.focus = 5 + std::min(A.filter, 6);
                else A.focus = clampInto(A.focus + dx, 0, 1);
            } else if (A.focus <= 11) {
                if (dy > 0 && shelfN > 0) A.focus = 12;
                else if (dy < 0) A.focus = clampInto(A.hero, 0, 1);
                else {
                    int f = A.focus - 5 + dx;
                    if (f < 0) f = 6;
                    if (f > 6) f = 0;
                    A.focus = 5 + f;
                }
            } else {
                if (dy < 0) A.focus = 5 + std::min(A.filter, 6);
                else {
                    int k = A.focus - 12 + dx;
                    if (shelfN > 0) { if (k < 0) k = shelfN - 1;
                    if (k > shelfN - 1) k = 0; }
                    else k = 0;
                    A.focus = 12 + k;
                }
            }
            break;
        }
        case Scr::Deals: {
            const std::vector<int> v = viewItems(View::Deals, A.filter);
            const int cardN = (int)std::min<std::size_t>(5, v.size());
            if (A.focus <= 4) {
                if (dy > 0 && cardN > 0) A.focus = 5;
                else {
                    int f = A.focus + dx;
                    if (f < 0) f = 4;
                    if (f > 4) f = 0;
                    A.focus = f;
                }
            } else {
                if (dy < 0) A.focus = std::min(A.filter, 4);
                else {
                    int k = A.focus - 5 + dx;
                    if (cardN > 0) { if (k < 0) k = cardN - 1;
                    if (k > cardN - 1) k = 0; }
                    else k = 0;
                    A.focus = 5 + k;
                }
            }
            break;
        }
        case Scr::Store: {
            const std::vector<int> v = viewItems(View::Store, A.filter);
            const int cardN = (int)std::min<std::size_t>(8, v.size());
            if (A.focus <= 7) {
                if (dy < 0) { if (cardN == 0) A.focus = 8; }
                else if (dy > 0) {
                    if (cardN == 0) A.focus = 9 + std::min(A.filter, 6);
                    else if (A.focus + 4 < cardN) A.focus += 4;
                    else A.focus = 8;
                } else {
                    int k = A.focus + dx;
                    if (cardN > 0) { if (k < 0) k = cardN - 1;
                    if (k > cardN - 1) k = 0; }
                    else k = 0;
                    A.focus = k;
                }
            } else if (A.focus == 8) {
                if (dy > 0) A.focus = 9 + std::min(A.filter, 6);
                else if (dy < 0) A.focus = cardN > 0 ? cardN - 1 : 9;
            } else if (A.focus <= 15) {
                if (dy < 0) A.focus = 8;
                else if (dy > 0) A.focus = 16;
                else {
                    int f = A.focus - 9 + dx;
                    if (f < 0) f = 6;
                    if (f > 6) f = 0;
                    A.focus = 9 + f;
                }
            } else {
                if (dy < 0) A.focus = 9 + std::min(A.filter, 6);
                else if (dx != 0) setStoreSort((storeSort() + (dx > 0 ? 1 : 2)) % 3);
            }
            break;
        }
        case Scr::Search: {
            const std::vector<int> v = viewItems(View::Search, A.filter, A.query);
            const int cardN = (int)std::min<std::size_t>(5, v.size());
            if (A.focus == 0) {
                if (dy > 0) A.focus = 1 + std::min(A.filter, 2);
            } else if (A.focus <= 3) {
                if (dy < 0) A.focus = 0;
                else if (dy > 0 && cardN > 0) A.focus = 4;
                else {
                    int f = A.focus - 1 + dx;
                    if (f < 0) f = 2;
                    if (f > 2) f = 0;
                    A.focus = 1 + f;
                }
            } else {
                if (dy < 0) A.focus = 1 + std::min(A.filter, 2);
                else {
                    int k = A.focus - 4 + dx;
                    if (cardN > 0) { if (k < 0) k = cardN - 1;
                    if (k > cardN - 1) k = 0; }
                    else k = 0;
                    A.focus = 4 + k;
                }
            }
            break;
        }
        case Scr::Detail: {
            const AddonItem& it = catalog()[(std::size_t)A.detailIdx];
            const bool ac = acUnlockOffered(it);
            if (dy > 0 || dx > 0) { if (ac) A.focus = 1; }
            else if (dy < 0 || dx < 0) A.focus = 0;
            break;
        }
        case Scr::Profile: {
            if (!account().signedIn) { A.focus = 0; break; }
            A.focus = clampInto(A.focus + dy + dx, 0, 5);
            break;
        }
        case Scr::Settings: {
            if (A.subFocus >= 0) {
                const int n = A.focus == 0 ? 8 : 2, cols = 2;
                if (dx > 0) A.subFocus = std::min(n - 1, A.subFocus + 1);
                else if (dx < 0) A.subFocus = std::max(0, A.subFocus - 1);
                else if (dy > 0 && A.focus == 0) A.subFocus = std::min(n - 1, A.subFocus + cols);
                else if (dy < 0 && A.focus == 0) A.subFocus = std::max(0, A.subFocus - cols);
            } else {
                const int rows = account().signedIn ? 6 : 5;
                A.focus = clampInto(A.focus + dy, 0, rows - 1);
            }
            break;
        }
        case Scr::Plans:
            break;   // v6.6 view-only
    }
    clampScroll();
    A.dirty = true;
}

static void onSettingsCross() {
    if (A.focus == 0) {
        if (A.subFocus < 0) { A.subFocus = 0; return; }
        A.lang = kLangs[A.subFocus];
        saveUiSettings();
        A.subFocus = -1;
        return;
    }
    if (A.focus == 1) {
        if (A.subFocus < 0) { A.subFocus = themeLight() ? 1 : 0; return; }
        setThemeLight(A.subFocus == 1);
        saveUiSettings();
        A.subFocus = -1;
        return;
    }
    if (A.focus == 2 && !account().signedIn) { startSignIn(); return; }
    if (A.focus == 3) {
        A.autoLaunch = !A.autoLaunch;
        saveAutoLaunchSetting(A.autoLaunch);
        ui_sfx(Sfx::Toggle);
        return;
    }
    if (A.focus == 5 && account().signedIn) {
        A.confirmSignOut = true;
        A.confirmFocus = 1;
    }
}

static void handle(Btn b) {
    if (b == Btn::None) return;
    A.dirty = true;
    // sounds
    if (b == Btn::Up || b == Btn::Down || b == Btn::Left || b == Btn::Right) ui_sfx(Sfx::Focus);
    else if (b == Btn::Cross) ui_sfx(Sfx::Press);
    else if (b == Btn::Circle) ui_sfx(Sfx::Close);
    else if (b == Btn::L1 || b == Btn::R1) ui_sfx(Sfx::Tab);

    // ---- dialogs first ----
    if (A.askSignIn) {
        if (b == Btn::Up || b == Btn::Down) { A.askFocus ^= 1; }
        else if (b == Btn::Cross) {
            A.askSignIn = false;
            if (A.askFocus == 0) startSignIn();
        } else if (b == Btn::Circle) A.askSignIn = false;
        return;
    }
    if (A.confirmSignOut) {
        if (b == Btn::Left || b == Btn::Right) A.confirmFocus ^= 1;
        else if (b == Btn::Cross) {
            A.confirmSignOut = false;
            if (A.confirmFocus == 0) {
                ui_sfx(Sfx::Toggle);
                hookSignOut();
                toast(tr("settings_signout", A.lang));
                go(Scr::Home);
            }
        } else if (b == Btn::Circle) A.confirmSignOut = false;
        return;
    }
    // ---- global: △ = Search (no triangle menu in v5) ----
    if (b == Btn::Triangle && A.scr != Scr::SignIn && A.scr != Scr::Search) {
        pushScr(Scr::Search);
        return;
    }
    switch (A.scr) {
        case Scr::SignIn:
            if (A.codeStatus == 1) {
                if (b == Btn::Cross || b == Btn::Circle) goBack();
            } else if (A.qrView) {
                if (b == Btn::Cross) {
                    A.qrView = false;
                    A.focus = 0;
                    if (A.codeStatus == 2 || A.codeStatus == 3 || A.codeSecs <= 0) newCode();
                } else if (b == Btn::Circle) { A.qrView = false; A.focus = 1; }
            } else {
                if (b == Btn::Up || b == Btn::Down) A.focus ^= 1;
                else if (b == Btn::Cross) {
                    if (A.focus == 0) newCode();
                    else { A.qrView = true; A.focus = 0; }
                } else if (b == Btn::Circle) goBack();
            }
            break;
        case Scr::Settings:
            if (A.subFocus >= 0) {
                if (b == Btn::Cross) onSettingsCross();
                else if (b == Btn::Circle) A.subFocus = -1;
                else moveFocus(b == Btn::Left ? -1 : b == Btn::Right ? 1 : 0,
                               b == Btn::Up ? -1 : b == Btn::Down ? 1 : 0);
            } else {
                if (b == Btn::Cross) onSettingsCross();
                else if (b == Btn::Circle) go(Scr::Home);
                else if (b == Btn::Up) moveFocus(0, -1);
                else if (b == Btn::Down) moveFocus(0, 1);
            }
            break;
        case Scr::Search:
            if (b == Btn::Cross && A.focus == 0) {
                const std::string t = openImeDialog("Search", A.query, 64);
                if (!t.empty()) {
                    A.query = t;
                    setScroll(View::Search, 0);
                }
            } else if (b == Btn::Cross && A.focus >= 1 && A.focus <= 3) {
                A.filter = A.focus - 1;
                setScroll(View::Search, 0);
            } else if (b == Btn::Cross) {
                openItem(focusedCard());
            } else if (b == Btn::Circle) {
                goBack();
            } else if (b == Btn::Up) moveFocus(0, -1);
            else if (b == Btn::Down) moveFocus(0, 1);
            else if (b == Btn::Left) moveFocus(-1, 0);
            else if (b == Btn::Right) moveFocus(1, 0);
            break;
        case Scr::Detail: {
            if (A.detailIdx < 0 || A.detailIdx >= (int)catalog().size()) { goBack(); break; }
            const AddonItem& it = catalog()[(std::size_t)A.detailIdx];
            const bool ac = acUnlockOffered(it);
            if (b == Btn::Up || b == Btn::Left) moveFocus(-1, 0);
            else if (b == Btn::Down || b == Btn::Right) moveFocus(1, 0);
            else if (b == Btn::Cross && A.focus == 1 && ac) {
                // AC unlock (v7 payment rules)
                RpcResult r = callAcUnlock(gCfg, it.id, gSession.accessToken);
                if (r.ok && (r.rpcOk || jsonGetBool(r.body, "ok", true))) {
                    ui_sfx(Sfx::Purchase);
                    applyAccountFromSession();
                    loadWalletHistory();
                    AddonItem u = it;
                    u.unlocked = true;
                    upsertCatalogItem(u);
                    A.detailIdx = findItem(u.id);
                    A.focus = 0;
                } else {
                    ui_sfx(Sfx::Error);
                    A.notice = tr("err_not_enough_ac", A.lang);
                }
            } else if (b == Btn::Cross && A.focus == 0) {
                switch (accessFor(it)) {
                    case Access::NeedSignIn: startSignIn(); break;
                    case Access::Download:
                        if (it.installed) {
                            A.dl = (int)DemoState::Installed;
                        } else {
                            startDownloadFlow(A.detailIdx);
                        }
                        break;
                    case Access::NeedPlan: go(Scr::Plans); break;
                }
            } else if (b == Btn::Circle) {
                goBack();
            }
            break;
        }
        default:   // Home / Deals / Store / Plans / Profile
            if (b == Btn::Cross) {
                if (A.scr == Scr::Plans) break;
                if (A.scr == Scr::Profile && !account().signedIn) { startSignIn(); break; }
                if (A.scr == Scr::Home && A.focus <= 1) {
                    const int s = homeSlideAt(A.hero);
                    if (s >= 0) openItem(s);
                    else if (s == -1) go(A.focus == 0 ? Scr::Deals : Scr::Store);
                    else if (account().signedIn) go(Scr::Plans);
                    else startSignIn();
                    break;
                }
                if (A.scr == Scr::Home && A.focus >= 5 && A.focus <= 11) {
                    A.filter = A.focus - 5;
                    setScroll(View::Home, 0);
                    break;
                }
                if (A.scr == Scr::Deals && A.focus <= 4) {
                    A.filter = A.focus;
                    setScroll(View::Deals, 0);
                    break;
                }
                if (A.scr == Scr::Store && A.focus == 16) {
                    setStoreSort((storeSort() + 1) % 3);
                    setScroll(View::Store, 0);
                    ui_sfx(Sfx::Toggle);
                    break;
                }
                if (A.scr == Scr::Store && A.focus >= 9 && A.focus <= 15) {
                    A.filter = A.focus - 9;
                    setScroll(View::Store, 0);
                    break;
                }
                if (A.scr == Scr::Store && A.focus == 8) {
                    if (saleLive()) go(Scr::Deals);
                    else if (!catalog().empty()) openItem(0);
                    break;
                }
                openItem(focusedCard());
            } else if (b == Btn::Circle) {
                if (A.scr == Scr::Home) break;          // never exits the app
                go(Scr::Home);
            } else if (b == Btn::Up) moveFocus(0, -1);
            else if (b == Btn::Down) moveFocus(0, 1);
            else if (b == Btn::Left) moveFocus(-1, 0);
            else if (b == Btn::Right) moveFocus(1, 0);
            if (A.scr == Scr::Home && (b == Btn::L2 || b == Btn::R2)) {
                const int n = (int)homeSlides().size();
                if (n > 0) {
                    A.hero += (b == Btn::R2 ? 1 : -1);
                    if (A.hero < 0) A.hero = n - 1;
                    if (A.hero >= n) A.hero = 0;
                    A.dirty = true;
                }
            }
            if (b == Btn::L1 || b == Btn::R1) {
                static const Scr order[6] = {Scr::Home, Scr::Deals, Scr::Store, Scr::Plans, Scr::Profile, Scr::Settings};
                int i = 0;
                while (i < 6 && order[i] != A.scr) ++i;
                go(order[(i + (b == Btn::R1 ? 1 : 5)) % 6]);
            }
            break;
    }
    A.dirty = true;
}

// ---------------------------------------------------------------- drawing ---
static void drawFrame(UiEngine& e) {
    const std::string& L = A.lang;
    switch (A.scr) {
        case Scr::SignIn:   drawSignIn(e, A.focus, A.code, A.codeSecs, A.codeStatus, L, A.back != Scr::Home, A.qrView); break;
        case Scr::Home:     drawHome(e, A.focus, A.hero, A.filter, L); break;
        case Scr::Deals:    drawDeals(e, A.focus, A.filter, L); break;
        case Scr::Store:    drawStore(e, A.focus, A.filter, L); break;
        case Scr::Plans:    drawPlans(e, -1, L); break;
        case Scr::Profile:  drawProfile(e, A.focus, L); break;
        case Scr::Settings: drawSettings(e, A.focus, A.subFocus, A.autoLaunch, L); break;
        case Scr::Search:   drawSearch(e, A.focus, A.query, A.filter, L); break;
        case Scr::Detail:   drawAddonDetail(e, A.focus, A.detailIdx, (DemoState)A.dl.load(), A.dlPct.load(), L, A.notice); break;
    }
    if (A.askSignIn) drawSignInRequired(e, A.askFocus, L);
    if (A.confirmSignOut)
        drawConfirmDialog(e, tr("signout_q", L), tr("signout_sub", L), tr("settings_signout", L), tr("cancel", L), A.confirmFocus, L);
    if (!A.toastText.empty()) drawToast(e, A.toastText, L);
}

// ---------------------------------------------------------------- main ------
static std::vector<Btn> drainButtons() {
    std::vector<Btn> out;
    std::lock_guard<std::mutex> lk(gBtnMx);
    while (!gBtnQ.empty()) {
        out.push_back(gBtnQ.front());
        gBtnQ.pop_front();
    }
    return out;
}

int main(int, char**) {
    setvbuf(stdout, nullptr, _IONBF, 0);
    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_JOYSTICK) != 0) return 1;
    SDL_Window* window = SDL_CreateWindow("AddonStation", SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED,
                                          UiEngine::kWidth, UiEngine::kHeight, 0);
    if (!window) { SDL_Quit(); return 1; }
    // v0.22-proven: SOFTWARE renderer bound to the window surface (never GPU)
    SDL_Surface* windowSurface = SDL_GetWindowSurface(window);
    SDL_Renderer* renderer = windowSurface ? SDL_CreateSoftwareRenderer(windowSurface) : nullptr;
    if (!renderer) { SDL_DestroyWindow(window); SDL_Quit(); return 1; }

    std::vector<SDL_Joystick*> joysticks;
    const int numJoy = SDL_NumJoysticks();
    for (int i = 0; i < numJoy; ++i)
        if (SDL_Joystick* joy = SDL_JoystickOpen(i)) joysticks.push_back(joy);
    SDL_JoystickEventState(SDL_ENABLE);

    UiEngine engine;
    gEngine = &engine;
    // PS4 app root is /app0 — plain relative paths do not exist on the console
    if (!engine.init("/app0/assets/ui.pak") && !engine.init("assets/ui.pak")) {
        std::fprintf(stderr, "ui.pak init failed\n");
        return 1;
    }
    engine.setScaledCacheBudget(48u * 1024u * 1024u);

    // ---- language: saved choice, else the PS4 system language (auto) ----
    gCfg = loadSupabaseConfig();
    loadUiSettings();
    {
        // First run = no saved language yet -> the PS4 system language (instant, no module loads)
        FILE* f = std::fopen(kUiCfgPath, "r");
        const bool haveSaved = (f != nullptr);
        if (f) std::fclose(f);
        if (!haveSaved && (A.lang.empty() || A.lang == "en"))
            A.lang = langFromSystem(hookSystemLanguage());
    }
    A.autoLaunch = loadAutoLaunchSetting();

    // ---- background data loading (runs while the splash is on screen) ----
    gSession = loadLinkSession();
    std::thread([]() {
        loadCatalogNow();
        loadBannerNow();
        loadPlansNow();
        if (gSession.valid) {
            applyAccountFromSession();
            loadWalletHistory();
        }
    }).detach();

    // ---- HUD sounds: audio ready at boot; the startup music plays AFTER the splash (v0.33) ----
    if (!ui_audio_init("/app0/assets/sfx")) ui_audio_init("assets/sfx");
    ui_sfx_volume(100);

    // ---- boot image (pic1): put our first frame on screen, THEN dismiss it ----
    drawLegalSplashFade(engine, A.lang, 0);
    engine.present(SDL_GetWindowSurface(window));
    SDL_UpdateWindowSurface(window);
#if defined(__ORBIS__) || defined(__PROSPERO__)
    sceSystemServiceHideSplashScreen();   // boot screen -> our splash (fade starts from black)
#endif

    // ---- v5.5 legal splash: fade in -> hold -> smooth cross-fade into guest Home (v0.30) ----
    {
        const auto t0 = std::chrono::steady_clock::now();
        const int fadeOutAt = kSplashFadeInMs + kSplashHoldMs;
        for (;;) {
            const int ms = (int)std::chrono::duration_cast<std::chrono::milliseconds>(
                                std::chrono::steady_clock::now() - t0).count();
            bool running;
            if (ms < fadeOutAt) {
                running = drawLegalSplashFade(engine, A.lang, ms);
            } else {
                drawFrame(engine);                       // Home live beneath the splash
                running = drawLegalSplashOver(engine, A.lang, ms);
            }
            engine.present(SDL_GetWindowSurface(window));
            SDL_UpdateWindowSurface(window);
            SDL_Delay(16);
            if (!running) break;
        }
        releaseLegalSplash(engine);
        A.dirty = true;
    }
    // v0.33: startup music AFTER the splash — it plays when the main store page appears
    // (user request 2026-10-28: never with the boot/legal splash). Same intent as the
    // Sfx::AppStart comment ("عند ظهور المنزل بعد الإسبلاش").
    ui_sfx(Sfx::AppStart);
    setAppVersion(kAppVersion);
    if (!gSession.valid) setAccount(Account{});
    setCoverRequestHandler([](const AddonItem& it) { requestCoverAsync(it); });

    // ---- v7.3 live-publish sync: phone publishes appear here within seconds ----
    std::thread([]() {
        int cycle = 0;
        for (;;) {
            std::this_thread::sleep_for(std::chrono::seconds(5));
            ++cycle;
            syncCatalogLive();
            if (cycle % 6 == 0) loadBannerNow();       // 30s: sale banner goes live at once
            if (cycle % 60 == 0) loadPlansNow();       // 5min: plan changes
        }
    }).detach();

    // ---- syncWatcher: staged .raw installs complete when Minecraft mounts ----
    std::thread([]() {
        for (;;) {
            std::this_thread::sleep_for(std::chrono::seconds(2));
            CopyResult r = syncPendingAddonsIfMounted(kStageDir, "", A.lang);
            if (r.ok && !r.lastAddonName.empty()) {
                ui_sfx(Sfx::DownloadDone);
                toast(formatEnjoyNotification(r.lastAddonName, A.lang));
            }
        }
    }).detach();

    // ---- main loop: redraw only when something changes (dirty flag) ----
    std::time_t lastSec = std::time(nullptr);
    bool running = true;
    while (running) {
        SDL_Event ev;
        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_QUIT) {
                pushBtn(Btn::Circle);       // ○ never exits the app
            } else if (ev.type == SDL_JOYHATMOTION) {
                // v0.29: track held state so the D-pad repeats like the PS menu
                const Uint8 hat = ev.jhat.value;
                markHeld(Btn::Up, (hat & SDL_HAT_UP) != 0);
                markHeld(Btn::Down, (hat & SDL_HAT_DOWN) != 0);
                markHeld(Btn::Left, (hat & SDL_HAT_LEFT) != 0);
                markHeld(Btn::Right, (hat & SDL_HAT_RIGHT) != 0);
            } else if (ev.type == SDL_JOYBUTTONDOWN || ev.type == SDL_JOYBUTTONUP) {
                // OpenOrbis SDL-PS4 driver (src/joystick/ps4): 0=✕ 1=○ 2=□ 3=△
                // 4=L1 5=R1 18=L2 19=R2 (6/7 kept as legacy aliases)
                const Uint8 b = ev.jbutton.button;
                Btn mapped = Btn::None;
                if (b == 0) mapped = Btn::Cross;
                else if (b == 1) mapped = Btn::Circle;
                else if (b == 2) mapped = Btn::Square;
                else if (b == 3) mapped = Btn::Triangle;
                else if (b == 4) mapped = Btn::L1;
                else if (b == 5) mapped = Btn::R1;
                else if (b == 6 || b == 18) mapped = Btn::L2;
                else if (b == 7 || b == 19) mapped = Btn::R2;
                if (mapped != Btn::None) markHeld(mapped, ev.type == SDL_JOYBUTTONDOWN);
            } else if (ev.type == SDL_KEYDOWN) {
                const SDL_Keycode k = ev.key.keysym.sym;
                if (k == SDLK_UP) pushBtn(Btn::Up);
                else if (k == SDLK_DOWN) pushBtn(Btn::Down);
                else if (k == SDLK_LEFT) pushBtn(Btn::Left);
                else if (k == SDLK_RIGHT) pushBtn(Btn::Right);
                else if (k == SDLK_RETURN || k == SDLK_SPACE) pushBtn(Btn::Cross);
                else if (k == SDLK_BACKSPACE || k == SDLK_ESCAPE) pushBtn(Btn::Circle);
                else if (k == SDLK_t) pushBtn(Btn::Triangle);
                else if (k == SDLK_s) pushBtn(Btn::Square);
                else if (k == SDLK_LEFTBRACKET) pushBtn(Btn::L2);
                else if (k == SDLK_RIGHTBRACKET) pushBtn(Btn::R2);
                else if (k == SDLK_COMMA) pushBtn(Btn::L1);
                else if (k == SDLK_PERIOD) pushBtn(Btn::R1);
            }
        }
        pumpRepeats();                       // v0.29: PS-menu hold-repeat
        for (Btn b : drainButtons()) handle(b);

        const std::time_t now = std::time(nullptr);
        if (now != lastSec) {
            if (A.scr == Scr::SignIn && A.codeStatus == 0 && A.codeSecs > 0) {
                --A.codeSecs;
                A.dirty = true;
            }
            if (now / 60 != lastSec / 60) A.dirty = true;   // clock + sale countdown
            lastSec = now;
        }
        if (A.scr == Scr::SignIn && A.codeStatus == 0) {
            static std::time_t lastPoll = 0;
            static std::atomic<bool> pollBusy{false};
            if (now - lastPoll >= 2 && !pollBusy.exchange(true)) {
                lastPoll = now;
                std::thread([]() {
                    const int r = pollCodeOnce();
                    if (r == 1) {
                        ui_sfx(Sfx::Success);
                        finishSignIn();
                        A.codeStatus = 1;
                        A.focus = 0;
                    } else if (r == 2 || r == 3) {
                        A.codeStatus = r;
                    }
                    pollBusy = false;
                    A.dirty = true;
                }).detach();
            }
        }

        pumpCovers();

        if (!A.toastText.empty() && (int)(std::chrono::duration_cast<std::chrono::milliseconds>(
                                              std::chrono::steady_clock::now().time_since_epoch()).count()) > A.toastUntil) {
            A.toastText.clear();
            A.dirty = true;
        }

        const Uint32 nowMs = SDL_GetTicks();
        if (A.dirty.exchange(false)) {
            animReset();
            drawFrame(engine);
            gFrameBase = engine.framebuffer();
            engine.present(SDL_GetWindowSurface(window));
            SDL_UpdateWindowSurface(window);
            gLastAnimMs = nowMs;
        } else if (animHas() && nowMs - gLastAnimMs >= 33) {
            // v0.30: ~30 fps motion (glow comet, gem, mascot, settings tiles) without a full redraw
            gLastAnimMs = nowMs;
            std::vector<int> rects;
            animRects(rects);
            if (!rects.empty())
                engine.restoreFrom(gFrameBase, rects.data(), static_cast<int>(rects.size()) / 4);
            animReplay(engine);
            engine.present(SDL_GetWindowSurface(window));
            SDL_UpdateWindowSurface(window);
        }
        SDL_Delay(8);       // 120 Hz input polling — PS-menu responsiveness
    }
    ui_audio_shutdown();
    return 0;
}
