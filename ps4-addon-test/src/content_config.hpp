#ifndef PKG_ADDON_TEST_CONTENT_CONFIG_HPP
#define PKG_ADDON_TEST_CONTENT_CONFIG_HPP

// ============================================================================
//  إعداد المحتوى الذي يثبّته التطبيق — يُترك فارغاً حتى يحدّده صاحب المشروع.
//
//  عند اختيار إضافة/عالم لاحقاً:
//    1) ارفع الملفات على GitHub Release عبر:
//         python3 tools/publish_content.py <zip> --name "<الاسم>" --trigger
//    2) اضبط القيم هنا:
//         kAddonFolderName = اسم مجلد الحزمة داخل com.mojang
//         kDownloadUrl     = رابط ملف .raw على الـ Release
// ============================================================================

namespace pkgtest {
namespace content {

// اسم الإضافة/العالم (يظهر في الإشعار والواجهة). اتركه فارغاً حتى يُحدَّد.
constexpr const char* kAddonFolderName = "";

// رابط ملف .raw على GitHub Release (PVRAW001). اتركه فارغاً حتى يُحدَّد.
constexpr const char* kDownloadUrl = "";

// ----------------------------------------------------------------------------
//  إعداد Supabase — لنظام الدخول برمز (ربط جهاز السوني بالحساب)
//  املأ القيمتين من لوحة Supabase: Settings → API
//    kSupabaseUrl      = Project URL   (مثل https://xxxx.supabase.co)
//    kSupabaseAnonKey  = anon / publishable key (المفتاح العام — ليس السري!)
//  يمكن أيضاً تعديلهما على جهاز السوني مباشرة عبر ملف:
//    /data/AddonStation/supabase.cfg  (سطر 1: الرابط — سطر 2: المفتاح)
// ----------------------------------------------------------------------------
constexpr const char* kSupabaseUrl = "";
constexpr const char* kSupabaseAnonKey = "";

} // namespace content
} // namespace pkgtest

#endif
