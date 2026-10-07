// ============================================================================
//  AddonStation — دالة إرسال الإشعارات (Supabase Edge Function · Deno)
//
//  ماذا تفعل؟
//    تقرأ الطابور (push_queue) وترسل كل صف إلى أجهزة أصحابه عبر FCM v1،
//    بلغة كل جهاز نفسه، وعلى قناة الإشعارات المناسبة له (chat/content/...).
//
//  لماذا دالة على الخادم لا من التطبيق؟
//    • مفتاح الخدمة السري لا يدخل التطبيق أبداً (وإلا استطاع أي أحد إرسال
//      إشعارات لكل المستخدمين).
//    • التطبيق قد يكون مغلقاً — لا يرسل شيئاً حينها. الخادم هو الذي يوقظه.
//
//  ثلاثة أوامر (action):
//    dispatch  (افتراضي) — يفرّغ الطابور: كل صف مُستحق الإرسال ⇒ إشعارات.
//    reengage            — يبحث عن الغائبين طويلاً ويضيف تذكيراً لهم (مرة/14 يوماً).
//    cleanup             — ينظّف السجل والأجهزة المهجورة.
//    all                 — الثلاثة معاً (للمهمة اليومية).
//
//  الأمان: تُنادى بمفتاح سري في الترويسة x-push-secret (اضبطه في أسرار الدالة).
//  الأسرار المطلوبة (Settings → Edge Functions → Secrets):
//    FCM_SERVICE_ACCOUNT = نصّ JSON لحساب خدمة Firebase (Project settings →
//                          Service accounts → Generate new private key)
//    PUSH_CRON_SECRET    = كلمة سرّية تخترعها أنت (تضعها في مهمة cron)
//    SUPABASE_URL و SUPABASE_SERVICE_ROLE_KEY — تُضاف تلقائياً في Supabase.
// ============================================================================

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const SA_RAW = Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "";
const CRON_SECRET = Deno.env.get("PUSH_CRON_SECRET") ?? "";

