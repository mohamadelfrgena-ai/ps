#pragma once
// AddonStation UI engine — v5 header.
// v5 additions (marked "v5"): runtime image loading (covers downloaded at runtime), lazy decoding of
// ui.pak images, LRU byte-budget caches, memcpy fast paths.
#include <SDL2/SDL.h>
#include <cstdint>
#include <map>
#include <string>
#include <vector>
namespace pkgtest {
enum class FontRole { Body = 0, Pixel = 1, Arabic = 2 };
class UiEngine {
public:
    static constexpr int kWidth = 1920;
    static constexpr int kHeight = 1080;
    bool init(const std::string& pakPath = "");
    void clear(SDL_Color c);
    void fillRect(int x, int y, int w, int h, SDL_Color c);
    void fillGradH(int x, int y, int w, int h, SDL_Color l, SDL_Color r);
    void fillGradV(int x, int y, int w, int h, SDL_Color t, SDL_Color b);
    void drawRoundRect(int x, int y, int w, int h, int radius, SDL_Color c);
    void drawRoundGradH(int x, int y, int w, int h, int radius, SDL_Color l, SDL_Color r);
    void drawRoundGradV(int x, int y, int w, int h, int radius, SDL_Color t, SDL_Color b);
    void drawRoundGradD(int x, int y, int w, int h, int radius, SDL_Color c0, SDL_Color c1);
    void drawRoundBorder(int x, int y, int w, int h, int radius, int thickness, SDL_Color c);
    void drawFocusRing(int x, int y, int w, int h, int radius);
    void drawCircle(int cx, int cy, int radius, SDL_Color c);
    void dropShadow(int x, int y, int w, int h, int radius, int blur, Uint8 alpha);
    void drawRoundRectRotated(int cx, int cy, int w, int h, int radius, float angleDeg, SDL_Color c);
    void drawImage(const std::string& name, int x, int y, int w, int h, Uint8 alpha = 255);
    void drawImageRounded(const std::string& name, int x, int y, int w, int h, int radius, Uint8 alpha = 255);
    void drawImageRotated(const std::string& name, int cx, int cy, int w, int h, float angleDeg, Uint8 alpha = 255);
    void drawImageZoomed(const std::string& name, int x, int y, int w, int h, float zoom, Uint8 alpha = 255);
    void drawImageCropped(const std::string& name, int sx0, int sy0, int sw, int sh, int x, int y, int w, int h,
                          Uint8 alpha = 255, int radius = 0);
    bool imageSize(const std::string& name, int& w, int& h) const;
    int drawText(const std::string& utf8, int x, int y, int pxSize, SDL_Color color, FontRole role = FontRole::Body,
                 bool alignRight = false, bool isArabicLang = false, int maxWidth = 0);
    // v6.7: width of the drawn glyph pixels (no bitmap padding / side bearings) — use it to centre groups
    int inkWidth(const std::string& utf8, int pxSize, FontRole role = FontRole::Body, bool isArabicLang = false);
    // v6.7: height of the glyph ink from its top to the baseline (cap / digit height) — to line up units with numbers
    int inkCapHeight(const std::string& utf8, int pxSize, FontRole role = FontRole::Body, bool isArabicLang = false);
    int drawTextCentered(const std::string& utf8, int cx, int cy, int pxSize, SDL_Color color,
                         FontRole role = FontRole::Body, bool isArabicLang = false);
    int drawTextGradV(const std::string& utf8, int x, int y, int pxSize, SDL_Color top, SDL_Color bottom,
                      FontRole role = FontRole::Body, bool alignRight = false, bool isArabicLang = false, int maxWidth = 0);
    int drawTextRotated(const std::string& utf8, int cx, int cy, int pxSize, SDL_Color color, float angleDeg,
                        FontRole role = FontRole::Body, bool isArabicLang = false);
    int drawTextGlow(const std::string& utf8, int x, int y, int pxSize, SDL_Color color, SDL_Color glow,
                     FontRole role = FontRole::Body, bool alignRight = false, bool isArabicLang = false);
    int measureText(const std::string& utf8, int pxSize, FontRole role = FontRole::Body, bool isArabicLang = false);
    void present(SDL_Surface* windowSurface);

