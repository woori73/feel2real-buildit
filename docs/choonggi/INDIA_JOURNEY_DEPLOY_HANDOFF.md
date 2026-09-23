# CHOONGGI India Journey guide — deploy handoff (2026-09-23)

Status snapshot at session close. This is a handoff document only — it does not
change code, migrations, or production state.

## Current checkpoint

- PRs: [#393](https://github.com/woori73/feel2real-buildit/pull/393) (India
  Journey guide, public + private) and
  [#394](https://github.com/woori73/feel2real-buildit/pull/394) (Flyway
  V995→V999 rename, unblocked #393's deploy) — **both MERGED**.
- **Production deploy: SUCCESS.**
- DEPLOYED HEAD: `24dc9287055e0f4b69c1682844ecc22d49bafd9f`
- This is also current `origin/main` as of this handoff.

## What shipped

Two-tier India Journey travel guide for CHOONGGI's real 2026-09-25 Korea →
Hanoi → Mumbai trip:

- **Public** `/choonggi/journey` — route-level only (no exact flight numbers
  or clock times — see Privacy decision below). Linked from `/choonggi` home
  as "INDIA JOURNEY / AIRPORT GUIDE".
- **Private** `/choonggi/admin/journey` — full detail (real flight
  numbers/times, "I am here now" 3-button quick mode, emergency English with
  flight numbers). Gated by the existing `/choonggi/admin/**`
  ADMIN-or-CREATOR SecurityConfig matcher — no new security rule was added.
  Linked from the Creator dashboard (`/choonggi/admin`).

### Privacy decision (confirmed with the user before building)

Real, near-future, identifiable travel by a real person — publishing the
exact flight number + exact time publicly two days before departure is a
safety disclosure, not a style choice. Resolved as: public page stays
route-level; exact detail only exists behind the existing Creator/ADMIN
login. See PR #393's description for the full reasoning.

## Deploy history this session

1. First deploy attempt of #393 (head `0c87ac70`) **FAILED** —
   `f2r-app.service` crash-looped 3x and the deploy script's own
   health-check-and-rollback correctly reverted to the previous good jar.
   Root cause: Flyway out-of-order validation. `V995` (EASY,
   `add_easy_card_event_confirmation_date`) was merged to `main` by an
   unrelated PR/session *after* `V996` (this PR's own
   `youtube_video_id` column) had already been deployed and recorded in
   `flyway_schema_history`. Flyway refuses to apply a lower-numbered
   migration after a higher one is already applied — this blocked *any*
   deploy of `main`, not something specific to #393's own code (#393 has
   zero migrations).
   - Production was **never broken** — the rollback worked exactly as
     designed. Confirmed via live health checks and route smoke checks
     immediately after.
2. PR #394 renamed `V995` → `V999` (pure rename, no semantic change, CI
   green, reviewed as minimal/scoped).
3. Second deploy attempt (head `24dc9287`) — **SUCCESS**. Full smoke QA
   below.

## Production smoke QA (performed live, this session, read-only)

| Check | Result |
| --- | --- |
| `/actuator/health` | `{"status":"UP"}` |
| Flyway | Started cleanly (no validation error in `journalctl`; app reached `Started PrivateTutorSolutionBackApplication`) |
| `/choonggi` | 200, correct content |
| `/choonggi/journey` (public) | 200, contains the journey hero + YouTube embed, **zero** matches for `VJ961/VJ907/11:05/13:35/20:05/23:40/PNR/e-ticket/passport` |
| `/choonggi/admin` | anonymous → login-entry redirect (not raw 401) — PR #392 behavior preserved |
| `/choonggi/admin/journey` (private) | anonymous → login-entry redirect (not raw 401) — confirms the new private guide is correctly auth-gated, same mechanism as `/choonggi/admin` |
| `/easy` | 401 (unaffected, unchanged) |
| `/admin/newstart` | 401 (unaffected, unchanged) |

No test content was created. No manual production DB write was performed at
any point.

## What's NOT done / remaining

- **Real Creator login + mobile QA by 충기 himself** is still pending — this
  requires his actual credentials, which no Claude session can or should
  use (see the earlier E2E QA handoff exchange in this session's history —
  entering/using someone else's login credentials is out of bounds
  regardless of who asks). Once he logs in on his phone, walk him through:
  1. `/choonggi/admin/journey` — confirm the "지금 어디예요?" quick-mode
     buttons work and the flight timeline/vouchers/emergency-English content
     reads correctly on his actual phone.
  2. `/choonggi/journey` (public, logged out or another browser) — confirm
     it reads well and contains nothing he wouldn't want public.
  3. The original CHOONGGI Creator post flow (photo/YouTube/title/text →
     올리기) is unrelated to this PR and was already verified working in
     earlier PRs (#391/#392) — not re-tested this round, no reason to
     expect regression (smoke-checked above).
- The `indianvisaonline.gov.in` links (e-Visa, e-Arrival) on the journey
  pages could not be reached from this session's browser tool (timeout,
  likely bot/geo-blocking on India's gov WAF) — they're real,
  correctly-formed, search-indexed URLs, just never independently opened.
  Worth a manual click-check from a normal browser at some point, not
  urgent.
- No other known issues.

## Do not do any of this until explicitly asked

- Add new CHOONGGI features beyond what's described here
- Touch NewStart / EASY / 내신Wiki files
- Any production DB write
- Change the migration numbering scheme further
