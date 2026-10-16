#ifndef PKG_ADDON_TEST_UI_ENGINE_HPP
#define PKG_ADDON_TEST_UI_ENGINE_HPP

// Framebuffer-based UI engine for PS4 (OpenOrbis SDL2) + host preview.
// Renders EVERYTHING (text, images, shapes) into its own RGBA32 pixel buffer
// with the proven stb_truetype/stb_image code path — no SDL textures and no
// SDL_RenderCopy — then blits the finished frame into the SDL window surface
// (the only SDL surface calls used by the working v0.5 app).

#include <SDL2/SDL.h>
#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

namespace pkgtest {

enum class FontRole {
    Pixel = 0,   // F77-Minecraft.ttf (Tajawal in Arabic)
    Body = 1,    // Nunito / DejaVuSans-Bold (Tajawal for Arabic script)
    Arabic = 2   // Tajawal-ExtraBold.ttf
};

class UiEngine {
public:
    static constexpr int kWidth = 1920;
    static constexpr int kHeight = 1080;

    UiEngine() = default;
    ~UiEngine() = default;

    // Loads assets/ui.pak (fonts + images). On failure the engine still works
    // with a built-in fallback font so the screen is NEVER black.
    bool init(const std::string& pakPath = "");
    bool fontsLoaded() const { return fontsLoaded_; }

    void clear(SDL_Color c);
    void fillRect(int x, int y, int w, int h, SDL_Color c);
    void fillGradH(int x, int y, int w, int h, SDL_Color left, SDL_Color right);
    void fillGradV(int x, int y, int w, int h, SDL_Color top, SDL_Color bottom);

    void drawRoundRect(int x, int y, int w, int h, int radius, SDL_Color c);
    void drawRoundGradH(int x, int y, int w, int h, int radius, SDL_Color left, SDL_Color right);
    void drawRoundBorder(int x, int y, int w, int h, int radius, int thickness, SDL_Color c);
    void drawFocusRing(int x, int y, int w, int h, int radius);
    void drawCircle(int cx, int cy, int radius, SDL_Color c);

    void drawImage(const std::string& name, int x, int y, int w, int h, Uint8 alpha = 255);
    void drawImageZoomed(const std::string& name, int x, int y, int w, int h, float zoom, Uint8 alpha = 255);

    int measureText(const std::string& utf8, int pxSize, FontRole role, bool isArabicLang = false);
    int drawText(const std::string& utf8,
                 int x,
                 int y,
                 int pxSize,
                 SDL_Color color,
                 FontRole role = FontRole::Body,
                 bool alignRight = false,
                 bool isArabicLang = false,
                 int maxWidth = 0);
    int drawTextCentered(const std::string& utf8,
                         int cx,
                         int cy,
                         int pxSize,
                         SDL_Color color,
                         FontRole role = FontRole::Body,
                         bool isArabicLang = false);

    // Copies the finished frame into an SDL window surface (any 32-bit format).
    void present(SDL_Surface* windowSurface);

    const std::uint32_t* pixels() const { return fb_.data(); }
    int width() const { return kWidth; }
    int height() const { return kHeight; }

private:
    struct ImageData {
        int w = 0;
        int h = 0;
        std::vector<std::uint8_t> rgba;
    };
    struct TextBitmap {
        std::vector<std::uint32_t> px; // RGBA32
        int w = 0;
        int h = 0;
    };
    struct FontData;

    void blendPx(int x, int y, Uint8 r, Uint8 g, Uint8 b, Uint8 a);
    void blendRow(int x0, int x1, int y, Uint8 r, Uint8 g, Uint8 b, Uint8 a);
    const TextBitmap& getOrBuildText(const std::string& utf8, int pxSize, FontRole role, bool isArabicLang);
    const TextBitmap& buildFallbackText(const std::string& utf8, int pxSize);

    std::vector<std::uint32_t> fb_;
    FontData* fonts_ = nullptr;
    bool fontsLoaded_ = false;
    bool fallbackReady_ = false;
    std::unordered_map<std::string, ImageData> images_;
    std::unordered_map<std::string, TextBitmap> textCache_;
};

} // namespace pkgtest

#endif
