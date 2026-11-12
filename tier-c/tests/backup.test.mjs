import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const hashTool=path.join(root,'tools','hash-tree.mjs');
const verifyTool=path.join(root,'tools','verify-backup.mjs');

function fixture(){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'tier-c-backup-'));const data=path.join(dir,'com.mojang');fs.mkdirSync(path.join(data,'minecraftWorlds'),{recursive:true});fs.mkdirSync(path.join(data,'skin_packs'),{recursive:true});fs.writeFileSync(path.join(data,'minecraftWorlds','level.dat'),'fixture-save-do-not-edit');fs.writeFileSync(path.join(data,'skin_packs','existing.txt'),'existing-pack');return{dir,data,manifest:path.join(dir,'manifest.json')};}
function run(tool,args){return spawnSync(process.execPath,[tool,...args],{encoding:'utf8'});}
function cleanup(f){fs.rmSync(f.dir,{recursive:true,force:true});}

test('backup hash helpers pass on an unchanged nested fixture',()=>{const f=fixture();try{const h=run(hashTool,[f.data,f.manifest]);assert.equal(h.status,0,h.stderr);const v=run(verifyTool,[f.data,f.manifest]);assert.equal(v.status,0,v.stderr);assert.match(v.stdout,/BACKUP_HASH_PASS: 2 files/);}finally{cleanup(f);}});

test('backup verification fails when a saved file is modified',()=>{const f=fixture();try{assert.equal(run(hashTool,[f.data,f.manifest]).status,0);fs.writeFileSync(path.join(f.data,'minecraftWorlds','level.dat'),'mutated');const v=run(verifyTool,[f.data,f.manifest]);assert.notEqual(v.status,0);assert.match(v.stdout,/BACKUP_HASH_FAIL/);}finally{cleanup(f);}});

test('backup verification fails on missing or extra files',()=>{const f=fixture();try{assert.equal(run(hashTool,[f.data,f.manifest]).status,0);fs.rmSync(path.join(f.data,'skin_packs','existing.txt'));fs.writeFileSync(path.join(f.data,'extra.txt'),'extra');const v=run(verifyTool,[f.data,f.manifest]);assert.notEqual(v.status,0);assert.match(v.stderr,/missing/);assert.match(v.stderr,/unexpected/);}finally{cleanup(f);}});

test('backup hashing rejects symlinks instead of following them',()=>{const f=fixture();try{fs.symlinkSync(path.join(f.data,'minecraftWorlds','level.dat'),path.join(f.data,'skin_packs','link.dat'));const h=run(hashTool,[f.data,f.manifest]);assert.notEqual(h.status,0);assert.match(h.stderr,/Symlink in backup/);}finally{cleanup(f);}});
