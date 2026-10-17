// PS4 system keyboard (sceImeDialog) — real text input for search/chat/code.
// Uses the OpenOrbis OrbisImeDialogSetting (wchar_t buffers) exactly.
// Host builds use a stub so flows remain testable.

#include "ime_dialog.hpp"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <cwchar>
#include <vector>

#ifdef __ORBIS__

#include <orbis/ImeDialog.h>
#include <orbis/Sysmodule.h>
#include <orbis/UserService.h>
#include <orbis/libkernel.h>

namespace pkgtest {
namespace {

// PS4 system IME uses UTF-16 buffers (proven by shipping homebrew) — the
// OpenOrbis header declares wchar_t* but the OS reads/writes UTF-16 units.
void utf8ToUtf16(const char* src, std::uint16_t* dst, std::size_t maxChars) {
    std::size_t o = 0;
    for (std::size_t i = 0; src[i] != '\0' && o + 1 < maxChars;) {
        const unsigned char c = static_cast<unsigned char>(src[i]);
        std::uint32_t cp = '?';
        std::size_t adv = 1;
        if (c < 0x80) { cp = c; adv = 1; }
        else if ((c >> 5) == 0x6 && src[i + 1]) {
            cp = ((c & 0x1Fu) << 6) | (static_cast<unsigned char>(src[i + 1]) & 0x3Fu); adv = 2;
        } else if ((c >> 4) == 0xE && src[i + 1] && src[i + 2]) {
            cp = ((c & 0x0Fu) << 12) |
                 ((static_cast<unsigned char>(src[i + 1]) & 0x3Fu) << 6) |
                 (static_cast<unsigned char>(src[i + 2]) & 0x3Fu); adv = 3;
        } else if ((c >> 3) == 0x1E && src[i + 1] && src[i + 2] && src[i + 3]) {
            cp = ((c & 0x07u) << 18) |
                 ((static_cast<unsigned char>(src[i + 1]) & 0x3Fu) << 12) |
                 ((static_cast<unsigned char>(src[i + 2]) & 0x3Fu) << 6) |
                 (static_cast<unsigned char>(src[i + 3]) & 0x3Fu); adv = 4;
        }
        i += adv;
        if (cp > 0xFFFF) {  // surrogate pair
            cp -= 0x10000;
            if (o + 2 >= maxChars) break;
            dst[o++] = static_cast<std::uint16_t>(0xD800 + (cp >> 10));
            dst[o++] = static_cast<std::uint16_t>(0xDC00 + (cp & 0x3FF));
        } else {
            dst[o++] = static_cast<std::uint16_t>(cp);
        }
    }
    dst[o] = 0;
}

std::string utf16ToUtf8(const std::uint16_t* src, std::size_t maxChars) {
    std::string out;
    for (std::size_t i = 0; i < maxChars && src[i] != 0; ++i) {
        std::uint32_t cp = src[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < maxChars &&
            src[i + 1] >= 0xDC00 && src[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (src[i + 1] - 0xDC00);
            ++i;
        }
        if (cp < 0x80) out.push_back(static_cast<char>(cp));
        else if (cp < 0x800) {
            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    return out;
}

} // namespace

std::string openImeDialog(const std::string& title,
                          const std::string& initial,
                          int maxLen) {
    // 1) تحميل وحدات النظام — بدونها يتعطل التطبيق (CE-34878) عند أول استدعاء.
    //    نفس مسار التطبيقات المنزلية العاملة (ps4-ezremote-client).
    {
        static bool sLoaded = false;
        if (!sLoaded) {
            sceSysmoduleLoadModule(ORBIS_SYSMODULE_LIBIME);
            sceSysmoduleLoadModule(ORBIS_SYSMODULE_IME_DIALOG);
            sceSysmoduleLoadModuleInternal(
                static_cast<OrbisSysModuleInternal>(ORBIS_SYSMODULE_IME_DIALOG));
            sceSysmoduleLoadModuleInternal(ORBIS_SYSMODULE_INTERNAL_USER_SERVICE);
            sLoaded = true;
        }
    }

    // 2) معرف المستخدم الحقيقي إلزامي — الصفر غير صالح وقد يسبب التعطل.
    int uid = 0;
    if (sceUserServiceGetInitialUser(&uid) < 0 || uid <= 0) return "";

    // 3) نصوص UTF-16 (النظام لا يقرأ wchar_t عريض)
    static std::uint16_t sTitle16[128];
    static std::uint16_t sInput16[512];
    std::memset(sTitle16, 0, sizeof(sTitle16));
    std::memset(sInput16, 0, sizeof(sInput16));
    utf8ToUtf16(title.c_str(), sTitle16, 128);
    utf8ToUtf16(initial.c_str(), sInput16, 512);

    OrbisImeDialogSetting setting;
    std::memset(&setting, 0, sizeof(setting));
    setting.userId = static_cast<unsigned int>(uid);
    setting.type = (title.find("mail") != std::string::npos ||
                    title.find("Email") != std::string::npos)
                       ? ORBIS_TYPE_MAIL
                       : ORBIS_TYPE_DEFAULT;
    setting.supportedLanguages = 0;             // 0 = لغات النظام (مُثبَّت بالمرجع)
    setting.enterLabel = ORBIS_BUTTON_LABEL_DEFAULT;
    setting.inputMethod = ORBIS__DEFAULT;
    setting.filter = nullptr;
    setting.option = 0;
    setting.maxTextLength = static_cast<unsigned int>(maxLen > 0 && maxLen < 500 ? maxLen : 64);
    setting.inputTextBuffer = reinterpret_cast<wchar_t*>(sInput16);
    setting.posx = 0.0f;
    setting.posy = 0.0f;
    setting.horizontalAlignment = ORBIS_H_CENTER;
    setting.verticalAlignment = ORBIS_V_CENTER;
    setting.placeholder = nullptr;
    setting.title = reinterpret_cast<const wchar_t*>(sTitle16);

    const int rc = sceImeDialogInit(&setting, nullptr);
    if (rc < 0) return "";

    std::string result;
    int waitedMs = 0;
    for (;;) {
        const OrbisDialogStatus st = sceImeDialogGetStatus();
        if (st == ORBIS_DIALOG_STATUS_STOPPED) {
            OrbisDialogResult res;
            std::memset(&res, 0, sizeof(res));
            sceImeDialogGetResult(&res);
            if (res.endstatus == ORBIS_DIALOG_OK) {
                result = utf16ToUtf8(sInput16, 511);
            }
            break;
        }
        if (st == ORBIS_DIALOG_STATUS_NONE) break;
        sceKernelUsleep(16 * 1000);
        if ((waitedMs += 16) > 90000) break;   // safety: never freeze the app
    }
    sceImeDialogTerm();
    return result;
}

} // namespace pkgtest

#else // ---- host stub (preview / unit tests) ----

namespace pkgtest {

std::string openImeDialog(const std::string& title,
                          const std::string& initial,
                          int maxLen) {
    (void)title;
    (void)maxLen;
    if (!initial.empty()) return initial;
    return "Player1";
}

} // namespace pkgtest

#endif
