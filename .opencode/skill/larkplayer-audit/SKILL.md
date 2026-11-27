---
name: larkplayer-audit
description: Use when auditing a clean-room Android reimplementation against a decompiled APK, hunting data-loss/security bugs in LarkClean or Lark Player source, or reconciling stale docs with the real Kotlin code. Triggers on "audit", "compare with original", "missing features", "fix bugs", "revisar vs APK original".
---

# LarkClean ↔ Lark Player static audit

A repeatable technique for finding real bugs in `lark-clean/` without an emulator or `adb`.
Evidence is source code and Gradle output, not assumptions.

## Core principle

**Docs are claims; code is truth.** `docs/03-FUNCIONES.md` and `docs/11-REA-ORIGINAL.md`
were written before many features existed and still mark implemented code as pending.
Never report a "missing feature" until you have searched the Kotlin sources for it.

## Procedure

1. **Inventory first.** List modules (`core/*`, `feature/*`, `app`) and the decompiled
   original (`jadx_full/sources`, `apk_extracted`, `ANALISIS.md`). Read `README.md` and
   `docs/00-INDICE.md` before forming conclusions.
2. **Split into independent domains** and dispatch one agent per domain (player;
   library+lyrics+database; settings+backup+update+build). Give each agent a path,
   a list of files, and a required output format. Do not let agents edit code during
   the audit: shared files cause conflicts.
3. **Require evidence for every finding:** `file:line`, the exact code fragment, the
   observable failure, a proposed fix, and a severity (Crítico/Alto/Medio/Bajo).
   A finding without a line number is a hypothesis, not a finding.
4. **Check the failure path, not only the happy path.** The highest-value bugs found
   here were: a failed MediaStore query treated as "no songs" and used to clear Room;
   a ZIP entry used as a path (Zip Slip); an APK installed without re-validating its
   hash; a notification without duration metadata; a wake lock released but never
   reacquired. For each data source ask: *what happens when it fails, is empty, or is
   restored after a process death?*
5. **Reconcile docs.** Mark stale ✅/⏳ rows and broken file references. Record every
   change in `docs/05-CAMBIOS.md` with the date and the verification command.
6. **Fix with TDD.** Extract pure logic (merge/sync/path validation/seek detection)
   into an `internal object` and test it with JUnit4. Watch the test fail with the
   stub before implementing; then watch it pass. Do not write a test that only
   asserts a constant or the source text.
7. **Verify with the real toolchain.** Run `gradlew testDebugUnitTest assembleDebug
   --no-daemon`. Never claim success from a command that was still running or was
   killed by a file lock from a concurrent Gradle build.

## Quick reference

| Check | Why it matters |
|---|---|
| `queryAudioOrNull` vs `queryAudio` | An empty list is not proof of an empty library |
| `BackupPath.safeMusicFileName` | `Music/../../x` is a Zip Slip write |
| `AppUpdate.validateApk` before install | `Ready` state survives process death |
| `MediaMetadata.setDurationMs` | Otherwise the custom notification shows 0:00 |
| `LyricsClock.isSeek` threshold × speed | A 2× tick is 500 ms and looks like a seek |
| `SongDao.replaceAll` @Transaction | A crash mid-rescan must not leave an empty DB |
| Manifest permissions | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` needs its own permission |

## Common mistakes

- Reporting `docs/` status as fact.
- Bundling several fixes in one Gradle run so neither can be isolated.
- Treating a concurrent-build `FileSystemException` as a failing test.
- Skipping the RED step "because the helper is trivial".
- Adding a dependency (`junit`, `palette`) without checking the version catalog first.
