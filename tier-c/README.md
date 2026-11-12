# Tier C — PS4 local skin-pack loading experiment

Research artifact and backup-gated hardware test plan. **PS4 hardware NOT RUN. Overall result: FAIL — not demonstrated.**

Deliverables:

- `RESEARCH.md` — architecture, directories, formats, version/Title ID requirements, sources.
- `TEST-PLAN.md` — C1–C5 success criteria and stop conditions.
- `INSTALL.md` — exact backup-gated FTP procedure.
- `pack/tier_c_local_probe/` — minimal four-skin standard Bedrock `skin_pack` candidate for the hardware probe.
- `dist/tier-c-local-probe.mcpack` — reference ZIP container only; not proof of PS4 import support.
- `reports/tier-b-audit.md` — read-only audit of Tier B’s PS4 procedure.
- `TEST-RESULTS.md` — human-readable PASS/FAIL matrix and local check evidence.
- `reports/results.json` — current PASS/FAIL record: all five success criteria FAIL/NOT RUN.
- `reports/backup-gate.template.json`, `reports/hardware-record.template.json` — required hardware records.
- `tools/`, `tests/` — local build/validation/backup-hash helpers.

Current criterion results:

| Criterion | Result |
|---|---|
| C1 pack recognition | FAIL — NOT RUN |
| C2 actual in-game selection UI | FAIL — NOT RUN |
| C3 selection without sign-in | FAIL — NOT RUN |
| C4 real local-player application | FAIL — NOT RUN |
| C5 restart persistence | FAIL — NOT RUN |

Local structural checks passed: 9/9 Node tests, 23/23 structural checks, Python ZIP CRC pass. These do not demonstrate PS4 loading.

Tier A and Tier B are preserved without modification. No Realms, Microsoft/PSN sign-in, executable modification, authentication/DRM bypass, save editing, or original game-file replacement is part of this experiment.
