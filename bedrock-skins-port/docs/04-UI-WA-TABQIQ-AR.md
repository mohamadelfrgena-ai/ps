# 04 — واجهة JSON-UI + التنفيذ بدون سكربتات

## 1. الحقيقة التقنية أولًا (مهمة جدًا)

طلبك كان: «أزرار JSON-UI تُرسل أمر الشات تلقائيًا عبر `button_mappings`».
بعد مراجعة محرك واجهة بيدروك: **هذا غير موجود**. `button_mappings` موجودة فعلًا، لكنها تربط
**معرّف إدخال** (`button.menu_select`, `button.controller_secondary_select`, …) بـ
**معرّف إجراء واجهة** (`button.xxx`) — لا تحمل نص أمر ولا تنفّذ شيئًا في العالم.

خصائص JSON-UI المسموحة للزر هي: تغيير مظهره، إغلاق/فتح شاشة، تشغيل صوت، وتحويل إدخال.
لا يوجد أي `binding` نوعه «نفّذ أمرًا». لذلك أي حزمة تدّعي ذلك فهي: إما تستخدم Script API
من الخلف، أو تعديل عميل (client mod).

لذلك قسّمنا الميزة إلى نصفين يعملان فعليًا:

| الطبقة | الملف | ماذا يفعل |
|---|---|---|
| **الواجهة** | `ui/pause_screen.json` | لوحة أزرار داخل شاشة التوقف، تنقّل كامل باليد (D-Pad + Stick) |
| **التنفيذ بدون سكربت** | `entities/bs_emote_console.behavior.json` | كيان يعطي 15 خيارًا، كل خيار يشغّل `function bs/emote/<id>` |
| **التنفيذ بالسكربت** | `scripts/main.js` | **Slot Binder**: ربط الحركة بخانة 1..6 + عجلة من 6 خانات + عنصر `bs:emote_wheel` |

### لماذا لا يمكن ربط أزرار JSON-UI بالسكربت؟

واجهة بيدروك تُنفَّذ على **العميل** بشكل تصريحي، والسكربتات تعمل على **الخادم**. لا يوجد أي
قناة أحداث من واجهة مخصصة إلى `@minecraft/server`؛ النماذج الرسمية (`ActionFormData`)
تعمل لأن **السكربت هو من فتحها** فيستلم نتيجتها. لذلك زر JSON-UI لا يمكنه استدعاء
`openBinder()`، ولا يمكنه أيضاً إرسال `scriptevent`.

كذلك **العجلة الدائرية الرسمية للإيموتات** تُبنى من إيموتات حساب اللاعب (Persona) ولا تقبل
إضافة من الحزم. لذلك بنينا **عجلة بديلة من 6 خانات** بنفس منطق `EmoteManager` (6 خانات
محفوظة لكل لاعب) تُفتح بضغطة زر واحدة على اليد من داخل العالم.

---

## 2. هيكل `pause_screen.json` المعدّل

نأخذ ملف موجان الرسمي (مُخزَّن في `packs/src_rp/ui/pause_screen.vanilla.json`) ونحقن فيه:

```jsonc
"bs_emote_panel": {                      // ← تعريف اللوحة (namespace: pause)
  "type": "stack_panel",
  "orientation": "vertical",
  "anchor_from": "left_middle",
  "anchor_to": "left_middle",
  "offset": [4, 0],
  "layer": 3,
  "alpha": 0.92,
  "controls": [
    { "bs_emote_title": { "type": "label", "text": "§eEmotes §7(BS)", "font_scale_factor": 0.9 } },
    { "bs_emote_grid": {
        "type": "stack_panel",
        "orientation": "horizontal",
        "controls": [                       // 3 أعمدة × 5 أزرار
          { "bs_emote_col_0": { "type": "stack_panel", "orientation": "vertical", "controls": [
              { "bs_emote_btn_0@common_buttons.light_text_button": {
                  "$button_text": "1. Beam Me Up",
                  "$pressed_button_name": "button.bs_emote_0",
                  "size": [58, 20],
                  "focus_identifier": "bs_emote_0",
                  "focus_change_up": "bs_emote_12",
                  "focus_change_down": "bs_emote_3",
                  "focus_change_left": "bs_emote_0",
                  "focus_change_right": "bs_emote_1",
                  "button_mappings": [
                    { "from_button_id": "button.menu_select",
                      "to_button_id": "button.bs_emote_0",
                      "mapping_type": "global", "scope": "view" },
                    { "from_button_id": "button.controller_secondary_select",
                      "to_button_id": "button.bs_emote_0",
                      "mapping_type": "global", "scope": "controller" }
                  ]
              } }
              /* … btn_1 … btn_14 … */
          ] } }
        ]
    } },
    { "bs_emote_hint": { "type": "label", "text": "§8Console: interact with bs:emote_console", "font_scale_factor": 0.7 } }
  ],
  "button_mappings": [                    // لا تُنفّذ أوامر — فقط تمنع "المتابعة" من سرقة التركيز
    { "from_button_id": "button.menu_select", "to_button_id": "button.bs_emote_select",
      "mapping_type": "global", "scope": "view" }
  ]
},

// الحقن داخل شاشة التوقف
"pause_screen_content": {
  "controls": [
    /* … الضوابط الأصلية … */
    { "bs_emote_panel@pause.bs_emote_panel": {} }
  ]
}
```

الهيكل الكامل يُولَّد آليًا في `tools/bsport/ui.py`، فلا تُحرّره يدويًا: أضف/احذف حركة في
المصدر ثم أعد البناء.

