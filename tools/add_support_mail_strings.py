#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يضيف نصوص مراسلة الدعم (فتح البريد تلقائياً على silverfoxinteractive@gmail.com)
إلى ملفات اللغات الثمانية، ويصحّح البريد القديم في نصوص الدعم.
وصفية: إعادة تشغيلها لا تُكرّر شيئاً.
"""
import io
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

SUPPORT_EMAIL = "silverfoxinteractive@gmail.com"

SUBJECTS = {
 "ar": {"set_support_mail_help": "AddonStation — طلب مساعدة",
        "set_support_mail_report": "AddonStation — بلاغ عن مشكلة",
        "set_support_mail_contact": "AddonStation — تواصل"},
 "en": {"set_support_mail_help": "AddonStation — Help request",
        "set_support_mail_report": "AddonStation — Problem report",
        "set_support_mail_contact": "AddonStation — Contact"},
 "es": {"set_support_mail_help": "AddonStation — Solicitud de ayuda",
        "set_support_mail_report": "AddonStation — Informe de problema",
        "set_support_mail_contact": "AddonStation — Contacto"},
 "fr": {"set_support_mail_help": "AddonStation — Demande d’aide",
        "set_support_mail_report": "AddonStation — Signalement de problème",
        "set_support_mail_contact": "AddonStation — Contact"},
 "pt": {"set_support_mail_help": "AddonStation — Pedido de ajuda",
        "set_support_mail_report": "AddonStation — Relato de problema",
        "set_support_mail_contact": "AddonStation — Contato"},
 "ru": {"set_support_mail_help": "AddonStation — Запрос помощи",
        "set_support_mail_report": "AddonStation — Сообщение о проблеме",
        "set_support_mail_contact": "AddonStation — Связь"},
 "tr": {"set_support_mail_help": "AddonStation — Yardım isteği",
        "set_support_mail_report": "AddonStation — Sorun bildirimi",
        "set_support_mail_contact": "AddonStation — İletişim"},
 "zh": {"set_support_mail_help": "AddonStation — 帮助请求",
        "set_support_mail_report": "AddonStation — 问题反馈",
        "set_support_mail_contact": "AddonStation — 联系"},
}

BODIES = {
 "ar": """مرحباً فريق الدعم،

(اكتب رسالتك هنا)

—————
إصدار التطبيق: %1$s
الجهاز: %2$s
أندرويد: %3$s
لغة التطبيق: %4$s
المستخدم: %5$s""",
 "en": """Hello support team,

(write your message here)

—————
App version: %1$s
Device: %2$s
Android: %3$s
App language: %4$s
User: %5$s""",
 "es": """Hola equipo de soporte:

(escribe tu mensaje aquí)

—————
Versión de la app: %1$s
Dispositivo: %2$s
Android: %3$s
Idioma de la app: %4$s
Usuario: %5$s""",
 "fr": """Bonjour l’équipe support,

(écris ton message ici)

—————
Version de l’app : %1$s
Appareil : %2$s
Android : %3$s
Langue de l’app : %4$s
Utilisateur : %5$s""",
 "pt": """Olá equipe de suporte,

(escreva sua mensagem aqui)

—————
Versão do app: %1$s
Aparelho: %2$s
Android: %3$s
Idioma do app: %4$s
Usuário: %5$s""",
 "ru": """Здравствуйте, команда поддержки!

(напишите сообщение здесь)

—————
Версия приложения: %1$s
Устройство: %2$s
Android: %3$s
Язык приложения: %4$s
Пользователь: %5$s""",
 "tr": """Merhaba destek ekibi,

(mesajını buraya yaz)

—————
Uygulama sürümü: %1$s
Cihaz: %2$s
Android: %3$s
Uygulama dili: %4$s
Kullanıcı: %5$s""",
 "zh": """你好，支持团队：

（在这里写下你的消息）

