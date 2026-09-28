import { createClient } from '@supabase/supabase-js';

/*
 * Supabase configuration is public client configuration (URL + anon/publishable
 * key).  Keep a fallback here as well as in .env because the Android wrapper
 * serves the built site from a local origin where build-time environment
 * variables are not always available.
 */
const DEFAULT_SUPABASE_URL = 'https://hakxwvtdvsynrmkthatk.supabase.co';
const DEFAULT_SUPABASE_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhha3h3dnRkdnN5bnJta3RoYXRrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc0MDc0MTQsImV4cCI6MjEwMjk4MzQxNH0.fwlkrkow1qpkHfpeBSjmNe4VaDk_qvEXQZhZGNmzfSM';

// دعم تطبيق الأندرويد والموقع المحلي: يمكن حقن الإعدادات في window قبل تحميل الصفحة.
const appConfig = (typeof window !== 'undefined' && window.__ADDONSTATION_CONFIG__) || {};

const envUrl = import.meta.env.PUBLIC_SUPABASE_URL;
const envKey = import.meta.env.PUBLIC_SUPABASE_PUBLISHABLE_KEY || import.meta.env.PUBLIC_SUPABASE_ANON_KEY;

function cleanUrl(value) {
  return String(value || '').trim().replace(/\/+$/, '');
}

function usableUrl(value) {
  const url = cleanUrl(value);
  return /^https?:\/\/[^\s/]+(?:\/[^\s]*)?$/i.test(url) ? url : '';
}

// Runtime config is used by the APK; .env is used by the website; defaults keep
// a missing build environment from turning login into an opaque fetch failure.
const supabaseUrl = usableUrl(appConfig.supabaseUrl) || usableUrl(envUrl) || DEFAULT_SUPABASE_URL;
const supabaseKey = String(appConfig.supabaseKey || envKey || DEFAULT_SUPABASE_KEY).trim();

let supabase = null;
let supabaseConfigError = null;

/*
 * A short retry helps with the first request after an Android WebView wakes up
 * or changes network. It does not hide real Supabase errors: HTTP errors are
 * returned normally by Supabase and are shown to the user.
 */
async function resilientFetch(input, init = {}) {
  let lastError;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      return await fetch(input, init);
    } catch (error) {
      lastError = error;
      if (attempt === 0) await new Promise((resolve) => setTimeout(resolve, 350));
    }
  }
  throw lastError;
}

try {
  if (supabaseUrl && supabaseKey) {
    supabase = createClient(supabaseUrl, supabaseKey, {
      global: { fetch: resilientFetch },
      auth: {
        persistSession: true,
        autoRefreshToken: true,
        detectSessionInUrl: true,
      },
    });
  }
} catch (error) {
  // لا نسمح لإعداد خاطئ بكسر كل JavaScript في الصفحة؛ تعرض صفحات الشات رسالة واضحة.
  supabaseConfigError = error;
}

export function isSupabaseNetworkError(error) {
  const message = String(error?.message || error || '').toLowerCase();
  return /failed to fetch|networkerror|network request failed|load failed|fetch failed|connection refused|timed out/.test(message)
    || error?.name === 'TypeError' && /fetch|network|load/.test(message);
}

export function supabaseErrorMessage(error, fallback = 'حدث خطأ غير متوقع.') {
  if (isSupabaseNetworkError(error)) {
    return 'تعذر الاتصال بخادم AddonStation. تحقق من الإنترنت ثم اضغط حاول مرة أخرى.';
  }
  return error?.message || fallback;
}

export { supabase, supabaseConfigError };
export const supabaseReady = Boolean(supabase);
