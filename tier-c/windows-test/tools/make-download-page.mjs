// Generates index.html with the packs embedded as base64, so the download works
// even if the preview frame blocks server downloads or binary responses.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const packs = [
  { file: 'windows-skin-test-azure.mcpack', title: 'Azure Mechanic Skin Test', names: 'Azure Mechanic · Ember Explorer · Moss Gardener · Violet Navigator', tag: 'مطابق لطلبك — يحتوي Azure Mechanic', rec: true },
  { file: 'windows-skin-test.mcpack', title: 'Tier C Windows Skin Test', names: 'C1 Signal Red · C2 Beacon Green · C3 Relay Blue · C4 Marker Yellow', tag: 'حزمة Tier C الأصلية', rec: false }
];

const sandbox = process.env.E2B_SANDBOX_ID || '';
const port = process.env.PORT || '4175';
const origin = sandbox ? `https://${port}-${sandbox}.e2b.app` : '';

const data = packs.map(p => {
  const b = fs.readFileSync(path.join(root, p.file));
  return { ...p, bytes: b.length, sha: crypto.createHash('sha256').update(b).digest('hex'), b64: b.toString('base64') };
});

const cards = data.map((p, i) => `
  <div class="card">
    <span class="tag${p.rec ? ' rec' : ''}">${p.tag}</span>
    <h2>${p.title}</h2>
    <p class="names">${p.names}</p>
    <div class="row">
      <button class="btn" data-i="${i}">⬇ تنزيل مباشر</button>
      <a class="btn alt" href="${p.file}" target="_blank" rel="noopener">فتح في تبويب جديد</a>
      <a class="btn alt" href="alt/${p.file.replace('.mcpack', '.zip')}" target="_blank" rel="noopener">نسخة .zip</a>
    </div>
    <div class="meta">${p.file} &nbsp;|&nbsp; ${p.bytes.toLocaleString('en-US')} bytes<br>SHA256: ${p.sha}</div>
  </div>`).join('\n');

