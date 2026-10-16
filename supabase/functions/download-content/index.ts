// ============================================================================
//  AddonStation — دالة تحميل ملفات المحتوى (Supabase Edge Function · Deno)
//
//  ماذا تفعل؟
//    تحمّل ملفات الإضافات والعوالم من GitHub Releases للهواتف/الكمبيوتر/السوني:
//      GET /download-content?item=<uuid>       ⇒ يبثّ ملف الـ .raw للمستخدم المصرّح له
//      GET /download-content?item=<uuid>&meta=1 ⇒ بيانات الحزم فقط (pack_targets)
//
//    • تتحقق من تسجيل الدخول (JWT) ومن صلاحية الوصول عبر can_access_item
//      (خطة/شراء/مجاناً) قبل إعطاء أي بايت واحد.
//    • تسجّل التحميل تلقائياً في content_downloads (نفس ما يفعله الهاتف).
//    • لمستودع خاص: تحمّل من Releases بمفتاح GITHUB_TOKEN (سر الدالة) ولا
//      يرى المستخدم المفتاح أبداً. لمستودع عام (مثل mohamadelfrgena-ai/vae):
//      تحوّل مباشرة إلى رابط التحميل العام (بلا استهلاك bandwidth).
//
//  الأسرار المطلوبة (Settings → Edge Functions → Secrets):
//    GITHUB_TOKEN          = توكن قراءة (اختياري — يلزم للمستودعات الخاصة فقط)
//    CONTENT_CDN_PUBLIC    = 'true' إن كانت ملفات المحتوى على مستودع عام
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const GITHUB_TOKEN = Deno.env.get("GITHUB_TOKEN") ?? "";
const CDN_PUBLIC = (Deno.env.get("CONTENT_CDN_PUBLIC") ?? "").toLowerCase() === "true";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json; charset=utf-8" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "GET") return json(405, { ok: false, message: "method_not_allowed" });

  const url = new URL(req.url);
  const itemId = url.searchParams.get("item") ?? "";
  const metaOnly = url.searchParams.get("meta") === "1";
  if (!itemId) return json(400, { ok: false, message: "missing_item" });

  // 1) التحقق من المستخدم (JWT)
  const authHeader = req.headers.get("Authorization") ?? "";
  const token = authHeader.replace(/^Bearer\s+/i, "");
  if (!token) return json(401, { ok: false, message: "sign_in_required" });

  const admin = createClient(SUPABASE_URL, SERVICE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: userData, error: userErr } = await admin.auth.getUser(token);
  if (userErr || !userData?.user) {
    return json(401, { ok: false, message: "sign_in_required" });
  }
  const uid = userData.user.id;

  // 2) جلب العنصر والتحقق من صلاحية الوصول
  const { data: item, error: itemErr } = await admin
    .from("content_items")
    .select("id, title, slug, ps4_raw_url, pack_targets, file_path, is_published")
    .eq("id", itemId)
    .maybeSingle();
  if (itemErr || !item || !item.is_published) {
    return json(404, { ok: false, message: "not_found" });
  }

  // نفس دالة الهاتف: تتحقق من الخطة/الشراء/حد التحميلات وتسجّل التحميل
  // وتزيد العدّاد — ثم نبثّ الملف.
  const { data: dl, error: dlErr } = await admin.rpc("record_content_download", {
    p_item_id: itemId,
  });
  if (dlErr) return json(500, { ok: false, message: "server_error" });
  const record = (dl ?? {}) as {
    ok?: boolean;
    message?: string;
    ps4_raw_url?: string | null;
    pack_targets?: unknown;
  };
  if (!record.ok) {
    return json(403, { ok: false, message: record.message ?? "no_permission" });
  }

  if (metaOnly) {
    return json(200, {
      ok: true,
      title: item.title,
      slug: item.slug,
      ps4_raw_url: record.ps4_raw_url ?? item.ps4_raw_url,
      pack_targets: record.pack_targets ?? item.pack_targets ?? [],
    });
  }

  const rawUrl: string = record.ps4_raw_url ?? item.ps4_raw_url ?? "";
  if (!rawUrl) return json(404, { ok: false, message: "file_not_uploaded" });

  // 3) المستودع العام: توجيه مباشر (بلا bandwidth على Supabase)
  if (CDN_PUBLIC || !GITHUB_TOKEN) {
    return new Response(null, { status: 302, headers: { ...cors, Location: rawUrl } });
  }

  // 4) مستودع خاص: بثّ الملف من Releases عبر التوكن السري
  const ghRes = await fetch(rawUrl, {
    headers: {
      Authorization: `token ${GITHUB_TOKEN}`,
      Accept: "application/octet-stream",
      "User-Agent": "AddonStation-Edge",
    },
    redirect: "follow",
  });
  if (!ghRes.ok || !ghRes.body) {
    return json(502, { ok: false, message: "upstream_error", status: ghRes.status });
  }

  const filename = rawUrl.split("/").pop() ?? "content.raw";
  const headers = new Headers(cors);
  headers.set("Content-Type", "application/octet-stream");
  headers.set("Content-Disposition", `attachment; filename="${filename}"`);
  const len = ghRes.headers.get("content-length");
  if (len) headers.set("Content-Length", len);

  return new Response(ghRes.body, { status: 200, headers });
});
