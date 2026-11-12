// Generates a standalone Arabic download page with the add-on embedded as base64,
// so it works even if file hosting is blocked. No external requests.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dist = path.join(root, 'dist');
const files = [
  { name: 'skin-menu.mcaddon', label: 'الحزمة الكاملة (موصى بها)', hint: 'استيراد واحد يعطيك الحزمتين' },
  { name: 'skinmenu-behavior.mcpack', label: 'حزمة السلوك وحدها', hint: 'للتثبيت اليدوي' },
  { name: 'skinmenu-resource.mcpack', label: 'حزمة الموارد وحدها', hint: 'للتثبيت اليدوي' }
];
const sha = b => crypto.createHash('sha256').update(b).digest('hex');

const cards = files.map(f => {
  const buf = fs.readFileSync(path.join(dist, f.name));
  return { ...f, bytes: buf.length, sha256: sha(buf), b64: buf.toString('base64') };
});

const rows = cards.map(c => `      <tr><td dir="ltr">${c.name}</td><td>${c.bytes.toLocaleString('en-US')}</td><td dir="ltr" class="hash">${c.sha256}</td></tr>`).join('\n');
const buttons = cards.map((c, i) => `    <div class="card">
      <h3>${c.label}</h3>
      <p class="hint">${c.hint}</p>
      <p class="meta" dir="ltr">${c.name} — ${c.bytes.toLocaleString('en-US')} bytes</p>
      <button data-i="${i}">تنزيل</button>
      <a class="alt" href="dist/${c.name}" download>رابط مباشر بديل</a>
    </div>`).join('\n');

const html = `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>تنزيل قائمة السكنات داخل العالم</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; padding:24px; background:#0f1115; color:#e8eaed; font-family:"Segoe UI",Tahoma,sans-serif; line-height:1.8; }
  .wrap { max-width:880px; margin:0 auto; }
  h1 { font-size:1.6rem; margin:0 0 4px; }
  .sub { color:#9aa0a6; margin:0 0 20px; }
  .warn { background:#2a1f10; border:1px solid #6b4e18; border-radius:10px; padding:12px 16px; margin:0 0 20px; }
  .warn b { color:#ffcb66; }
  .grid { display:grid; gap:14px; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); }
  .card { background:#171a21; border:1px solid #262b36; border-radius:12px; padding:16px; }
  .card h3 { margin:0 0 6px; font-size:1.05rem; }
  .hint { color:#9aa0a6; margin:0 0 8px; font-size:.9rem; }
  .meta { color:#6f7681; font-size:.78rem; margin:0 0 12px; word-break:break-all; }
  button { background:#2563eb; color:#fff; border:0; border-radius:8px; padding:10px 18px; font-size:1rem; cursor:pointer; font-family:inherit; }
  button:hover { background:#1d4ed8; }
  .alt { display:block; margin-top:10px; color:#7aa2f7; font-size:.85rem; }
  table { width:100%; border-collapse:collapse; margin-top:12px; font-size:.82rem; }
  th,td { border:1px solid #262b36; padding:8px; text-align:right; }
  .hash { word-break:break-all; color:#6f7681; font-size:.72rem; }
  section { margin-top:28px; }
  ol { padding-inline-start:22px; }
  code { background:#0b0d11; padding:2px 6px; border-radius:4px; direction:ltr; display:inline-block; }
  #status { margin-top:14px; color:#7ee787; min-height:1.4em; }
</style>
</head>
<body>
<div class="wrap">
  <h1>قائمة السكنات داخل العالم</h1>
  <p class="sub">إضافة لماينكرافت بيدروك — اختيار المظهر من داخل العالم، بلا غرفة ملابس وبلا حساب.</p>

  <div class="warn">
    <b>تنبيه مهم:</b> هذه الإضافة مبنيّة ومُتحقَّق من ملفاتها، لكن <b>لم تُختبر داخل اللعبة بعد</b>.
    активиّرها على العالم ثم اتبع خطوات الاختبار، وأخبرني بالنتيجة الحقيقية.
  </div>

  <div class="grid">
${buttons}
  </div>
  <div id="status"></div>

  <section>
    <h2>التثبيت باختصار</h2>
    <ol>
      <li>خذ نسخة احتياطية من مجلد <code>com.mojang</code> أولًا.</li>
      <li>نزّل الحزمة الكاملة وافتحها بالنقر المزدوج.</li>
      <li>من إعدادات عالمك: فعّل <b>Skin Menu</b> في <b>Behavior Packs</b> و<b>Resource Packs</b> معًا.</li>
      <li>ادخل العالم واكتب <code>!skin</code> في الدردشة.</li>
      <li>اختر مظهرًا ثم اضغط <code>F5</code> لترى شخصيتك.</li>
    </ol>
    <p>لا تحتاج تفعيل الغش ولا أي خيار تجريبي.</p>
  </section>

  <section>
    <h2>بصمات الملفات</h2>
    <table>
      <tr><th>الملف</th><th>الحجم بالبايت</th><th>SHA-256</th></tr>
${rows}
    </table>
  </section>
</div>
<script>
const FILES = ${JSON.stringify(cards.map(c => ({ name: c.name, b64: c.b64 })))};
document.querySelectorAll('button[data-i]').forEach(function (btn) {
  btn.addEventListener('click', function () {
    var f = FILES[Number(btn.dataset.i)];
    try {
      var bin = atob(f.b64);
      var arr = new Uint8Array(bin.length);
      for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
      var url = URL.createObjectURL(new Blob([arr], { type: 'application/octet-stream' }));
      var a = document.createElement('a');
      a.href = url; a.download = f.name;
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
      document.getElementById('status').textContent = 'بدأ تنزيل ' + f.name;
    } catch (e) {
      document.getElementById('status').textContent = 'تعذّر التنزيل من الزر. استخدم الرابط المباشر البديل أدناه.';
    }
  });
});
</script>
</body>
</html>
`;
fs.writeFileSync(path.join(root, 'index.html'), html);
console.log(`Wrote index.html (${Buffer.byteLength(html)} bytes) with ${cards.length} embedded files`);
