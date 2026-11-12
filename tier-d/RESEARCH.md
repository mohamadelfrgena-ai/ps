# لماذا هذا التصميم — بحث واستبعاد

تاريخ البحث: 2026-11-12. الهدف: قائمة داخل العالم لتغيير مظهر اللاعب، بلا Dressing Room
وبلا حساب مايكروسوفت.

## ما استُبعد ولماذا

| الطريق | الحكم | السبب |
|---|---|---|
| `player.setSkin()` من Script API | **غير موجود** | تحقّق مباشر من ملفات التعريف المخزّنة (`@minecraft/server` 1.17.0 و 2.11.0): لا توجد أي دالة تضبط سكن اللاعب |
| `minecraft:skin_id` / `EntitySkinIdComponent` | لا ينطبق | هذه لتنويعات الكائنات (variant)، ليست سكن اللاعب |
| `SimulatedPlayer.setSkin(PlayerSkinData)` | لا ينطبق | خاصة ببيئة GameTest قبل الإصدار، وتعمل على لاعب محاكى لا اللاعب الحقيقي، ومبنية على قطع Persona |
| الأمر `/setskin` | **مُزال** | كان أمر تطوير ظهر بالخطأ في 1.2.13 ثم أُزيل في 1.2.14 |
| حزمة `skin_pack` عادية | لا تحقق الطلب | تعمل، لكنها تفرض الاختيار من Dressing Room وهو ما رفضه المستخدم |
| استبدال سكن ستيف في حزمة موارد | مرفوض | يفرض مظهرًا واحدًا ثابتًا، بلا قائمة ولا اختيار |
| ملفات GoldHEN لتجاوز تسجيل الدخول أو فتح السكنات | **محظور** | خارج حدود المشروع تمامًا |
| تعديل الملف التنفيذي أو تجاوز الحماية | **محظور** | خارج حدود المشروع تمامًا |
| حلول جافا (SkinsRestorer، SkinShifter، Paper) | لا تنطبق | إصدار مختلف كليًا |

## ما اعتُمد

القائمة من `ActionFormData` في `@minecraft/server-ui` — واجهة مستقرّة تُعرض داخل العالم.
أربع طرق للفتح لتقليل احتمال أن يفشل كل شيء بسبب طريقة واحدة:

1. `world.beforeEvents.chatSend` مع كلمة `!skin` (الأكثر اعتمادية).
2. `world.afterEvents.itemUse` مع غرض مخصّص.
3. القرفصاء أثناء حمل الغرض (استطلاع كل 10 ticks، مع كشف الحافة حتى لا يتكرر).
4. `customCommandRegistry.registerCommand` مع `cheatsRequired: false` — داخل `try`
   لأن توفره يختلف بين الإصدارات.

التغيير البصري عبر متحكّم رسم جديد في حزمة الموارد، يفهرس مصفوفة صور بمولانج.
مصدر الفهرس قناتان مستقلة، لأن دعم خصائص الكيان على `minecraft:player` تحديدًا غير موثّق:

- `query.property('skinmenu:look')` — خاصية معرّفة في حزمة السلوك مع `client_sync: true`.
- `query.mark_variant` — مكوّن تخزين عددي يعمل عبر مجموعات المكوّنات والأحداث.

يُستخدم `math.max` للاثنين، فيكفي أن تعمل إحداهما. السكربت يكتب في القناتين، كل واحدة
داخل `try` مستقل، ويُبلغ صراحة إن فشلت الاثنتان.

## تقليل ضرر تعديل ملف اللاعب

تعريف اللاعب في حزمة الموارد يستوجب نسخ الملف الأصلي، وهذا خطر معروف: نسخة قديمة من
`player.entity.json` تكسر أنيميشن السباحة والرمح في الإصدارات الحديثة. لذلك:

- استُخدمت أحدث نسخة أصلية متاحة كقاعدة، لا نسخة قديمة.
- لم يُستبدل `render_controllers/player.render_controllers.json` إطلاقًا؛ أُضيف ملف جديد.
- المتحكّمات الأصلية تبقى مُستدعاة، فقط مُقيّدة بالشرط `== 0`، فتعمل كما كانت عند عدم الاختيار.
- `part_visibility` والمواد والمجسّمات والأنيميشن منسوخة كما هي، ويتحقق الفحص البنيوي من ذلك.
- `min_engine_version: 1.13.0` مثبّت لدعم سكنات Persona.
- متحكّمات وضع المشاهدة (spectator) والخريطة لم تُقيّد.

## خطر متبقٍ معروف

أي إضافة أخرى تعدّل `entities/player.json` أو `entity/player.entity.json` ستتعارض مع هذه
الإضافة، لأن اللعبة لا تدمج هذه الملفات. الحل عند التعارض: تشغيل الإضافة وحدها على العالم.

## خطة بديلة إن فشل متحكّم الرسم

زي كامل الجسم قابل للارتداء (Attachable) مع `minecraft:wearable`، يُخفي طبقات السكن عبر
`scripts.parent_setup` بضبط `v.helmet_layer_visible` وأخواتها على صفر، ويُلبَس من السكربت.
هذه الطريقة موثّقة جيدًا لكنها تستهلك خانة درع، ولذلك لم تكن الخيار الأول.

## المصادر التي أثّرت في القرار

- learn.microsoft.com — Introduction to Entity Properties: `client_sync` و`query.property` و`set_property`.
- learn.microsoft.com — Custom Behaviors and Render Controllers: مصفوفة صور مفهرسة بخاصية كيان.
- learn.microsoft.com — Animation Documentation / Render Controllers: صيغة `arrays.textures` والفهرسة.
- wiki.bedrock.dev — Entity Properties، Dummy Components، Render Controllers، Equipment-Based Commands.
- wiki.bedrock.dev — Intro to Entities RP: شرط `min_engine_version` لدعم Persona.
- minecraft.wiki — Commands/setskin: أُزيل في 1.2.14.
- ملفات اللاعب الأصلية من مستودعات المرايا على GitHub (نُسخ منها الأساس، وبصماتها في `reports/build.json`).
