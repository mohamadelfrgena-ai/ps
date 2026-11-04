// ui_audio.hpp — AddonStation HUD sound effects (v7.2+)
// Tiny PCM mixer: all clips are loaded ONCE at boot (mono, 16-bit PCM, 48kHz)
// and mixed on a background audio thread. Zero per-frame cost of the renderer.
#pragma once
#include <cstdint>

enum class Sfx : int {
    Focus = 0,     // تحديد عنصر — يظهر مع حركة الـd-pad
    Tab,           // لتنقل بين تبويبات التطبيق — تبديل تبويب (الريل/الهيدر)
    Press,         // لضغط على الأزرار — X / تأكيد
    Close,         // اغلاق او خروج — ○ رجوع / خروج
    Toggle,        // تفعيل تعطيل الاعدادت — مفاتيح الإعدادات
    Error,         // خطا — الحالات الفاشلة
    AppStart,      // صوت البداية — عند ظهور المنزل بعد الإسبلاش
    Success,       // نجاح عملية — (ربط الحساب / تفعيل)
    Purchase,      // نجاح شراء عنصر — شراء/فتح عنصر
    DownloadDone,  // تحميل الاضافة… انتهاء التحميل
    Notify,        // وصول إشعار (محجوز)
    _count
};

// Call once at boot (before the first frame) with the asset directory
// (normally "assets/sfx"). Safe to call ui_sfx_shutdown() at exit.
bool ui_audio_init(const char* dir);
void ui_audio_shutdown();

// Fire & forget — returns immediately (never blocks, audio runs on its thread).
// Both are no-ops if audio failed to init (the UI keeps silent but safe).
void ui_sfx(Sfx id);

// Volume 0..100 (default 80) for all SFX — wire to your Settings slider if wanted.
void ui_sfx_volume(int v01);
