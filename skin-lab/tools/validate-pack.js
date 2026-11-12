#!/usr/bin/env node
'use strict';
/* Validate a .mcpack/.zip skin pack without importing it into Minecraft. */
const fs = require('node:fs');
const path = require('node:path');
const core = require('../src/pack-core.js');

async function main() {
  const file = process.argv[2];
  if (!file) {
    console.error('Usage: node tools/validate-pack.js <pack.mcpack|pack.zip>');
    process.exit(2);
  }
  const bytes = fs.readFileSync(file);
  const files = await core.readZip(bytes);
  const pack = core.validatePack(files);
  const report = {
    file: path.resolve(file),
    bytes: bytes.length,
    sha256: require('node:crypto').createHash('sha256').update(bytes).digest('hex'),
    pack: { id: pack.id, name: pack.name, skins: pack.skins.length },
    skins: pack.skins.map(s => ({ name: s.name, model: s.model, texture: s.texturePath, bytes: s.bytes.length, width: s.width, height: s.height })),
    warnings: pack.warnings,
    result: 'STRUCTURAL_PASS',
    note: 'Structural validation only. Not a Minecraft import, equip, Realms, or PS4 compatibility test.'
  };
  console.log(JSON.stringify(report, null, 2));
}
main().catch(e => { console.error(JSON.stringify({ result: 'FAIL', error: e.message }, null, 2)); process.exit(1); });
