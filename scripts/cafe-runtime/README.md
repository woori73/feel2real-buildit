# Cafe thanks member → NewStart roster

Verified `감사멤버` observations from Cafe 31312978 are appended to the existing
`newstart_thanks_roster` table. Existing history and linked accounts are preserved.
A stable Studio member tag prevents duplicate registration. A nickname/ID-prefix
collision is held for review; a masked ID never grants an account entitlement.

## Deployment state (2026-10-31)

- Sandboxed server Chromium installed; private ec2-user profile.
- Six local and server tests pass; production roster columns checked read-only.
- `f2r-cafe-roster-sync.timer` active every five minutes; empty queue verified.
- **Studio observation producer and grade-change executor are NOT active.**
  The dedicated server browser still needs manager sign-in and Cafe identity
  verification. No customer roster insertion or continuous end-to-end sync has
  been verified. The timer alone does not mean the Cafe connection is complete.

## Files and credentials

Install Python files under `/opt/f2r-cafe-runtime`; use the dedicated Python 3.11
Playwright virtualenv. Install the existing `scripts/operations/a1-precheck.py`
credential adapter at `/opt/f2r-ebook/a1-precheck.py` (root 0600).
The DB adapter reads `/etc/f2r/env`, using a private RAM-backed MySQL client file.
No credentials, browser profile, screenshots, real observations, ebook or email
body belong in this public repository.

The root consumer reads verified JSON observations from
`/home/ec2-user/f2r-cafe-browser/roster-outbox` (ec2-user 0700), archives successful
receipts under `/var/lib/f2r-ebook/roster-synced` (root 0700), and leaves failed
items in place. Status exposes counts only. Systemd restricts writable paths.

An observation requires cafe ID, the actual Studio 12-character member tag,
observed nickname and ID prefix, grade `감사멤버`, member level 150, a verification
actor and a timezone-aware observation timestamp less than 24 hours old. An
optional grade-change date must come from the page. Never infer identity from
buyer name, nickname or email alone. A stale observation requires fresh checking.

## Remaining activation work

1. Complete user-controlled manager authentication in the dedicated profile.
2. Inspect the actual Studio member rows and grade dialog; implement and verify
   the visible UI producer, including manually upgraded members, pagination,
   uniqueness checks, session-expiry holds and safe order identity matching.
3. Validate an actual, operator-approved member observation and its resulting
   roster row. Do not create a synthetic production member to test the pipeline.
4. Connect verified grade receipts to order fulfillment; retain payment/claim
   rechecks, duplicate-send protection and delivery-state audit.

Run tests: `python -m unittest discover -s scripts/cafe-runtime -p 'test_*.py'`.
Stop consumer: `systemctl disable --now f2r-cafe-roster-sync.timer`.
