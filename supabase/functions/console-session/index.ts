// ============================================================================
// AddonStation - Secure console-session Edge Function
//
// Deploy with JWT verification disabled because a console is not signed in yet:
//   supabase functions deploy console-session --no-verify-jwt
//
// Required secret:
//   CONSOLE_LINK_PEPPER = a random secret containing at least 32 characters
//
// SUPABASE_URL and the service-role/secret key are supplied by Supabase.
// Never include either CONSOLE_LINK_PEPPER or a service-role key in a client.
//
// Actions:
//   create    - creates a short-lived device code and poll secret.
//   exchange  - consumes an approved code and returns an opaque console token.
//   heartbeat - validates that token and updates the console's last-seen time.
//
// The console token is not a Supabase access token. It only authenticates calls
// routed through trusted Edge Functions, so it cannot change email/password,
// purchase a plan, or call arbitrary authenticated REST endpoints.
// ============================================================================

import { createClient } from "npm:@supabase/supabase-js@2.118.0";

function readKeyEnv(jsonEnv: string, plainEnv: string): string {
  const raw = Deno.env.get(jsonEnv);
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      const first = parsed.default ?? Object.values(parsed)[0];
      if (typeof first === "string" && first.length > 0) return first;
    } catch (_) {
      // Fall through to the legacy environment variable.
    }
  }
  return Deno.env.get(plainEnv) ?? "";
}

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = readKeyEnv(
  "SUPABASE_SECRET_KEYS",
  "SUPABASE_SERVICE_ROLE_KEY",
);
const LINK_PEPPER = Deno.env.get("CONSOLE_LINK_PEPPER") ?? "";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, x-client-info, apikey",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const encoder = new TextEncoder();

type RpcResult = { data: unknown; error: unknown | null };
type RpcClient = {
  rpc: (
    functionName: string,
    params?: Record<string, unknown>,
  ) => Promise<RpcResult>;
};

Deno.serve(async (request) => {
  if (request.method === "OPTIONS")
    return new Response("ok", { headers: cors });
  if (request.method !== "POST")
    return json({ ok: false, message: "method_not_allowed" }, 405);
  if (!SUPABASE_URL || !SERVICE_KEY || LINK_PEPPER.length < 32) {
    return json({ ok: false, message: "server_not_configured" }, 503);
  }

  const contentLength = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(contentLength) && contentLength > 8192) {
    return json({ ok: false, message: "request_too_large" }, 413);
  }

  try {
    const body = (await request.json()) as Record<string, unknown>;
    const action = cleanString(body.action, 24);
    const admin = createClient(SUPABASE_URL, SERVICE_KEY, {
      auth: { persistSession: false, autoRefreshToken: false },
    }) as unknown as RpcClient;

    if (action === "create") return await createCode(request, body, admin);
    if (action === "exchange") return await exchangeCode(body, admin);
    if (action === "heartbeat") return await heartbeat(body, admin);
    return json({ ok: false, message: "invalid_action" }, 400);
  } catch (_) {
    return json({ ok: false, message: "invalid_request" }, 400);
  }
});

async function createCode(
  request: Request,
  body: Record<string, unknown>,
  admin: RpcClient,
): Promise<Response> {
  const installationId = cleanString(body.installation_id, 128);
  const deviceName = cleanString(body.device_name, 80) || "PlayStation 4";
  const appVersion = cleanString(body.app_version, 40) || null;
  const platform = (cleanString(body.platform, 24) || "ps4").toLowerCase();

  if (!installationId || !/^[A-Za-z0-9._:-]{16,128}$/.test(installationId)) {
    return json({ ok: false, message: "invalid_installation_id" }, 400);
  }
  if (!/^[a-z0-9_-]{2,24}$/.test(platform)) {
    return json({ ok: false, message: "invalid_platform" }, 400);
  }

  const forwarded =
    request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "unknown";
  const fingerprint = await hmacHex(`${forwarded}|${installationId}`);
  const { data, error } = await admin.rpc("console_code_create", {
    p_device_name: deviceName,
    p_app_version: appVersion,
    p_platform: platform,
    p_request_fingerprint: fingerprint,
  });

  if (error) return json({ ok: false, message: "create_failed" }, 500);
  const payload = objectValue(data);
  const status =
    payload.message === "too_many_attempts" ? 429 : payload.ok ? 200 : 400;
  return json(payload, status);
}

