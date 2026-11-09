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
#include <sys/stat.h>
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
static const char* kAppVersion = "0.35";
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
enum class Scr { SignIn, Home, Deals, Store, Plans, Profile, Settings, Search, Detail, Mine };

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
    std::string linkedUsername, linkedDisplayName, linkedAvatar;   // v0.42/0.48: profile from the poll response
    int codeSecs = 0, codeStatus = 4;
    bool qrView = false;
    std::atomic<int> dl{0};          // DemoState
    std::atomic<int> dlPct{0};
    std::atomic<bool> busyDownload{false};
    bool autoLaunch = false;
    std::string toastText;
    int toastKind = 0;   // v0.64: 0 success | 1 error | 2 info
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
static std::map<std::string, int> gCoverTries;   // v0.63: bounded retry (was: lost forever)
static const char* kCoverDir = "/data/AddonStation/covers";

// ---- v7.3 live-publish sync: a phone publish appears on PS4/PS5 at once ----
static std::mutex gSyncMx;
static std::map<std::string, std::string> gTouchSeen;   // id -> updated_at/created_at
static std::string gDetailId;

static UiEngine* gEngine = nullptr;

static void toast(const std::string& text, int ms = 4200, int kind = 0) {
    A.toastText = text;
    A.toastKind = kind;   // v0.64: errors show a red X — never a green check
    A.toastUntil = (int)(std::chrono::duration_cast<std::chrono::milliseconds>(
                             std::chrono::steady_clock::now().time_since_epoch()).count()) + ms;
    A.dirty = true;
}

// v0.64 («كنت تطلع مثل ذي الاخطاء»): raw engine strings like "SEND FAILED (-2143088536)"
// never reach the user — they map to the localized network error. Real server messages
// (e.g. "not enough AC") pass through unchanged.
static std::string netErrText(const std::string& msg, const std::string& lang) {
    static const char* raw[] = {"SEND FAILED", "CONNECT FAILED", "REQUEST FAILED", "TEMPLATE FAILED",
                                "STATUS FAILED", "HTTP STATUS", "OPEN FAILED", "READ ", "WRITE ",
                                "TIMEOUT", "INIT FAILED", "NOT AVAILABLE"};
    for (const char* r : raw) {
        if (msg.find(r) != std::string::npos) return tr("err_network", lang);
    }
    return msg.empty() ? tr("err_network", lang) : msg;
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

static std::atomic<unsigned> gSessGen{0};   // v0.62: bumped on sign-out — kills in-flight refreshes
static void requestAvatarAsync(const std::string& url);   // v0.65e: defined with the cover worker
static void applyAccountFromSession() {
    const unsigned gen0 = gSessGen.load();
    ensureFreshSession();
    if (gen0 != gSessGen.load()) return;
    ProfileRow p = fetchProfile(gCfg, gSession.userId, gSession.accessToken);
    if (!p.ok && gSession.displayName.empty() && gSession.username.empty()) return;   // v0.42
    Account a;
    a.signedIn = true;
    a.displayName = p.ok ? p.displayName : gSession.displayName;
    if (a.displayName.empty()) a.displayName = gSession.username;
    a.username = p.ok ? p.username : gSession.username;
    a.avatar = p.ok ? p.avatarUrl : gSession.avatarUrl;   // v0.48: the picture was never assigned
    requestAvatarAsync(a.avatar);   // v0.65e («صورة البروفايل لا تظهر»): download the image too
    a.plan = tierFromPlan(p.ok ? p.plan : gSession.plan);
    a.acBalance = p.ok ? p.acBalance : (int)gSession.acBalance;
    a.downloads = (int)p.downloads;
    a.favorites = (int)p.favorites;
    for (const AddonItem& it : catalog())
        if (it.installed) a.installed += 1;
    if (gen0 != gSessGen.load()) return;   // signed out while fetching — do NOT resurrect
    setAccount(a);
    A.dirty = true;   // v0.50: the AC balance chip repaints the moment it changes
}

#ifdef AS_BUILD_2121
// v0.36 build "2121": NO account at all — browse + download anonymously.
// Any saved session is dropped at launch so the build can never hold an account.
static void forceNoAccount2121() {
    std::lock_guard<std::mutex> lk(gSessionMx);
    clearLinkSession();
    gSession = LinkSession{};
}
#endif

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

// v0.63 («يقول مثبتة وهي غير مثبتة»): the folder names a REAL install uses — each
// pack's dest_folder, or the display title for single-file grants. The installed
// check and the marks must speak the same names.
static std::vector<std::string> packFolderNames(const AddonItem& it) {
    std::vector<std::string> names;
    for (const std::string& p : splitJsonObjects(it.packTargetsRaw)) {
        const std::string f = jsonGetString(p, "dest_folder");
        if (!f.empty()) names.push_back(f);
    }
    names.push_back(it.title);   // single-file grants install under the display title
    return names;
}

// Reality wins when the game is mounted — old staging-era marks lied forever
// (marked installed on QUEUED, never-copied packs). Marks are the fallback while
// the game is closed and are reconciled against reality on every mount.
static bool computeInstalled(const AddonItem& it) {
    const std::vector<std::string> names = packFolderNames(it);
    const std::string mounted = mountedMojangDir();
    if (!mounted.empty()) {
        for (const std::string& n : names) if (addonFolderPresent(mounted, n)) return true;
        return false;
    }
    for (const std::string& n : names) if (isAddonMarkedInstalled(n, kStageDir)) return true;
    return false;
}

// Heal the marks + the visible state whenever the game is mounted.
static void reconcileInstalled() {
    const std::string mounted = mountedMojangDir();
    if (mounted.empty()) return;
    std::vector<std::string> real;
    bool changed = false;
    for (AddonItem it : catalog()) {
        bool present = false;
        for (const std::string& n : packFolderNames(it)) {
            if (addonFolderPresent(mounted, n)) {
                present = true;
                real.push_back(n);
                break;
            }
        }
        if (it.installed != present) {
            it.installed = present;
            upsertCatalogItem(it);
            changed = true;
        }
    }
    static std::string sLast;
    std::string key;
    for (const std::string& n : real) key += n + "\n";
    if (changed || key != sLast) {
        sLast = key;
        rewriteInstalledMarks(kStageDir, real);
    }
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
    it.views = (int)d.views;
    it.ps4RawUrl = d.ps4RawUrl;
    it.packTargetsRaw = d.packTargetsRaw;
    it.rating = (float)d.rating;
    it.ratingCount = (int)d.ratingCount;
    it.exclusive = d.isExclusive;
    it.exclusiveSlot = (int)d.exclusiveSlot;
    it.featured = d.isFeatured;
    it.installed = computeInstalled(it);
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
    reconcileInstalled();   // v0.63: heal «مثبتة» lies at load
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
struct CoverJob { std::string name; std::string url; std::string file; };   // v0.65e: file = safe cache name
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
    gCoverJobs.push_back(CoverJob{it.cover, it.coverUrl, it.cover});
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
            // v0.63: covers are CACHED on disk (/data/AddonStation/covers/<name>.img).
            // The old flow wrote every cover to a shared tmp inside the install dir —
            // if that dir was missing the fopen failed and the cover was lost forever
            // («صور الاضافات لم تظهر»), and every launch redownloaded everything.
            const std::string cache = std::string(kCoverDir) + "/" + (job.file.empty() ? job.name : job.file) + ".img";
            CoverBlob blob;
            blob.name = job.name;
            auto readAll = [&blob](const std::string& path) {
                FILE* f = std::fopen(path.c_str(), "rb");
                if (!f) return false;
                std::fseek(f, 0, SEEK_END);
                long sz = std::ftell(f);
                std::fseek(f, 0, SEEK_SET);
                blob.data.resize((std::size_t)(sz > 0 ? sz : 0));
                if (!blob.data.empty()) std::fread(blob.data.data(), 1, blob.data.size(), f);
                std::fclose(f);
                return !blob.data.empty();
            };
            if (!readAll(cache)) {
                ::mkdir("/data", 0777);
                ::mkdir("/data/AddonStation", 0777);
                ::mkdir(kCoverDir, 0777);
                const std::string tmp = cache + ".tmp";
                DownloadResult r = downloadToFile(job.url, tmp, nullptr);
                bool got = r.ok && readAll(tmp);
                if (got) {
                    ::rename(tmp.c_str(), cache.c_str());   // atomic-ish cache write
                } else {
                    ::remove(tmp.c_str());
                }
                if (!got) {
                    // bounded retry on the next frame — never lose a cover to one failure
                    std::lock_guard<std::mutex> lk(gCoverMx);
                    if (++gCoverTries[job.name] < 3) gCoverRequested[job.name] = false;
                    continue;
                }
            }
            {
                std::lock_guard<std::mutex> lk(gCoverMx);
                gCoverQ.push_back(std::move(blob));
                A.dirty = true;
            }
        }
    }).detach();
}

