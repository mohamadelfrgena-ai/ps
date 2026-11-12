// Confirms the frozen Tier A / Tier B / Tier C deliverables were not touched by Tier D work.
// Compares tracked files outside tier-d/ against git HEAD; reports any modification.
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const git = args => execFileSync('git', args, { cwd: repo, encoding: 'utf8' });

const tracked = git(['ls-files']).trim().split('\n').filter(Boolean);
const frozen = tracked.filter(f => !f.startsWith('tier-d/'));
const dirty = git(['diff', '--name-only', 'HEAD']).trim().split('\n').filter(Boolean);
const changedFrozen = dirty.filter(f => !f.startsWith('tier-d/'));
const untrackedOutside = git(['ls-files', '--others', '--exclude-standard'])
  .trim().split('\n').filter(Boolean).filter(f => !f.startsWith('tier-d/'));

console.log(`Frozen tracked files outside tier-d/: ${frozen.length}`);
if (changedFrozen.length) {
  console.log(`FREEZE_BROKEN — ${changedFrozen.length} modified:`);
  for (const f of changedFrozen) console.log('  ' + f);
  process.exit(1);
}
if (untrackedOutside.length) {
  console.log(`NOTE: ${untrackedOutside.length} new untracked file(s) outside tier-d/ (not modifications):`);
  for (const f of untrackedOutside.slice(0, 10)) console.log('  ' + f);
}
console.log(`FREEZE_UNCHANGED ${frozen.length}/${frozen.length}`);
