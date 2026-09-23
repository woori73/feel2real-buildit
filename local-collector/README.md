# EASY Visible Local Browser Collector — agent (V1 foundation)

A standalone Node.js process meant to run on the business's own PC (e.g. the academy office
computer). It is **not** part of the Java/Gradle build and does not run on the EASY server. It
opens a **visible** (headed) Chromium window, lets the human authenticate normally in it, then
resumes that same window to navigate a deterministic, per-institution workflow (menu → date
range → search → download) and reports status + file metadata back to EASY.

No Qoder dependency. Browser automation is [Playwright](https://playwright.dev) (MIT license,
free, open source) only.

## What this process never does

- Never fills in, reads, or stores a certificate, certificate password, OTP, card password, CVC,
  or full card number. The human enters those directly into the real site's own UI, in the same
  visible window this process opened — this process only *waits* for that to finish.
- Never sends file content to EASY — only status transitions and, on completion, the downloaded
  file's name/size/SHA-256.
- Never listens on any inbound network port. It only makes outbound calls to EASY (poll model),
  so there is nothing on the business's PC for an outside party to connect to.
- Never writes downloaded files into a git-tracked directory (default: a per-user folder outside
  any repo — see `src/config.js`).

## Setup

```bash
cd local-collector
npm install
```

Playwright's Chromium is downloaded automatically on first `npm install` (skipped if already
cached under `%LOCALAPPDATA%\ms-playwright`).

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

## Safe mock verification (no real financial site involved)

This is what proves the lifecycle mechanics end-to-end without touching anything real:

```bash
npm run test:e2e-mock
```

This starts a tiny local "mock financial site" (`mock-site/`, plain HTML — a fake login page and
a fake usage-history page with a downloadable CSV) and a tiny in-memory fake EASY backend
(`test/fakeBackend.js`, same HTTP contract as the real one), then runs the **real, unmodified**
collector against them. A visible Chromium window opens on this machine; since there's no live
human in an unattended CI-style run, the script stands in for the click on the exact same page
the collector opened (see the `onWaitingForAuth` test hook in `src/collector.js` — never used in
production, where this always blocks for a real human). It exits 0 and prints `E2E LIFECYCLE:
PASS` when every stage — including "wrong token gets rejected" — actually happened.

## Adding a real institution (future phase — not this one)

Add a new file under `src/workflows/` shaped like `cardCompanyTestWorkflow.js`: a login URL, a
deterministic "the human finished authenticating" signal (a selector or URL pattern that appears
only after real login), and a declarative step list (fill/click). Register it in
`src/workflows/index.js` keyed by `source_code`. No code change is needed anywhere else, and no
LLM call is ever made in the normal path.

Per the manual-fallback requirement: if a real site changes its layout and a workflow starts
failing, the same EASY admin can always fall back to the existing manual Excel/CSV upload path
(`EasyCollectionIngestService.ingestCsv`) — this collector is additive, not a replacement.
