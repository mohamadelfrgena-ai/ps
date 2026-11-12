import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { makeZip, readStoredZip, safePath, crc32 } from '../tools/zip.mjs';
test('stored ZIP round-trips exact bytes and CRC',()=>{const entries=[{path:'manifest.json',data:Buffer.from('{"format_version":2}\n')},{path:'textures/a.png',data:Buffer.from([0,255,12,7])}];const z=makeZip(entries);const m=readStoredZip(z);assert.equal(m.size,2);assert.deepEqual(m.get('manifest.json'),entries[0].data);assert.deepEqual(m.get('textures/a.png'),entries[1].data);assert.equal(crc32(Buffer.from('123456789')),0xcbf43926);});
test('unsafe paths and duplicate paths are rejected',()=>{for(const p of ['../x','/x','a\\b','c:/x','a//b','a/./b','a/../b','x\0y'])assert.equal(safePath(p),false);assert.throws(()=>makeZip([{path:'a',data:'1'},{path:'A',data:'2'}]),/Duplicate/);assert.throws(()=>makeZip([{path:'../a',data:'1'}]),/Unsafe/);});
test('corrupted CRC is rejected',()=>{const z=makeZip([{path:'a.txt',data:'hello'}]);const i=z.indexOf(Buffer.from('hello'));z[i]^=1;assert.throws(()=>readStoredZip(z),/CRC/);});
test('all shipped archives parse with the independent Node reader',()=>{for(const f of ['tier-b-preview-rp.mcpack','tier-b-preview-bp.mcpack','tier-b-manual-skins-CONTROL.mcpack','tier-b-preview.mcaddon']){const m=readStoredZip(fs.readFileSync(new URL('../dist/'+f,import.meta.url)));assert.ok(m.size>0);}});
