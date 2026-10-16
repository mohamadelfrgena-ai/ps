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
#include <orbis/UserService.h>
#include <orbis/libkernel.h>

namespace pkgtest {
namespace {

std::wstring toWide(const std::string& utf8) {
    std::wstring out;
    out.reserve(utf8.size());
    std::size_t i = 0;
    while (i < utf8.size()) {
        const unsigned char c = static_cast<unsigned char>(utf8[i]);
        std::uint32_t cp = '?';
        std::size_t adv = 1;
        if (c < 0x80) { cp = c; adv = 1; }
        else if ((c >> 5) == 0x6 && i + 1 < utf8.size()) {
            cp = ((c & 0x1Fu) << 6) | (utf8[i + 1] & 0x3Fu); adv = 2;
        } else if ((c >> 4) == 0xE && i + 2 < utf8.size()) {
            cp = ((c & 0x0Fu) << 12) | ((utf8[i + 1] & 0x3Fu) << 6) | (utf8[i + 2] & 0x3Fu); adv = 3;
        } else if ((c >> 3) == 0x1E && i + 3 < utf8.size()) {
            cp = ((c & 0x07u) << 18) | ((utf8[i + 1] & 0x3Fu) << 12) |
                 ((utf8[i + 2] & 0x3Fu) << 6) | (utf8[i + 3] & 0x3Fu); adv = 4;
        }
        out.push_back(static_cast<wchar_t>(cp));
        i += adv;
    }
    return out;
}

std::string fromWide(const wchar_t* w, std::size_t maxChars) {
    std::string out;
    for (std::size_t i = 0; i < maxChars && w[i] != 0; ++i) {
        const std::uint32_t cp = static_cast<std::uint32_t>(w[i]);
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
    static wchar_t sTitle[128];
    static wchar_t sInput[256];
    const std::wstring wTitle = toWide(title);
    const std::wstring wInit = toWide(initial);
    std::memset(sTitle, 0, sizeof(sTitle));
    std::memset(sInput, 0, sizeof(sInput));
    std::wmemcpy(sTitle, wTitle.c_str(), std::min<std::size_t>(wTitle.size(), 127));
    std::wmemcpy(sInput, wInit.c_str(), std::min<std::size_t>(wInit.size(), 255));

    OrbisImeDialogSetting setting;
    std::memset(&setting, 0, sizeof(setting));
    setting.userId = 0;
    {
        int uid = 0;
        if (sceUserServiceGetInitialUser(&uid) == 0 && uid != 0) {
            setting.userId = static_cast<unsigned int>(uid);
        }
    }
    setting.type = ORBIS_TYPE_DEFAULT;
    setting.supportedLanguages = 0;             // system default set
    setting.enterLabel = ORBIS_BUTTON_LABEL_DEFAULT;
    setting.inputMethod = ORBIS__DEFAULT;
    setting.filter = nullptr;
    setting.option = 0;
    setting.maxTextLength = static_cast<unsigned int>(maxLen > 0 && maxLen < 200 ? maxLen : 64);
    setting.inputTextBuffer = sInput;
    setting.posx = 0.0f;
    setting.posy = 0.0f;
    setting.horizontalAlignment = ORBIS_H_CENTER;
    setting.verticalAlignment = ORBIS_V_CENTER;
    setting.placeholder = nullptr;
    setting.title = sTitle;

    const int rc = sceImeDialogInit(&setting, nullptr);
    if (rc < 0) return "";

    std::string result;
    for (;;) {
        const OrbisDialogStatus st = sceImeDialogGetStatus();
        if (st == ORBIS_DIALOG_STATUS_STOPPED) {
            OrbisDialogResult res;
            std::memset(&res, 0, sizeof(res));
            sceImeDialogGetResult(&res);
            if (res.endstatus == ORBIS_DIALOG_OK) {
                result = fromWide(sInput, 255);
            }
            break;
        }
        if (st == ORBIS_DIALOG_STATUS_NONE) break;
        sceKernelUsleep(16 * 1000);
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
