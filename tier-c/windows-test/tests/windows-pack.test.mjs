import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repo = path.resolve(root, '..', '..');
const build = JSON.parse(fs.readFileSync(path.join(root, 'reports', 'build-manifest.json'), 'utf8'));
const sha = b => crypto.createHash('sha256').update(b).digest('hex');

test('both Windows packs exist and match SHA256SUMS', () => {
  const sums = fs.readFileSync(path.join(root, 'SHA256SUMS.txt'), 'utf8');
  for (const p of build.packs) {
    const b = fs.readFileSync(path.join(root, p.file));
    assert.equal(sha(b), p.sha256);
    assert.ok(sums.includes(p.sha256), `${p.file} missing from SHA256SUMS`);
  }
});

test('each pack declares skin_pack, never resources/data', () => {
  for (const p of build.packs) {
    const m = JSON.parse(fs.readFileSync(path.join(root, 'pack', p.track, 'manifest.json'), 'utf8'));
    assert.equal(m.modules.length, 1);
    assert.equal(m.modules[0].type, 'skin_pack');
    assert.notEqual(m.header.uuid, m.modules[0].uuid);
  }
});

test('every pack has exactly four free classic skins', () => {
  for (const p of build.packs) {
    const s = JSON.parse(fs.readFileSync(path.join(root, 'pack', p.track, 'skins.json'), 'utf8'));
    assert.equal(s.skins.length, 4);
    for (const sk of s.skins) {
      assert.equal(sk.type, 'free');
      assert.equal(sk.geometry, 'geometry.humanoid.custom');
      assert.ok(fs.existsSync(path.join(root, 'pack', p.track, sk.texture)));
    }
  }
});

test('textures are byte-identical to the frozen sources', () => {
  const sources = {
    tier_c_windows: path.join(repo, 'tier-c', 'pack', 'tier_c_local_probe'),
    tier_b_azure_windows: path.join(repo, 'tier-b', 'packs', 'tier_b_skins')
  };
  for (const p of build.packs) {
    for (const sk of p.skins) {
      const a = fs.readFileSync(path.join(root, 'pack', p.track, sk.texture));
      const b = fs.readFileSync(path.join(sources[p.track], sk.texture));
      assert.equal(sha(a), sha(b), `${p.track}/${sk.texture} differs from frozen source`);
    }
  }
});

test('Azure Mechanic is present and is the equip target of the azure pack', () => {
  const p = build.packs.find(x => x.track === 'tier_b_azure_windows');
  assert.equal(p.equip_target, 'Azure Mechanic');
  const lang = fs.readFileSync(path.join(root, 'pack', p.track, 'texts', 'en_US.lang'), 'utf8');
  assert.match(lang, /skin\.tier_b_azure_windows\.azure=Azure Mechanic/);
});

test('no mannequin, entity, script or executable payload in either pack', () => {
  for (const p of build.packs) {
    for (const f of p.files) {
      assert.ok(!/(entity\/|entities\/|render_controllers\/|scripts?\/)/i.test(f.path), f.path);
      assert.ok(!/\.(exe|dll|bin|self|sprx|mcworld|mctemplate)$/i.test(f.path), f.path);
    }
  }
});

test('rebuild is deterministic', () => {
  const before = build.packs.map(p => sha(fs.readFileSync(path.join(root, p.file))));
  execFileSync(process.execPath, [path.join(root, 'tools', 'build-windows-pack.mjs')], { stdio: 'pipe' });
  const after = build.packs.map(p => sha(fs.readFileSync(path.join(root, p.file))));
  assert.deepEqual(after, before);
});

test('all frozen Tier A/B/C files are unchanged', () => {
  const baseline = JSON.parse(fs.readFileSync(path.join(root, 'reports', 'freeze-before.json'), 'utf8'));
  const changed = [], missing = [];
  for (const f of baseline.files) {
    const p = path.join(repo, f.path);
    if (!fs.existsSync(p)) { missing.push(f.path); continue; }
    if (sha(fs.readFileSync(p)) !== f.sha256) changed.push(f.path);
  }
  assert.deepEqual(missing, [], 'frozen files removed');
  assert.deepEqual(changed, [], 'frozen files modified');
});
