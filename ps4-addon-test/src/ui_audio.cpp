// ui_audio.cpp — AddonStation HUD sound effects (v7.2+, v0.29 rework)
// v0.29 fixes for silent audio on PS4:
//   1. clips are EMBEDDED in the eboot (audio_bank, generated from assets/sfx/*.wav)
//      — no file paths, no PKG entries, no fopen can fail;
//   2. output block is 512 frames (a multiple of the 256-sample audio grain —
//      480 was silently rejected by sceAudioOutOpen on some firmwares);
//   3. the port open tries several user ids and sets the volume explicitly;
//   4. one broken clip never silences the rest.
// Mixing = 4 mono voices of PCM16 @ 48 kHz.
#include "ui_audio.hpp"
#include "audio_bank.hpp"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <chrono>

#if defined(__ORBIS__) || defined(__PROSPERO__)
#  define PKGTEST_AUDIO_HDR 0
#  if defined(__has_include)
#    if __has_include(<orbis/AudioOut.h>)
#      include <orbis/AudioOut.h>
#      undef PKGTEST_AUDIO_HDR
#      define PKGTEST_AUDIO_HDR 1
#    elif __has_include(<audioout.h>)
#      include <audioout.h>
#      undef PKGTEST_AUDIO_HDR
#      define PKGTEST_AUDIO_HDR 1
#    endif
#  endif
#  if !PKGTEST_AUDIO_HDR
extern "C" {
int32_t sceAudioOutInit(void);
int32_t sceAudioOutOpen(int32_t userId, int32_t type, int32_t index, int32_t len, int32_t freq, int32_t format);
int32_t sceAudioOutOutput(int32_t handle, const void* data);
int32_t sceAudioOutClose(int32_t handle);
int32_t sceAudioOutSetVolume(int32_t handle, int32_t flag, int32_t* volume);
}
#  endif
#endif

namespace {
constexpr int kRate = 48000;
constexpr int kVoices = 4;             // concurrent SFX slots
constexpr int kFrames = 512;           // v0.29: 2 x 256-sample grain (480 was rejected)
constexpr int kGrain = 256;

struct Clip { std::vector<int16_t> pcm; };

std::vector<Clip> gClips;
bool gOk = false;
std::atomic<bool> gRun{false};
std::thread gThread;
std::atomic<int> gVol{100};

struct Voice { const Clip* clip = nullptr; std::size_t pos = 0; };
Voice gVoices[kVoices];
std::mutex gMix;

// ---------------------------------------------------------------- WAV loader
// Minimal RIFF/WAVE PCM reader: mono, 16-bit, 48 kHz (the audio_bank format).
static bool parseWav(const unsigned char* data, std::size_t len, Clip& out) {
    if (len < 44 || std::memcmp(data, "RIFF", 4) || std::memcmp(data + 8, "WAVE", 4))
        return false;
    std::size_t pos = 12;
    int ch = 0, rate = 0, bits = 0;
    bool gotData = false;
    while (pos + 8 <= len) {
        const unsigned char* p = data + pos;
        unsigned int sz = (unsigned int)p[4] | ((unsigned int)p[5] << 8) |
                          ((unsigned int)p[6] << 16) | ((unsigned int)p[7] << 24);
        pos += 8;
        if (!std::memcmp(p, "fmt ", 4)) {
            if (pos + 16 > len) return false;
            const unsigned char* f = data + pos;
            const unsigned short audioFmt = (unsigned short)(f[0] | (f[1] << 8));
            ch = f[2] | (f[3] << 8);
            rate = f[4] | (f[5] << 8) | (f[6] << 16) | (f[7] << 24);
            bits = f[14] | (f[15] << 8);
            if (audioFmt != 1) return false;                     // PCM only
        } else if (!std::memcmp(p, "data", 4)) {
            if (ch != 1 || bits != 16 || rate != kRate) return false;
            if (pos + sz > len) sz = (unsigned int)(len - pos);
            out.pcm.resize(sz / 2);
            std::memcpy(out.pcm.data(), data + pos, sz / 2 * 2);
            gotData = true;
            break;
        }
        pos += sz + (sz & 1u);
    }
    return gotData;
}

static bool loadWavFile(const char* path, Clip& out) {
    FILE* f = std::fopen(path, "rb");
    if (!f) return false;
    std::fseek(f, 0, SEEK_END);
    long n = std::ftell(f);
    std::fseek(f, 0, SEEK_SET);
    if (n <= 0 || n > 8 * 1024 * 1024) { std::fclose(f); return false; }
    std::vector<unsigned char> buf((std::size_t)n);
    const std::size_t rd = std::fread(buf.data(), 1, buf.size(), f);
    std::fclose(f);
    return rd == buf.size() && parseWav(buf.data(), buf.size(), out);
}

const char* clipName(Sfx s) {
    switch (s) {
        case Sfx::Focus:        return "focus";
        case Sfx::Tab:          return "tab";
        case Sfx::Press:        return "press";
        case Sfx::Close:        return "close";
        case Sfx::Toggle:       return "toggle";
        case Sfx::Error:        return "error";
        case Sfx::AppStart:     return "app_start";
        case Sfx::Success:      return "success";
        case Sfx::Purchase:     return "purchase";
        case Sfx::DownloadDone: return "download_done";
        case Sfx::Notify:       return "notify";
        case Sfx::_count:       return "";
    }
    return "";
}

// ------------------------------------------------------------ mixing
void mixBlock(int16_t* dst, int frames) {
    // mono voices -> interleaved L/R output
    int acc[512];
    if (frames > 512) frames = 512;
    for (int i = 0; i < frames; ++i) acc[i] = 0;
    {
        std::lock_guard<std::mutex> lk(gMix);
        const int vol = gVol.load();
        for (int v = 0; v < kVoices; ++v) {
            Voice& vo = gVoices[v];
            if (!vo.clip) continue;
            const int16_t* d = vo.clip->pcm.data();
            const std::size_t n = vo.clip->pcm.size();
            for (int i = 0; i < frames && vo.pos < n; ++i, ++vo.pos)
                acc[i] += (int(d[vo.pos]) * vol) / 100;
            if (vo.pos >= n) vo.clip = nullptr;   // finished
        }
    }
    for (int i = 0; i < frames; ++i) {
        int s = acc[i];
        if (s > 32767) s = 32767; else if (s < -32768) s = -32768;
        dst[2 * i] = dst[2 * i + 1] = static_cast<int16_t>(s);
    }
}
#if defined(__ORBIS__) || defined(__PROSPERO__)
int32_t openAudioPort() {
    // Try several user ids — the system user (0xFF) exists on every console,
    // the interactive user is the reliable one on retail.
    const int users[5] = {0xFF, 0, 1, 2, 3};
    for (int u = 0; u < 5; ++u) {
#if PKGTEST_AUDIO_HDR
        const int32_t h = sceAudioOutOpen(users[u], ORBIS_AUDIO_OUT_PORT_TYPE_MAIN, 0,
                                          (uint32_t)kFrames, (uint32_t)kRate,
                                          (uint32_t)ORBIS_AUDIO_OUT_PARAM_FORMAT_S16_STEREO);
#else
        const int32_t h = sceAudioOutOpen(users[u], 0, 0, kFrames, kRate, 1);
#endif
        if (h >= 0) return h;
    }
    return -1;
}

void audioThreadOrbis() {
    sceAudioOutInit();                       // required before open on some firmwares
    int32_t hnd = openAudioPort();
    if (hnd < 0) return;                     // no audio device -> silent UI (never crash)
    int32_t vol[2] = {32768, 32768};         // full scale L+R (flag bits: 1=L, 2=R)
    sceAudioOutSetVolume(hnd, 3, vol);
    std::vector<int16_t> buf(kFrames * 2);
    int fails = 0;
    while (gRun) {
        for (int off = 0; off < kFrames; off += kGrain)
            mixBlock(&buf[off * 2], kGrain);
        if (sceAudioOutOutput(hnd, buf.data()) < 0) {
            if (++fails > 8) break;          // give up after ~a few bad blocks
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        } else {
            fails = 0;
        }
    }
    sceAudioOutClose(hnd);
}
#endif

#if !(defined(__ORBIS__) || defined(__PROSPERO__))
// Host fallback (dev machine preview): SDL2 audio.
#include <SDL2/SDL.h>
static Uint32 gDev = 0;
void sdlAudioCb(void*, Uint8* stream, int len) {
    mixBlock(reinterpret_cast<int16_t*>(stream), len / 4);
}
#endif
} // namespace

