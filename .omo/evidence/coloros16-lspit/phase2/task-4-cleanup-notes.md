# task-4-cleanup-notes.md

Task 4 (host verify) cleanup register annotation. Written 2026-09-07T09:49+08:00.
Per plan :82 and :33 (issues.md 3 residues). Nothing is moved, deleted, or edited.

## Residue 2 — archived filename collision `predevice/task-4-build.txt`
- Status: **quarantined-under-archive**
- The old Phase-1 `task-4-build.txt` lives in the archived evidence tree
  (`.omo/evidence/coloros16-lspit-archive-20260907-090632/...` / historical `predevice/`).
- Phase 2 Task 4 evidence is written ONLY under
  `.omo/evidence/coloros16-lspit/phase2/` (`task-4-host-device-verify.json`,
  `task-4-flip-journal.json`, `task-4-systemui.sha256`, `task-4-manager-sha.txt`,
  `task-4-injection.log`, `task-4-cleanup-notes.md`).
- Phase 2 build evidence (old original Task 4 = build step) is named
  `phase2/task-5-build.txt` in Task 5 — never collides, never touches the archive.
- The archived file is NOT authoritative, NOT consulted for Task-4 values, NOT moved, NOT deleted.

## Residue 3 — stale GraalVM record `hook/notes2.md`
- Status: **quarantined-under-archive**
- `hook/notes2.md` records a stale GraalVM environment that is superseded.
- Authoritative environment = `.omo/notepads/coloros16-lspit-navigation-gesture/learnings.md`
  + this plan (`JAVA_HOME` Temurin jdk-21.0.12.1, forward-slash `sdk.dir`,
  `io.github.libxposed:api:102.0.0`, module format META-INF/xposed).
- Executor MUST NOT consult `hook/notes2.md`. It is NOT moved, NOT deleted.

## Residue 1 — stale `app/` hook source
- Status: still open; disposition = Task 5 rewrites the stale AOSP-targeting prototype
  in place against the proven Oplus target. Not part of Task 4 scope (Task 4 is read-only;
  `app/` untouched this task).

## Task-4 device state guarantee
- Read-only verification only: no install, no enable, no settings change, no reboot,
  no module state change, no lock-credential touch. Device returned to the same state.