// v0.65e («صورة البروفايل لا تظهر»): the account avatar URL was stored but its
// image was NEVER downloaded — the person glyph always showed. Queue it through
// the same cover worker; the engine key stays the URL (screens look it up there),
// the disk cache uses a safe hashed name.
static void requestAvatarAsync(const std::string& url) {
    if (url.empty()) return;
    unsigned long long h = 1469598103934665603ULL;   // FNV-1a -> safe cache name
    for (unsigned char c : url) { h ^= c; h *= 1099511628211ULL; }
    char hb[24]; std::snprintf(hb, sizeof hb, "acc_%016llx", h);
    {
        std::lock_guard<std::mutex> lk(gCoverMx);
        if (gCoverRequested[url]) return;
        gCoverRequested[url] = true;
    }
    std::lock_guard<std::mutex> jk(gCoverJobMx);
    gCoverJobs.push_back(CoverJob{url, url, hb});
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
            const std::string cache = std::string(kCoverDir) + "/" + job.file + ".img";
            CoverBlob blob;
            blob.name = job.name;
            auto readAll = [&blob](const std::string& path) {
                FILE* f = std::fopen(path.c_str(), "rb");
                if (!f) return false;
                std::fseek(f, 0, SEEK_END);
                long sz = std::ftell(f);
                std::fseek(f, 0, SEEK_SET);
                blob.data.resize((std::size_t)(sz > 0 ? sz : 0));
                if (!blob.data.empty()) std::fread(blob.data.data(), 1, blob.data.size(), f);
                std::fclose(f);
                return !blob.data.empty();
            };
            if (!readAll(cache)) {
                ::mkdir("/data", 0777);
                ::mkdir("/data/AddonStation", 0777);
                ::mkdir(kCoverDir, 0777);
                const std::string tmp = cache + ".tmp";
                DownloadResult r = downloadToFile(job.url, tmp, nullptr);
                bool got = r.ok && readAll(tmp);
                if (got) ::rename(tmp.c_str(), cache.c_str());
                else ::remove(tmp.c_str());
                if (!got) {
                    std::lock_guard<std::mutex> lk(gCoverMx);
                    if (++gCoverTries[job.name] < 3) gCoverRequested[job.name] = false;
                    continue;
                }
            }
            {
                std::lock_guard<std::mutex> lk(gCoverMx);
                gCoverQ.push_back(std::move(blob));
                A.dirty = true;
            }
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
    // v0.35: expiry — server key first (expires_in_seconds), legacy alias, hard default 600.
    // The timer can never show a wrong value; poll re-syncs it every 2 s anyway.
    secs = (int)jsonGetInt(r.body, "expires_in_seconds", 0);
    if (secs <= 0) secs = (int)jsonGetInt(r.body, "expires_in", 0);
    if (secs <= 0) secs = 600;
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
        parseCodeCreate(r, code, secret, secs);
        for (int i = 0; code.empty() && i < 2; ++i) {   // v0.35: transient network retries
            std::this_thread::sleep_for(std::chrono::seconds(1));
            r = callCreateLinkCode(gCfg, "ps4", "PlayStation 4");
            parseCodeCreate(r, code, secret, secs);
        }
        if (code.empty()) {
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

// v0.35: one network hiccup must NOT kill the code screen — 8 consecutive failures
// (~16 s) are needed before showing "connection error", and polling keeps running
// afterwards so the screen recovers on its own. Waiting responses re-sync the
// countdown from the server clock (authoritative 10-minute lifetime).
// returns 0 pending, 1 approved, 2 expired, 3 error, 4 transient (keep waiting)
static int gPollFailStreak = 0;
static int pollCodeOnce() {
    // legacy poll first — poll_console_link_code (40_ps4_and_deals.sql)
    RpcResult old = callPollLinkCode(gCfg, A.code, A.pollSecret);
    if (old.ok) {
        gPollFailStreak = 0;
        const std::string st = jsonGetString(old.body, "status");
        if (st == "linked" || st == "approved") {
            // one-shot response: carry the session + profile back to the app
            A.sessionRef = jsonGetString(old.body, "access_token");
            A.sessionRefresh = jsonGetString(old.body, "refresh_token");
            A.linkedConsoleId = jsonGetString(old.body, "user_id");
            // v0.42: the RPC returns the account nested under "profile" — parse it
            // (was ignored entirely: blank name, 0 AC and no saved session).
            const std::string prof = jsonGetObject(old.body, "profile");
            if (!prof.empty()) {
                if (A.linkedConsoleId.empty()) A.linkedConsoleId = jsonGetString(prof, "id");
                A.linkedUsername = jsonGetString(prof, "username");
                A.linkedDisplayName = jsonGetString(prof, "display_name");
                A.linkedAvatar = jsonGetString(prof, "avatar_url");
            }
            A.exchangeId.clear();
            return 1;
        }
        if (st == "expired") return 2;
        if (st == "waiting" || st == "pending") {   // "ok":true while waiting — NOT linked
            const long s = jsonGetInt(old.body, "expires_in_seconds", -1);
            if (s > 0 && s < 3600) A.codeSecs = (int)s; // v0.35: re-sync from server
            return 0;
        }
        return 3;
    }
    // fallback: console_code_* RPCs (newer schema)
    RpcResult r = callConsoleCodeConsume(gCfg, A.code, A.pollSecret);
    if (r.ok) {
        gPollFailStreak = 0;
        const std::string st = jsonGetString(r.body, "status");
        A.exchangeId = jsonGetString(r.body, "exchange_id");
        A.linkedConsoleId = jsonGetString(r.body, "linked_console_id");
        A.sessionRef = jsonGetString(r.body, "session_ref");
        if (A.sessionRefresh.empty()) A.sessionRefresh = jsonGetString(r.body, "refresh_token");
        // v0.42: also try user_id + nested profile on this schema
        if (A.linkedConsoleId.empty()) A.linkedConsoleId = jsonGetString(r.body, "user_id");
        {
            const std::string prof = jsonGetObject(r.body, "profile");
            if (!prof.empty()) {
                if (A.linkedConsoleId.empty()) A.linkedConsoleId = jsonGetString(prof, "id");
                if (A.linkedUsername.empty()) A.linkedUsername = jsonGetString(prof, "username");
                if (A.linkedDisplayName.empty()) A.linkedDisplayName = jsonGetString(prof, "display_name");
                if (A.linkedAvatar.empty()) A.linkedAvatar = jsonGetString(prof, "avatar_url");
            }
        }
        if (st == "approved" || jsonGetBool(r.body, "approved", false) || jsonGetBool(r.body, "claimed", false))
            return 1;
        if (st == "expired") return 2;
        if (st == "pending" || st == "waiting" || r.rpcOk) return 0;
        return 3;
    }
    if (++gPollFailStreak >= 8) return 3;
    return 4; // transient network failure — keep waiting
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
        // the complete/consume response may carry the linked console/user id
        if (!A.linkedConsoleId.empty()) gSession.userId = A.linkedConsoleId;
        // v0.42: seed the profile captured from the poll response (was discarded)
        if (gSession.username.empty()) gSession.username = A.linkedUsername;
        if (gSession.displayName.empty()) gSession.displayName = A.linkedDisplayName;
        if (gSession.avatarUrl.empty()) gSession.avatarUrl = A.linkedAvatar;   // v0.48
        // v0.42: SAVE NOW — a slow profile fetch must never lose the session
        // (this was the bug: «كأنني لم أسجّل» after every launch).
        saveLinkSession(gSession);
    }
    applyAccountFromSession();
    loadWalletHistory();
    {
        // v0.42: if the profile fetch failed but the poll delivered the account,
        // build it from what we HAVE so the UI is never blank.
        if (!account().signedIn && (!gSession.username.empty() || !gSession.displayName.empty())) {
            Account a;
            a.signedIn = true;
            a.displayName = gSession.displayName.empty() ? gSession.username : gSession.displayName;
            a.username = gSession.username;
            a.avatar = gSession.avatarUrl;   // v0.48
            a.plan = tierFromPlan(gSession.plan);
            a.acBalance = (int)gSession.acBalance;
            for (const AddonItem& it : catalog())
                if (it.installed) a.installed += 1;
            setAccount(a);
        }
    }
    {
        std::lock_guard<std::mutex> lk(gSessionMx);
        if (account().signedIn) {
            gSession.displayName = account().displayName;
            gSession.username = account().username;
            gSession.avatarUrl = account().avatar;   // v0.48
            gSession.plan = account().plan == Tier::Legendary ? "legendary"
                            : account().plan == Tier::Epic ? "epic" : "free";
            gSession.acBalance = account().acBalance;
            saveLinkSession(gSession);
        }
    }
}

static void hookSignOut() {
    // v0.61 («تسجيل الخروج لا يعمل» + «ويعلق»): the unlink RPC ran on the UI thread and
    // froze the whole app on a slow/offline network. Local clear is INSTANT — the server
    // unlink is fire-and-forget in the background.
    {
        const std::string tok = gSession.accessToken;
        std::thread([tok]() { callConsoleUnlink(gCfg, tok); }).detach();
    }
    gSessGen.fetch_add(1);   // v0.62: in-flight profile refreshes must not re-apply the account
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
    std::thread([it, lang]() {
        // v0.62 (user spec): the game must be RUNNING first — no staging / temp storage.
        if (mountedMojangDir().empty()) {
            ui_sfx(Sfx::Error);
            toast(tr("need_game_first", lang), 4200, 2);   // info: guidance, not a failure
            A.dl = (int)DemoState::Idle;
            A.busyDownload = false;
            A.dirty = true;
            return;
        }
#ifdef AS_BUILD_2121
        DownloadGrant g = localGrantFrom(it.title, it.isWorld ? "world" : "addon", it.ps4RawUrl, it.packTargetsRaw);
#else
        DownloadGrant g = callRecordContentDownload(gCfg, it.id, gSession.accessToken);
#endif
        if (!g.ok || g.ps4RawUrl.empty()) {
            ui_sfx(Sfx::Error);
            toast(netErrText(g.message, lang), 4200, 1);
            A.dl = (int)DemoState::Idle;
            A.busyDownload = false;
            A.dirty = true;
            return;
        }
        // v0.49 (the real install bug): an addon ships TWO packs (behavior +
        // resource) in pack_targets — the old code downloaded ONE file only, so
        // the addon could never work in-game. Download + install EVERY pack,
        // each into its own category and unique dest_folder.
        const std::string displayName = it.title;
        std::vector<PackTarget> packs = g.packs;
        if (packs.empty()) {
            PackTarget one;
            one.ps4RawUrl = g.ps4RawUrl;
            one.target = it.isWorld ? "minecraftWorlds" : "resource_packs";
            one.destFolder = displayName;
            packs.push_back(one);
        }
        bool allOk = true;
        bool anyQueued = false;
        CopyResult r;
        const int npk = static_cast<int>(packs.size());
        for (int idx = 0; idx < npk; ++idx) {
            const PackTarget& pk = packs[static_cast<std::size_t>(idx)];
            if (pk.ps4RawUrl.empty()) continue;
            const std::string rawPath = std::string(kStageDir) + "/" + basenameOf(pk.ps4RawUrl);
            // v0.64: flaky networks (0x80431068 = connection/timeout) must not kill the
            // download — three attempts with a short pause between them.
            DownloadResult dr;
            for (int attempt = 0; attempt < 3; ++attempt) {
                dr = downloadToFile(pk.ps4RawUrl, rawPath, [idx, npk](int p) {
                    A.dlPct = (idx * 100 + p) / std::max(1, npk);
                    A.dirty = true;
                });
                if (dr.ok) break;
                std::this_thread::sleep_for(std::chrono::milliseconds(1500));
            }
            if (!dr.ok) {
                allOk = false;
                r.message = dr.message;   // raw text kept for the diag only
                break;
            }
            A.dl = (int)DemoState::Installing;
            A.dlPct = ((idx + 1) * 100) / std::max(1, npk);
            A.dirty = true;
            const std::string folder = pk.destFolder.empty() ? displayName : pk.destFolder;
            const std::string category = !pk.target.empty() ? pk.target
                                         : (it.isWorld ? "minecraftWorlds" : "resource_packs");
            r = installOrQueueAddon(
                rawPath, folder, kStageDir, "", category, lang,
                [](InstallPhase phase, int pct) {
                    if (phase == InstallPhase::Installing) {
                        A.dl = (int)DemoState::Installing;
                        A.dirty = true;
                    }
                },
                false);   // v0.62: auto-launch cancelled («نلغي التشغيل التلقائي»)
            if (!r.ok) { allOk = false; break; }
            if (r.queuedForMinecraft) anyQueued = true;
        }
        r.queuedForMinecraft = anyQueued;
        r.ok = allOk;
        if (r.ok && r.queuedForMinecraft) {
            // v0.41: HONEST status — staged only, NOT inside the game yet.
            A.dl = (int)DemoState::Queued;
            ui_sfx(Sfx::DownloadDone);
            toast(formatQueuedMessage(lang));
            A.detailIdx = findItem(it.id);
        } else if (r.ok) {
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
            toast(netErrText(r.message, lang), 4200, 1);
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
#ifndef AS_BUILD_2121
    if (!account().signedIn) {
        A.askSignIn = true;
        A.askFocus = 0;
        A.detailIdx = catIdx;
        A.dirty = true;
        return;
    }
#endif
    A.detailIdx = catIdx;
    gDetailId = catalog()[(std::size_t)catIdx].id;
    {
        // v0.51: if this item is staged (its .raw is downloaded and waiting for
        // Minecraft), show the TRUE «بانتظار Minecraft» state instead of a plain
        // «تحميل» button that would download everything again.
        const AddonItem& itt = catalog()[(std::size_t)catIdx];
        bool staged = false;
        std::string j = itt.packTargetsRaw;
        std::size_t pos = 0;
        while (!staged && (pos = j.find("\"raw_file\"", pos)) != std::string::npos) {
            const std::size_t c = j.find(':', pos);
            const std::size_t q1 = (c == std::string::npos) ? std::string::npos : j.find('"', c + 1);
            const std::size_t q2 = (q1 == std::string::npos) ? std::string::npos : j.find('"', q1 + 1);
            if (q2 == std::string::npos) break;
            const std::string rawName = j.substr(q1 + 1, q2 - q1 - 1);
            struct stat stt {};
            if (::stat((std::string(kStageDir) + "/" + rawName + ".pending").c_str(), &stt) == 0)
                staged = true;
            pos = q2 + 1;
        }
        if (!staged && !itt.ps4RawUrl.empty()) {
            struct stat stt {};
            if (::stat((std::string(kStageDir) + "/" + basenameOf(itt.ps4RawUrl) + ".pending").c_str(), &stt) == 0)
                staged = true;
        }
        A.dl = itt.installed ? (int)DemoState::Installed
             : (staged ? (int)DemoState::Queued : (int)DemoState::Idle);
    }
    pushScr(Scr::Detail);
    {   // v0.36: REAL view counter (record_content_view RPC) — fire and forget
        const std::string body = std::string("{\"p_item_id\":\"") + gDetailId + "\"}";
        std::thread([body]() { supabaseRpc(gCfg, "record_content_view", body); }).detach();
    }
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
        case Scr::Home: {
            v = viewItems(View::Home, A.filter);  first = scrollOf(View::Home);  idx = A.focus - 12;
            const int nCards = (int)std::min<std::size_t>(10, v.size());
            if (idx < 0 || idx >= nCards) return -1;   // v0.65: the "View more" tile is not an item
            break;
        }
        case Scr::Deals:  v = viewItems(View::Deals, A.filter); first = scrollOf(View::Deals); idx = A.focus - 5; break;
        case Scr::Store:  v = viewItems(View::Store, A.filter); first = scrollOf(View::Store); idx = A.focus; break;
        case Scr::Search: v = viewItems(View::Search, A.filter, A.query); first = scrollOf(View::Search); idx = A.focus - 4; break;
        case Scr::Mine:   v = viewItems(View::Mine, 0);  first = scrollOf(View::Mine);  idx = A.focus; break;
        default: return -1;
    }
    const int k = first + idx;
    return (idx >= 0 && k < (int)v.size()) ? v[(std::size_t)k] : -1;
}

static void clampScroll() {
    if (A.scr == Scr::Home) return;   // v0.65: the strip scroll is owned by moveFocus (one-tile scroll + wrap)
    // v0.58: window = ONE full row (5) — the half-peeked row scrolls fully into view on Down.
    // v0.61: windows are ROW-ALIGNED — the old clamp could start mid-row (first = idx - win + 1)
    // which visually shuffled the cards («البطاقات عشوائية»). Mine = 7-row list (cols 1).
    // v0.63: window = the VISIBLE 2 rows (10) — focus walks the visible cards without jumps.
    int win = 10, idx = -1, cols = 5;
    View view = View::Home;
    if (A.scr == Scr::Home) { idx = A.focus - 12; view = View::Home; }
    else if (A.scr == Scr::Deals) { idx = A.focus - 5; view = View::Deals; }
    else if (A.scr == Scr::Store) { idx = A.focus; view = View::Store; }
    else if (A.scr == Scr::Search) { idx = A.focus - 4; view = View::Search; win = 5; }  // single-row render
    else if (A.scr == Scr::Mine)   { idx = A.focus;  view = View::Mine;  win = 7; cols = 1; }   // v0.65c: rows 0..N-1 (no check button)
    if (idx < 0) return;
    int first = (std::max(0, scrollOf(view)) / cols) * cols;
    if (idx < first) first = (idx / cols) * cols;
    else if (idx > first + win - 1) first = ((idx - win + 1 + cols - 1) / cols) * cols;
    if (first < 0) first = 0;
    if (first != scrollOf(view)) setScroll(view, first);
}

// v0.36 grid navigation: the focused card walks the WHOLE list with wrap-around («التقليب عبر
// البطاقات» — never bounces back mid-list), and the visible window follows it (row-aligned).
// v0.63 («التنقل بالبطاقات غير صحيح»): the window is the VISIBLE 2 rows — Left/Right walk to
// the neighbouring card with NO scroll-jump (the old 1-row window teleported the highlight to
// the far side on every row change), and vertical wrap keeps the COLUMN (down at the end goes
// to the top of the same column — the old mod-wrap landed mid-list at a random card).
static void moveGridCard(View view, int fb, int cols, int win, int dx, int dy) {
    const std::vector<int> v = viewItems(view, A.filter);
    const int n = (int)v.size();
    if (n == 0) return;
    int first = scrollOf(view);
    int abs = first + (A.focus - fb);
    if (abs < 0) abs = 0;
    if (abs >= n) abs = n - 1;
    if (dx != 0) {
        abs += dx;
        if (abs < 0) abs += n;
        if (abs >= n) abs -= n;
    }
    if (dy != 0) {
        const int rows = (n + cols - 1) / cols;
        const int col = abs % cols;
        int row = abs / cols + dy;
        while (row < 0) row += rows;
        while (row >= rows) row -= rows;
        abs = std::min(row * cols + col, n - 1);
    }
    // minimal row-aligned scroll: the focused row becomes the full top row (peek below)
    if (abs < first || abs >= first + win) first = (abs / cols) * cols;
    if (first < 0) first = 0;
    if (first != scrollOf(view)) setScroll(view, first);
    A.focus = fb + (abs - first);
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
            // v0.65 («نفس ذا»): hero 0-1 | chips 5-11 | ONE strip 12.. = the 10 newest
            // cards + the gold "View more" tile at the end («شريط واحد يعرض اخر عشر بطاقات»).
            const std::vector<int> v = viewItems(View::Home, A.filter);
            const int nTiles = (int)std::min<std::size_t>(10, v.size()) + 1;
            if (A.focus >= 12 && A.focus - 12 >= nTiles) A.focus = 12 + std::max(0, nTiles - 1);
            if (A.focus <= 1) {
                if (dy > 0) A.focus = 5 + std::min(A.filter, 6);
                else A.focus = clampInto(A.focus + dx, 0, 1);
            } else if (A.focus <= 11) {
                if (dy > 0) A.focus = 12;
                else if (dy < 0) A.focus = clampInto(A.hero, 0, 1);
                else {
                    int f = A.focus - 5 + dx;
                    if (f < 0) f = 6;
                    if (f > 6) f = 0;
                    A.focus = 5 + f;
                }
            } else {
                if (dy < 0) A.focus = 5 + std::min(A.filter, 6);
                else if (dx != 0) {
                    // v0.65: strip walk with wrap-around. 4 full card slots scroll one tile
                    // at a time; the "View more" rectangle is the last focus tile (pinned at
                    // the row end after the peek card — «بس المستطيل العرض بعد آخر بطاقة»).
                    const int nCards = (int)std::min<std::size_t>(10, v.size());
                    int first = scrollOf(View::Home);
                    int tt = (A.focus - 12 >= nCards) ? nCards : first + (A.focus - 12);
                    if (tt < 0) tt = 0;
                    if (tt >= nTiles) tt = nTiles - 1;
                    tt += dx;
                    if (tt < 0) tt += nTiles;
                    if (tt >= nTiles) tt -= nTiles;
                    if (tt >= nCards) {
                        A.focus = 12 + nCards;   // the View-more tile (window unchanged)
                    } else {
                        if (tt < first) first = tt;
                        else if (tt > first + 3) first = tt - 3;
                        if (first < 0) first = 0;
                        if (first > tt) first = tt;
                        if (first != scrollOf(View::Home)) setScroll(View::Home, first);
                        A.focus = 12 + (tt - first);
                    }
                }
            }
            break;
        }
        case Scr::Deals: {
            const std::vector<int> v = viewItems(View::Deals, A.filter);
            const int gridN = (int)std::min<std::size_t>(10, v.size());
            if (A.focus <= 4) {
                if (dy > 0 && gridN > 0) A.focus = 5;
                else {
                    int f = A.focus + dx;
                    if (f < 0) f = 4;
                    if (f > 4) f = 0;
                    A.focus = f;
                }
            } else {
                if (v.empty()) A.focus = std::min(A.filter, 4);
                else if (dy < 0 && A.focus < 10 && scrollOf(View::Deals) == 0) A.focus = std::min(A.filter, 4);
                else moveGridCard(View::Deals, 5, 5, 10, dx, dy);
            }
            break;
        }
        case Scr::Store: {
            // v0.36: grid 0-9 (5-wide x2 rows) | banner 10 | categories 11-17 | sort pill 18
            const std::vector<int> v = viewItems(View::Store, A.filter);
            const int gridN = (int)std::min<std::size_t>(10, v.size());
            if (A.focus <= 9) {
                if (v.empty()) A.focus = 10;
                else if (dy < 0 && A.focus < 5 && scrollOf(View::Store) == 0) A.focus = 10;
                else if (dy > 0 && A.focus >= 5 && scrollOf(View::Store) + 10 >= (int)v.size()) A.focus = 10;
                else moveGridCard(View::Store, 0, 5, 10, dx, dy);
            } else if (A.focus == 10) {
                if (dy > 0) A.focus = 11 + std::min(A.filter, 6);
                else if (dy < 0) A.focus = gridN > 0 ? 9 : 11 + std::min(A.filter, 6);
            } else if (A.focus <= 17) {
                if (dy < 0) A.focus = 10;
                else if (dy > 0) A.focus = 18;
                else {
                    int f = A.focus - 11 + dx;
                    if (f < 0) f = 6;
                    if (f > 6) f = 0;
                    A.focus = 11 + f;
                }
            } else {
                if (dy < 0) A.focus = 11 + std::min(A.filter, 6);
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
                // v0.63: the result row scrolls like every grid (results 6+ were UNREACHABLE —
                // the old code wrapped inside the first five only).
                if (dy < 0 && scrollOf(View::Search) == 0) A.focus = 1 + std::min(A.filter, 2);
                else moveGridCard(View::Search, 4, 5, 5, dx, dy);
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
            const int rows = 5;   // v0.62: auto-launch row removed («نلغي التشغيل التلقائي»)
            if (A.subFocus >= 0) {
                const int n = A.focus == 0 ? 8 : 2, cols = 2;
                if (dx > 0) A.subFocus = std::min(n - 1, A.subFocus + 1);
                else if (dx < 0) A.subFocus = std::max(0, A.subFocus - 1);
                else if (dy > 0 && A.focus == 0) A.subFocus = std::min(n - 1, A.subFocus + cols);
                else if (dy < 0 && A.focus == 0) A.subFocus = std::max(0, A.subFocus - cols);
                // v0.54: the side rows NEVER get stuck — stepping past a
                // sub-menu edge returns to the row list (Up/Down keep working).
                if (A.focus == 0 && dy < 0 && A.subFocus < cols) A.subFocus = -1;
                else if (A.focus == 0 && dy > 0 && A.subFocus >= n - cols) A.subFocus = -1;
                else if (A.focus != 0 && dy != 0) A.subFocus = -1;
            } else {
                A.focus = clampInto(A.focus + dy, 0, rows - 1);
            }
            break;
        }
        case Scr::Mine: {
            // v0.65c: 0..N-1 = list rows (vertical) — the check button is gone
            const int n = (int)viewItems(View::Mine, 0).size();
            if (n == 0) { A.focus = 0; break; }
            A.focus = clampInto(A.focus + dy, 0, n - 1);
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
    if (A.focus == 4) {
        // v0.62: sign-out row is ALWAYS here and ALWAYS works (was focus 5 + gated on
        // account().signedIn — a ghost-guest session made the button do nothing)
        A.confirmSignOut = true;
        A.confirmFocus = 0;   // v0.62: «تسجيل الخروج» pre-selected (was Cancel -> looked broken)
    }
}

// v0.65c: gMineCheckReq removed with the «Check for updates» button

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
    // ---- v0.54: the sidebar rail cycles on EVERY screen (was missing on
    // Settings/Detail/Search/SignIn — "at Settings the sidebar does nothing").
    // L1/R1 always work; L2/R2 join in everywhere except Home (hero slides).
    {
        const bool shoulder = (b == Btn::L1 || b == Btn::R1) ||
                              ((b == Btn::L2 || b == Btn::R2) && A.scr != Scr::Home);
        if (shoulder) {
            static const Scr order[7] = {Scr::Home, Scr::Deals, Scr::Store, Scr::Mine,
                                         Scr::Plans, Scr::Profile, Scr::Settings};
            int i = 0;
            while (i < 7 && order[i] != A.scr) ++i;
            go(order[(i + (b == Btn::R1 || b == Btn::R2 ? 1 : 6)) % 7]);
            return;
        }
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
                // AC unlock (v7 payment rules) — v0.61: BACKGROUND thread (was on the UI
                // thread: the app froze for the whole round-trip «بطء استجابة» + «ويعلق»).
                static std::atomic<bool> sAcBusy{false};
                if (!sAcBusy.exchange(true)) {
                    const std::string itemId = it.id;
                    const std::string langU = A.lang;
                    std::thread([itemId, langU]() {
                        RpcResult r = callAcUnlock(gCfg, itemId, gSession.accessToken);
                        if (r.ok && (r.rpcOk || jsonGetBool(r.body, "ok", true))) {
                            ui_sfx(Sfx::Purchase);
                            applyAccountFromSession();   // balance drops in the same moment
                            loadWalletHistory();
                            const int gi = findItem(itemId);
                            if (gi >= 0) {
                                AddonItem u = catalog()[(std::size_t)gi];
                                u.unlocked = true;
                                upsertCatalogItem(u);
                                A.detailIdx = findItem(u.id);
                                A.focus = 0;
                            }
                        } else {
                            ui_sfx(Sfx::Error);
                            if (r.statusCode == 401) {
                                // v0.51: the session expired — ask to link again instead
                                // of a misleading "not enough AC" message.
                                A.notice = tr("sign_in_continue", langU);
                                gSession.accessToken.clear();
                                gSession.valid = false;
                                startSignIn();
                            } else {
                                A.notice = tr("err_not_enough_ac", langU);
                            }
                        }
                        sAcBusy = false;
                        A.dirty = true;
                    }).detach();
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
                // v0.65c: the «Check for updates» button is gone — rows start at focus 0
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
                if (A.scr == Scr::Store && A.focus == 18) {
                    setStoreSort((storeSort() + 1) % 3);
                    setScroll(View::Store, 0);
                    ui_sfx(Sfx::Toggle);
                    break;
                }
                if (A.scr == Scr::Store && A.focus >= 11 && A.focus <= 17) {
                    A.filter = A.focus - 9;
                    setScroll(View::Store, 0);
                    break;
                }
                if (A.scr == Scr::Store && A.focus == 10) {
                    if (saleLive()) go(Scr::Deals);
                    else if (!catalog().empty()) openItem(0);
                    break;
                }
                if (A.scr == Scr::Home && A.focus >= 12) {
                    // v0.65 («واذا اختاره يشيل للمتجر»): the "View more" tile opens the Store
                    const std::vector<int> v = viewItems(View::Home, A.filter);
                    const int nCards = (int)std::min<std::size_t>(10, v.size());
                    if (scrollOf(View::Home) + (A.focus - 12) >= nCards) { go(Scr::Store); break; }
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
            break;
    }
    A.dirty = true;
}

// ---------------------------------------------------------------- drawing ---
static void drawFrame(UiEngine& e) {
    const std::string& L = A.lang;
    // v0.38: while a modal is open the page's focus rings must not be animation-replayed
    // over it (they were drawing ON TOP of the sign-in dialog). The dialog's own gleams
    // register after this block, so they keep animating normally.
    const bool modal = A.askSignIn || A.confirmSignOut;
    animBlock(modal);
    switch (A.scr) {
        case Scr::SignIn:   drawSignIn(e, A.focus, A.code, A.codeSecs, A.codeStatus, L, A.back != Scr::Home, A.qrView); break;
        case Scr::Home:     drawHome(e, A.focus, A.hero, A.filter, L); break;
        case Scr::Deals:    drawDeals(e, A.focus, A.filter, L); break;
        case Scr::Store:    drawStore(e, A.focus, A.filter, L); break;
        case Scr::Plans:    drawPlans(e, -1, L); break;
        case Scr::Profile:  drawProfile(e, A.focus, L); break;
        case Scr::Settings: drawSettings(e, A.focus, A.subFocus, A.autoLaunch, L); break;
        case Scr::Mine: drawMine(e, A.focus, L); break;
        case Scr::Search:   drawSearch(e, A.focus, A.query, A.filter, L); break;
        case Scr::Detail:   drawAddonDetail(e, A.focus, A.detailIdx, (DemoState)A.dl.load(), A.dlPct.load(), L, A.notice); break;
    }
    animBlock(false);
    if (A.askSignIn) drawSignInRequired(e, A.askFocus, L);
    if (A.confirmSignOut)
        drawConfirmDialog(e, tr("signout_q", L), tr("signout_sub", L), tr("settings_signout", L), tr("cancel", L), A.confirmFocus, L);
    if (!A.toastText.empty()) drawToast(e, A.toastText, L, A.toastKind);
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

    // v0.63: app data dirs ALWAYS exist (covers cache + .raw downloads + icons wrote
    // into /data/AddonStation/... and silently failed when the folder was missing).
    {
        const char* dirs[] = {"/data", "/data/AddonStation", "/data/AddonStation/installed",
                              "/data/AddonStation/icons", "/data/AddonStation/covers"};
        for (const char* d : dirs) ::mkdir(d, 0777);
    }
    {
        // v0.63: one-time reset of the pre-v0.63 "installed" marks — they were written
        // on QUEUED (staged) installs and said «مثبتة» for packs that never entered
        // the game. Reality re-marks real installs on the next mount.
        const std::string sentinel = std::string(kStageDir) + "/.marks_v2";
        struct stat st {};
        if (::stat(sentinel.c_str(), &st) != 0) {
            ::remove((std::string(kStageDir) + "/installed.json").c_str());
            FILE* mk = std::fopen(sentinel.c_str(), "wb");
            if (mk) std::fclose(mk);
        }
    }

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
#ifdef AS_BUILD_2121
        forceNoAccount2121();   // 2121 build: no account — downloads work anonymously
#endif
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
    setAppVersion(std::string(kAppVersion) + " (2026-11-01)");  // ختم البناء في الإعدادات ← تمييز هذه النسخة
    if (!gSession.valid) setAccount(Account{});
    setCoverRequestHandler([](const AddonItem& it) { requestCoverAsync(it); });

    // ---- v7.3 live-publish sync: phone publishes appear here within seconds ----
    std::thread([]() {
        int cycle = 0;
        for (;;) {
            std::this_thread::sleep_for(std::chrono::seconds(5));
            ++cycle;
            syncCatalogLive();
            // v0.65c: «فحص التحديثات» button removed (user request) — no manual check toast
            if (cycle % 6 == 0) loadBannerNow();       // 30s: sale banner goes live at once
            if (cycle % 60 == 0) loadPlansNow();       // 5min: plan changes
            // v0.50: LIVE currency — the AC balance moves BOTH ways in the same
            // moment (spends refresh instantly; gains from the phone/other devices
            // appear within seconds). Also keeps plan/streak state fresh.
            if (gSession.valid && gSession.accessToken.size() > 20) {
                if (cycle % 3 == 0) applyAccountFromSession();   // ~15s
                requestAvatarAsync(gSession.avatarUrl);   // v0.65e: keep the avatar image loaded
                if (cycle % 6 == 0) loadWalletHistory();         // ~30s
            }
        }
    }).detach();

    // v0.62: the staged-install syncWatcher is GONE («نلغي التخزين المؤقت») — installs are
    // direct-only while the game is mounted («شغّل ماينكرفت أولاً ثم التطبيق ينقل الإضافة»).
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
            {   // v0.63: keep the installed state honest while the game is mounted
                static long long lastRec = 0;
                if (now - lastRec >= 8000) { lastRec = now; reconcileInstalled(); }
            }
        }
        if (A.scr == Scr::SignIn && (A.codeStatus == 0 || A.codeStatus == 3)) {
            static std::time_t lastPoll = 0;
            static std::atomic<bool> pollBusy{false};
            const long long cadence = A.codeStatus == 3 ? 5 : 2;   // v0.35: slower while recovering
            if (now - lastPoll >= cadence && !pollBusy.exchange(true)) {
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
                    } else if (r == 0 && A.codeStatus == 3) {
                        A.codeStatus = 0; // v0.35: connection recovered — countdown resumes
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

        if (bakedFadeActive()) { bakedFadeReset(); A.dirty = true; }   // v0.38: banner glide ticks per frame
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