bool ui_audio_init(const char* dir) {
    static const int kN = static_cast<int>(Sfx::_count);
    gClips.resize(kN);
    bool any = false;
    for (int i = 0; i < kN; ++i) {
        const char* nm = clipName(static_cast<Sfx>(i));
        // 1) embedded bank — always present in the eboot (v0.29)
        for (int k = 0; k < pkgtest::kAudioBankCount; ++k) {
            if (std::strcmp(pkgtest::kAudioBank[k].name, nm) != 0) continue;
            if (parseWav(pkgtest::kAudioBank[k].wav, pkgtest::kAudioBank[k].wavLen, gClips[i]) &&
                !gClips[i].pcm.empty())
                any = true;
            break;
        }
        // 2) optional file override (host dev, experiments)
        if (gClips[i].pcm.empty() && dir && *dir) {
            char path[512];
            std::snprintf(path, sizeof(path), "%s/%s.wav", dir, nm);
            if (loadWavFile(path, gClips[i]) && !gClips[i].pcm.empty()) any = true;
        }
    }
    if (!any) { gOk = false; return false; }
    gRun = true;
#if defined(__ORBIS__) || defined(__PROSPERO__)
    gThread = std::thread(audioThreadOrbis);
#else
    if (SDL_WasInit(SDL_INIT_AUDIO) || SDL_InitSubSystem(SDL_INIT_AUDIO) == 0) {
        SDL_AudioSpec want{};
        want.freq = kRate; want.format = AUDIO_S16SYS; want.channels = 2;
        want.samples = 1024; want.callback = sdlAudioCb;
        gDev = SDL_OpenAudioDevice(nullptr, 0, &want, nullptr, 0);
        if (gDev) SDL_PauseAudioDevice(gDev, 0);
    }
#endif
    gOk = true;
    return true;
}

void ui_audio_shutdown() {
    if (!gOk) return;
#if !(defined(__ORBIS__) || defined(__PROSPERO__))
    if (gDev) { SDL_CloseAudioDevice(gDev); gDev = 0; }
#endif
    gRun = false;
    if (gThread.joinable()) gThread.join();
    gOk = false;
}

void ui_sfx(Sfx id) {
    if (!gOk) return;
    const Clip& c = gClips[static_cast<int>(id)];
    if (c.pcm.empty()) return;
    std::lock_guard<std::mutex> lk(gMix);
    for (int v = 0; v < kVoices; ++v)          // steal the first free voice …
        if (!gVoices[v].clip) { gVoices[v].clip = &c; gVoices[v].pos = 0; return; }
    gVoices[0].clip = &c; gVoices[0].pos = 0;  // … or replace the oldest.
}

void ui_sfx_volume(int v01) {
    if (v01 < 0) v01 = 0;
    if (v01 > 100) v01 = 100;
    gVol = v01;
}
