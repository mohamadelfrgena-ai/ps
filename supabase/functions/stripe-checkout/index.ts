// ============================================================================
//  AddonStation Mobile — Edge Function: stripe-checkout
//  تُنشر بالأمر:  supabase functions deploy stripe-checkout
//  المتغيرات السرية المطلوبة:
//    STRIPE_SECRET_KEY        (من لوحة Stripe → Developers → API keys)
//    SUPABASE_URL             (يُضاف تلقائياً في Supabase)
//    SUPABASE_ANON_KEY        (يُضاف تلقائياً)
//    SUPABASE_SERVICE_ROLE_KEY(يُضاف تلقائياً)
//    SITE_SUCCESS_URL / SITE_CANCEL_URL  (روابط العودة، اختيارية)
// ============================================================================

import Stripe from "npm:stripe@16";
import { createClient } from "npm:@supabase/supabase-js@2";

// ---------------------------------------------------------------------------
//  قراءة مفاتيح Supabase بنوعيها:
//  الجديدة (sb_publishable_… / sb_secret_…) موجودة في متغيّري البيئة على شكل JSON
//  باسم "default"، والقديمة (anon / service_role) ما زالت موجودة كسلاسل JWT.
//  هذه الدوال تعمل مع الحالتين تلقائياً.
// ---------------------------------------------------------------------------
function readKeyEnv(jsonEnv: string, plainEnv: string): string {
  const raw = Deno.env.get(jsonEnv);
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      const first = parsed["default"] ?? Object.values(parsed)[0];
      if (typeof first === "string" && first.length > 0) return first;
    } catch (_) {
      // ليس JSON — نتجاهله وننتقل للمفتاح القديم
    }
  }
  return Deno.env.get(plainEnv) ?? "";
}

const PUBLIC_KEY = () => readKeyEnv("SUPABASE_PUBLISHABLE_KEYS", "SUPABASE_ANON_KEY");
const SECRET_KEY = () => readKeyEnv("SUPABASE_SECRET_KEYS", "SUPABASE_SERVICE_ROLE_KEY");

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    if (!authHeader) {
      return json({ ok: false, message: "sign_in_required" }, 401);
    }

    const { plan_id } = await req.json();
    if (!plan_id) return json({ ok: false, message: "plan_required" }, 400);

    const stripe = new Stripe(Deno.env.get("STRIPE_SECRET_KEY")!, { apiVersion: "2024-06-20" });

    // 1) عميل المستخدم (لإنشاء الطلب باسمه والتحقق من هويته)
    const userClient = createClient(
      Deno.env.get("SUPABASE_URL")!,
      PUBLIC_KEY(),
      { global: { headers: { Authorization: authHeader } } },
    );

    const { data: userData, error: userErr } = await userClient.auth.getUser();
    if (userErr || !userData?.user) return json({ ok: false, message: "invalid_session" }, 401);

    // 2) إنشاء طلب الدفع على الخادم (المبلغ يُقرأ من قاعدة البيانات، لا من التطبيق)
    const { data: order, error: orderErr } = await userClient.rpc("create_payment_order", {
      p_plan_id: plan_id,
      p_method: "stripe",
    });
    if (orderErr || !order?.ok) {
      return json({ ok: false, message: orderErr?.message ?? order?.message ?? "order_failed" }, 400);
    }

    // 3) جلسة دفع Stripe (المبلغ بالدولار لتجنب قيود العملات المحلية)
    //    المبلغ يأتي من سعر الخطة نفسه (23_payment_order_usd.sql) — لا قيمة ثابتة،
    //    حتى يُحصَّل Legendary بـ 5.99$ و Epic بـ 2.99$ لا أقلّ.
    const amountUsd = Number(order.amount_usd ?? 0);
    if (!(amountUsd > 0)) {
      return json({ ok: false, message: "order_amount_missing" }, 400);
    }
    const session = await stripe.checkout.sessions.create({
      mode: "payment",
      client_reference_id: order.reference_code,
      metadata: {
        order_id: order.order_id,
        reference_code: order.reference_code,
        user_id: userData.user.id,
        plan_id,
      },
      line_items: [
        {
          quantity: 1,
          price_data: {
            currency: "usd",
            unit_amount: Math.round(amountUsd * 100),
            product_data: {
              name: `AddonStation — ${plan_id.toUpperCase()} plan`,
              description: `${order.duration_days ?? 30} days subscription`,
            },
          },
        },
      ],
      success_url: Deno.env.get("SITE_SUCCESS_URL") ?? "addonstation://payment/success",
      cancel_url: Deno.env.get("SITE_CANCEL_URL") ?? "addonstation://payment/cancel",
    });

    // 4) ربط الجلسة بالطلب
    const adminClient = createClient(
      Deno.env.get("SUPABASE_URL")!,
      SECRET_KEY(),
    );
    await adminClient.rpc("gateway_attach_session", {
      p_order_id: order.order_id,
      p_provider: "stripe",
      p_session_id: session.id,
    });

    return json({ ok: true, url: session.url, reference_code: order.reference_code });
  } catch (e) {
    return json({ ok: false, message: String(e) }, 500);
  }
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}
