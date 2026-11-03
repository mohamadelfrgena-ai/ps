#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يكتب سياسة الخصوصية وشروط وأحكام الاستخدام في ملفات النصوص الثمانية.

المصدر: النص العربي الذي أرسله صاحب المشروع — يُكتب حرفياً في values-ar،
وتُرجم إلى اللغات السبع الأخرى بنفس المعنى والترتيب.

العناوين:
    set_privacy = سياسة الخصوصية          (توحيد الإملاء — لا «سياسية»)
    set_terms   = شروط وأحكام الاستخدام

كل نص ينتهي بسطر «آخر تحديث: 30 سبتمبر 2026» بحسب ما ورد في الطلب.

الاستخدام: python3 tools/set_policy_texts.py
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
FOLDERS = ["values", "values-ar", "values-es", "values-fr", "values-pt", "values-ru", "values-tr", "values-zh"]

UPDATED = "30 سبتمبر 2026"

# ---------------------------------------------------------------------------
# النصوص: ar = كما أرسله المستخدم حرفياً · الباقي ترجمة مطابقة
# ---------------------------------------------------------------------------
PRIVACY = {
    "ar": (
        "نحترم خصوصيتك ونلتزم بحماية بياناتك. قد نجمع معلومات مثل البريد الإلكتروني، اسم المستخدم، "
        "صورة الملف الشخصي، والمحتوى الذي تنشره لتشغيل خدمات AddonStation وتحسينها.\n\n"
        "قد نستخدم خدمات خارجية مثل الاستضافة والمصادقة والتخزين والدفع. قد تتم معالجة بعض بياناتك "
        "بواسطة هذه الخدمات وفقًا لسياساتها الخاصة.\n\n"
        "المحتوى الذي تنشره في المجتمع أو غرف الدردشة قد يكون ظاهرًا للمستخدمين الآخرين. "
        "لا تشارك معلومات شخصية أو سرية لا ترغب في نشرها.\n\n"
        "نحافظ على بياناتك باستخدام إجراءات أمنية مناسبة، لكن لا يمكن ضمان أمان أي خدمة "
        "عبر الإنترنت بشكل كامل.\n\n"
        "يمكنك طلب حذف حسابك وبياناتك، مع إمكانية الاحتفاظ ببعض المعلومات عندما يكون ذلك "
        "مطلوبًا قانونيًا أو ضروريًا للأمان.\n\n"
        "قد نحدّث سياسة الخصوصية من وقت لآخر. استمرارك في استخدام AddonStation بعد التحديث "
        "يعني قبول السياسة المحدثة.\n\n"
        "آخر تحديث: 30 سبتمبر 2026"
    ),
    "default": (
        "We respect your privacy and are committed to protecting your data. We may collect information "
        "such as your email address, username, profile picture, and the content you post in order to run "
        "and improve AddonStation's services.\n\n"
        "We may use external services such as hosting, authentication, storage, and payments. Some of your "
        "data may be processed by these services under their own policies.\n\n"
        "Content you post in the community or chat rooms may be visible to other users. Do not share "
        "personal or confidential information you do not want to publish.\n\n"
        "We protect your data with appropriate security measures, but no online service can be guaranteed "
        "to be completely secure.\n\n"
        "You can request deletion of your account and data, although some information may be retained when "
        "legally required or necessary for safety.\n\n"
        "We may update this privacy policy from time to time. Continuing to use AddonStation after an "
        "update means you accept the updated policy.\n\n"
        "Last updated: 30 September 2026"
    ),
    "es": (
        "Respetamos tu privacidad y nos comprometemos a proteger tus datos. Podemos recopilar información "
        "como el correo electrónico, el nombre de usuario, la foto de perfil y el contenido que publicas "
        "para operar y mejorar los servicios de AddonStation.\n\n"
        "Podemos usar servicios externos como alojamiento, autenticación, almacenamiento y pagos. Algunos "
        "de tus datos pueden ser procesados por estos servicios según sus propias políticas.\n\n"
        "El contenido que publicas en la comunidad o en las salas de chat puede ser visible para otros "
        "usuarios. No compartas información personal o confidencial que no quieras publicar.\n\n"
        "Protegemos tus datos con medidas de seguridad adecuadas, pero no se puede garantizar por completo "
        "la seguridad de ningún servicio en línea.\n\n"
        "Puedes solicitar la eliminación de tu cuenta y tus datos, aunque cierta información puede "
        "conservarse cuando sea legalmente exigido o necesario por seguridad.\n\n"
        "Podemos actualizar esta política de privacidad de vez en cuando. Si sigues usando AddonStation "
        "después de una actualización, significa que aceptas la política actualizada.\n\n"
        "Última actualización: 30 de septiembre de 2026"
    ),
    "fr": (
        "Nous respectons votre vie privée et nous nous engageons à protéger vos données. Nous pouvons "
        "collecter des informations telles que l'adresse e-mail, le nom d'utilisateur, la photo de profil "
        "et le contenu que vous publiez afin d'exploiter et d'améliorer les services d'AddonStation.\n\n"
        "Nous pouvons faire appel à des services externes tels que l'hébergement, l'authentification, le "
        "stockage et le paiement. Certaines de vos données peuvent être traitées par ces services selon "
        "leurs propres politiques.\n\n"
        "Le contenu que vous publiez dans la communauté ou les salons de discussion peut être visible par "
        "les autres utilisateurs. Ne partagez pas d'informations personnelles ou confidentielles que vous "
        "ne souhaitez pas publier.\n\n"
        "Nous protégeons vos données au moyen de mesures de sécurité appropriées, mais la sécurité d'un "
        "service en ligne ne peut jamais être totalement garantie.\n\n"
        "Vous pouvez demander la suppression de votre compte et de vos données, certaines informations "
        "pouvant être conservées lorsque la loi l'exige ou que cela est nécessaire à la sécurité.\n\n"
        "Nous pouvons mettre à jour cette politique de confidentialité de temps à autre. Continuer à "
        "utiliser AddonStation après une mise à jour signifie que vous acceptez la politique mise à jour.\n\n"
        "Dernière mise à jour : 30 septembre 2026"
    ),
    "pt": (
        "Respeitamos a sua privacidade e comprometemo-nos a proteger os seus dados. Podemos recolher "
        "informações como o e-mail, o nome de usuário, a foto de perfil e o conteúdo que você publica "
        "para operar e melhorar os serviços da AddonStation.\n\n"
        "Podemos usar serviços externos como hospedagem, autenticação, armazenamento e pagamento. Alguns "
        "dos seus dados podem ser processados por esses serviços de acordo com as políticas deles.\n\n"
        "O conteúdo que você publica na comunidade ou nas salas de chat pode ser visível para outros "
        "usuários. Não compartilhe informações pessoais ou confidenciais que você não queira publicar.\n\n"
        "Protegemos os seus dados com medidas de segurança adequadas, mas a segurança de qualquer serviço "
        "online não pode ser totalmente garantida.\n\n"
        "Você pode solicitar a exclusão da sua conta e dos seus dados, podendo algumas informações ser "
        "mantidas quando exigido por lei ou necessário para a segurança.\n\n"
        "Podemos atualizar esta política de privacidade de tempos em tempos. Continuar a usar a "
        "AddonStation após uma atualização significa que você aceita a política atualizada.\n\n"
        "Última atualização: 30 de setembro de 2026"
    ),
    "ru": (
        "Мы уважаем вашу приватность и обязуемся защищать ваши данные. Мы можем собирать информацию, "
        "такую как адрес электронной почты, имя пользователя, фото профиля и публикуемый вами контент, "
        "чтобы работать и улучшать сервисы AddonStation.\n\n"
        "Мы можем использовать сторонние сервисы, такие как хостинг, аутентификация, хранение данных и "
        "платежи. Часть ваших данных может обрабатываться этими сервисами в соответствии с их "
        "собственными политиками.\n\n"
        "Контент, который вы публикуете в сообществе или чатах, может быть виден другим пользователям. "
        "Не делитесь личной или конфиденциальной информацией, которую не хотите публиковать.\n\n"
        "Мы защищаем ваши данные надлежащими мерами безопасности, однако полную безопасность любого "
        "онлайн-сервиса гарантировать невозможно.\n\n"
        "Вы можете запросить удаление своей учётной записи и данных, при этом часть информации может "
        "быть сохранена, если это требуется по закону или необходимо для безопасности.\n\n"
        "Мы можем время от времени обновлять эту политику конфиденциальности. Продолжая использовать "
        "AddonStation после обновления, вы принимаете обновлённую политику.\n\n"
        "Последнее обновление: 30 сентября 2026 г."
    ),
    "tr": (
        "Gizliliğine saygı duyuyor ve verilerini korumayı taahhüt ediyoruz. AddonStation hizmetlerini "
        "sunmak ve geliştirmek için e-posta adresi, kullanıcı adı, profil resmi ve paylaştığın içerik gibi "
        "bilgileri toplayabiliriz.\n\n"
        "Barındırma, kimlik doğrulama, depolama ve ödeme gibi üçüncü taraf hizmetleri kullanabiliriz. Bazı "
        "verilerin bu hizmetler tarafından kendi politikalarına göre işlenebilir.\n\n"
        "Toplulukta veya sohbet odalarında paylaştığın içerik diğer kullanıcılar tarafından görülebilir. "
        "Paylaşmak istemediğin kişisel veya gizli bilgileri paylaşma.\n\n"
        "Verilerini uygun güvenlik önlemleriyle koruruz, ancak hiçbir çevrimiçi hizmetin güvenliği "
        "tamamen garanti edilemez.\n\n"
        "Hesabının ve verilerinin silinmesini talep edebilirsin; bazı bilgiler yasal olarak gerekli "
        "olduğunda veya güvenlik için zorunlu olduğunda saklanabilir.\n\n"
        "Bu gizlilik politikasını zaman zaman güncelleyebiliriz. Güncellemeden sonra AddonStation "
        "kullanmaya devam etmen, güncellenmiş politikayı kabul ettiğin anlamına gelir.\n\n"
        "Son güncelleme: 30 Eylül 2026"
    ),
    "zh": (
        "我们尊重你的隐私并承诺保护你的数据。为运行和改进 AddonStation 的服务，我们可能收集电子邮箱、"
        "用户名、头像以及你发布的内容等信息。\n\n"
        "我们可能使用托管、身份验证、存储和支付等外部服务。你的部分数据可能由这些服务按其自身政策处理。\n\n"
        "你在社区或聊天室发布的内容可能对其他用户可见。请勿分享你不想公开的个人或机密信息。\n\n"
        "我们采用适当的安全措施保护你的数据，但任何在线服务都无法保证绝对安全。\n\n"
        "你可以请求删除你的账号和数据，但在法律要求或安全需要时，部分信息可能会被保留。\n\n"
        "我们可能不时更新本隐私政策。更新后你继续使用 AddonStation 即表示接受更新后的政策。\n\n"
        "最后更新：2026年9月30日"
    ),
}