### قاعدة التنقّل
`focus_change_up/down/left/right` تُبنى بحساب شبكي: `index ± COLUMNS` و`index ± 1` مع
التفاف (wrap) عند الحواف، حتى لا يقفز المؤشر خارج اللوحة على يد التحكم.

---

## 3. التنفيذ بدون سكربتات: `bs:emote_console`

```jsonc
"minecraft:interact": {
  "interactions": [
    {
      "on_interact": {
        "filters": { "test": "is_family", "subject": "other", "value": "player" },
        "event": "bs:play_overhead_wave",
        "target": "self"
      },
      "interact_text": "action.interact.bs_overhead_wave",
      "sound_event": "random.orb",
      "cooldown": 0.1
    }
    /* … 14 أخرى … */
  ]
},
"events": {
  "bs:play_overhead_wave": {
    "run_command": {
      "command": ["execute as @p[distance=..8] run function bs/emote/overhead_wave"],
      "target": "self"
    }
  }
}
```

- **لماذا يعمل؟** أحداث الكيانات (`events`) هي المكان الوحيد بجانب أوامر اللعبة الذي يقبل
  `run_command` بشكل تصريحي، و`minecraft:interact` يحوّل ضغطة زر التفاعل إلى حدث.
- **لماذا `execute as @p`؟** لأن `run_command` في أحداث الكيان يُنفَّذ بمركز الكيان لا اللاعب،
  و`playanimation` يحتاج اللاعب نفسه. `@p[distance=..8]` = أقرب لاعب للكيان = من تفاعل معه.
- **على اليد (Controller):** زر التفاعل يفتح قائمة بأسماء الحركات (15 عنصرًا)؛ الاختيار يشغّل
  الحركة. لا كتابة، لا GameTest، لا وضع تجارب.

### الاستخدام
```
/function bs/summon_console      ← يضع الكيان أمام اللاعب
/summon bs:emote_console ~ ~ ~   ← أو يدويًا
```

---

## 4. مسارات التشغيل الأربعة (نفس الناتج)

| الطريق | يحتاج سكربت | يحتاج كتابة | الكونسول |
|---|---|---|---|
| `bs:emote_console` (تفاعل) | ❌ | ❌ | ✅ |
| `/function bs/emote/<id>` | ❌ | ✅ | ✅ |
| `/scriptevent bs:emote <id>` | ✅ | ✅ | ✅ |
| عجلة `server-ui` | ✅ | ❌ | ✅ (مع تفعيل السكربتات) |

الواجهة الجديدة (`pause_screen.json`) هي طبقة الاكتشاف: تُظهر كل الحركات وتربطها بمسار
`bs:emote_console` المكتوب أسفلها.


---

## 5. Slot Binder — ربط الحركات بخانات 1..6

### 5.1 دورة العمل

```
اللاعب يحمل bs:emote_wheel  →  RT / الزر الأيمن  →  openWheel()
        ├─ ضغط خانة 1..6     →  playSlot()  (كبسة واحدة، بدون قوائم إضافية)
        ├─ "Rebind a slot..." →  openLibrary() → openBinder()
        └─ "Clear slots"      →  تصفير خانات اللاعب

openBinder(player, emote)  →  ModalFormData:
        ├─ dropdown "Emote"        (15 حركة)
        ├─ dropdown "Pin to slot"  (1..6)
        └─ toggle   "Play it now"
```

### 5.2 التخزين (Offline بالكامل)

```js
player.setDynamicProperty("bs:emote_slots",
  JSON.stringify(["bs:overhead_wave", "bs:faceplant", null, null, null, null]));
```

- محفوظ داخل ملف اللاعب (Dynamic Property) — لا شبكة، لا قاعدة بيانات.
- عند أول دخول تُملأ الخانات 1..6 بالحركات الست الأولى، تمامًا كما يفعل
  `EmoteManager.reload()` في الجافا.
- الحركة الواحدة لا تُثبَّت في خانتين: الربط الجديد يفرغ الخانة القديمة تلقائيًا.

### 5.3 واجهة الأوامر (`@minecraft/server` scriptevent)

| الأمر | الأثر |
|---|---|
| `/scriptevent bs:bind overhead_wave 3` | ربط مباشر بدون نموذج |
| `/scriptevent bs:bind overhead_wave` | يفتح نموذج الاختيار (Modal) |
| `/scriptevent bs:slot 3` | تشغيل الخانة 3 |
| `/scriptevent bs:wheel` | فتح العجلة |
| `/scriptevent bs:stop` | إيقاف الحركة الحالية |
| `/scriptevent bs:list` | طباعة كل المعرّفات في الشات |

### 5.4 العناصر (الوصول بضغطة واحدة على اليد)

```mcfunction
/function bs/give_wheel     →  give @s bs:emote_wheel     (RT = العجلة)
/function bs/give_slots     →  bs:emote_slot_1 .. 6       (RT = تشغيل فوري)
```

`world.afterEvents.itemUse` يقرأ `typeId`، فإن كان `bs:emote_wheel` فتح العجلة، وإن كان
`bs:emote_slot_N` شغّل الخانة مباشرة — أي **ضغطة واحدة = حركة** على الكونسول بدون أي قائمة.

### 5.5 الاختبار الآلي

```bash
node tools/test_main_js.mjs dist/BedrockSkins_BP/scripts/main.js
```

يُحمّل الملف المبني فعليًا مع وهم (`mock`) لوحدتي `@minecraft/server` و`@minecraft/server-ui`
ويتحقق من: الاشتراك في الأحداث، ملء الخانات، الربط بأمر، فتح النموذج، تشغيل الخانة، عنصر
العجلة، عنصر الخانة، ورفض الحركات المجهولة — **11 فحصًا**.
