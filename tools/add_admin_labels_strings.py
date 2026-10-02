#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يضيف نصوص لوحة الإدارة الثابتة (الإنجليزية) إلى ملفات اللغات الثماني.

المشكلة: 12 نصاً إنجليزياً مكتوباً داخل AdminScreens.kt لا يتبع لغة التطبيق.
الحل: مفتاح لكل نص × 8 لغات، ثم استدعاؤه بـ stringResource.

الاستخدام: python3 tools/add_admin_labels_strings.py
"""
import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

# key -> {locale: value}
T = {
    "admin_kicker_dashboard": {
        "values": "Admin dashboard", "values-ar": "لوحة الإدارة", "values-es": "Panel de administración",
        "values-fr": "Tableau de bord admin", "values-pt": "Painel de administração",
        "values-ru": "Панель администратора", "values-tr": "Yönetim paneli", "values-zh": "管理面板",
    },
    "admin_kicker_content": {
        "values": "Content administration", "values-ar": "إدارة المحتوى", "values-es": "Administración de contenido",
        "values-fr": "Gestion du contenu", "values-pt": "Gestão de conteúdo",
        "values-ru": "Управление контентом", "values-tr": "İçerik yönetimi", "values-zh": "内容管理",
    },
    "admin_kicker_payments": {
        "values": "Payments", "values-ar": "المدفوعات", "values-es": "Pagos", "values-fr": "Paiements",
        "values-pt": "Pagamentos", "values-ru": "Платежи", "values-tr": "Ödemeler", "values-zh": "支付",
    },
    "admin_kicker_chat": {
        "values": "Chat moderation", "values-ar": "إدارة الشات", "values-es": "Moderación del chat",
        "values-fr": "Modération du chat", "values-pt": "Moderação do chat",
        "values-ru": "Модерация чата", "values-tr": "Sohbet moderasyonu", "values-zh": "聊天管理",
    },
    "admin_kicker_assets": {
        "values": "Asset management", "values-ar": "إدارة الأصول", "values-es": "Gestión de recursos",
        "values-fr": "Gestion des ressources", "values-pt": "Gestão de recursos",
        "values-ru": "Управление ресурсами", "values-tr": "Kaynak yönetimi", "values-zh": "资源管理",
    },
    "admin_pack_id_label": {
        "values": "Pack id (meme / Minecraft_Memes1 …)", "values-ar": "معرّف الحزمة (meme / Minecraft_Memes1 …)",
        "values-es": "Id del pack (meme / Minecraft_Memes1 …)", "values-fr": "Id du pack (meme / Minecraft_Memes1 …)",
        "values-pt": "Id do pack (meme / Minecraft_Memes1 …)", "values-ru": "Id пакета (meme / Minecraft_Memes1 …)",
        "values-tr": "Paket kimliği (meme / Minecraft_Memes1 …)", "values-zh": "包 ID (meme / Minecraft_Memes1 …)",
    },
    "admin_amount_ac_label": {
        "values": "Amount in AC", "values-ar": "المبلغ بعملات AC", "values-es": "Importe en AC",
        "values-fr": "Montant en AC", "values-pt": "Valor em AC", "values-ru": "Сумма в AC",
        "values-tr": "AC cinsinden tutar", "values-zh": "AC 金额",
    },
    "admin_price_lyd_label": {
        "values": "Price in LYD", "values-ar": "السعر بالدينار", "values-es": "Precio en LYD",
        "values-fr": "Prix en LYD", "values-pt": "Preço em LYD", "values-ru": "Цена в LYD",
        "values-tr": "Fiyat (LYD)", "values-zh": "价格（LYD）",
    },
    "admin_duration_days_label": {
        "values": "Duration (days)", "values-ar": "المدة (أيام)", "values-es": "Duración (días)",
        "values-fr": "Durée (jours)", "values-pt": "Duração (dias)", "values-ru": "Срок (дни)",
        "values-tr": "Süre (gün)", "values-zh": "时长（天）",
    },
    "admin_download_limit_label": {
        "values": "Download limit (empty = unlimited)", "values-ar": "حد التنزيل (فارغ = بلا حد)",
        "values-es": "Límite de descargas (vacío = sin límite)", "values-fr": "Limite de téléchargements (vide = illimité)",
        "values-pt": "Limite de downloads (vazio = ilimitado)", "values-ru": "Лимит загрузок (пусто = без лимита)",
        "values-tr": "İndirme sınırı (boş = sınırsız)", "values-zh": "下载上限（留空 = 不限）",
    },
    "admin_method_code_label": {
        "values": "Code", "values-ar": "الرمز", "values-es": "Código", "values-fr": "Code",
        "values-pt": "Código", "values-ru": "Код", "values-tr": "Kod", "values-zh": "代码",
    },
    "admin_method_name_label": {
        "values": "Name", "values-ar": "الاسم", "values-es": "Nombre", "values-fr": "Nom",
        "values-pt": "Nome", "values-ru": "Название", "values-tr": "Ad", "values-zh": "名称",
    },
}


def esc(v):
    return v.replace("'", "\\'")


def main():
    for locale, _ in [("values", 0)]:
        pass
    locales = ["values", "values-ar", "values-es", "values-fr", "values-pt",
               "values-ru", "values-tr", "values-zh"]
    for loc in locales:
        path = os.path.join(RES, loc, "strings.xml")
        s = io.open(path, encoding="utf-8").read()
        added = 0
        for key, table in T.items():
            if f'name="{key}"' in s:
                continue
            val = esc(table[loc])
            line = f'    <string name="{key}">{val}</string>\n'
            assert "</resources>" in s
            s = s.replace("</resources>", line + "</resources>", 1)
            added += 1
        io.open(path, "w", encoding="utf-8").write(s)
        print(f"  ✓ {loc:<12} +{added} نصاً")
    print("✓ أُضيفت النصوص إلى اللغات الثماني")


if __name__ == "__main__":
    main()