    // ---- v5: runtime images (covers from the network / disk cache) ----------
    // Decodes PNG/JPEG. maxW/maxH > 0 => the image is downscaled once at load
    // (area filter) so it never exceeds that size: keeps RAM low and draws fast.
    // Loading an existing name replaces it and drops its cached scales.
    bool loadImageFile(const std::string& name, const std::string& path, int maxW = 0, int maxH = 0);
    bool loadImageMemory(const std::string& name, const void* data, std::size_t size, int maxW = 0, int maxH = 0);
    bool addImageRGBA(const std::string& name, int w, int h, const std::uint8_t* rgba);
    void unloadImage(const std::string& name);
    // v0.30: restore dirty rectangles from a frame snapshot (animation ticks)
    void restoreFrom(const std::vector<std::uint32_t>& base, const int* rects, int nRects);
    bool hasImage(const std::string& name) const { return images_.count(name) != 0 || packed_.count(name) != 0; }
    std::size_t imageBytes() const { return imageBytes_; }      // decoded RGBA held by images_
    std::size_t scaledCacheBytes() const { return scaledBytes_; }
    void setScaledCacheBudget(std::size_t bytes) { scaledBudget_ = bytes; }
    // v5: registers dst = a tiny copy of src (longest side <= maxDim). Drawn big it is a smooth blur
    // (bilinear upscale) — used to fill the space around artwork shown whole. Cheap: ~10 KB.
    bool makeBlurred(const std::string& src, const std::string& dst, int maxDim = 40);
    const std::vector<std::uint32_t>& framebuffer() const { return fb_; }
    void fadeToBlack(Uint8 keep);   // v5.5: every pixel *= keep/255 (splash fade, ~1 ms per frame)
private:
    struct FontData;
    struct ImageData { int w = 0, h = 0; std::vector<std::uint8_t> rgba; std::string key; };
    struct ScaledImage { int w = 0, h = 0; std::vector<std::uint32_t> px; std::uint64_t used = 0; bool opaque = false; };
    struct TextBitmap {
        int w = 0, h = 0; std::vector<std::uint32_t> px; std::uint64_t used = 0;
        int baseline = 0;                                   // v6.4: baseline row inside the bitmap
        int inkX0 = 0, inkX1 = -1, inkY0 = 0, inkY1 = -1;   // v6.4: bounding box of the drawn glyph pixels
        bool arabic = false;                                // v6.4: drawn with the Arabic font (fixed line box)
    };
    static void measureInk(TextBitmap& b);                  // v6.4
    bool storeImage(const std::string& name, int w, int h, const std::uint8_t* rgba, int maxW, int maxH);  // v5
    ImageData* findImage(const std::string& name);                                                         // v5: lazy decode
    void dropScaled(const std::string& name);                                                              // v5
    void trimScaled(std::size_t incoming);                                                                 // v5
    void trimText();                                                                                       // v5
    void blendPx(int x, int y, Uint8 r, Uint8 g, Uint8 b, Uint8 a);
    void blendRow(int x0, int x1, int y, Uint8 r, Uint8 g, Uint8 b, Uint8 a);
    const TextBitmap& buildFallbackText(const std::string& utf8, int pxSize);
    const TextBitmap& getOrBuildText(const std::string& utf8, int pxSize, FontRole role, bool isArabicLang);
    std::vector<std::uint32_t> fb_;
    FontData* fonts_ = nullptr;
    bool fontsLoaded_ = false, fallbackReady_ = false;
    // v0.65f («صور البروفايل المختارة متحركة»): animated GIF frames per image
    struct GifAnim { int frames = 1; std::vector<int> delays; int cur = 0; int acc = 0; unsigned last = 0; };
    std::map<std::string, GifAnim> gifs_;
    std::map<std::string, ImageData> images_;
    std::map<std::string, ScaledImage> scaledCache_;
    std::map<std::string, TextBitmap> textCache_;
    std::map<std::string, std::vector<unsigned char>> packed_;   // v5: compressed ui.pak images (decoded lazily)
    std::uint64_t tick_ = 0;                          // v5: LRU clock
    std::size_t imageBytes_ = 0;                      // v5
    std::size_t scaledBytes_ = 0;                     // v5
    std::size_t scaledBudget_ = 128u * 1024u * 1024u;  // v0.53 SPEED: 128 MB (PS4 has 8 GB) — zero re-resample thrash
};
}