// ---------------------------------------------------------------------------
// ترجمة نصوص الإشعارات: المفتاح يأتي من القاعدة (title_key/body_key) ويُترجم
// هنا حسب لغة الجهاز. {n} و{name} و{days} تُستبدل من args.
// ---------------------------------------------------------------------------
const I18N: Record<string, Record<string, string>> = {
  en: {
    push_test_title: "Notifications are working 🎉",
    push_test_body: "This is a test notification from AddonStation.",
    push_missing_title: "We miss you 👋",
    push_missing_body: "It's been {days} days — {n} new addons are waiting for you.",
    push_content_title: "{n} new addons just landed 🔥",
    push_content_body: "Open the app to see what's new today.",
    push_mention_title: "{name} mentioned you",
    push_mention_body: "Tap to read the message in chat.",
    push_reply_title: "{name} replied to you",
    push_reply_body: "Tap to see the reply.",
    push_thread_title: "New messages in your room",
    push_thread_body: "Tap to catch up on the conversation.",
    push_friend_title: "{name} sent you a friend request",
    push_friend_body: "Tap to accept or decline.",
    push_plan_title: "Your plan was activated 🎉",
    push_plan_body: "Enjoy all the features — tap to see your plan.",
    push_payment_title: "Payment received ✅",
    push_payment_body: "Tap to see your order status.",
    push_creator_title: "New content from creators",
    push_creator_body: "Tap to explore the latest uploads.",
    push_system_title: "AddonStation",
    push_system_body: "You have a new update in the app.",
  },
  ar: {
    push_test_title: "الإشعارات تعمل 🎉",
    push_test_body: "هذا إشعار تجريبي من AddonStation.",
    push_missing_title: "اشتقنا لك 👋",
    push_missing_body: "مرّ {days} يوماً — و{n} إضافات جديدة تنتظرك.",
    push_content_title: "{n} إضافات جديدة وصلت 🔥",
    push_content_body: "افتح التطبيق وشوف الجديد اليوم.",
    push_mention_title: "{name} أشار إليك",
    push_mention_body: "اضغط لتقرأ الرسالة في الشات.",
    push_reply_title: "{name} ردّ عليك",
    push_reply_body: "اضغط لتشوف الرد.",
    push_thread_title: "رسائل جديدة في غرفتك",
    push_thread_body: "اضغط لمتابعة الحديث.",
    push_friend_title: "{name} أرسل لك طلب صداقة",
    push_friend_body: "اضغط للقبول أو الرفض.",
    push_plan_title: "تم تفعيل خطتك 🎉",
    push_plan_body: "استمتع بكل الميزات — اضغط لعرض خطتك.",
    push_payment_title: "تم استلام الدفعة ✅",
    push_payment_body: "اضغط لترى حالة طلبك.",
    push_creator_title: "محتوى جديد من المبدعين",
    push_creator_body: "اضغط لاستكشاف أحدث ما نُشر.",
    push_system_title: "AddonStation",
    push_system_body: "لديك تحديث جديد في التطبيق.",
  },
  fr: {
    push_test_title: "Les notifications fonctionnent 🎉",
    push_test_body: "Ceci est une notification de test d'AddonStation.",
    push_missing_title: "Vous nous manquez 👋",
    push_missing_body: "Cela fait {days} jours — {n} nouveaux addons vous attendent.",
    push_content_title: "{n} nouveaux addons sont arrivés 🔥",
    push_content_body: "Ouvrez l'application pour voir les nouveautés.",
    push_mention_title: "{name} vous a mentionné",
    push_mention_body: "Touchez pour lire le message.",
    push_reply_title: "{name} vous a répondu",
    push_reply_body: "Touchez pour voir la réponse.",
    push_thread_title: "Nouveaux messages dans votre salon",
    push_thread_body: "Touchez pour suivre la conversation.",
    push_friend_title: "{name} vous a envoyé une demande d'ami",
    push_friend_body: "Touchez pour accepter ou refuser.",
    push_plan_title: "Votre abonnement est activé 🎉",
    push_plan_body: "Profitez de tout — touchez pour voir votre offre.",
    push_payment_title: "Paiement reçu ✅",
    push_payment_body: "Touchez pour voir l'état de votre commande.",
    push_creator_title: "Nouveau contenu des créateurs",
    push_creator_body: "Touchez pour découvrir les nouveautés.",
    push_system_title: "AddonStation",
    push_system_body: "Vous avez une nouvelle mise à jour.",
  },
  es: {
    push_test_title: "Las notificaciones funcionan 🎉",
    push_test_body: "Esta es una notificación de prueba de AddonStation.",
    push_missing_title: "Te echamos de menos 👋",
    push_missing_body: "Han pasado {days} días — {n} addons nuevos te esperan.",
    push_content_title: "Llegaron {n} addons nuevos 🔥",
    push_content_body: "Abre la app para ver lo nuevo de hoy.",
    push_mention_title: "{name} te mencionó",
    push_mention_body: "Toca para leer el mensaje.",
    push_reply_title: "{name} te respondió",
    push_reply_body: "Toca para ver la respuesta.",
    push_thread_title: "Mensajes nuevos en tu sala",
    push_thread_body: "Toca para seguir la conversación.",
    push_friend_title: "{name} te envió una solicitud de amistad",
    push_friend_body: "Toca para aceptar o rechazar.",
    push_plan_title: "Tu plan está activo 🎉",
    push_plan_body: "Disfruta de todo — toca para ver tu plan.",
    push_payment_title: "Pago recibido ✅",
    push_payment_body: "Toca para ver el estado de tu pedido.",
    push_creator_title: "Contenido nuevo de creadores",
    push_creator_body: "Toca para explorar lo último.",
    push_system_title: "AddonStation",
    push_system_body: "Tienes una novedad en la app.",
  },
  tr: {
    push_test_title: "Bildirimler çalışıyor 🎉",
    push_test_body: "Bu, AddonStation'dan bir test bildirimidir.",
    push_missing_title: "Seni özledik 👋",
    push_missing_body: "{days} gün oldu — {n} yeni addon seni bekliyor.",
    push_content_title: "{n} yeni addon geldi 🔥",
    push_content_body: "Bugünün yeniliklerini görmek için aç.",
    push_mention_title: "{name} senden bahsetti",
    push_mention_body: "Mesajı okumak için dokun.",
    push_reply_title: "{name} sana yanıt verdi",
    push_reply_body: "Yanıtı görmek için dokun.",
    push_thread_title: "Odanda yeni mesajlar var",
    push_thread_body: "Sohbete devam etmek için dokun.",
    push_friend_title: "{name} sana arkadaşlık isteği gönderdi",
    push_friend_body: "Kabul etmek için dokun.",
    push_plan_title: "Planın etkinleştirildi 🎉",
    push_plan_body: "Her şeyin keyfini çıkar — planını görmek için dokun.",
    push_payment_title: "Ödeme alındı ✅",
    push_payment_body: "Sipariş durumunu görmek için dokun.",
    push_creator_title: "Üreticilerden yeni içerik",
    push_creator_body: "En yenileri keşfetmek için dokun.",
    push_system_title: "AddonStation",
    push_system_body: "Uygulamada yeni bir gelişme var.",
  },
  ru: {
    push_test_title: "Уведомления работают 🎉",
    push_test_body: "Это тестовое уведомление от AddonStation.",
    push_missing_title: "Мы скучаем 👋",
    push_missing_body: "Прошло {days} дней — вас ждут {n} новых аддонов.",
    push_content_title: "Появилось {n} новых аддонов 🔥",
    push_content_body: "Откройте приложение и посмотрите новинки.",
    push_mention_title: "{name} упомянул вас",
    push_mention_body: "Нажмите, чтобы прочитать сообщение.",
    push_reply_title: "{name} ответил вам",
    push_reply_body: "Нажмите, чтобы увидеть ответ.",
    push_thread_title: "Новые сообщения в вашей комнате",
    push_thread_body: "Нажмите, чтобы продолжить.",
    push_friend_title: "{name} отправил заявку в друзья",
    push_friend_body: "Нажмите, чтобы принять или отклонить.",
    push_plan_title: "Ваш план активирован 🎉",
    push_plan_body: "Пользуйтесь всем — нажмите, чтобы посмотреть.",
    push_payment_title: "Платёж получен ✅",
    push_payment_body: "Нажмите, чтобы посмотреть статус заказа.",
    push_creator_title: "Новый контент от авторов",
    push_creator_body: "Нажмите, чтобы посмотреть новинки.",
    push_system_title: "AddonStation",
    push_system_body: "В приложении новое обновление.",
  },
  pt: {
    push_test_title: "As notificações funcionam 🎉",
    push_test_body: "Esta é uma notificação de teste do AddonStation.",
    push_missing_title: "Estamos com saudade 👋",
    push_missing_body: "Já passaram {days} dias — {n} addons novos esperam por você.",
    push_content_title: "Chegaram {n} addons novos 🔥",
    push_content_body: "Abra o app para ver as novidades de hoje.",
    push_mention_title: "{name} mencionou você",
    push_mention_body: "Toque para ler a mensagem.",
    push_reply_title: "{name} respondeu você",
    push_reply_body: "Toque para ver a resposta.",
    push_thread_title: "Novas mensagens na sua sala",
    push_thread_body: "Toque para continuar a conversa.",
    push_friend_title: "{name} enviou um pedido de amizade",
    push_friend_body: "Toque para aceitar ou recusar.",
    push_plan_title: "Seu plano foi ativado 🎉",
    push_plan_body: "Aproveite tudo — toque para ver seu plano.",
    push_payment_title: "Pagamento recebido ✅",
    push_payment_body: "Toque para ver o status do pedido.",
    push_creator_title: "Conteúdo novo dos criadores",
    push_creator_body: "Toque para explorar as novidades.",
    push_system_title: "AddonStation",
    push_system_body: "Há uma novidade no app.",
  },
  zh: {
    push_test_title: "通知正常工作 🎉",
    push_test_body: "这是来自 AddonStation 的测试通知。",
    push_missing_title: "我们想你了 👋",
    push_missing_body: "已经 {days} 天了 — 有 {n} 个新附加包在等你。",
    push_content_title: "新增了 {n} 个附加包 🔥",
    push_content_body: "打开应用看看今天的新内容。",
    push_mention_title: "{name} 提到了你",
    push_mention_body: "点按查看聊天消息。",
    push_reply_title: "{name} 回复了你",
    push_reply_body: "点按查看回复。",
    push_thread_title: "你的房间有新消息",
    push_thread_body: "点按继续对话。",
    push_friend_title: "{name} 向你发送了好友请求",
    push_friend_body: "点按接受或拒绝。",
    push_plan_title: "你的订阅已激活 🎉",
    push_plan_body: "尽情享受吧 — 点按查看你的方案。",
    push_payment_title: "已收到付款 ✅",
    push_payment_body: "点按查看订单状态。",
    push_creator_title: "创作者的新内容",
    push_creator_body: "点按浏览最新发布。",
    push_system_title: "AddonStation",
    push_system_body: "应用有新的更新。",
  },
};

