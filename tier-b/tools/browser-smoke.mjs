import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const tools=process.env.BROWSER_TOOLS||path.resolve(root,'../.cache/skin-feasibility/browser-tools/node_modules');
const { chromium: pw }=await import(pathToFileURL(path.join(tools,'playwright-core/index.mjs')).href);
const { default: chromium }=await import(pathToFileURL(path.join(tools,'@sparticuz/chromium/build/index.js')).href);
const base=process.env.BASE_URL||'http://127.0.0.1:4174/';
const browser=await pw.launch({executablePath:await chromium.executablePath(),args:[...chromium.args,'--no-sandbox'],headless:true});
const page=await browser.newPage({viewport:{width:1400,height:1050},acceptDownloads:true});
const errors=[]; const checks=[];
page.on('pageerror',e=>errors.push('pageerror: '+e.message)); page.on('console',m=>{if(m.type()==='error')errors.push('console: '+m.text());});
function check(name,ok,detail=''){checks.push({name,result:ok?'PASS':'FAIL',detail});}
try {
 await page.goto(base,{waitUntil:'networkidle'}); await page.waitForSelector('.skin-card');
 check('four skin cards',await page.locator('.skin-card').count()===4);
 check('initial selection',await page.locator('#selected-name').textContent()==='Azure Mechanic');
 check('boundary visible',await page.locator('.boundary').isVisible());
 check('actual-player boundary', (await page.locator('.notice').textContent()).includes('Actual player skin: unchanged'));
 const before=await page.locator('#model').evaluate(c=>c.toDataURL());
 await page.locator('.skin-card').nth(2).click(); await page.waitForFunction(()=>document.getElementById('selected-name').textContent==='Moss Gardener');
 check('selection updates',await page.locator('#selected-name').textContent()==='Moss Gardener');
 check('pressed state',await page.locator('.skin-card').nth(2).getAttribute('aria-pressed')==='true');
 const after=await page.locator('#model').evaluate(c=>c.toDataURL()); check('canvas changes with skin',before!==after);
 await page.locator('#yaw').fill('90'); const rotated=await page.locator('#model').evaluate(c=>c.toDataURL()); check('yaw changes render',rotated!==after);
 await page.locator('#left').click(); check('rotate left button',await page.locator('#yaw').inputValue()==='60');
 check('all atlas images decoded',await page.locator('.skin-card img').evaluateAll(imgs=>imgs.every(i=>i.complete&&i.naturalWidth===16)));
 for(const file of ['tier-b-preview.mcaddon','tier-b-preview-rp.mcpack','tier-b-preview-bp.mcpack','tier-b-manual-skins-CONTROL.mcpack']){const [dl]=await Promise.all([page.waitForEvent('download'),page.locator(`a[href="dist/${file}"]`).click()]);const b=fs.readFileSync(await dl.path());const expected=fs.readFileSync(path.join(root,'dist',file));check(`download ${file}`,dl.suggestedFilename()===file&&crypto.createHash('sha256').update(b).digest('hex')===crypto.createHash('sha256').update(expected).digest('hex'),`${b.length} bytes`);}
 await page.screenshot({path:path.join(root,'reports/browser-ui.png'),fullPage:true});
 await page.setViewportSize({width:390,height:844}); check('mobile no horizontal overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
 await page.screenshot({path:path.join(root,'reports/browser-mobile.png'),fullPage:true});
 const filePage=await browser.newPage(); const fileErrors=[]; filePage.on('pageerror',e=>fileErrors.push(e.message)); await filePage.goto(pathToFileURL(path.join(root,'index.html')).href); await filePage.waitForSelector('.skin-card'); check('file:// offline interface',await filePage.locator('.skin-card').count()===4&&fileErrors.length===0,fileErrors.join('; ')); await filePage.close();
} catch(e){check('smoke completed',false,e.stack||String(e));} finally {await browser.close();}
const report={scope:'Headless browser UI only; not Minecraft Bedrock, Android OS, Windows OS, or PS4 hardware.',base,checks,errors,errorCount:errors.length,passCount:checks.filter(c=>c.result==='PASS').length,checkCount:checks.length,result:checks.every(c=>c.result==='PASS')&&errors.length===0?'PASS':'FAIL'};
fs.writeFileSync(path.join(root,'reports/browser-smoke.json'),JSON.stringify(report,null,2)+'\n'); console.log(`${report.result}: ${report.passCount}/${report.checkCount}; errors=${errors.length}`); if(report.result!=='PASS')process.exitCode=1;