TERMS = {
    "ar": (
        "باستخدام AddonStation، فإنك توافق على هذه الشروط وتلتزم باستخدام الخدمة بطريقة قانونية ومسؤولة.\n\n"
        "أنت مسؤول عن حسابك وعن المحتوى الذي تنشره. يجب ألا تنشر محتوى ينتهك حقوق الآخرين أو يحتوي على "
        "برمجيات ضارة أو احتيال أو إساءة أو محتوى غير قانوني.\n\n"
        "تحتفظ بحقوق المحتوى الذي تنشئه، لكن عند رفعه إلى AddonStation تمنح المنصة الإذن اللازم "
        "لاستضافته وعرضه وتوزيعه ضمن خدماتها.\n\n"
        "قد تتضمن AddonStation اشتراكات وخدمات مدفوعة ونظام AC. استخدام AC يقتصر على الميزات التي "
        "تحددها المنصة، ولا تعتبر AC أموالًا نقدية ما لم يُذكر خلاف ذلك.\n\n"
        "يحق لـ AddonStation إزالة المحتوى المخالف أو تقييد أو تعليق الحسابات التي تنتهك هذه الشروط.\n\n"
        "AddonStation ليست مملوكة أو مدارة أو معتمدة من Mojang أو Microsoft ما لم يُذكر خلاف ذلك رسميًا.\n\n"
        "قد تتغير الخدمات أو الميزات أو هذه الشروط من وقت لآخر.\n\n"
        "آخر تحديث: 30 سبتمبر 2026"
    ),
    "default": (
        "By using AddonStation, you agree to these terms and undertake to use the service lawfully and "
        "responsibly.\n\n"
        "You are responsible for your account and for the content you post. You must not post content that "
        "infringes the rights of others or contains malicious software, fraud, abuse, or illegal material.\n\n"
        "You retain the rights to the content you create, but by uploading it to AddonStation you grant the "
        "platform the permission needed to host, display, and distribute it within its services.\n\n"
        "AddonStation may include subscriptions, paid services, and the AC system. AC usage is limited to "
        "the features defined by the platform, and AC is not considered cash unless stated otherwise.\n\n"
        "AddonStation may remove violating content or restrict or suspend accounts that breach these terms.\n\n"
        "AddonStation is not owned, operated, or endorsed by Mojang or Microsoft unless officially stated "
        "otherwise.\n\n"
        "Services, features, or these terms may change from time to time.\n\n"
        "Last updated: 30 September 2026"
    ),
    "es": (
        "Al usar AddonStation, aceptas estos términos y te comprometes a usar el servicio de forma legal y "
        "responsable.\n\n"
        "Eres responsable de tu cuenta y del contenido que publicas. No debes publicar contenido que "
        "vulnere los derechos de otros ni que contenga software dañino, fraude, abuso o material ilegal.\n\n"
        "Conservas los derechos sobre el contenido que creas, pero al subirlo a AddonStation otorgas a la "
        "plataforma el permiso necesario para alojarlo, mostrarlo y distribuirlo dentro de sus servicios.\n\n"
        "AddonStation puede incluir suscripciones, servicios de pago y el sistema AC. El uso de AC se "
        "limita a las funciones que define la plataforma, y AC no se considera dinero en efectivo salvo "
        "que se indique lo contrario.\n\n"
        "AddonStation puede eliminar el contenido infractor o restringir o suspender las cuentas que "
        "incumplan estos términos.\n\n"
        "AddonStation no es propiedad de Mojang ni de Microsoft, ni está gestionada ni respaldada por "
        "ellas, salvo que se indique oficialmente lo contrario.\n\n"
        "Los servicios, las funciones o estos términos pueden cambiar de vez en cuando.\n\n"
        "Última actualización: 30 de septiembre de 2026"
    ),
    "fr": (
        "En utilisant AddonStation, vous acceptez ces conditions et vous vous engagez à utiliser le service "
        "de manière légale et responsable.\n\n"
        "Vous êtes responsable de votre compte et du contenu que vous publiez. Vous ne devez pas publier de "
        "contenu qui porte atteinte aux droits d'autrui ou qui contient des logiciels malveillants, de la "
        "fraude, des abus ou du contenu illégal.\n\n"
        "Vous conservez les droits sur le contenu que vous créez, mais en le téléversant sur AddonStation, "
        "vous accordez à la plateforme l'autorisation nécessaire pour l'héberger, l'afficher et le "
        "distribuer au sein de ses services.\n\n"
        "AddonStation peut inclure des abonnements, des services payants et le système AC. L'utilisation "
        "des AC est limitée aux fonctionnalités définies par la plateforme, et les AC ne sont pas "
        "considérés comme de l'argent liquide sauf indication contraire.\n\n"
        "AddonStation peut supprimer tout contenu contraire ou restreindre, voire suspendre, les comptes "
        "qui enfreignent ces conditions.\n\n"
        "AddonStation n'est ni détenue, ni gérée, ni approuvée par Mojang ou Microsoft, sauf mention "
        "officielle contraire.\n\n"
        "Les services, les fonctionnalités ou ces conditions peuvent changer de temps à autre.\n\n"
        "Dernière mise à jour : 30 septembre 2026"
    ),
    "pt": (
        "Ao usar a AddonStation, você concorda com estes termos e compromete-se a usar o serviço de forma "
        "legal e responsável.\n\n"
        "Você é responsável pela sua conta e pelo conteúdo que publica. Não deve publicar conteúdo que "
        "viole os direitos de terceiros ou que contenha software malicioso, fraude, abuso ou conteúdo "
        "ilegal.\n\n"
        "Você mantém os direitos sobre o conteúdo que cria, mas ao enviá-lo para a AddonStation você "
        "concede à plataforma a permissão necessária para hospedá-lo, exibi-lo e distribuí-lo nos seus "
        "serviços.\n\n"
        "A AddonStation pode incluir assinaturas, serviços pagos e o sistema AC. O uso dos AC limita-se "
        "aos recursos definidos pela plataforma, e os AC não são considerados dinheiro, salvo indicação "
        "em contrário.\n\n"
        "A AddonStation pode remover conteúdo infrator ou restringir ou suspender contas que violem estes "
        "termos.\n\n"
        "A AddonStation não é propriedade da Mojang ou da Microsoft, nem é administrada ou endossada por "
        "elas, salvo indicação oficial em contrário.\n\n"
        "Os serviços, os recursos ou estes termos podem mudar de tempos em tempos.\n\n"
        "Última atualização: 30 de setembro de 2026"
    ),
    "ru": (
        "Используя AddonStation, вы соглашаетесь с этими условиями и обязуетесь использовать сервис "
        "законно и ответственно.\n\n"
        "Вы несёте ответственность за свою учётную запись и публикуемый контент. Запрещается "
        "публиковать контент, нарушающий права других лиц, а также содержащий вредоносное ПО, "
        "мошенничество, оскорбления или нелегальные материалы.\n\n"
        "Вы сохраняете права на созданный вами контент, но, загружая его в AddonStation, вы "
        "предоставляете платформе разрешение на его размещение, показ и распространение в рамках "
        "её сервисов.\n\n"
        "AddonStation может включать подписки, платные услуги и систему AC. Использование AC "
        "ограничено функциями, определёнными платформой, и AC не считается деньгами, если не указано "
        "иное.\n\n"
        "AddonStation вправе удалять нарушающий контент, а также ограничивать или блокировать учётные "
        "записи, нарушающие эти условия.\n\n"
        "AddonStation не принадлежит Mojang или Microsoft, не управляется и не одобрена ими, если "
        "официально не заявлено иное.\n\n"
        "Сервисы, функции и эти условия могут время от времени меняться.\n\n"
        "Последнее обновление: 30 сентября 2026 г."
    ),
    "tr": (
        "AddonStation kullanarak bu şartları kabul eder ve hizmeti yasal ve sorumlu bir şekilde "
        "kullanmayı taahhüt edersin.\n\n"
        "Hesabından ve paylaştığın içerikten sen sorumlusun. Başkalarının haklarını ihlal eden ya da "
        "zararlı yazılım, dolandırıcılık, kötüye kullanım veya yasa dışı içerik barındıran içerik "
        "paylaşmamalısın.\n\n"
        "Oluşturduğun içeriğin haklarını korursun; ancak onu AddonStation'a yüklediğinde platforma "
        "hizmetleri kapsamında barındırma, gösterme ve dağıtma izni vermiş olursun.\n\n"
        "AddonStation abonelikler, ücretli hizmetler ve AC sistemi içerebilir. AC kullanımı platformun "
        "belirlediği özelliklerle sınırlıdır ve aksi belirtilmedikçe AC nakit para sayılmaz.\n\n"
        "AddonStation ihlal içeren içeriği kaldırabilir, bu şartları ihlal eden hesapları kısıtlayabilir "
        "veya askıya alabilir.\n\n"
        "AddonStation, resmî olarak aksi belirtilmedikçe Mojang veya Microsoft'a ait değildir, onlar "
        "tarafından yönetilmez ve onaylanmaz.\n\n"
        "Hizmetler, özellikler veya bu şartlar zaman zaman değişebilir.\n\n"
        "Son güncelleme: 30 Eylül 2026"
    ),
    "zh": (
        "使用 AddonStation 即表示你同意这些条款，并承诺以合法、负责的方式使用本服务。\n\n"
        "你需对自己的账号及发布的内容负责。不得发布侵犯他人权利，或含有恶意软件、欺诈、滥用或"
        "违法内容的信息。\n\n"
        "你保留自己创作内容的相关权利，但将其上传至 AddonStation 即表示你授予平台在其服务范围内"
        "托管、展示和分发该内容的必要许可。\n\n"
        "AddonStation 可能包含订阅、付费服务和 AC 系统。AC 的使用仅限于平台规定的功能，除另有说明外，"
        "AC 不视为现金。\n\n"
        "AddonStation 有权删除违规内容，并限制或暂停违反这些条款的账号。\n\n"
        "除官方另有说明外，AddonStation 不归 Mojang 或 Microsoft 所有，也不由其运营或认可。\n\n"
        "服务、功能或这些条款可能不时变更。\n\n"
        "最后更新：2026年9月30日"
    ),
}

