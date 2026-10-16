#ifndef PKG_ADDON_TEST_IME_DIALOG_HPP
#define PKG_ADDON_TEST_IME_DIALOG_HPP

#include <string>

namespace pkgtest {

// Opens the PS4 system keyboard (sceImeDialog). Blocks until the user finishes
// (or cancels). Returns the typed text, or "" if cancelled/unavailable.
// On host builds a small stub returns a demo string so flows stay testable.
std::string openImeDialog(const std::string& title,
                          const std::string& initial,
                          int maxLen = 64);

} // namespace pkgtest

#endif
