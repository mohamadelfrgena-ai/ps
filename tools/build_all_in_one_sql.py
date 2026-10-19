#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
يبني ملفاً واحداً جامعاً `supabase/00_ALL_IN_ONE.sql` من ملفات SQL المرقّمة،
حتى يستطيع المستخدم نسخه مرة واحدة ولصقه في Supabase SQL Editor بدل سبع مرات.

الترتيب ملزم: 01 → 02 → 03 → 04 → 05 → 06 → 07 → 10
(08 إعداد الأدمن يُنفَّذ يدوياً بعد إنشاء الحساب، و09 بوابات الدفع اختياري.)
12_avatars.sql يضيف 25 أفاتاراً إلى site_assets (قديمة — يستبدلها 15_assets_v2.sql).
15_assets_v2.sql في النهاية: يحذف كل الأصول القديمة (أفاتارات/ملصقات/أدوات) ويزرع الدفعة
الجديدة (492 أصلاً في 21 قسماً حسب مجلدات المصدر) — لذا ينتهي التجهيز بالأصول الصحيحة.
11_grants.sql في النهاية وهو إلزامي لمشاريع Supabase الجديدة (منح صلاحيات anon/authenticated).

الاستخدام: python3 tools/build_all_in_one_sql.py
"""
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUPABASE = os.path.join(ROOT, "supabase")
ORDER = [
    "01_schema.sql",
    "02_functions_core.sql",
    "03_functions_chat.sql",
    "04_rls.sql",
    "05_storage.sql",
    "06_seed.sql",
    "07_seed_assets.sql",
    "12_avatars.sql",
    "10_realtime.sql",
    "11_grants.sql",
    "13_channels_icons.sql",
    "14_redeem_codes.sql",
    "15_assets_v2.sql",
    "16_language_room_names.sql",
    "17_message_delete_window.sql",
    "18_media_admin_only.sql",
    "19_admin_panel_guard.sql",
    "20_sticker_with_text.sql",
    "21_banners.sql",
    "22_plan_prices.sql",
    "23_payment_order_usd.sql",
    "24_free_default_avatar.sql",
    "25_fix_send_chat_message_unnest.sql",
    "26_fix_site_assets_sequence.sql",
    "27_admin_only_assets.sql",
    "28_chat_cleanup.sql",
    "29_chat_moderation_rooms.sql",
    "30_chat_members_only.sql",
    "31_single_free_avatar.sql",
    "32_onefree_canonical_path.sql",
    "33_user_block.sql",
    "34_push_notifications.sql",
    "35_push_live.sql",
    "36_profile_stats.sql",
    "37_profile_reports.sql",
    "38_user_mute.sql",
    "39_notif_revert.sql",
    "40_admin_asset_privacy.sql",
    "41_console_link_codes.sql",
    "42_visible_profiles_access.sql",
    # 43 is a production-data repair that requires an already-uploaded object;
    # it must never run as part of a fresh all-in-one installation.
    "44_fix_profile_asset_guard.sql",
    "45_single_attached_free_avatar.sql",
]

HEADER = """-- ============================================================================
--  AddonStation Mobile — ملف التجهيز الشامل (All-in-One)
--  يولّده tools/build_all_in_one_sql.py — لا تعدّله يدوياً، عدّل ملفات المصدر.
--
--  الطريقة الأسرع: انسخ هذا الملف كاملاً والصقه في Supabase → SQL Editor → Run.
--  إن ظهر خطأ في أي سطر، نفّذ الملفات الأصلية واحداً واحداً بنفس الترتيب:
--      01_schema → 02_functions_core → 03_functions_chat → 04_rls
--      → 05_storage → 06_seed → 07_seed_assets → 12_avatars → 10_realtime → 11_grants
--      → 13_channels_icons (أسماء الغرف بلا إيموجي)
--      → 14_redeem_codes (أكواد هدايا AC: توليد واستبدال)
--      → 15_assets_v2 (الأصول الجديدة: يستبدل كل الأفاتارات والملصقات والأدوات)
--      → 16_language_room_names (أسماء غرف اللغة بلغتها: العربية · Русский · 中文 …)
--      → 17_message_delete_window → 18_media_admin_only → 19_admin_panel_guard
--      → 20_sticker_with_text (النص مع الملصق في رسالة واحدة)
--      → 21_banners (أغلفة الملف الشخصي: فقرتان · مقفولة باشتراك · GIF متحرّك)
--      → 22_plan_prices (أسعار الاشتراكات: Epic 2.99$ · Legendary 5.99$)
--      → 23_payment_order_usd (مبلغ الطلب بالدولار + عملة الطلب حسب البوابة)
--      → 24_free_default_avatar (الصورة الافتراضية المجانية onefree لكل حساب جديد)
--      → 25_fix_send_chat_message_unnest (إصلاح column "unnest" does not exist عند الإرسال)
--      → 26_fix_site_assets_sequence (صلاحية تسلسل site_assets للتسجيل عبر REST)
--      → 27_admin_only_assets (Enchanted Bedrock · PFP 4 · أغلفة الأدمن = admin فقط)
--      → 28_chat_cleanup (تنظيف غرف الشات: حذف/إخفاء الرسائل — أقسام اختيارية)
--      → 40_admin_asset_privacy (أصول الأدمن لا تظهر لغيره + مخزن خاص)
--      → 41_console_link_codes (ربط PlayStation الآمن برمز مؤقت وموافقة صريحة)
--      → 42_visible_profiles_access → 44_fix_profile_asset_guard
--      → 45_single_attached_free_avatar
--  (43_content_download_integrity إصلاح بيانات إنتاجية مستقل، وليس للتنصيب الجديد.)
--  ثم بعد إنشاء حسابك من التطبيق: 08_admin_bootstrap.sql
--  و(اختيارياً) 09_gateway_functions.sql لبوابات الدفع الإلكتروني.
--
--  كل الملفات آمنة لإعادة التنفيذ (idempotent).
-- ============================================================================

"""


def main() -> int:
    parts = [HEADER]
    for name in ORDER:
        path = os.path.join(SUPABASE, name)
        if not os.path.exists(path):
            print(f"✗ الملف مفقود: {name}")
            return 1
        body = io.open(path, encoding="utf-8").read().rstrip() + "\n"
        parts.append("\n\n-- ############################################################################\n")
        parts.append(f"-- ##  القسم: {name}\n")
        parts.append("-- ############################################################################\n\n")
        parts.append(body)
    out_path = os.path.join(SUPABASE, "00_ALL_IN_ONE.sql")
    io.open(out_path, "w", encoding="utf-8").write("".join(parts))
    size = os.path.getsize(out_path)
    lines = io.open(out_path, encoding="utf-8").read().count("\n")
    print(f"✓ {out_path}  ({lines} سطراً، {size // 1024} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
