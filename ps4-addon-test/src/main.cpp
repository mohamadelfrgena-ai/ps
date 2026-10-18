// AddonStation "pkg addon test" PS4 Mini-App — full interactive design UI
//
// Performance: the UiEngine rasterizes into its own RGBA framebuffer (no SDL
// textures) and each screen is redrawn ONLY when something changes — the frame
// is then blitted/presented like the working v0.5 app:
//     SDL_RenderPresent(renderer); SDL_UpdateWindowSurface(window);
//
// Navigation (v0.14): EVERY button has its real action — Welcome (code /
// email / guest) leads to its own screen; ONLY content cards open the addon
// install page; category/filter chips apply a filter (visible active chip);
// banner Back returns; "Shop the sale" opens Deals; L1/R1 switch tabs only
// inside the app (Home · Deals · Store · Settings) and L2/R2 change
// the Home offer; Settings rows act (language /
// auto-launch Minecraft after install / link console / sign out with confirm
// dialog), text fields open the PS4 system
// keyboard (sceImeDialog), Download installs content from the GitHub Release.
// v0.14: splash screen removed entirely (fast boot straight to Welcome) and
// ○/ESC never exit the app — they only navigate back inside it (no path to
// the PS home menu from inside the app).

#include "content_config.hpp"
#include "http_download.hpp"
#include "ime_dialog.hpp"
#include "installer.hpp"
#include "screens.hpp"
#include "supabase_client.hpp"

#include <SDL2/SDL.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr const char* kStageDir = "/data/AddonStation/installed";

const char* kLangCodes[8] = {"en", "ar", "fr", "tr", "es", "ru", "pt", "zh"};

enum class Screen {
    Welcome, CodeLink, Email, Language,
    Home, Deals, Store, Settings, Detail, Confirm,
    Search, Profile, Wallet, Plans
};

struct AppState {
    Screen screen = Screen::Welcome;   // بدون splash — الإقلاع مباشرة
    Screen prevScreen = Screen::Welcome;
    Screen tabScreen = Screen::Home;   // active L1/R1 tab
    int focus = 0;
    int langIndex = 0;
    pkgtest::DemoState demoState = pkgtest::DemoState::Idle;
    int progress = 0;
    std::string toastText;
    double toastUntil = 0.0;

    // code link
    std::string code = "K7P 4QX";
    double codeUntil = 0.0;
    bool linked = false;
    std::string pollToken;
    bool haveSession = false;
    std::string sessionName;
    std::string sessionPlan;
    long sessionAc = 0;

    // v2 screens (search / profile / wallet / plans / triangle menu)
    std::string searchQuery;
    int searchFilter = 0;
    bool menuOpen = false;
    int menuFocus = 0;

    // محتوى Supabase المرتبط (بانرات + عناصر منشورة)
    std::vector<pkgtest::BannerData> banners;
    std::vector<pkgtest::ItemData> items;
    int contentStamp = 0;
    std::string detailItemId;
    std::string detailTitle;

    // email
    std::string email;
    std::string pass;
    bool emailError = false;

    int heroIndex = 0;
    int filterIndex = 0;   // Home category chip 0..6
    int storeFilter = 0;   // Store filter chip 0..2
    int dealsFilter = 0;   // Deals chip 0..4
    bool autoLaunch = false;   // «تشغيل Minecraft تلقائياً بعد التثبيت» (يُقرأ من الإعدادات)
    bool saved = false;

    std::atomic<bool> installing{false};
};

double nowSeconds() {
    return std::chrono::duration<double>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

const char* kHomeChips[7] = {"All", "Addons", "Worlds", "Free", "Epic", "Legendary", "New"};
const char* kStoreFilters[3] = {"Addons", "Worlds", "Newest"};
const char* kDealsChips[5] = {"All", "Addons", "Worlds", "Under 100 AC", "Biggest discount"};

std::string toLowerCopy(const std::string& v) {
    std::string o;
    for (unsigned char c : v) o += static_cast<char>(std::tolower(c));
    return o;
}

int planIndexFrom(const std::string& plan) {
    if (plan == "epic") return 1;
    if (plan == "legendary") return 2;
    return 0;
}

// indices into state.items matching the search query + filter chip (All/Addons/Worlds)
std::vector<int> searchMatchIdx(const std::vector<pkgtest::ItemData>& items,
                                const std::string& query, int filterIndex) {
    std::vector<int> out;
    const std::string q = toLowerCopy(query);
    for (int i = 0; i < static_cast<int>(items.size()) && static_cast<int>(out.size()) < 5; ++i) {
        const pkgtest::ItemData& it = items[i];
        const bool isWorld = (it.contentType == "world" || it.contentType == "map");
        if (filterIndex == 1 && isWorld) continue;
        if (filterIndex == 2 && !isWorld) continue;
        if (!q.empty() && toLowerCopy(it.title).find(q) == std::string::npos) continue;
        out.push_back(i);
    }
    return out;
}

std::string makeCode() {
    static const char* alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    std::string c;
    for (int i = 0; i < 6; ++i) {
        if (i == 3) c += ' ';
        c += alphabet[std::rand() % 32];
    }
    return c;
}

std::mutex gLinkMx;   // يحمي حقول ربط الكود بين خيوط الخلفية وواجهة الرسم

int frameSignature(const AppState& s, double t) {
    double linkUntil;
    bool linkDone;
    {
        std::lock_guard<std::mutex> lk(gLinkMx);
        linkUntil = s.codeUntil;
        linkDone = s.linked;
    }
    const int codeSecs = static_cast<int>(std::max(0.0, linkUntil - t));
    int sig = static_cast<int>(s.screen) * 100000000 +
              s.focus * 1000000 +
              s.langIndex * 100000 +
              static_cast<int>(s.demoState) * 10000 +
              (s.progress >= 100 ? 99 : s.progress) * 100 +
              (linkDone ? 11 : (codeSecs % 60));
    sig += s.heroIndex * 31337 + s.filterIndex * 2711 +
           s.storeFilter * 397 + s.dealsFilter * 53 +
           (s.autoLaunch ? 31 : 0) +
           (s.menuOpen ? 101 : 0) + s.searchFilter * 37 +
           static_cast<int>(s.searchQuery.size()) * 11 +
           s.contentStamp * 7919;
    return sig;
}

} // namespace