—————
应用版本：%1$s
设备：%2$s
Android：%3$s
应用语言：%4$s
用户：%5$s""",
}

NO_APP = {
 "ar": "لم يُعثر على تطبيق بريد على جهازك. راسلنا من بريدك على:\n" + SUPPORT_EMAIL,
 "en": "No email app was found on your device. Write to us from your mail at:\n" + SUPPORT_EMAIL,
 "es": "No se encontró una app de correo en tu dispositivo. Escríbenos desde tu correo a:\n" + SUPPORT_EMAIL,
 "fr": "Aucune appli de messagerie trouvée. Écris-nous depuis ta boîte mail à :\n" + SUPPORT_EMAIL,
 "pt": "Nenhum app de e-mail encontrado. Escreva para nós de seu e-mail em:\n" + SUPPORT_EMAIL,
 "ru": "Почтовое приложение не найдено. Напишите нам со своей почты на:\n" + SUPPORT_EMAIL,
 "tr": "Cihazında e-posta uygulaması bulunamadı. Bize kendi e-postandan yaz:\n" + SUPPORT_EMAIL,
 "zh": "设备上未找到邮件应用。请用你的邮箱写信给我们：\n" + SUPPORT_EMAIL,
}

FOLDER = {"ar": "values-ar", "en": "values", "es": "values-es", "fr": "values-fr",
          "pt": "values-pt", "ru": "values-ru", "tr": "values-tr", "zh": "values-zh"}

# ملاحظات الدعم القديمة كانت تذكر بريداً وهمياً — تُصحَّح هنا
NOTES = {
 "ar": {"set_support_note_help": "لأي سؤال عن التحميل أو الخطط أو الشات اضغط هذا الخيار وسيُفتح بريدك برسالة جاهزة إلى فريق الدعم.",
        "set_support_note_report": "اشرح المشكلة (الشاشة، الخطوات، نوع جهازك) — سيُفتح بريدك برسالة جاهزة تتضمن بيانات جهازك تلقائياً.",
        "set_support_note_contact": "سيُفتح بريدك فوراً على " + SUPPORT_EMAIL + " — نرد خلال 24 ساعة."},
 "en": {"set_support_note_help": "Tap this option and your mail app opens with a ready message to the support team.",
        "set_support_note_report": "Describe the problem (screen, steps, device) — your mail app opens with your device details filled in.",
        "set_support_note_contact": "Your mail app opens straight to " + SUPPORT_EMAIL + " — we reply within 24 hours."},
}


def main():
    for lang, folder in FOLDER.items():
        path = os.path.join(RES, folder, "strings.xml")
        text = io.open(path, encoding="utf-8").read()
        added = 0

        # 1) تصحيح ملاحظات الدعم (العربية والإنجليزية متوفّرة، والباقي يُترك للترجمة لاحقاً)
        for key, value in NOTES.get(lang, {}).items():
            pattern = re.compile(r'^[ \t]*<string name="%s">.*?</string>[ \t]*$' % re.escape(key), re.M)
            if pattern.search(text):
                text = pattern.sub(lambda _m, k=key, v=value: f'    <string name="{k}">{v}</string>', text, count=1)

        # 2) النصوص الجديدة
        pairs = [
            ("set_support_email", SUPPORT_EMAIL),
            *((k, v) for k, v in SUBJECTS[lang].items()),
            ("set_support_mail_body", BODIES[lang]),
            ("set_support_no_mail_app", NO_APP[lang]),
        ]
        def esc(value: str) -> str:
            """يحوّل الأسطر الحقيقية إلى \\n كما يفهمها أندرويد داخل موارد النصوص."""
            out = []
            for line in value.split("\n"):
                out.append(line.strip())
            return "\\n".join(out)

        block = "\n".join(f'    <string name="{k}">{esc(v)}</string>' for k, v in pairs)
        anchor = re.search(r'^[ \t]*<string name="set_contact">.*?</string>[ \t]*$', text, re.M)
        assert anchor, f"{lang}: مرساة set_contact غير موجودة"
        if not re.search(r'<string name="set_support_mail_body"', text):
            text = text[:anchor.end()] + "\n" + block + text[anchor.end():]
            added = len(pairs)
        io.open(path, "w", encoding="utf-8").write(text)
        print(f"✓ {folder:12} +{added} نصاً")


if __name__ == "__main__":
    main()