TITLES = {
    # folder: (privacy, terms)
    "values": ("Privacy policy", "Terms of use"),
    "values-ar": ("سياسة الخصوصية", "شروط وأحكام الاستخدام"),
    "values-es": ("Política de privacidad", "Términos de uso"),
    "values-fr": ("Politique de confidentialité", "Conditions d'utilisation"),
    "values-pt": ("Política de privacidade", "Termos de uso"),
    "values-ru": ("Политика конфиденциальности", "Условия использования"),
    "values-tr": ("Gizlilik politikası", "Kullanım şartları"),
    "values-zh": ("隐私政策", "使用条款"),
}

LOCALE_OF = {
    "values": "default", "values-ar": "ar", "values-es": "es", "values-fr": "fr",
    "values-pt": "pt", "values-ru": "ru", "values-tr": "tr", "values-zh": "zh",
}


def xml_escape(text: str) -> str:
    """يهيّئ النص لملف XML: فواصل الأسطر \\n والفواصل العليا \\' والشرطة «&»."""
    text = text.replace("&", "&amp;")
    text = text.replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("'", "\\'")
    return text.replace("\n", "\\n")


def replace_string(xml: str, name: str, value: str) -> str:
    pattern = re.compile(r'(<string name="%s">)(.*?)(</string>)' % re.escape(name), re.S)
    if not pattern.search(xml):
        raise SystemExit(f"✗ المفتاح غير موجود: {name}")
    return pattern.sub(lambda m: m.group(1) + value + m.group(3), xml, count=1)


def main() -> int:
    for folder in FOLDERS:
        locale = LOCALE_OF[folder]
        path = os.path.join(RES, folder, "strings.xml")
        with io.open(path, encoding="utf-8") as fh:
            xml = fh.read()

        privacy_title, terms_title = TITLES[folder]
        xml = replace_string(xml, "set_privacy", xml_escape(privacy_title))
        xml = replace_string(xml, "set_terms", xml_escape(terms_title))
        xml = replace_string(xml, "set_privacy_text", xml_escape(PRIVACY[locale]))
        xml = replace_string(xml, "set_terms_text", xml_escape(TERMS[locale]))

        with io.open(path, "w", encoding="utf-8") as fh:
            fh.write(xml)
        print(f"✓ {folder:12} {privacy_title} · {terms_title} · "
              f"{len(PRIVACY[locale])} حرفاً للسياسة و{len(TERMS[locale])} للشروط")
    return 0


if __name__ == "__main__":
    sys.exit(main())
