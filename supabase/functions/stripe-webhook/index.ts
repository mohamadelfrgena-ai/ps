// ============================================================================
//  AddonStation Mobile — Edge Function: stripe-webhook
//  تُنشر بالأمر:  supabase functions deploy stripe-webhook --no-verify-jwt
//  ثم أضف الرابط في لوحة Stripe → Developers → Webhooks:
//     https://<project-ref>.supabase.co/functions/v1/stripe-webhook
//  الحدث المطلوب: checkout.session.completed
//  المتغيرات السرية: STRIPE_SECRET_KEY , STRIPE_WEBHOOK_SECRET
// ============================================================================

import Stripe from "npm:stripe@16";
import { createClient } from "npm:@supabase/supabase-js@2";

Deno.serve(async (req) => {
  const signature = req.headers.get("stripe-signature");
  const body = await req.text();

  const stripe = new Stripe(Deno.env.get("STRIPE_SECRET_KEY")!, { apiVersion: "2024-06-20" });

  let event: Stripe.Event;
  try {
    event = await stripe.webhooks.constructEventAsync(
      body,
      signature!,
      Deno.env.get("STRIPE_WEBHOOK_SECRET")!,
    );
  } catch (err) {
    return new Response(`Webhook signature error: ${err}`, { status: 400 });
  }

  if (event.type === "checkout.session.completed") {
    const session = event.data.object as Stripe.Checkout.Session;
    const reference = session.client_reference_id ?? session.metadata?.reference_code;

    if (!reference) {
      return new Response("missing reference", { status: 400 });
    }

    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data, error } = await admin.rpc("gateway_confirm_order", {
      p_reference: reference,
      p_provider_payment_id: session.payment_intent?.toString() ?? session.id,
      p_provider: "stripe",
    });

    if (error) {
      console.error("confirm failed", error);
      return new Response("confirm failed", { status: 500 });
    }

    console.log("order confirmed", reference, data);
  }

  return new Response(JSON.stringify({ received: true }), {
    headers: { "Content-Type": "application/json" },
  });
});
