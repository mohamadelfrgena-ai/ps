#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يضيف نصوص أكواد هدايا AC (واجهة المستخدم + لوحة الإدارة) إلى ملفات اللغات الثمانية،
ويصلح الصياغة العربية في محفظة AC ويُزيل الإيموجي المتبقي منها.
الأداة وصفية (idempotent): إعادة تشغيلها لا تُكرّر النصوص.
"""
import io
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

T = {
"ar": {
 "ac_redeem_title": "كود هدية AC",
 "ac_redeem_hint": "الكود من 8 خانات: حروف كبيرة وصغيرة ورقمان",
 "ac_redeem_action": "استبدال الكود",
 "ac_redeem_success": "تمت إضافة %1$s AC إلى رصيدك.",
 "ac_redeem_invalid": "الكود غير صحيح. تحقّق من الحروف والأرقام.",
 "ac_redeem_used": "لقد استبدلت هذا الكود من قبل.",
 "ac_redeem_expired": "انتهت صلاحية هذا الكود.",
 "ac_redeem_off": "هذا الكود غير مُفعَّل حالياً.",
 "ac_redeem_exhausted": "انتهى عدد مرات استخدام هذا الكود.",
 "ac_redeem_sign_in": "سجّل الدخول أولاً لاستبدال الكود.",
 "ac_freq_once": "مرة واحدة",
 "ac_freq_daily": "يومياً",
 "ac_freq_streak": "سلسلة أيام",
 "ac_freq_manual": "من الإدارة",
 "ac_kind_code": "كود هدية",
 "ac_kind_task": "مهمة AC",
 "ac_kind_daily": "مكافأة يومية",
 "ac_kind_streak": "مكافأة سلسلة",
 "ac_kind_admin": "من الإدارة",
 "ac_kind_unlock": "فتح محتوى",
 "ac_kind_event": "مكافأة حدث",
 "ac_kind_welcome": "مكافأة ترحيب",
 "admin_codes_title": "أكواد هدايا AC",
 "admin_codes_amount": "قيمة الكود (AC)",
 "admin_codes_count": "عدد الأكواد",
 "admin_codes_generate": "توليد الأكواد",
 "admin_codes_created": "تم توليد %1$s كوداً — انسخها الآن:",
 "admin_codes_hint": "انسخ الأكواد وأرسلها لمن تريد. كل كود يُستخدم مرة واحدة لكل مستخدم.",
 "admin_codes_latest": "أحدث الأكواد",
},
"en": {
 "ac_redeem_title": "AC gift code",
 "ac_redeem_hint": "8 characters: uppercase, lowercase and two digits",
 "ac_redeem_action": "Redeem code",
 "ac_redeem_success": "Added %1$s AC to your balance.",
 "ac_redeem_invalid": "This code is not valid. Check the letters and digits.",
 "ac_redeem_used": "You have already redeemed this code.",
 "ac_redeem_expired": "This code has expired.",
 "ac_redeem_off": "This code is currently disabled.",
 "ac_redeem_exhausted": "This code reached its usage limit.",
 "ac_redeem_sign_in": "Sign in first to redeem a code.",
 "ac_freq_once": "one time",
 "ac_freq_daily": "daily",
 "ac_freq_streak": "day streak",
 "ac_freq_manual": "manual",
 "ac_kind_code": "Gift code",
 "ac_kind_task": "AC task",
 "ac_kind_daily": "Daily reward",
 "ac_kind_streak": "Streak reward",
 "ac_kind_admin": "Admin grant",
 "ac_kind_unlock": "Content unlock",
 "ac_kind_event": "Event reward",
 "ac_kind_welcome": "Welcome bonus",
 "admin_codes_title": "AC gift codes",
 "admin_codes_amount": "Code value (AC)",
 "admin_codes_count": "Number of codes",
 "admin_codes_generate": "Generate codes",
 "admin_codes_created": "Generated %1$s codes — copy them now:",
 "admin_codes_hint": "Copy the codes and send them to whoever you want. Each code works once per member.",
 "admin_codes_latest": "Latest codes",
},
"es": {
 "ac_redeem_title": "Código de regalo AC",
 "ac_redeem_hint": "8 caracteres: mayúsculas, minúsculas y dos dígitos",
 "ac_redeem_action": "Canjear código",
 "ac_redeem_success": "Se añadieron %1$s AC a tu saldo.",
 "ac_redeem_invalid": "El código no es válido. Revisa las letras y los dígitos.",
 "ac_redeem_used": "Ya canjeaste este código.",
 "ac_redeem_expired": "Este código ha caducado.",
 "ac_redeem_off": "Este código está desactivado.",
 "ac_redeem_exhausted": "Este código alcanzó su límite de usos.",
 "ac_redeem_sign_in": "Inicia sesión para canjear un código.",
 "ac_freq_once": "una vez",
 "ac_freq_daily": "diario",
 "ac_freq_streak": "racha de días",
 "ac_freq_manual": "manual",
 "ac_kind_code": "Código de regalo",
 "ac_kind_task": "Tarea AC",
 "ac_kind_daily": "Recompensa diaria",
 "ac_kind_streak": "Recompensa de racha",
 "ac_kind_admin": "Concedido por admin",
 "ac_kind_unlock": "Desbloqueo",
 "ac_kind_event": "Recompensa de evento",
 "ac_kind_welcome": "Bono de bienvenida",
 "admin_codes_title": "Códigos de regalo AC",
 "admin_codes_amount": "Valor del código (AC)",
 "admin_codes_count": "Cantidad de códigos",
 "admin_codes_generate": "Generar códigos",
 "admin_codes_created": "Se generaron %1$s códigos — cópialos ahora:",
 "admin_codes_hint": "Copia los códigos y envíalos a quien quieras. Cada código se usa una vez por miembro.",
 "admin_codes_latest": "Últimos códigos",
},
"fr": {
 "ac_redeem_title": "Code cadeau AC",
 "ac_redeem_hint": "8 caractères : majuscules, minuscules et deux chiffres",
 "ac_redeem_action": "Utiliser le code",
 "ac_redeem_success": "%1$s AC ajoutés à ton solde.",
 "ac_redeem_invalid": "Ce code n’est pas valide. Vérifie les lettres et les chiffres.",
 "ac_redeem_used": "Tu as déjà utilisé ce code.",
 "ac_redeem_expired": "Ce code a expiré.",
 "ac_redeem_off": "Ce code est désactivé pour le moment.",
 "ac_redeem_exhausted": "Ce code a atteint sa limite d’utilisation.",
 "ac_redeem_sign_in": "Connecte-toi d’abord pour utiliser un code.",
 "ac_freq_once": "une fois",
 "ac_freq_daily": "quotidien",
 "ac_freq_streak": "série de jours",
 "ac_freq_manual": "manuel",
 "ac_kind_code": "Code cadeau",
 "ac_kind_task": "Tâche AC",
 "ac_kind_daily": "Récompense quotidienne",
 "ac_kind_streak": "Récompense de série",
 "ac_kind_admin": "Accordé par l’admin",
 "ac_kind_unlock": "Déblocage",
 "ac_kind_event": "Récompense d’événement",
 "ac_kind_welcome": "Bonus de bienvenue",
 "admin_codes_title": "Codes cadeaux AC",
 "admin_codes_amount": "Valeur du code (AC)",
 "admin_codes_count": "Nombre de codes",
 "admin_codes_generate": "Générer les codes",
 "admin_codes_created": "%1$s codes générés — copie-les maintenant :",
 "admin_codes_hint": "Copie les codes et envoie-les à qui tu veux. Chaque code sert une fois par membre.",
 "admin_codes_latest": "Derniers codes",
},
"pt": {
 "ac_redeem_title": "Código de presente AC",
 "ac_redeem_hint": "8 caracteres: maiúsculas, minúsculas e dois dígitos",
 "ac_redeem_action": "Resgatar código",
 "ac_redeem_success": "%1$s AC adicionados ao seu saldo.",
 "ac_redeem_invalid": "Este código não é válido. Confira as letras e os dígitos.",
 "ac_redeem_used": "Você já resgatou este código.",
 "ac_redeem_expired": "Este código expirou.",
 "ac_redeem_off": "Este código está desativado no momento.",
 "ac_redeem_exhausted": "Este código atingiu o limite de usos.",
 "ac_redeem_sign_in": "Entre na conta primeiro para resgatar um código.",
 "ac_freq_once": "uma vez",
 "ac_freq_daily": "diário",
 "ac_freq_streak": "sequência de dias",
 "ac_freq_manual": "manual",
 "ac_kind_code": "Código de presente",
 "ac_kind_task": "Tarefa AC",
 "ac_kind_daily": "Recompensa diária",
 "ac_kind_streak": "Recompensa de sequência",
 "ac_kind_admin": "Concedido pela admin",
 "ac_kind_unlock": "Desbloqueio",
 "ac_kind_event": "Recompensa de evento",
 "ac_kind_welcome": "Bônus de boas-vindas",
 "admin_codes_title": "Códigos de presente AC",
 "admin_codes_amount": "Valor do código (AC)",
 "admin_codes_count": "Quantidade de códigos",
 "admin_codes_generate": "Gerar códigos",
 "admin_codes_created": "%1$s códigos gerados — copie agora:",
 "admin_codes_hint": "Copie os códigos e envie para quem quiser. Cada código é de uso único por membro.",
 "admin_codes_latest": "Últimos códigos",
},
"ru": {
 "ac_redeem_title": "Подарочный код AC",
 "ac_redeem_hint": "8 символов: заглавные, строчные и две цифры",
 "ac_redeem_action": "Применить код",
 "ac_redeem_success": "На баланс добавлено %1$s AC.",
 "ac_redeem_invalid": "Неверный код. Проверьте буквы и цифры.",
 "ac_redeem_used": "Вы уже использовали этот код.",
 "ac_redeem_expired": "Срок действия кода истёк.",
 "ac_redeem_off": "Этот код сейчас отключён.",
 "ac_redeem_exhausted": "Код достиг лимита использований.",
 "ac_redeem_sign_in": "Войдите в аккаунт, чтобы применить код.",
 "ac_freq_once": "один раз",
 "ac_freq_daily": "ежедневно",
 "ac_freq_streak": "серия дней",
 "ac_freq_manual": "вручную",
 "ac_kind_code": "Подарочный код",
 "ac_kind_task": "Задание AC",
 "ac_kind_daily": "Ежедневная награда",
 "ac_kind_streak": "Награда за серию",
 "ac_kind_admin": "Начислено админом",
 "ac_kind_unlock": "Открытие контента",
 "ac_kind_event": "Награда за событие",
 "ac_kind_welcome": "Приветственный бонус",
 "admin_codes_title": "Подарочные коды AC",
 "admin_codes_amount": "Номинал кода (AC)",
 "admin_codes_count": "Количество кодов",
 "admin_codes_generate": "Создать коды",
 "admin_codes_created": "Создано кодов: %1$s — скопируйте сейчас:",
 "admin_codes_hint": "Скопируйте коды и отправьте кому нужно. Каждый код работает один раз на участника.",
 "admin_codes_latest": "Последние коды",
},
"tr": {
 "ac_redeem_title": "AC hediye kodu",
 "ac_redeem_hint": "8 karakter: büyük, küçük harf ve iki rakam",
 "ac_redeem_action": "Kodu kullan",
 "ac_redeem_success": "Bakiyene %1$s AC eklendi.",
 "ac_redeem_invalid": "Bu kod geçerli değil. Harf ve rakamları kontrol et.",
 "ac_redeem_used": "Bu kodu zaten kullandın.",
 "ac_redeem_expired": "Bu kodun süresi doldu.",
 "ac_redeem_off": "Bu kod şu anda devre dışı.",
 "ac_redeem_exhausted": "Bu kod kullanım sınırına ulaştı.",
 "ac_redeem_sign_in": "Kodu kullanmak için önce giriş yap.",
 "ac_freq_once": "bir kez",
 "ac_freq_daily": "günlük",
 "ac_freq_streak": "gün serisi",
 "ac_freq_manual": "elle",
 "ac_kind_code": "Hediye kodu",
 "ac_kind_task": "AC görevi",
 "ac_kind_daily": "Günlük ödül",
 "ac_kind_streak": "Seri ödülü",
 "ac_kind_admin": "Yönetici verdi",
 "ac_kind_unlock": "İçerik açma",
 "ac_kind_event": "Etkinlik ödülü",
 "ac_kind_welcome": "Hoş geldin bonusu",
 "admin_codes_title": "AC hediye kodları",
 "admin_codes_amount": "Kod değeri (AC)",
 "admin_codes_count": "Kod sayısı",
 "admin_codes_generate": "Kod üret",
 "admin_codes_created": "%1$s kod üretildi — şimdi kopyala:",
 "admin_codes_hint": "Kodları kopyala ve istediğin kişiye gönder. Her kod üye başına bir kez çalışır.",
 "admin_codes_latest": "Son kodlar",
},
"zh": {
 "ac_redeem_title": "AC 礼品码",
 "ac_redeem_hint": "8 位：大写字母、小写字母和两个数字",
 "ac_redeem_action": "兑换代码",
 "ac_redeem_success": "已为余额添加 %1$s AC。",
 "ac_redeem_invalid": "该代码无效，请检查字母和数字。",
 "ac_redeem_used": "你已经兑换过该代码。",
 "ac_redeem_expired": "该代码已过期。",
 "ac_redeem_off": "该代码当前已停用。",
 "ac_redeem_exhausted": "该代码已达到使用次数上限。",
 "ac_redeem_sign_in": "请先登录再兑换代码。",
 "ac_freq_once": "一次",
 "ac_freq_daily": "每日",
 "ac_freq_streak": "连续天数",
 "ac_freq_manual": "手动",
 "ac_kind_code": "礼品码",
 "ac_kind_task": "AC 任务",
 "ac_kind_daily": "每日奖励",
 "ac_kind_streak": "连续奖励",
 "ac_kind_admin": "管理员发放",
 "ac_kind_unlock": "解锁内容",
 "ac_kind_event": "活动奖励",
 "ac_kind_welcome": "欢迎奖励",
 "admin_codes_title": "AC 礼品码",
 "admin_codes_amount": "代码面值（AC）",
 "admin_codes_count": "代码数量",
 "admin_codes_generate": "生成代码",
 "admin_codes_created": "已生成 %1$s 个代码 — 请立即复制：",
 "admin_codes_hint": "复制代码并发送给你想给的人。每个代码每位成员只能使用一次。",
 "admin_codes_latest": "最新代码",
},
}

# إصلاح الصياغة داخل محفظة AC + إزالة الإيموجي المتبقي
FIXES = {
 "ar": {
   "ac_desc": "AC هي عملة مكافآت داخلية. اكسبها من النشاط اليومي، واستخدمها لفتح الإضافات والعوالم المؤهَّلة. لا يمكن شراء AC بالنقود ولا تحويلها إلى نقود.",
   "ac_claim": "استلام",
   "ac_claimed": "تم الاستلام",
   "ac_history": "سجل العمليات",
   "ac_no_transactions": "لا توجد عمليات على AC حتى الآن.",
   "ac_unlocked": "تم فتحه",
 },
 "en": {"ac_unlocked": "Unlocked", "ac_history": "Transaction history"},
 "es": {"ac_unlocked": "Desbloqueado"},
 "fr": {"ac_unlocked": "Débloqué"},
 "pt": {"ac_unlocked": "Desbloqueado"},
 "ru": {"ac_unlocked": "Открыто"},
 "tr": {"ac_unlocked": "Açıldı"},
 "zh": {"ac_unlocked": "已解锁"},
}

FOLDER = {"ar": "values-ar", "en": "values", "es": "values-es", "fr": "values-fr",
          "pt": "values-pt", "ru": "values-ru", "tr": "values-tr", "zh": "values-zh"}


def upsert(text, key, value):
    """يضيف السطر أو يحدّثه إن وُجد."""
    line = f'    <string name="{key}">{value}</string>'
    pattern = re.compile(r'^[ \t]*<string name="%s">.*?</string>[ \t]*$' % re.escape(key), re.M)
    if pattern.search(text):
        return pattern.sub(lambda _m: line, text, count=1)
    return text.replace(f'    <string name="ac_unlocked">', line + "\n" + f'    <string name="ac_unlocked">', 1)


def main():
    for code, values in T.items():
        path = os.path.join(RES, FOLDER[code], "strings.xml")
        text = io.open(path, encoding="utf-8").read()
        before = text

        # الإصلاحات والتحديثات
        for key, value in FIXES.get(code, {}).items():
            pattern = re.compile(r'^[ \t]*<string name="%s">.*?</string>[ \t]*$' % re.escape(key), re.M)
            assert pattern.search(text), f"{code}:{key}"
            text = pattern.sub(lambda _m, v=value, k=key: f'    <string name="{k}">{v}</string>', text, count=1)

        # نصوص استبدال الكود: تُدرج قبل ac_unlocked لتظهر مع محفظة AC
        ac_keys = {k: v for k, v in values.items() if k.startswith(("ac_redeem", "ac_freq", "ac_kind"))}
        for key, value in ac_keys.items():
            text = upsert(text, key, value)

        # نصوص الإدارة: تُدرج بعد admin_reward
        admin_keys = {k: v for k, v in values.items() if k.startswith("admin_codes")}
        block = "\n".join(f'    <string name="{k}">{v}</string>' for k, v in admin_keys.items())
        anchor = re.search(r'^[ \t]*<string name="admin_reward">.*?</string>[ \t]*$', text, re.M)
        assert anchor, f"{code}: admin_reward غير موجود"
        text = text[:anchor.end()] + "\n" + block + text[anchor.end():]

        if text != before:
            io.open(path, "w", encoding="utf-8").write(text)
        print(f"✓ {FOLDER[code]:12} +{len(values)} نصاً")


if __name__ == "__main__":
    main()
