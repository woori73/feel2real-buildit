# CHOONGGI CREATOR role — handoff (open PR, not merged/deployed)

Status snapshot at session close. This is a handoff document only — it does not
change code, migrations, or production state.

## Current checkpoint

- PR: [#385](https://github.com/woori73/feel2real-buildit/pull/385)
- Branch: `feat/choonggi-creator-role`
- HEAD: `e6ef4d484ae2253f334d233b95ae499cb33366db`
- Base: `0baa63c5d80a2112f50da4553e457364e0a627c9`
- Migration: `V994__add_choonggi_creator_role.sql` (V993 is taken by EASY PR #379,
  `add_easy_merchant_account_mapping.sql` — confirmed clear against `origin/main`
  and all 26 open PRs at the time this branch was cut)

## Result at close

- PR mergeable/clean, GitHub CI (Build Check) PASS
- CHOONGGI tests 33/33 PASS; `compileJava` / `compileTestJava` / `bootJar` PASS
- Production write: **none**. Production account `login_id=choonggi` remains
  `role=STUDENT` (self-registered during the audit round of this work, untouched
  since).
- **Not merged. Not deployed.**
- Live Flyway migration validation against a real MySQL schema was **not**
  performed (local `f2r_user` lacks `CREATE DATABASE` on an isolated schema;
  running it against the shared local `f2r_db` was avoided to not interfere with
  concurrent sessions). The migration itself is a single `ALTER TABLE ... MODIFY
  COLUMN` widening the existing `users.role` ENUM — same pattern as the original
  `V1` definition, default and existing rows untouched.

## Required before merging (do this first, in this order)

1. `git fetch origin --prune`
2. Check current `origin/main` — has it moved since `0baa63c5`?
3. Check whether PR #385 is now behind latest `main`.
4. Re-check PR #384 / EASY #379 / any other PR that merged since — did the
   migration landscape change?
5. **Re-run the V994 collision check** against the *then-current* `origin/main`
   and *then-current* open PR list — do not trust the audit above once time has
   passed; re-derive it.
6. If possible, apply `V994` against a real isolated MySQL schema and verify it
   end to end (this was skipped this round, see above).
7. If `main` has moved, sync PR #385 with a `git merge` (never rebase, reset, or
   force-push).
8. Re-run CHOONGGI tests + full build + CI after any sync.
9. Only after 1–8 pass: decide whether to merge PR #385.

## Sequencing after merge (do not skip steps or reorder)

```
merge PR #385
  → confirm new origin/main
  → manual production deploy
  → read-only service/Flyway/CHOONGGI smoke QA
  → separate explicit approval
  → production choonggi: STUDENT → CREATOR (one-time provisioning)
  → real choonggi login / content-authoring QA
```

`choonggi`'s production role must **not** change immediately after merge or
deploy — provisioning is its own explicitly-approved step, after the deploy is
confirmed healthy.

## Do not do any of this until explicitly asked

- Merge PR #385
- Deploy
- `UPDATE` the production `choonggi` row (or any production DB write)
- Change any password
- Add a new migration
- Touch NewStart / EASY / 내신Wiki files
