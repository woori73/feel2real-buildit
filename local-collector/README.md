# EASY Visible Local Browser Collector — agent (V1 foundation)

A standalone Node.js process meant to run on the business's own PC (e.g. the academy office
computer). It is **not** part of the Java/Gradle build and does not run on the EASY server. It
opens a **visible** (headed) Chromium window, lets the human authenticate normally in it, then
resumes that same window to navigate a deterministic, per-institution workflow (menu → date
range → search → download) and reports status + file metadata back to EASY.

No Qoder dependency. Browser automation is [Playwright](https://playwright.dev) (Apache-2.0 license,
free, open source) only.

## What this process never does

- Never fills in, reads, or stores a certificate, certificate password, OTP, card password, CVC,
  or full card number. The human enters those directly into the real site's own UI, in the same
  visible window this process opened — this process only *waits* for that to finish.
- The polling collector sends status transitions and encrypted file name plus original size/SHA-256.
  The separate `uploadPreview.js` command can decrypt in memory and upload to the local EASY
  preview API only after another interactive user approval. It never stages or commits the ledger.
- Never listens on any inbound network port. It only makes outbound calls to EASY (poll model),
  so there is nothing on the business's PC for an outside party to connect to.
- Never writes downloaded files into a git-tracked directory (default: a per-user folder outside
  any repo — see `src/config.js`).

## Setup

```bash
cd local-collector
npm ci
npx playwright install chromium
```

Only `CARD_COMPANY_TEST` on an explicit-port `http://localhost` / `http://127.0.0.1` origin is
enabled. Real institution workflows require separate user authorization and verified provider
permission. Cross-origin requests, redirects, subresources and WebSockets are blocked.

Inject `EASY_COLLECTOR_FILE_KEY_BASE64`, a private Base64-encoded 32-byte random key, through
the local secret environment. Do not put actual keys into commands, documentation or git.
Downloads are AES-256-GCM encrypted with UUID filenames and private file permissions. Chromium's
temporary download and the isolated context are deleted on success/failure. Keep the key outside
the download folder; without it, the original cannot be recovered.

## Pairing a collector (one-time, done by an admin)

Register the collector against EASY (admin session required — this is a normal
`/api/admin/**` call):

```bash
curl -X POST https://<easy-host>/api/admin/easy/collector/register \
  -H "Content-Type: application/json" \
  -b "ACCESS_TOKEN=<admin JWT cookie>" \
  -d '{"businessId": 1, "name": "에듀홀릭-원장PC"}'
```

The response's `token` is shown **exactly once** — copy it into this machine's environment
(never into git, never into a chat log):

```bash
export EASY_BASE_URL=https://<easy-host>
export EASY_COLLECTOR_TOKEN=<token from register response>
npm start
```

## Running

```bash
npm start            # polls forever
node src/collector.js --once   # claims and runs at most one queued task, then exits
```

An admin queues work from `/api/admin/easy/collector/tasks` (businessId, collectorId,
sourceCode, dataType, requestedFrom, requestedTo). The collector picks it up on its next poll,
opens a **visible** browser, and you'll see it on screen.
After authentication, confirm the requested period in the terminal with `y`. Login success alone
does not authorize downloading. An unattended process cannot give production consent.

## Safe mock verification (no real financial site involved)

This is what proves the lifecycle mechanics end-to-end without touching anything real:

```bash
npm run test:e2e-mock
# Linux without a desktop:
xvfb-run -a npm run test:e2e-mock
npm test
```

This starts a tiny local "mock financial site" (`mock-site/`, plain HTML — a fake login page and
a fake usage-history page with a downloadable CSV) and a tiny in-memory fake EASY backend
(`test/fakeBackend.js`, same HTTP contract as the real one), then runs the **real, unmodified**
collector against them. A visible Chromium window opens on this machine; since there's no live
human in an unattended CI-style run, the script stands in for the click on the exact same page
the collector opened (see the `onWaitingForAuth` test hook in `src/collector.js` — never used in
production, where this always blocks for a real human). It exits 0 and prints `E2E LIFECYCLE:
PASS` when every stage — including "wrong token gets rejected" — actually happened.

## Real institutions and ledger bridge

Adding a workflow file does not enable real access: the engine's loopback policy still rejects
it. Provider agreements, explicit real-access authorization, narrowly reviewed origin policy,
human authentication/challenge handling and verified native export fixtures are prerequisites
for a future implementation. Do not add storageState, persistent profiles, credential filling,
authentication workarounds, stealth plugins or session import/export.

Per the manual-fallback requirement: if a real site changes its layout and a workflow starts
failing, the same EASY admin can always fall back to the existing manual Excel/CSV upload path
(`EasyCollectionIngestService.ingestCsv`) — this collector is additive, not a replacement.

For the encrypted-original preview bridge, use
[`API_AND_PROTOTYPE.md`](../docs/easy/financial-collector/API_AND_PROTOTYPE.md).
It documents the local-only API, user approval commands, limits of native export support,
and the separate dedicated accounting routes required by card settlements and tax evidence.
