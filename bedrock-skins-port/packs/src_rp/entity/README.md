# entity/player.entity.json

هذا الملف هو `player.entity.json` الرسمي من مستودع **Mojang/bedrock-samples**
(`resource_pack/entity/player.entity.json`)، ويُستخدم هنا كقالب فقط.

السبب: في بيدروك لا يمكن "إضافة" حركة إلى اللاعب دون تسجيلها في تعريف الكيان
العميل `minecraft:player`. وبما أن التعريف يُستبدل ولا يُدمج، فإن أرخص وأأمن
طريقة هي أخذ نسخة الفانيلا كما هي ثم **حقن** حركاتنا في `description.animations`
(انظر `tools/bsport/emotes.py::register_in_player_entity`).

عند تحديث إصدار اللعبة بشكل كبير (تغيّر `format_version` أو أسماء
`render_controllers`)، استبدل هذا الملف بنسخة الفانيلا الجديدة ثم أعد البناء.

الترخيص: الملف جزء من عيّنات Mojang الرسمية المخصّصة لتطوير الإضافات
(Minecraft Usage Guidelines / bedrock-samples). لا تُستخدم إلا داخل حزمة موارد
تعمل مع نسخة مرخّصة من اللعبة.