int main(int, char**) {
    setvbuf(stdout, nullptr, _IONBF, 0);
    std::srand(static_cast<unsigned>(nowSeconds()));

    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_JOYSTICK) != 0) {
        return 1;
    }

    SDL_Window* window = SDL_CreateWindow(
        "pkg addon test",
        SDL_WINDOWPOS_UNDEFINED,
        SDL_WINDOWPOS_UNDEFINED,
        1920,
        1080,
        0);
    if (!window) {
        SDL_Quit();
        return 1;
    }

    SDL_Surface* windowSurface = SDL_GetWindowSurface(window);
    SDL_Renderer* renderer = windowSurface ? SDL_CreateSoftwareRenderer(windowSurface) : nullptr;
    if (!renderer) {
        SDL_DestroyWindow(window);
        SDL_Quit();
        return 1;
    }

    std::vector<SDL_Joystick*> joysticks;
    const int numJoy = SDL_NumJoysticks();
    for (int i = 0; i < numJoy; ++i) {
        if (SDL_Joystick* joy = SDL_JoystickOpen(i)) {
            joysticks.push_back(joy);
        }
    }
    SDL_JoystickEventState(SDL_ENABLE);

    pkgtest::UiEngine engine;
    engine.init("/app0/assets/ui.pak");

    AppState state;
    const double bootTime = nowSeconds();
    state.codeUntil = bootTime + 582.0;   // 9:42 like the design
    state.autoLaunch = pkgtest::loadAutoLaunchSetting();
    const pkgtest::SupabaseConfig supaCfg = pkgtest::loadSupabaseConfig();
    {
        const pkgtest::LinkSession sess = pkgtest::loadLinkSession();
        if (sess.valid) {
            state.haveSession = true;
            state.sessionName = sess.displayName.empty() ? sess.username : sess.displayName;
            state.sessionPlan = sess.plan;
            state.sessionAc = sess.acBalance;
        }
    }

    auto currentLang = [&state]() { return std::string(kLangCodes[state.langIndex]); };
    auto toast = [&state](const std::string& text) {
        state.toastText = text;
        state.toastUntil = nowSeconds() + 4.5;
    };

    // ---- نظام الدخول برمز (Supabase) --------------------------------------
    // كود جديد من الخادم (create_console_link_code) — مع كود تجريبي محلي
    // إذا لم يُضبط Supabase بعد في content_config.hpp أو supabase.cfg.
    auto requestNewCode = [&state, &supaCfg, &toast]() {
        {
            std::lock_guard<std::mutex> lk(gLinkMx);
            state.linked = false;
            state.pollToken.clear();
        }
        if (!supaCfg.ok()) {
            const std::string c = makeCode();
            std::lock_guard<std::mutex> lk(gLinkMx);
            state.code = c;
            state.codeUntil = nowSeconds() + 582.0;
            return;
        }
        {
            std::lock_guard<std::mutex> lk(gLinkMx);
            state.code = "------";
            state.codeUntil = nowSeconds() + 30.0;
        }
        std::thread([&state, &supaCfg, &toast]() {
            const pkgtest::RpcResult r =
                pkgtest::callCreateLinkCode(supaCfg, "ps4", "PlayStation 4");
            if (r.ok && r.rpcOk) {
                std::string code = pkgtest::jsonGetString(r.body, "formatted_code");
                if (code.empty()) code = pkgtest::jsonGetString(r.body, "code");
                const std::string token = pkgtest::jsonGetString(r.body, "poll_token");
                const double secs = static_cast<double>(
                    pkgtest::jsonGetInt(r.body, "expires_in_seconds", 600));
                std::lock_guard<std::mutex> lk(gLinkMx);
                state.code = code;
                state.pollToken = token;
                state.codeUntil = nowSeconds() + secs;
            } else {
                const std::string c = makeCode();
                {
                    std::lock_guard<std::mutex> lk(gLinkMx);
                    state.code = c;
                    state.codeUntil = nowSeconds() + 582.0;
                    state.pollToken.clear();
                }
                toast("Link service unavailable \xE2\x80\x94 demo code");
            }
        }).detach();
    };

    // استطلاع حالة الكود كل 3 ثوانٍ (poll_console_link_code)
    std::thread linkPoller([&state, &supaCfg, &toast, &currentLang]() {
        while (true) {
            std::this_thread::sleep_for(std::chrono::seconds(3));
            std::string code, token;
            {
                std::lock_guard<std::mutex> lk(gLinkMx);
                if (state.linked || state.pollToken.empty()) continue;
                code = state.code;
                token = state.pollToken;
            }
            const pkgtest::RpcResult r = pkgtest::callPollLinkCode(supaCfg, code, token);
            if (!r.ok || !r.rpcOk) continue;
            const std::string status = pkgtest::jsonGetString(r.body, "status");
            if (status == "waiting") {
                const double secs = static_cast<double>(
                    pkgtest::jsonGetInt(r.body, "expires_in_seconds", 600));
                std::lock_guard<std::mutex> lk(gLinkMx);
                state.codeUntil = nowSeconds() + secs;
            } else if (status == "linked") {
                pkgtest::LinkSession s;
                s.valid = true;
                s.userId = pkgtest::jsonGetString(r.body, "user_id");
                s.username = pkgtest::jsonGetString(r.body, "username");
                s.displayName = pkgtest::jsonGetString(r.body, "display_name");
                s.plan = pkgtest::jsonGetString(r.body, "plan");
                s.acBalance = pkgtest::jsonGetInt(r.body, "ac_balance", 0);
                s.accessToken = pkgtest::jsonGetString(r.body, "access_token");
                s.refreshToken = pkgtest::jsonGetString(r.body, "refresh_token");
                pkgtest::saveLinkSession(s);
                const std::string name =
                    s.displayName.empty() ? s.username : s.displayName;
                {
                    std::lock_guard<std::mutex> lk(gLinkMx);
                    state.linked = true;
                    state.haveSession = true;
                    state.sessionName = name;
                    state.sessionPlan = s.plan;
                    state.sessionAc = s.acBalance;
                }
                toast(pkgtest::tr("link_linked", currentLang()));
            } else if (status == "expired") {
                {
                    std::lock_guard<std::mutex> lk(gLinkMx);
                    state.codeUntil = nowSeconds();
                    state.pollToken.clear();
                }
                toast(pkgtest::tr("link_expired", currentLang()));
            } else if (status == "consumed") {
                std::lock_guard<std::mutex> lk(gLinkMx);
                state.pollToken.clear();
            }
        }
    });
    linkPoller.detach();

    // تحميل البانرات والمحتوى المنشور من Supabase (اختياري — صامت عند غياب التهيئة)
    std::thread contentLoader([&state, &supaCfg]() {
        if (!supaCfg.ok()) return;
        const std::vector<pkgtest::BannerData> banners = pkgtest::fetchStoreBanners(supaCfg);
        const std::vector<pkgtest::ItemData> items = pkgtest::fetchContentItems(supaCfg, 20);
        if (banners.empty() && items.empty()) return;
        std::lock_guard<std::mutex> lk(gLinkMx);
        state.banners = banners;
        state.items = items;
        ++state.contentStamp;
    });
    contentLoader.detach();
    auto go = [&state](Screen s) {
        if (state.screen != s) state.prevScreen = state.screen;
        state.screen = s;
        state.focus = 0;
    };

    // Background auto-sync: delivers staged installs the moment com.mojang is
    // mounted (game open/suspended) — and flips the button to "Installed ✓"
    // with the completion toast when the deferred install finishes.
    std::thread syncWatcher([&state, &currentLang, &toast]() {
        while (true) {
            if (!state.installing.load()) {
                const pkgtest::CopyResult res =
                    pkgtest::syncPendingAddonsIfMounted(kStageDir, "", currentLang());
                if (res.ok && state.demoState == pkgtest::DemoState::Installing) {
                    state.demoState = pkgtest::DemoState::Installed;
                    state.progress = 100;
                    toast(pkgtest::formatEnjoyNotification(
                        pkgtest::brandTitle(res.lastAddonName.empty() ? "the addon" : res.lastAddonName),
                        currentLang()));
                }
            }
            std::this_thread::sleep_for(std::chrono::seconds(2));
        }
    });
    syncWatcher.detach();

    // اختيار عنصر السيرفر للصفحة التفصيلية (بطاقات الحصريات/المتجر/العروض)
    auto selectDetail = [&state](int idx, bool exclusive) {
        std::lock_guard<std::mutex> lk(gLinkMx);
        state.detailItemId.clear();
        state.detailTitle.clear();
        int n = 0;
        for (const pkgtest::ItemData& it : state.items) {
            if (exclusive && !it.isExclusive) continue;
            if (n == idx) {
                state.detailItemId = it.id;
                state.detailTitle = it.title;
                break;
            }
            ++n;
        }
    };
    auto selectDetailAbs = [&state](int absIdx) {
        std::lock_guard<std::mutex> lk(gLinkMx);
        state.detailItemId.clear();
        state.detailTitle.clear();
        if (absIdx >= 0 && absIdx < static_cast<int>(state.items.size())) {
            state.detailItemId = state.items[absIdx].id;
            state.detailTitle = state.items[absIdx].title;
        }
    };

    // Download the .raw from the GitHub Release, then install it (dual-mode)
    auto triggerInstall = [&state, &currentLang, &toast, &supaCfg]() {
        if (state.installing.load()) return;
        std::string itemId, itemTitle;
        {
            std::lock_guard<std::mutex> lk(gLinkMx);
            itemId = state.detailItemId;
            itemTitle = state.detailTitle;
        }
        const pkgtest::LinkSession sess = pkgtest::loadLinkSession();
        const bool useServer = !itemId.empty() && supaCfg.ok() && sess.valid &&
                               !sess.accessToken.empty();
        if (!useServer &&
            (std::string(pkgtest::content::kAddonFolderName).empty() ||
             std::string(pkgtest::content::kDownloadUrl).empty())) {
            state.demoState = pkgtest::DemoState::Idle;
            state.progress = 0;
            toast("No content configured yet — add it in content_config.hpp");
            return;
        }
        state.installing.store(true);
        state.demoState = pkgtest::DemoState::Downloading;
        state.progress = 0;

        std::thread([&, lang = currentLang(), useServer, itemId, itemTitle, sess]() {
            if (useServer) {
                // المسار الرسمي: طلب التحميل المسجَّل ثم تنزيل كل حزمة لوجهتها
                const pkgtest::DownloadGrant grant =
                    pkgtest::callRecordContentDownload(supaCfg, itemId, sess.accessToken);
                if (!grant.ok) {
                    state.demoState = pkgtest::DemoState::Idle;
                    state.progress = 0;
                    toast(pkgtest::tr("link_failed", lang));
                    state.installing.store(false);
                    return;
                }
                std::vector<pkgtest::PackTarget> packs = grant.packs;
                if (packs.empty()) {
                    pkgtest::PackTarget t;
                    t.target = "resource_packs";
                    t.destFolder = itemTitle.empty() ? "addon" : itemTitle;
                    t.ps4RawUrl = grant.ps4RawUrl;
                    packs.push_back(t);
                }
                const int total = static_cast<int>(packs.size());
                pkgtest::CopyResult lastRes;
                for (int i = 0; i < total; ++i) {
                    const pkgtest::PackTarget& pk = packs[static_cast<std::size_t>(i)];
                    const std::string rawName = pk.rawFile.empty()
                        ? (pk.destFolder.empty() ? ("pack" + std::to_string(i)) : pk.destFolder)
                        : pk.rawFile;
                    const std::string rawPath = std::string(kStageDir) + "/" + rawName + ".raw";
                    const std::string url = pk.ps4RawUrl.empty() ? grant.ps4RawUrl : pk.ps4RawUrl;
                    const int base = i * 100 / total;
                    const pkgtest::DownloadResult dl = pkgtest::downloadToFile(
                        url, rawPath,
                        [&](int percent) {
                            state.demoState = pkgtest::DemoState::Downloading;
                            state.progress = std::min(99, base + percent / total);
                        });
                    if (!dl.ok) {
                        state.demoState = pkgtest::DemoState::Idle;
                        state.progress = 0;
                        toast(pkgtest::tr("link_failed", lang));
                        state.installing.store(false);
                        return;
                    }
                    state.demoState = pkgtest::DemoState::Installing;
                    state.progress = 50;
                    lastRes = pkgtest::installOrQueueAddon(
                        rawPath,
                        pk.destFolder.empty() ? itemTitle : pk.destFolder,
                        kStageDir,
                        "",
                        pk.target.empty() ? "resource_packs" : pk.target,
                        lang,
                        [&](pkgtest::InstallPhase phase, int percent) {
                            if (phase == pkgtest::InstallPhase::Installing) {
                                state.demoState = pkgtest::DemoState::Installing;
                                state.progress = std::min(99, 50 + percent / 4);
                            } else if (phase == pkgtest::InstallPhase::Installed) {
                                state.demoState = pkgtest::DemoState::Installed;
                                state.progress = 100;
                            }
                        },
                        state.autoLaunch);
                    if (!lastRes.ok) {
                        state.demoState = pkgtest::DemoState::Idle;
                        state.progress = 0;
                        state.installing.store(false);
                        return;
                    }
                }
                if (lastRes.ok && lastRes.queuedForMinecraft) {
                    state.demoState = pkgtest::DemoState::Installing;
                    state.progress = 75;
                } else if (lastRes.ok) {
                    state.demoState = pkgtest::DemoState::Installed;
                    state.progress = 100;
                    state.toastText = pkgtest::formatEnjoyNotification(
                        pkgtest::brandTitle(itemTitle.empty() ? "the addon" : itemTitle), lang);
                    state.toastUntil = nowSeconds() + 6.0;
                }
                state.installing.store(false);
                return;
            }

            // المسار التجريبي: من content_config.hpp (GitHub Release مباشرة)
            const std::string rawPath =
                std::string(kStageDir) + "/" + pkgtest::content::kAddonFolderName + ".raw";

            const pkgtest::DownloadResult dl = pkgtest::downloadToFile(
                pkgtest::content::kDownloadUrl,
                rawPath,
                [&](int percent) {
                    state.demoState = pkgtest::DemoState::Downloading;
                    state.progress = percent;
                });

            if (!dl.ok) {
                state.demoState = pkgtest::DemoState::Idle;
                state.progress = 0;
                state.installing.store(false);
                return;
            }

            state.demoState = pkgtest::DemoState::Installing;
            state.progress = 10;

            const pkgtest::CopyResult res = pkgtest::installOrQueueAddon(
                rawPath,
                pkgtest::content::kAddonFolderName,
                kStageDir,
                "",
                "resource_packs",
                lang,
                [&](pkgtest::InstallPhase phase, int percent) {
                    if (phase == pkgtest::InstallPhase::Installing) {
                        state.demoState = pkgtest::DemoState::Installing;
                        state.progress = std::min(100, 10 + percent * 9 / 10);
                    } else if (phase == pkgtest::InstallPhase::Installed) {
                        state.demoState = pkgtest::DemoState::Installed;
                        state.progress = 100;
                    }
                },
                state.autoLaunch);

            if (res.ok && res.queuedForMinecraft) {
                state.demoState = pkgtest::DemoState::Installing;
                state.progress = 75;
            } else if (res.ok) {
                state.demoState = pkgtest::DemoState::Installed;
                state.progress = 100;
                state.toastText = pkgtest::formatEnjoyNotification(pkgtest::brandTitle(pkgtest::content::kAddonFolderName), lang);
                state.toastUntil = nowSeconds() + 6.0;
            } else {
                state.demoState = pkgtest::DemoState::Idle;
                state.progress = 0;
            }
            state.installing.store(false);
        }).detach();
    };

    // ---- input helpers -------------------------------------------------
    // Joystick map (as used by the working v0.5 app): 0=✕ 1=○ 2=□ 3=△ 4=L1 5=R1 6=L2 7=R2
    auto back = [&]() {
        if (state.menuOpen) { state.menuOpen = false; return; }
        switch (state.screen) {
            case Screen::Welcome: break;  // stay (guest entry screen)
            case Screen::CodeLink: go(Screen::Welcome); break;
            case Screen::Email: go(Screen::Welcome); break;
            case Screen::Language: go(state.prevScreen == Screen::Language ? Screen::Welcome : state.prevScreen); break;
            case Screen::Home:
            case Screen::Deals:
            case Screen::Store:
            case Screen::Settings:
                go(Screen::Welcome);
                break;
            case Screen::Detail: go(state.prevScreen == Screen::Detail ? state.tabScreen : state.prevScreen); break;
            case Screen::Search:
            case Screen::Profile:
            case Screen::Wallet:
            case Screen::Plans:
                go(state.prevScreen == state.screen ? Screen::Home : state.prevScreen);
                break;
            case Screen::Confirm: go(Screen::Settings); break;
        }
    };

    auto activate = [&](int cross) {
        (void)cross;
        if (state.menuOpen) {
            state.menuOpen = false;
            if (state.menuFocus == 0) go(Screen::Profile);
            else if (state.menuFocus == 1) go(Screen::Wallet);
            else if (state.menuFocus == 2) go(Screen::Plans);
            else go(Screen::Search);
            return;
        }
        switch (state.screen) {
            case Screen::Welcome:
                if (state.focus == 0) { requestNewCode(); go(Screen::CodeLink); }
                else if (state.focus == 1) go(Screen::Email);
                else { state.tabScreen = Screen::Home; go(Screen::Home); }
                break;
            case Screen::CodeLink:
                requestNewCode();
                toast(pkgtest::tr("link_new", currentLang()));
                break;
            case Screen::Email:
                if (state.focus == 0) {
                    const std::string t = pkgtest::openImeDialog("Email", state.email, 64);
                    if (!t.empty()) { state.email = t; state.emailError = false; }
                } else if (state.focus == 1) {
                    const std::string t = pkgtest::openImeDialog("Password", state.pass, 32);
                    if (!t.empty()) state.pass = t;
                } else {
                    if (state.email.empty()) { state.emailError = true; }
                    else if (!supaCfg.ok()) {
                        // بدون تهيئة: دخول تجريبي محلي
                        state.emailError = false; state.tabScreen = Screen::Home; go(Screen::Home);
                    } else {
                        // دخول حقيقي عبر Supabase Auth
                        const std::string em = state.email;
                        const std::string pw = state.pass;
                        std::thread([&state, &supaCfg, &toast, &currentLang, em, pw]() {
                            const pkgtest::AuthLogin auth =
                                pkgtest::callEmailLogin(supaCfg, em, pw);
                            if (!auth.ok) {
                                state.emailError = true;
                                toast(pkgtest::tr("login_failed", currentLang()));
                                return;
                            }
                            pkgtest::LinkSession s;
                            s.valid = true;
                            s.userId = auth.userId;
                            s.username = auth.email;
                            s.displayName = auth.displayName;
                            s.accessToken = auth.accessToken;
                            s.refreshToken = auth.refreshToken;
                            pkgtest::saveLinkSession(s);
                            {
                                std::lock_guard<std::mutex> lk(gLinkMx);
                                state.haveSession = true;
                                state.sessionName = auth.displayName;
                                state.sessionPlan = "free";
                                state.sessionAc = 0;
                            }
                            state.emailError = false;
                            state.tabScreen = Screen::Home;
                            state.screen = Screen::Home;
                            state.focus = 0;
                            toast(pkgtest::tr("login_ok", currentLang()));
                        }).detach();
                    }
                }
                break;
            case Screen::Language:
                state.langIndex = state.focus;
                go(state.prevScreen == Screen::Language ? Screen::Welcome : state.prevScreen);
                break;
            case Screen::Home:
                // banner primary -> store, banner back -> return,
                // exclusive/recommended cards -> the addon page,
                // category chips -> apply a filter (never the install page)
                if (state.focus == 0) { state.tabScreen = Screen::Store; go(Screen::Store); }
                else if (state.focus == 1) { back(); }
                else if (state.focus <= 4) { selectDetail(state.focus - 2, true); go(Screen::Detail); }
                else if (state.focus <= 11) {
                    state.filterIndex = state.focus - 5;
                    toast(std::string("Filter: ") + kHomeChips[state.filterIndex]);
                } else { selectDetail(state.focus - 12, false); go(Screen::Detail); }
                break;
            case Screen::Deals:
                if (state.focus <= 4) {
                    state.dealsFilter = state.focus;
                    toast(std::string("Filter: ") + kDealsChips[state.dealsFilter]);
                } else { selectDetail(state.focus - 5, false); go(Screen::Detail); }
                break;
            case Screen::Store:
                // cards -> the addon page, promo button -> Deals, chips -> filter
                if (state.focus <= 4) { selectDetail(state.focus, false); go(Screen::Detail); }
                else if (state.focus == 8) { state.tabScreen = Screen::Deals; go(Screen::Deals); }
                else {
                    state.storeFilter = std::min(2, std::max(0, state.focus - 9));
                    toast(std::string("Filter: ") + kStoreFilters[state.storeFilter]);
                }
                break;
            case Screen::Settings:
                if (state.focus == 0) { state.focus = state.langIndex; state.prevScreen = state.screen; state.screen = Screen::Language; }
                else if (state.focus == 1) {
                    std::lock_guard<std::mutex> lk(gLinkMx);
                    toast(state.haveSession ? ("Account: " + state.sessionName)
                                            : std::string("Account: name@example.com"));
                }
                else if (state.focus == 2) {
                    state.autoLaunch = !state.autoLaunch;
                    pkgtest::saveAutoLaunchSetting(state.autoLaunch);
                    toast(pkgtest::tr(state.autoLaunch ? "autolaunch_on" : "autolaunch_off", currentLang()));
                }
                else if (state.focus == 3) { requestNewCode(); go(Screen::CodeLink); }
                else if (state.focus == 4) toast("AddonStation PS4 v0.22 — official app");
                else go(Screen::Confirm);
                break;
            case Screen::Detail:
                triggerInstall();
                break;
            case Screen::Search: {
                if (state.focus == 0) {
                    const std::string q = pkgtest::openImeDialog("Search", state.searchQuery, 80);
                    if (!q.empty()) state.searchQuery = q;
                } else if (state.focus <= 3) {
                    state.searchFilter = state.focus - 1;
                    toast(std::string("Filter: ") + (state.searchFilter == 0 ? "All" :
                          (state.searchFilter == 1 ? "Addons" : "Worlds")));
                } else {
                    std::vector<int> hits;
                    {
                        std::lock_guard<std::mutex> lk(gLinkMx);
                        hits = searchMatchIdx(state.items, state.searchQuery, state.searchFilter);
                    }
                    const int j = state.focus - 4;
                    if (j < static_cast<int>(hits.size())) {
                        selectDetailAbs(hits[j]);
                        go(Screen::Detail);
                    }
                }
                break;
            }
            case Screen::Profile:
                if (state.focus == 0) go(Screen::Wallet);
                else if (state.focus == 1) go(Screen::Plans);
                else { state.tabScreen = Screen::Settings; go(Screen::Settings); }
                break;
            case Screen::Wallet:
            case Screen::Plans:
                break;  // view-only screens
            case Screen::Confirm:
                if (state.focus == 0) go(Screen::Settings);
                else {
                    toast("Signed out");
                    state.email.clear();
                    state.pass.clear();
                    pkgtest::clearLinkSession();
                    {
                        std::lock_guard<std::mutex> lk(gLinkMx);
                        state.haveSession = false;
                        state.sessionName.clear();
                        state.sessionPlan.clear();
                        state.sessionAc = 0;
                        state.linked = false;
                        state.pollToken.clear();
                    }
                    go(Screen::Welcome);
                }
                break;
        }
    };

    auto moveFocus = [&](int dx, int dy) {
        if (state.menuOpen) {
            if (dy != 0) state.menuFocus = (state.menuFocus + (dy > 0 ? 1 : 3)) % 4;
            return;
        }
        switch (state.screen) {
            case Screen::Welcome: {
                if (dy != 0) state.focus = (state.focus + (dy > 0 ? 1 : 2)) % 3;
                break;
            }
            case Screen::Email: {
                if (dy != 0) state.focus = std::max(0, std::min(2, state.focus + dy));
                break;
            }
            case Screen::Language: {
                if (dy != 0) state.focus = (state.focus + (dy > 0 ? 1 : 7)) % 8;
                if (dx != 0) state.focus = (state.focus + (dx > 0 ? 1 : 7)) % 8;
                break;
            }
            case Screen::Home: {
                // rows: [0-1] banner | [2-4] exclusives | [5-11] chips | [12-16] recommended
                static const int rowStart[4] = {0, 2, 5, 12};
                static const int rowLen[4] = {2, 3, 7, 5};
                int row = 0, col = state.focus;
                for (int r = 3; r >= 0; --r) {
                    if (state.focus >= rowStart[r]) { row = r; col = state.focus - rowStart[r]; break; }
                }
                if (dy != 0) {
                    row = std::max(0, std::min(3, row + dy));
                    col = std::min(col, rowLen[row] - 1);
                } else if (dx != 0) {
                    col = (col + dx + rowLen[row]) % rowLen[row];
                }
                state.focus = rowStart[row] + col;
                break;
            }
            case Screen::Store: {
                // rows top->bottom: promo button [8] | filters [9-11] | cards [0-4]
                int row = (state.focus == 8) ? 0 : (state.focus >= 9 ? 1 : 2);
                int col = (row == 1) ? state.focus - 9 : (row == 2 ? state.focus : 0);
                if (dy != 0) {
                    row = (row + (dy > 0 ? 1 : 2)) % 3;
                    col = std::min(col, row == 1 ? 2 : (row == 2 ? 4 : 0));
                } else if (dx != 0 && row != 0) {
                    const int len = (row == 1 ? 3 : 5);
                    col = (col + dx + len) % len;
                }
                state.focus = (row == 0) ? 8 : (row == 1 ? 9 + col : col);
                break;
            }
            case Screen::Deals: {
                // rows: filter chips [0-4] | card grid [5-9]
                int row = state.focus < 5 ? 0 : 1;
                int col = state.focus - (row ? 5 : 0);
                if (dy != 0) {
                    row = 1 - row;
                    col = std::min(col, 4);
                } else if (dx != 0) {
                    col = (col + dx + 5) % 5;
                }
                state.focus = row * 5 + col;
                break;
            }
            case Screen::Settings: {
                if (dy != 0) state.focus = std::max(0, std::min(5, state.focus + dy));
                break;
            }
            case Screen::Confirm: {
                if (dy != 0) state.focus = 1 - state.focus;
                break;
            }
            case Screen::Search: {
                // rows: field[0] | filter chips[1-3] | result cards[4-8]
                if (dy != 0) {
                    int row = (state.focus == 0) ? 0 : (state.focus <= 3 ? 1 : 2);
                    row = std::max(0, std::min(2, row + dy));
                    if (row == 0) state.focus = 0;
                    else if (row == 1) state.focus = 1 + std::min(2, (state.focus <= 3 ? state.focus - 1 : 0));
                    else state.focus = 4 + std::min(4, (state.focus >= 4 ? state.focus - 4 : 0));
                } else if (dx != 0 && state.focus >= 1) {
                    if (state.focus <= 3) state.focus = 1 + ((state.focus - 1 + dx + 3) % 3);
                    else state.focus = 4 + ((state.focus - 4 + dx + 5) % 5);
                }
                break;
            }
            case Screen::Profile: {
                if (dy != 0) state.focus = std::max(0, std::min(2, state.focus + dy));
                break;
            }
            case Screen::Wallet: {
                if (dy != 0) state.focus = std::max(0, std::min(5, state.focus + dy));
                break;
            }
            case Screen::Plans: {
                if (dy != 0) state.focus = std::max(0, std::min(2, state.focus + dy));
                break;
            }
            case Screen::Detail:
            case Screen::CodeLink:
                break;
        }
    };

    auto switchTab = [&](int dir) {
        if (state.menuOpen) return;
        // L1/R1 tabs are ONLY for the app area (Home Deals Store Settings)
        switch (state.screen) {
            case Screen::Home:
            case Screen::Deals:
            case Screen::Store:
            case Screen::Settings:
            case Screen::Detail:
                break;
            default:
                return;
        }
        static const Screen tabs[4] = {Screen::Home, Screen::Deals, Screen::Store, Screen::Settings};
        int idx = 0;
        if (state.screen == Screen::Deals) idx = 1;
        else if (state.screen == Screen::Store || state.screen == Screen::Detail) idx = 2;
        else if (state.screen == Screen::Settings) idx = 3;
        idx = (idx + dir + 4) % 4;
        state.tabScreen = tabs[idx];
        go(tabs[idx]);
    };

    bool running = true;
    SDL_Event ev;
    int lastSig = -1;
    bool lastToastVisible = false;
    bool frameDirty = true;   // first frame must present
    long long frameCount = 0;

    while (running) {
        const double t = nowSeconds();
        const std::string lang = currentLang();
        bool stateChanged = false;
        bool backRequested = false;

        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_QUIT) {
                backRequested = true;   // (○ على السوني) — لا خروج من التطبيق
            } else if (ev.type == SDL_JOYHATMOTION) {
                const Uint8 hat = ev.jhat.value;
                if (hat & SDL_HAT_UP) moveFocus(0, -1);
                if (hat & SDL_HAT_DOWN) moveFocus(0, 1);
                if (hat & SDL_HAT_LEFT) moveFocus(-1, 0);
                if (hat & SDL_HAT_RIGHT) moveFocus(1, 0);
                stateChanged = true;
            } else if (ev.type == SDL_JOYBUTTONDOWN) {
                const Uint8 b = ev.jbutton.button;
                if (b == 0) { activate(1); stateChanged = true; }
                else if (b == 1) { backRequested = true; }
                else if (b == 2) { // square - language on Welcome, favorite on Detail, Search elsewhere
                    if (state.screen == Screen::Detail) {
                        state.saved = !state.saved;
                        toast(state.saved ? "Saved to your favorites" : "Removed from favorites");
                    } else if (state.screen == Screen::Welcome) {
                        state.prevScreen = state.screen;
                        state.focus = state.langIndex;
                        if (state.screen != Screen::Language) state.screen = Screen::Language;
                    } else {
                        state.focus = 0;
                        go(Screen::Search);
                    }
                    stateChanged = true;
                } else if (b == 3) { // triangle - menu overlay (open Minecraft on Detail)
                    if (state.screen == Screen::Detail) {
                        toast("Opening Minecraft\xE2\x80\xA6");
                        pkgtest::launchMinecraftOnPs4();
                    } else if (state.menuOpen) {
                        state.menuOpen = false;
                    } else if (state.screen != Screen::Welcome && state.screen != Screen::CodeLink &&
                               state.screen != Screen::Email && state.screen != Screen::Language &&
                               state.screen != Screen::Confirm) {
                        state.menuOpen = true;
                        state.menuFocus = 0;
                    }
                    stateChanged = true;
                } else if (b == 4) { switchTab(-1); stateChanged = true; }             // L1
                else if (b == 5) { switchTab(1); stateChanged = true; }                // R1
                else if (b == 6) {                                                   // L2 - prev offer on Home
                    if (state.screen == Screen::Home) state.heroIndex = (state.heroIndex + 2) % 3;
                    else switchTab(-1);
                    stateChanged = true;
                } else if (b == 7) {                                                 // R2 - next offer on Home
                    if (state.screen == Screen::Home) state.heroIndex = (state.heroIndex + 1) % 3;
                    else switchTab(1);
                    stateChanged = true;
                }
            } else if (ev.type == SDL_KEYDOWN) {
                const SDL_Keycode k = ev.key.keysym.sym;
                if (k == SDLK_ESCAPE) { backRequested = true; }
                if (k == SDLK_UP) moveFocus(0, -1);
                else if (k == SDLK_DOWN) moveFocus(0, 1);
                else if (k == SDLK_LEFT) moveFocus(-1, 0);
                else if (k == SDLK_RIGHT) moveFocus(1, 0);
                else if (k == SDLK_RETURN || k == SDLK_SPACE) activate(1);
                else if (k == SDLK_BACKSPACE) back();
                else if (k == SDLK_l) {
                    state.prevScreen = state.screen;
                    state.focus = state.langIndex;
                    if (state.screen != Screen::Language) state.screen = Screen::Language;
                } else if (k == SDLK_LEFTBRACKET) {
                    if (state.screen == Screen::Home) state.heroIndex = (state.heroIndex + 2) % 3;
                    else switchTab(-1);
                } else if (k == SDLK_RIGHTBRACKET) {
                    if (state.screen == Screen::Home) state.heroIndex = (state.heroIndex + 1) % 3;
                    else switchTab(1);
                }
                stateChanged = true;
            }
        }

        // ○ / ESC never exits the app — they only navigate back inside it
        if (backRequested) {
            back();
            stateChanged = true;
        }

        // code countdown expiry
        if (state.screen == Screen::CodeLink) {
            std::lock_guard<std::mutex> lk(gLinkMx);
            if (!state.linked && t > state.codeUntil) state.codeUntil = t;  // hold at 0
        }

        // Redraw ONLY when something changed (perf):
        const int sig = frameSignature(state, t);
        const bool toastVisible = !state.toastText.empty() && t < state.toastUntil;
        const bool codeAnim = state.screen == Screen::CodeLink && (frameCount % 20 == 0);

        if (stateChanged || sig != lastSig || toastVisible != lastToastVisible ||
            codeAnim) {
            std::string accountCopy;
            std::string sessionPlanCopy;
            long sessionAcCopy = 0;
            bool searchNoResults = false;
            pkgtest::HeroInfo heroInfos[3];
            pkgtest::CardInfo exInfos[3];
            pkgtest::CardInfo gridInfos[5];
            {
                std::lock_guard<std::mutex> lk(gLinkMx);
                accountCopy = state.haveSession ? state.sessionName
                                                : std::string("name@example.com");
                sessionPlanCopy = state.sessionPlan;
                sessionAcCopy = state.sessionAc;
                for (int i = 0; i < 3 && i < static_cast<int>(state.banners.size()); ++i) {
                    const pkgtest::BannerData& b = state.banners[i];
                    heroInfos[i].title = pkgtest::i18nPick(b.titleI18n, lang);
                    heroInfos[i].subtitle = pkgtest::i18nPick(b.subtitleI18n, lang);
                    heroInfos[i].kicker = pkgtest::i18nPick(b.kickerI18n, lang);
                    heroInfos[i].badge = !b.badgeText.empty() ? b.badgeText
                        : (b.discountPercent > 0 ? ("-" + std::to_string(b.discountPercent) + "%")
                                                 : std::string());
                }
                int exN = 0, gridN = 0;
                for (const pkgtest::ItemData& it : state.items) {
                    pkgtest::CardInfo c;
                    c.title = it.title;
                    c.price = std::to_string(it.saleAcPrice >= 0 ? it.saleAcPrice : it.acPrice);
                    if (it.isExclusive && exN < 3) exInfos[exN++] = c;
                    if (gridN < 5) gridInfos[gridN++] = c;
                }
                if (state.screen == Screen::Search) {
                    const std::vector<int> hits =
                        searchMatchIdx(state.items, state.searchQuery, state.searchFilter);
                    for (int j = 0; j < 5; ++j) gridInfos[j] = pkgtest::CardInfo{};
                    for (int j = 0; j < 5 && j < static_cast<int>(hits.size()); ++j) {
                        const pkgtest::ItemData& it = state.items[hits[j]];
                        gridInfos[j].title = it.title;
                        gridInfos[j].price = std::to_string(it.saleAcPrice >= 0 ? it.saleAcPrice
                                                                                : it.acPrice);
                    }
                    searchNoResults = !state.searchQuery.empty() && hits.empty();
                }
            }
            pkgtest::setServerContent(heroInfos, exInfos, gridInfos);
            {
                std::lock_guard<std::mutex> lk(gLinkMx);
                pkgtest::setDetailTitle(state.detailTitle);
            }
            engine.clear(pkgtest::theme::BG);
            switch (state.screen) {
                case Screen::Welcome:
                    pkgtest::drawWelcome(engine, state.focus, lang);
                    break;
                case Screen::CodeLink: {
                    std::string codeCopy;
                    int secsLeft = 0;
                    bool linkedCopy = false;
                    {
                        std::lock_guard<std::mutex> lk(gLinkMx);
                        codeCopy = state.code;
                        secsLeft = static_cast<int>(std::max(0.0, state.codeUntil - t));
                        linkedCopy = state.linked;
                    }
                    pkgtest::drawCodeLink(engine, codeCopy, secsLeft, linkedCopy, lang);
                    break;
                }
                case Screen::Email:
                    pkgtest::drawEmailSignin(engine, state.focus, state.email, state.pass,
                                             state.emailError, lang);
                    break;
                case Screen::Language:
                    pkgtest::drawLanguageDialog(engine, state.focus, lang);
                    break;
                case Screen::Home:
                    pkgtest::drawHome(engine, state.focus, state.heroIndex, state.filterIndex, lang);
                    break;
                case Screen::Store:
                    pkgtest::drawStore(engine, state.focus, state.storeFilter, lang);
                    break;
                case Screen::Deals:
                    pkgtest::drawDeals(engine, state.focus, state.dealsFilter, lang);
                    break;
                case Screen::Settings:
                    pkgtest::drawSettings(engine, state.focus, state.autoLaunch,
                                          accountCopy, lang);
                    break;
                case Screen::Detail:
                    pkgtest::drawInstallDemo(engine, state.demoState, state.progress, lang);
                    break;
                case Screen::Confirm:
                    pkgtest::drawSettings(engine, state.focus, state.autoLaunch,
                                          accountCopy, lang);
                    pkgtest::drawConfirmDialog(engine, "Sign out?",
                                               "You can sign in again any time with a code.",
                                               "Sign out", "Cancel",
                                               state.focus, lang);
                    break;
                case Screen::Search:
                    pkgtest::drawSearch(engine, state.focus, state.searchQuery,
                                        state.searchFilter, searchNoResults, lang);
                    break;
                case Screen::Profile:
                    pkgtest::drawProfile(engine, state.focus, accountCopy,
                                         planIndexFrom(sessionPlanCopy),
                                         static_cast<int>(sessionAcCopy), 24, 8, 5, lang);
                    break;
                case Screen::Wallet:
                    pkgtest::drawWallet(engine, state.focus,
                                        static_cast<int>(sessionAcCopy), lang);
                    break;
                case Screen::Plans:
                    pkgtest::drawPlans(engine, state.focus,
                                       planIndexFrom(sessionPlanCopy), lang);
                    break;
            }
            if (state.menuOpen) {
                pkgtest::drawMenuOverlay(engine, state.menuFocus, lang);
            }
            if (toastVisible) {
                pkgtest::drawToast(engine, state.toastText, lang);
            }
            lastSig = sig;
            lastToastVisible = toastVisible;
            frameDirty = true;
        }

        // Present ONLY when the frame actually changed — idle frames cost nothing
        // (PS-menu feel: controller polls fast, CPU stays quiet).
        if (frameDirty) {
            engine.present(windowSurface);
            SDL_RenderPresent(renderer);
            SDL_UpdateWindowSurface(window);
            frameDirty = false;
        }
        ++frameCount;
        SDL_Delay(8);   // 120Hz command polling -> snappy navigation
    }

    state.installing.store(true);
    for (SDL_Joystick* joy : joysticks) {
        SDL_JoystickClose(joy);
    }
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