const html = `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>تنزيل حزمة السكن — Windows Bedrock</title>
<style>
  :root{--bg:#0f1216;--card:#171c23;--line:#26303a;--txt:#e8eef4;--mut:#93a3b3;--acc:#38bdf8;--warn:#fbbf24;--ok:#34d399}
  *{box-sizing:border-box}
  body{margin:0;background:var(--bg);color:var(--txt);font-family:system-ui,"Segoe UI",Tahoma,sans-serif;line-height:1.7}
  .wrap{max-width:900px;margin:0 auto;padding:32px 20px 64px}
  h1{font-size:26px;margin:0 0 6px}
  .sub{color:var(--mut);margin:0 0 22px;font-size:15px}
  .banner{background:#2a2113;border:1px solid #5b4519;border-right:4px solid var(--warn);padding:14px 16px;border-radius:10px;margin-bottom:20px;font-size:14.5px}
  .banner b{color:var(--warn)}
  .fix{background:#0d2b21;border:1px solid #17543f;border-right:4px solid var(--ok);padding:14px 16px;border-radius:10px;margin-bottom:24px;font-size:14.5px}
  .fix b{color:var(--ok)}
  .card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:20px;margin-bottom:18px}
  .card h2{margin:0 0 4px;font-size:19px}
  .tag{display:inline-block;font-size:12px;padding:2px 9px;border-radius:99px;background:#0b2b3a;color:var(--acc);border:1px solid #17475c;margin-bottom:10px}
  .tag.rec{background:#0d2b21;color:var(--ok);border-color:#17543f}
  .names{color:var(--mut);font-size:14px;margin:8px 0 14px}
  .row{display:flex;gap:10px;flex-wrap:wrap}
  .btn{display:inline-block;background:var(--acc);color:#04222f;font-weight:700;text-decoration:none;padding:11px 22px;border-radius:10px;font-size:15px;border:0;cursor:pointer;font-family:inherit}
  .btn:hover{filter:brightness(1.1)}
  .btn.alt{background:#243040;color:var(--txt)}
  .meta{margin-top:14px;font-size:12.5px;color:var(--mut);direction:ltr;text-align:left;word-break:break-all;font-family:ui-monospace,Consolas,monospace}
  ol{padding-inline-start:22px}li{margin-bottom:9px}
  code{background:#0b0e12;border:1px solid var(--line);padding:2px 6px;border-radius:5px;font-size:13px;direction:ltr;display:inline-block;word-break:break-all}
  .docs a{color:var(--acc);text-decoration:none;margin-inline-end:16px;font-size:14.5px}
  h3{font-size:17px;margin:26px 0 10px}
  .note{font-size:14px;color:var(--mut)}
  .url{background:#0b0e12;border:1px solid var(--line);padding:10px 12px;border-radius:8px;direction:ltr;text-align:left;font-family:ui-monospace,Consolas,monospace;font-size:13px;word-break:break-all;margin:8px 0}
  #msg{margin-top:10px;font-size:14px;color:var(--ok);min-height:20px}
  textarea{width:100%;height:90px;background:#0b0e12;color:var(--mut);border:1px solid var(--line);border-radius:8px;font-family:ui-monospace,monospace;font-size:11px;direction:ltr}
  details{margin-top:12px}summary{cursor:pointer;color:var(--acc);font-size:14.5px}
</style>
</head>
<body>
<div class="wrap">

  <h1>حزمة السكن — اختبار Windows Bedrock</h1>
  <p class="sub">ثلاث طرق للتنزيل. جرّب الأولى، وإن لم تعمل استخدم التي تليها.</p>

  <div class="fix">
    <b>إذا لم يعمل التنزيل:</b> المعاينة تفتح داخل إطار، والمتصفح قد يمنع التنزيل داخله.
    اضغط <b>«فتح في تبويب جديد»</b>، أو انسخ الرابط أدناه وافتحه في متصفح عادي.
  </div>

  ${origin ? `<h3>الرابط المباشر للصفحة</h3>
  <div class="url" id="origin">${origin}</div>
  <p class="note">انسخه وافتحه في تبويب جديد خارج المعاينة، ثم اضغط تنزيل.</p>` : ''}

  <div class="banner">
    <b>تنبيه:</b> لم يتم تشغيل ماينكرافت فعليًا. جميع اختبارات داخل اللعبة <b>NOT RUN</b>.
    النجاح لا يُحتسب إلا إذا تغيّر شكل <b>اللاعب الحقيقي داخل العالم</b> — وليس المعاينة في Dressing Room.
  </div>

${cards}

  <div id="msg"></div>

  <h3>طريقة بديلة مضمونة — بدون تنزيل ملف</h3>
  <p class="note">
    إذا منع المتصفح كل التنزيلات، انسخ النص Base64 والصقه في PowerShell على الويندوز
    لإنشاء الملف محليًا. اضغط الزر لنسخ النص والأمر معًا.
  </p>
  <details>
    <summary>إظهار Base64 وأمر PowerShell</summary>
    <div class="row" style="margin:12px 0">
      <button class="btn alt" id="copy-ps">نسخ أمر PowerShell كامل</button>
    </div>
    <textarea id="b64view" readonly placeholder="اضغط الزر أعلاه"></textarea>
  </details>

  <h3>طريقة الاختبار باختصار</h3>
  <ol>
    <li>أغلق ماينكرافت، وخذ نسخة احتياطية من مجلد <code>com.mojang</code>.</li>
    <li>اضغط مرتين على ملف <code>.mcpack</code> — تظهر رسالة «Successfully imported».</li>
    <li>من <b>Settings → General</b> أوقف خيار <b>Only Allow Trusted Skins</b>.</li>
    <li>افتح <b>Dressing Room → Classic Skins</b> وتأكد من ظهور السكنات الأربعة.</li>
    <li>اختر <b>Azure Mechanic</b> واضغط <b>Equip</b>.</li>
    <li>ادخل عالمًا واضغط <b>F5</b> منظور الشخص الثالث — <b>هنا الاختبار الحقيقي</b>.</li>
    <li>أغلق اللعبة كليًا وأعد تشغيلها، وتأكد أن السكن ما زال مطبّقًا.</li>
  </ol>

  <p class="note">مسار الحزم في 1.21.120 وما بعدها:
    <code>%appdata%\\Minecraft Bedrock\\Users\\Shared\\games\\com.mojang\\skin_packs</code></p>

  <h3>الملفات المرفقة</h3>
  <div class="docs">
    <a href="INSTALL-WINDOWS.md" target="_blank" rel="noopener">دليل التركيب</a>
    <a href="TEST-CHECKLIST.md" target="_blank" rel="noopener">قائمة الاختبار</a>
    <a href="VALIDATION-RESULTS.md" target="_blank" rel="noopener">نتائج الفحص</a>
    <a href="SHA256SUMS.txt" target="_blank" rel="noopener">بصمات SHA256</a>
  </div>

</div>
<script>
const PACKS = ${JSON.stringify(data.map(p => ({ file: p.file, b64: p.b64, sha: p.sha })))};
const msg = document.getElementById('msg');

function b64ToBytes(b64){
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i=0;i<bin.length;i++) out[i]=bin.charCodeAt(i);
  return out;
}

// Builds the file entirely in the browser from embedded bytes: no server request,
// so proxy/attachment restrictions cannot interfere.
document.querySelectorAll('button[data-i]').forEach(btn=>{
  btn.addEventListener('click', async ()=>{
    const p = PACKS[Number(btn.dataset.i)];
    try{
      const bytes = b64ToBytes(p.b64);
      const blob = new Blob([bytes], {type:'application/octet-stream'});
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = p.file; a.rel='noopener';
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(()=>URL.revokeObjectURL(url), 4000);
      msg.textContent = 'تم إنشاء الملف: ' + p.file + ' — تحقّق من مجلد التنزيلات.';
    }catch(e){
      msg.style.color = '#fbbf24';
      msg.textContent = 'المتصفح منع التنزيل داخل الإطار. استخدم «فتح في تبويب جديد» أو أمر PowerShell.';
    }
  });
});

document.getElementById('copy-ps').addEventListener('click', async ()=>{
  const p = PACKS[0];
  const dest = '$env:USERPROFILE\\\\Downloads\\\\' + p.file;
  const ps = "$b64='" + p.b64 + "'\\n"
    + '[IO.File]::WriteAllBytes("' + dest + '", [Convert]::FromBase64String($b64))\\n'
    + 'Get-FileHash "' + dest + '" -Algorithm SHA256';
  document.getElementById('b64view').value = ps;
  try{ await navigator.clipboard.writeText(ps); msg.textContent='تم نسخ أمر PowerShell. الصقه في PowerShell على الويندوز.'; }
  catch{ document.getElementById('b64view').select(); msg.textContent='حدّد النص وانسخه يدويًا (Ctrl+C).'; }
});
</script>
</body>
</html>
`;

fs.writeFileSync(path.join(root, 'index.html'), html);
console.log(`index.html written: ${html.length} bytes, ${data.length} embedded packs, origin=${origin || '(unknown)'}`);