async function exchangeCode(
  body: Record<string, unknown>,
  admin: RpcClient,
): Promise<Response> {
  const code = cleanString(body.code, 16).toUpperCase();
  const pollSecret = cleanString(body.poll_secret, 256);
  if (!code || !pollSecret) return json({ ok: false, status: "invalid" }, 400);

  const { data: consumed, error: consumeError } = await admin.rpc(
    "console_code_consume",
    {
      p_code: code,
      p_poll_secret: pollSecret,
    },
  );
  if (consumeError) return json({ ok: false, status: "exchange_failed" }, 500);

  const approval = objectValue(consumed);
  if (approval.status !== "approved") {
    const status = approval.status === "pending" ? 200 : 400;
    return json(
      { ok: approval.ok === true, status: approval.status ?? "invalid" },
      status,
    );
  }

  const exchangeId = cleanString(approval.exchange_id, 64);
  const linkedConsoleId = cleanString(approval.linked_console_id, 64);
  if (!exchangeId || !linkedConsoleId) {
    return json({ ok: false, status: "exchange_failed" }, 500);
  }

  const consoleToken = randomToken();
  const sessionRef = await sha256Hex(consoleToken);
  const { data: completed, error: completeError } = await admin.rpc(
    "console_code_complete",
    {
      p_exchange_id: exchangeId,
      p_linked_console_id: linkedConsoleId,
      p_session_ref: sessionRef,
    },
  );
  const completion = objectValue(completed);
  if (completeError || completion.ok !== true) {
    return json({ ok: false, status: "exchange_failed" }, 500);
  }

  return json({
    ok: true,
    status: "approved",
    linked_console_id: linkedConsoleId,
    console_token: consoleToken,
    device_name: nullableString(approval.device_name, 80),
    platform: nullableString(approval.platform, 24),
    app_version: nullableString(approval.app_version, 40),
  });
}

async function heartbeat(
  body: Record<string, unknown>,
  admin: RpcClient,
): Promise<Response> {
  const linkedConsoleId = cleanString(body.linked_console_id, 64);
  const consoleToken = cleanString(body.console_token, 256);
  if (!/^[0-9a-f-]{36}$/i.test(linkedConsoleId) || consoleToken.length < 32) {
    return json({ ok: false, message: "invalid_session" }, 401);
  }

  const sessionRef = await sha256Hex(consoleToken);
  const { data, error } = await admin.rpc("console_session_touch", {
    p_linked_console_id: linkedConsoleId,
    p_session_ref: sessionRef,
  });
  const result = objectValue(data);
  if (error || result.ok !== true) {
    return json({ ok: false, message: "session_revoked" }, 401);
  }
  return json({ ok: true, message: "active" });
}

function cleanString(value: unknown, maxLength: number): string {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function nullableString(value: unknown, maxLength: number): string | null {
  const cleaned = cleanString(value, maxLength);
  return cleaned || null;
}

function objectValue(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function randomToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return base64Url(bytes);
}

async function sha256Hex(value: string): Promise<string> {
  const digest = new Uint8Array(
    await crypto.subtle.digest("SHA-256", encoder.encode(value)),
  );
  return hex(digest);
}

async function hmacHex(value: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(LINK_PEPPER),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign("HMAC", key, encoder.encode(value)),
  );
  return hex(signature);
}

function hex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join(
    "",
  );
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...cors,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store, max-age=0",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
