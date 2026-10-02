#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""نصوص «منح AC لمستخدم» في لوحة الإدارة — إلى ملفات اللغات الثماني.

الميزة: يعطي الأدمن أي مبلغ (أي رقم) لأي مستخدم من لوحة التحكم، ويشوف
النتيجة (الرصيد الجديد) وآخر العمليات داخل اللوحة نفسها.

الاستخدام: python3 tools/add_admin_give_ac_strings.py
"""
import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

LOCALES = ["values", "values-ar", "values-es", "values-fr", "values-pt",
           "values-ru", "values-tr", "values-zh"]

# key -> {locale: value}   (%1$d = المبلغ ، %2$s = اسم المستخدم ، %2$d = عدد)
T = {
    "admin_give_ac_title": {
        "values": "Give AC to a user", "values-ar": "منح AC لمستخدم",
        "values-es": "Dar AC a un usuario", "values-fr": "Donner des AC à un utilisateur",
        "values-pt": "Dar AC a um usuário", "values-ru": "Выдать AC пользователю",
        "values-tr": "Bir kullanıcıya AC ver", "values-zh": "给用户发放 AC",
    },
    "admin_give_ac_amount": {
        "values": "Amount (any number)", "values-ar": "المبلغ (أي رقم)",
        "values-es": "Cantidad (cualquier número)", "values-fr": "Montant (n'importe quel nombre)",
        "values-pt": "Valor (qualquer número)", "values-ru": "Сумма (любое число)",
        "values-tr": "Miktar (herhangi bir sayı)", "values-zh": "金额（任意数字）",
    },
    "admin_give_ac_note": {
        "values": "Reason (the user sees it)", "values-ar": "السبب (يظهر للمستخدم)",
        "values-es": "Motivo (lo ve el usuario)", "values-fr": "Motif (visible par l'utilisateur)",
        "values-pt": "Motivo (o usuário vê)", "values-ru": "Причина (видна пользователю)",
        "values-tr": "Sebep (kullanıcı görür)", "values-zh": "原因（用户可见）",
    },
    "admin_give_ac_quick": {
        "values": "Quick amounts", "values-ar": "مبالغ سريعة",
        "values-es": "Cantidades rápidas", "values-fr": "Montants rapides",
        "values-pt": "Valores rápidos", "values-ru": "Быстрые суммы",
        "values-tr": "Hızlı miktarlar", "values-zh": "快捷金额",
    },
    "admin_give_ac_give": {
        "values": "Give", "values-ar": "منح", "values-es": "Dar", "values-fr": "Donner",
        "values-pt": "Dar", "values-ru": "Выдать", "values-tr": "Ver", "values-zh": "发放",
    },
    "admin_give_ac_deduct": {
        "values": "Deduct", "values-ar": "خصم", "values-es": "Restar", "values-fr": "Retirer",
        "values-pt": "Descontar", "values-ru": "Списать", "values-tr": "Düş", "values-zh": "扣除",
    },
    "admin_give_ac_all": {
        "values": "Give to every user", "values-ar": "منح لكل المستخدمين",
        "values-es": "Dar a todos los usuarios", "values-fr": "Donner à tous les utilisateurs",
        "values-pt": "Dar a todos os usuários", "values-ru": "Выдать всем пользователям",
        "values-tr": "Tüm kullanıcılara ver", "values-zh": "发给所有用户",
    },
    "admin_give_ac_select": {
        "values": "Pick a user from the list below", "values-ar": "اختر مستخدماً من القائمة بالأسفل",
        "values-es": "Elige un usuario de la lista", "values-fr": "Choisissez un utilisateur dans la liste",
        "values-pt": "Escolha um usuário da lista", "values-ru": "Выберите пользователя из списка",
        "values-tr": "Listeden bir kullanıcı seçin", "values-zh": "从下方列表中选择用户",
    },
    "admin_give_ac_confirm": {
        "values": "Give %1$d AC to %2$s?", "values-ar": "منح %1$d AC للمستخدم %2$s؟",
        "values-es": "¿Dar %1$d AC a %2$s?", "values-fr": "Donner %1$d AC à %2$s ?",
        "values-pt": "Dar %1$d AC a %2$s?", "values-ru": "Выдать %1$d AC пользователю %2$s?",
        "values-tr": "%2$s kullanıcısına %1$d AC verilsin mi?", "values-zh": "给 %2$s 发放 %1$d AC？",
    },
    "admin_give_ac_confirm_deduct": {
        "values": "Deduct %1$d AC from %2$s?", "values-ar": "خصم %1$d AC من %2$s؟",
        "values-es": "¿Restar %1$d AC a %2$s?", "values-fr": "Retirer %1$d AC à %2$s ?",
        "values-pt": "Descontar %1$d AC de %2$s?", "values-ru": "Списать %1$d AC у %2$s?",
        "values-tr": "%2$s kullanıcısından %1$d AC düşülsün mü?", "values-zh": "从 %2$s 扣除 %1$d AC？",
    },
    "admin_give_ac_confirm_all": {
        "values": "Give %1$d AC to every user?", "values-ar": "منح %1$d AC لكل المستخدمين؟",
        "values-es": "¿Dar %1$d AC a todos los usuarios?", "values-fr": "Donner %1$d AC à tous les utilisateurs ?",
        "values-pt": "Dar %1$d AC a todos os usuários?", "values-ru": "Выдать %1$d AC всем пользователям?",
        "values-tr": "Tüm kullanıcılara %1$d AC verilsin mi?", "values-zh": "给所有用户发放 %1$d AC？",
    },
    "admin_give_ac_done": {
        "values": "+%1$d AC · balance %2$d", "values-ar": "+%1$d AC · الرصيد %2$d",
        "values-es": "+%1$d AC · saldo %2$d", "values-fr": "+%1$d AC · solde %2$d",
        "values-pt": "+%1$d AC · saldo %2$d", "values-ru": "+%1$d AC · баланс %2$d",
        "values-tr": "+%1$d AC · bakiye %2$d", "values-zh": "+%1$d AC · 余额 %2$d",
    },
    "admin_give_ac_done_deduct": {
        "values": "−%1$d AC · balance %2$d", "values-ar": "−%1$d AC · الرصيد %2$d",
        "values-es": "−%1$d AC · saldo %2$d", "values-fr": "−%1$d AC · solde %2$d",
        "values-pt": "−%1$d AC · saldo %2$d", "values-ru": "−%1$d AC · баланс %2$d",
        "values-tr": "−%1$d AC · bakiye %2$d", "values-zh": "−%1$d AC · 余额 %2$d",
    },
    "admin_give_ac_done_all": {
        "values": "%1$d AC given to %2$d users", "values-ar": "تم منح %1$d AC لـ %2$d مستخدم",
        "values-es": "%1$d AC dados a %2$d usuarios", "values-fr": "%1$d AC donnés à %2$d utilisateurs",
        "values-pt": "%1$d AC dados a %2$d usuários", "values-ru": "%1$d AC выдано %2$d пользователям",
        "values-tr": "%2$d kullanıcıya %1$d AC verildi", "values-zh": "已向 %2$d 位用户发放 %1$d AC",
    },
    "admin_give_ac_invalid": {
        "values": "Type a valid amount", "values-ar": "اكتب مبلغاً صحيحاً",
        "values-es": "Escribe una cantidad válida", "values-fr": "Saisissez un montant valide",
        "values-pt": "Escreva um valor válido", "values-ru": "Введите корректную сумму",
        "values-tr": "Geçerli bir miktar yazın", "values-zh": "请输入有效金额",
    },
    "admin_give_ac_history": {
        "values": "Latest AC operations", "values-ar": "آخر عمليات AC",
        "values-es": "Últimas operaciones AC", "values-fr": "Dernières opérations AC",
        "values-pt": "Últimas operações AC", "values-ru": "Последние операции AC",
        "values-tr": "Son AC işlemleri", "values-zh": "最近的 AC 操作",
    },
    "admin_give_ac_balance_after": {
        "values": "balance", "values-ar": "الرصيد", "values-es": "saldo", "values-fr": "solde",
        "values-pt": "saldo", "values-ru": "баланс", "values-tr": "bakiye", "values-zh": "余额",
    },
    "admin_give_ac_none": {
        "values": "No AC operations yet", "values-ar": "لا توجد عمليات AC بعد",
        "values-es": "Aún no hay operaciones AC", "values-fr": "Aucune opération AC",
        "values-pt": "Ainda não há operações AC", "values-ru": "Операций AC пока нет",
        "values-tr": "Henüz AC işlemi yok", "values-zh": "暂无 AC 操作",
    },
    "admin_give_ac_short": {
        "values": "+AC", "values-ar": "+AC", "values-es": "+AC", "values-fr": "+AC",
        "values-pt": "+AC", "values-ru": "+AC", "values-tr": "+AC", "values-zh": "+AC",
    },
    "admin_plan_days": {
        "values": "+30 days", "values-ar": "+30 يوم", "values-es": "+30 días",
        "values-fr": "+30 jours", "values-pt": "+30 dias", "values-ru": "+30 дней",
        "values-tr": "+30 gün", "values-zh": "+30 天",
    },
    "admin_ac_tools": {
        "values": "AC management and grants", "values-ar": "إدارة AC ومنح الأرصدة",
        "values-es": "Gestión de AC y concesiones", "values-fr": "Gestion des AC et attributions",
        "values-pt": "Gestão de AC e concessões", "values-ru": "Управление AC и начисления",
        "values-tr": "AC yönetimi ve hediyeler", "values-zh": "AC 管理与发放",
    },
    "admin_tips_title": {
        "values": "Creator tips", "values-ar": "نصائح صنّاع المحتوى",
        "values-es": "Propinas de creadores", "values-fr": "Pourboires des créateurs",
        "values-pt": "Gorjetas de criadores", "values-ru": "Чаевые создателям",
        "values-tr": "İçerik üreticisi bahşişleri", "values-zh": "创作者小费",
    },
    "admin_give_ac_no_users": {
        "values": "No matching user", "values-ar": "لا يوجد مستخدم مطابق",
        "values-es": "Ningún usuario coincide", "values-fr": "Aucun utilisateur trouvé",
        "values-pt": "Nenhum utilizador encontrado", "values-ru": "Подходящих пользователей нет",
        "values-tr": "Eşleşen kullanıcı yok", "values-zh": "没有匹配的用户",
    },
    "admin_reason_reward": {
        "values": "Admin reward", "values-ar": "مكافأة من الإدارة",
        "values-es": "Recompensa del admin", "values-fr": "Récompense admin",
        "values-pt": "Recompensa do admin", "values-ru": "Награда от администратора",
        "values-tr": "Yönetici ödülü", "values-zh": "管理员奖励",
    },
    "admin_reason_adjust": {
        "values": "Admin adjustment", "values-ar": "تعديل من الإدارة",
        "values-es": "Ajuste del admin", "values-fr": "Ajustement admin",
        "values-pt": "Ajuste do admin", "values-ru": "Корректировка администратора",
        "values-tr": "Yönetici düzeltmesi", "values-zh": "管理员调整",
    },
}


def esc(v: str) -> str:
    return v.replace("'", "\\'").replace("&", "&amp;").replace("<", "&lt;")


def main():
    for loc in LOCALES:
        path = os.path.join(RES, loc, "strings.xml")
        s = io.open(path, encoding="utf-8").read()
        added = 0
        for key, table in T.items():
            if 'name="%s"' % key in s:
                continue
            line = '    <string name="%s">%s</string>\n' % (key, esc(table[loc]))
            s = s.replace("</resources>", line + "</resources>", 1)
            added += 1
        io.open(path, "w", encoding="utf-8").write(s)
        print("  ✓ %-12s +%d نصاً" % (loc, added))
    print("✓ نصوص «منح AC» في اللغات الثماني")


if __name__ == "__main__":
    main()
