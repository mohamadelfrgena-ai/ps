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

} // namespace content
} // namespace pkgtest

#endif