/** قناة الإشعارات في أندرويد (يجب أن تطابق أسماء القنوات في التطبيق). */
function channelFor(category: string): string {
  switch (category) {
    case "chat": return "as_chat";
    case "content": return "as_content";
    case "social": return "as_social";
    case "reminders": return "as_reminders";
    default: return "as_account";
  }
}

/** ترجمة نص واحد مع استبدال العناصر {n} {name} {days}. */
function tr(key: string, locale: string, args: Record<string, unknown>): string {
  const lang = (locale || "en").slice(0, 2).toLowerCase();
  const table = I18N[lang] ?? I18N.en;
  let text = table[key] ?? I18N.en[key] ?? "";
  for (const [k, v] of Object.entries(args ?? {})) {
    text = text.replaceAll(`{${k}}`, String(v));
  }
  return text;
}

// ---------------------------------------------------------------------------
// FCM v1: رمز وصول من حساب الخدمة (JWT موقّع RS256 — يتجدّد كل ساعة)
// ---------------------------------------------------------------------------
type ServiceAccount = { project_id: string; client_email: string; private_key: string };

let cachedToken: { token: string; exp: number } | null = null;

function b64url(bytes: Uint8Array): string {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}
const enc = (s: string) => new TextEncoder().encode(s);

function pemToDer(pem: string): Uint8Array {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const raw = atob(body);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

async function accessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.exp > now + 60) return cachedToken.token;

  const header = b64url(enc(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = b64url(enc(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  })));
  const unsigned = `${header}.${claim}`;

  const key = await crypto.subtle.importKey(
    "pkcs8", pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc(unsigned));
  const jwt = `${unsigned}.${b64url(new Uint8Array(sig))}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) throw new Error(`token ${res.status}: ${await res.text()}`);
  const json = await res.json();
  cachedToken = { token: json.access_token, exp: now + (json.expires_in ?? 3600) };
  return cachedToken.token;
}

/** مراسلة واحد أو مجموعة: يُعاد عدد الناجح وما فشل. */
async function sendToTokens(
  sa: ServiceAccount, token: string,
  devices: { token: string; locale: string }[],
  payload: { titleKey: string; bodyKey: string; args: Record<string, unknown>; route: string | null;
             category: string; collapse: string | null; priority: string; queueId: number },
): Promise<{ ok: number; bad: string[] }> {
  let ok = 0;
  const bad: string[] = [];
  const CHUNK = 20;

  for (let i = 0; i < devices.length; i += CHUNK) {
    const chunk = devices.slice(i, i + CHUNK);
    await Promise.all(chunk.map(async (d) => {
      const message = {
        token: d.token,
        // data فقط (بلا notification): التطبيق هو الذي يبني الإشعار بلغته
        // ويختار قناته — فلا إشعار مزدوج ولا نص بلغة خاطئة.
        data: {
          title: tr(payload.titleKey, d.locale, payload.args),
          body: tr(payload.bodyKey, d.locale, payload.args),
          category: payload.category,
          route: payload.route ?? "",
          queue: String(payload.queueId),
        },
        android: {
          priority: payload.priority === "high" ? "HIGH" : "NORMAL",
          ttl: payload.priority === "high" ? "3600s" : "86400s",
          collapse_key: payload.collapse ?? undefined,
        },
      };
      const r = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ message }),
      });
      if (r.ok) { ok++; return; }
      const text = await r.text();
      // توكن ميت (تطبيق حُذف/أُعيد تثبيت) ⇒ يُنظَّف ولا يُحسب خطأً
      if (r.status === 404 || text.includes("UNREGISTERED") || text.includes("INVALID_ARGUMENT")) {
        bad.push(d.token);
      } else {
        console.error(`FCM ${r.status}: ${text.slice(0, 200)}`);
      }
    }));
  }
  return { ok, bad };
}

// ---------------------------------------------------------------------------
// الوصول إلى القاعدة بمفتاح الخدمة (يتجاوز RLS — لا يصل للعميل أبداً)
// ---------------------------------------------------------------------------
const rest = (path: string, init: RequestInit = {}) =>
  fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: SERVICE_KEY,
      Authorization: `Bearer ${SERVICE_KEY}`,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });

async function rpc<T>(name: string, body: Record<string, unknown>): Promise<T | null> {
  const r = await rest(`rpc/${name}`, { method: "POST", body: JSON.stringify(body) });
  if (!r.ok) { console.error(`rpc ${name} ${r.status}: ${(await r.text()).slice(0, 200)}`); return null; }
  return await r.json() as T;
}

// ---------------------------------------------------------------------------
// الأوامر
// ---------------------------------------------------------------------------
async function dispatch(sa: ServiceAccount, limit: number): Promise<Record<string, number>> {
  const token = await accessToken(sa);
  const r = await rest(
    `push_queue?status=eq.pending&scheduled_at=lte.${new Date().toISOString()}` +
    `&order=scheduled_at.asc&limit=${limit}&select=*`,
  );
  if (!r.ok) throw new Error(`queue ${r.status}`);
  const rows = await r.json() as Record<string, unknown>[];

  let sent = 0, skipped = 0, failed = 0, devices = 0;
  const deadTokens: string[] = [];

  for (const row of rows) {
    const id = Number(row.id);
    const category = String(row.category);
    const userId = (row.user_id as string | null) ?? null;

    // أجهزة الهدف: مستخدم واحد، أو كل من فعّل هذا القسم (صفوف البثّ)
    const targets = await rpc<{ token: string; locale: string }[]>("push_targets", {
      p_category: category, p_user: userId,
    });
    if (!targets || targets.length === 0) {
      await rest(`push_queue?id=eq.${id}`, {
        method: "PATCH",
        headers: { Prefer: "return=minimal" },
        body: JSON.stringify({ status: "skipped", error: "no_devices" }),
      });
      skipped++;
      continue;
    }

    const res = await sendToTokens(sa, token, targets, {
      titleKey: String(row.title_key),
      bodyKey: String(row.body_key),
      args: (row.args ?? {}) as Record<string, unknown>,
      route: (row.route as string | null) ?? null,
      category,
      collapse: (row.collapse_key as string | null) ?? null,
      priority: String(row.priority ?? "normal"),
      queueId: id,
    });

    deadTokens.push(...res.bad);
    devices += res.ok;

    if (res.ok > 0) {
      await rest(`push_queue?id=eq.${id}`, {
        method: "PATCH", headers: { Prefer: "return=minimal" },
        body: JSON.stringify({ status: "sent", sent_at: new Date().toISOString(), attempts: Number(row.attempts ?? 0) + 1 }),
      });
      sent++;
    } else {
      const attempts = Number(row.attempts ?? 0) + 1;
      await rest(`push_queue?id=eq.${id}`, {
        method: "PATCH", headers: { Prefer: "return=minimal" },
        body: JSON.stringify({
          status: attempts >= 3 ? "failed" : "pending",
          attempts, error: "fcm_rejected",
        }),
      });
      failed++;
    }
  }

  // تنظيف التوكنات الميتة فوراً (لا نُزعج جهازاً غير موجود)
  const unique = [...new Set(deadTokens)];
  for (const t of unique) {
    await rest(`push_devices?token=eq.${encodeURIComponent(t)}`, {
      method: "DELETE", headers: { Prefer: "return=minimal" },
    });
  }

  return { rows: rows.length, sent, skipped, failed, devices, deadTokens: unique.length };
}

// ---------------------------------------------------------------------------
// نقطة الدخول
// ---------------------------------------------------------------------------
Deno.serve(async (req: Request) => {
  const url = new URL(req.url);
  const action = (url.searchParams.get("action") ?? "dispatch").toLowerCase();

  // حماية: مفتاح سري في الترويسة (تُنشر الدالة بـ --no-verify-jwt)
  const secret = req.headers.get("x-push-secret") ?? "";
  if (!CRON_SECRET || secret !== CRON_SECRET) {
    return new Response(JSON.stringify({ ok: false, error: "unauthorized" }), {
      status: 401, headers: { "Content-Type": "application/json" },
    });
  }

  if (!SA_RAW || !SUPABASE_URL || !SERVICE_KEY) {
    return new Response(JSON.stringify({ ok: false, error: "missing_secrets" }), {
      status: 500, headers: { "Content-Type": "application/json" },
    });
  }

  let sa: ServiceAccount;
  try {
    sa = JSON.parse(SA_RAW) as ServiceAccount;
    if (!sa.project_id || !sa.client_email || !sa.private_key) throw new Error("bad sa");
  } catch {
    return new Response(JSON.stringify({ ok: false, error: "bad_service_account" }), {
      status: 500, headers: { "Content-Type": "application/json" },
    });
  }

  const out: Record<string, unknown> = { ok: true, action };
  try {
    if (action === "dispatch" || action === "all") {
      out.dispatch = await dispatch(sa, Math.min(Number(url.searchParams.get("limit") ?? 200), 500));
    }
    if (action === "reengage" || action === "all") {
      out.reengage = await rpc<number>("push_reengage_scan", { p_limit: 500 });
    }
    if (action === "cleanup" || action === "all") {
      out.cleanup = await rpc<number>("push_cleanup", { p_days: 7 });
    }
  } catch (e) {
    return new Response(JSON.stringify({ ok: false, error: String(e) }), {
      status: 500, headers: { "Content-Type": "application/json" },
    });
  }

  return new Response(JSON.stringify(out), {
    headers: { "Content-Type": "application/json" },
  });
});
