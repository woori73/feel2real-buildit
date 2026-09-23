# NewStart A to Z Rebuild Handoff

## Workspace

- Base main SHA: `bb6e3795b74184702f3e3c6c876ff91e6ec5a523`
- Branch: `feat/newstart-atoz-core-tools-rebuild`
- Current HEAD: `bb6e3795b74184702f3e3c6c876ff91e6ec5a523`
- Dedicated worktree: `C:\Users\VIEW LIFE\feel2real-buildit-newstart-rebuild`

## Completed

- Created and verified the dedicated clean worktree from the fetched `origin/main`.
- Preserved all existing NewStart and non-NewStart worktrees without changes.
- Located the existing FSS complaint helper implementation on `origin/feat/newstart-fss-complaint-helper-v1`.

## Current Phase

Phase 1–3 complete: repository audit, recovery audit, and safe-integration plan. No product implementation has started.

## Final Phase 4–7 Status

- Final implementation CI: Build Check `35831184023` succeeded for `eb1284d8` before this handoff-finalization commit.
- FSS: recovered selectively; GitHub Build Check `35830100541` succeeded for `62336db6`. Local Gradle remains blocked by the loopback environment.
- Credit4U, application preparation, result/status, and court guides: implemented as public, non-authenticating guide routes with direct official handoffs and no credential collection.
- A-to-Z: implemented at `/newstart/a-to-z`; its contextual links reuse the same helper routes as the home cards.
- Home: rebuilt around the mandated hero, debt-situation entry, five practical tools, A-to-Z, chat, and preserved latest updates.
- Official fact audit: `NEWSTART_ATOZ_2025_TO_2026_UPDATE_AUDIT.md` added; no unsupported UI numbers or court deadline calculations were added.
- Untouched: internal-detail worktree and canonical-RAG runtime branch.

## QA Summary

- Route and link contract: static review confirms home and A-to-Z use the same FSS, Credit4U, application, status, and court helper routes.
- Official handoffs: FSS, Credit4U, NewStart Fund application/result, and ECFS links point to the specified official domains.
- Court safety: the client-side note builder only interpolates user-entered text and adds no deadline calculation or legal conclusion.
- Credit4U privacy: no input or API exists on the guide page.
- Visual QA: CSS uses responsive single-column mobile cards and bounded page widths; interactive browser rendering remains a follow-up because local Gradle cannot establish a loopback listener in this environment.

## Current Main Map

| Area | Current main implementation | Recovery decision |
|---|---|---|
| Home | `/newstart`, `NewStartPageController`, `templates/newstart/index.html`, `newstart.js`, `newstart.css` | Rebuild the page information architecture in a later phase; preserve AI, chat, profile, and updates integrations. |
| AI | `NewStartAiChatController` / `NewStartAiChatService`, `POST /api/newstart/ai/chat` | Keep the existing route and use it for the new hero CTA. |
| Debt situation | Home-only non-persistent situation panel | Replace its entry presentation later; do not claim the data is stored. |
| Information-sharing chat | `/newstart/chat`, chat controller/service/WebSocket stack | Keep as the secondary community entry. |
| Latest information | official-update entity, repository, public/admin controllers, `updates.html`, discovery adapters | Keep; later route home cards to internal details only when the recovered detail view is integrated. |
| Official discovery | NewStart Fund, FAQ, FSC, CCRS, Hope Return adapters; scheduler and review/publication tests | Already in main through the official-discovery work. |
| Knowledge source registry | `newstartknowledge` entities, retrieval, review/ingest services, official source research files | Keep as the authoritative in-product knowledge layer. |
| Naver login | existing auth fragment/profile and Naver/OAuth work | Keep unchanged. |
| Application, result/status, Credit4U, ECFS | No dedicated user route, API, template, or test located in current main | Build only after recovery map and official-source audit. |

## Recent Branch and PR Recovery Audit

### PR #381 — FSS complaint helper

- Source: `origin/feat/newstart-fss-complaint-helper-v1`
- Head: `87c0922683e1455dc418c57359994ed0647f2492`
- Merge base with current main: `1352bb01f6cd5a2b7ee059b738db3645cba5ab3b`
- Merged / main included: no / no
- Unique commits: `476d048a`, `fd68daf1`, `0c19f469`, `87c09226`
- Files: two controllers, `FssComplaintHelperService`, FSS source JSON, FSS template, home entry change, two tests, and two audit/source documents.
- Dependencies: existing authenticated `/api/**` policy and `WikiAiRouterClient`; no migration or persistence layer.
- Tests: `NewStartFssComplaintControllerTest`, `FssComplaintHelperServiceTest`.
- Safe to reuse: yes, with minimal integration rather than blind cherry-pick.
- Reuse method: copy the eight FSS-specific production/test/resource files into the current-main structure, then manually add only the intended home/A-to-Z links. Do not take the old full `index.html`, because the home is being rebuilt and current main has later UI/auth/discovery changes.

### PR #384 — discovery URL normalization

- Source: `origin/fix/newstart-discovery-url-normalization`
- Head/base: `3e648282758710138a8f2d9d9e42176fcce9c5d2`
- Merged / main included: yes / yes.
- Unique files against current main: none.
- Reuse method: none; preserve the current-main code.

### Official discovery completion

- Source: `origin/feat/newstart-official-discovery-complete`
- Head/base: `4dd7c330be72e32e363394ab20c78fe7274fd9b5`
- Merged / main included: yes / yes.
- Reuse method: none; use current main's reviewed publication pipeline and adapters.

### Canonical RAG integration

- Source: `origin/feat/newstart-canonical-rag-20260922`
- Head: `aba26fa5f98ac2b05b5ff819406a63e7454d4d05`
- Merge base: `1cd88f64c5cac935781472c1caeaa32620795c14`
- Merged / main included: no / no.
- Unique work: canonical answer policy/evidence composer, retrieval/controller wiring, source-link bundles, and golden offline QA fixtures.
- Dependencies: existing `NewStartAiChat*` and `newstartknowledge` retrieval/relation model.
- Reuse method: do not merge during the home rebuild. Treat as a dedicated later integration candidate after its answer contract, source priority, and internal-vs-official link semantics are reviewed against the new A-to-Z UI.

### Canonical source-data handoff

- Source: `origin/feat/newstart-agent1-rag-integration-20260922`
- Head: `728e3970d62b543e9290db59143447d415e218d2`
- Merged / main included: no / no.
- Unique work: canonical FAQ, case, relation, and answer-policy documents; no runtime code.
- Reuse method: retain as a research/reference source; do not copy it into the UI implementation yet.

## Existing Work Found

### FSS complaint helper

- Source branch: `origin/feat/newstart-fss-complaint-helper-v1`
- Relevant commits: `fd68daf1`, `0c19f469`, `87c09226`
- Page route: `/newstart/fss-helper`
- API: `GET /api/newstart/fss/companion`, `POST /api/newstart/fss/compose`
- Controller: `NewStartFssComplaintController`, `NewStartFssHelperPageController`
- Service: `FssComplaintHelperService`
- Template: `templates/newstart/fss-helper.html`
- Sources: `newstart/fss/fss_complaint_sources.json`
- Tests: `NewStartFssComplaintControllerTest`, `FssComplaintHelperServiceTest`
- Behaviour: ebook-based samples, user-authored situation input, AI draft only, user review/edit, and direct official FSS handoff. No automatic submission or persistence of complaint text.

### FSS deep audit

1. Sample selection: `GET /api/newstart/fss/companion` returns three provenance-tagged ebook samples.
2. User situation input: `POST /api/newstart/fss/compose` accepts `sampleId` and a max-4,000-character user narrative.
3. Draft: the service requests a max-6,000-character organized draft through `WikiAiRouterClient`.
4. No fabricated facts: the system prompt prohibits dates, amounts, institutions' statements, legal conclusions, outcomes, and harm facts not supplied by the user; missing facts are marked for confirmation.
5. Review/edit: the returned text is explicitly a draft for the user to inspect, edit, copy, and submit personally.
6. Official connection: the page/template provides the FSS official handoff; no automatic submission exists.
7. Stored data: none for complaint narratives or generated drafts (stateless request/response).
8. Privacy: no complaint-body logging, analytics capture, or error echo; the final branch added explicit AI-transmission disclosure.
9. Authentication: the page follows the NewStart chat policy; APIs rely on the existing authenticated `/api/**` catch-all policy.
10. Tests: controller and service unit tests are present in the source branch and must travel with the recovered feature.

## Core Tool Reuse Matrix

| Core tool | Existing | Source | Route/API/UI/test | Reuse |
|---|---|---|---|---|
| 1. FSS complaint | Yes | `origin/feat/newstart-fss-complaint-helper-v1` / PR #381 | `/newstart/fss-helper`; companion/compose APIs; template; two tests | Recover the focused feature files; do not cherry-pick the old home. |
| 2. Credit4U | Partial research only | official comparison evidence and ebook/research corpus | No dedicated route/API/UI/test | Design a guided internal page after official/source-image audit. |
| 3. Bad-debtor application | Partial research only | CCRS official research corpus and ebook | No dedicated route/API/UI/test | Design an application-preparation guide, never an in-app application flow. |
| 4. Result/status | No dedicated implementation | current home has only generic status notes | No result lookup route/API/test | Add an explanatory guide plus official result lookup later. |
| 5. ECFS/payment-order response | Partial case/research corpus only | ebook/research knowledge corpus | No dedicated route/API/UI/test | Create the factual-input-only helper after court-source verification. |
| A to Z | No | ebook and official/research documents | No navigation/page implementation | Build one shared content model with contextual links, not duplicate tool pages. |
| Latest information | Yes | current main official-update/discovery system | public/admin APIs, discovery adapters, tests | Preserve; internal-detail recovery remains separate. |
| RAG | Partial, branch-only runtime integration | canonical RAG branches | controller/service/policy/QA branch diff | Defer to a separate, contract-led integration. |
| Internal detail | Partial, uncommitted only | `feel2real-buildit-newstart-internal` | update detail template/DTO/service/migration/tests | Recovery needed, but do not copy until its owner commits or provides a reviewable branch. |

## Internal Detail Read-only Audit

- Worktree: `C:\Users\VIEW LIFE\feel2real-buildit-newstart-internal`
- Base head: `bb6e3795`.
- Tracked changes: 14 modified files, plus 7 untracked source/test/template/migration files.
- Scope: internal official-update detail page, official-source link policy, content persistence migration `V993`, home/update template wiring, security/workflow changes, and related tests.
- Main inclusion: not in current main.
- Recovery needed: yes, but only after the worktree owner turns the changes into a committed reviewable branch or PR. No files were copied and no state was changed.

## Home Information Architecture Plan

### Current

Hero → four generic cards (status, AI, chat, updates) → embedded AI → non-persistent situation panel → latest updates.

### Target

Hero with the mandated title/subtitle and AI CTA → debt situation → five action-oriented core tools → A-to-Z guide → information-sharing chat → latest information.

### Gap and implementation rule

The five quick actions must link to the same focused helpers that A-to-Z items link to. They are not copied explanatory cards. The obsolete generic four-card section and duplicated AI card will be removed only in the later home/UI phase.

## Implemented

### FSS helper recovery checkpoint

- Selectively restored the FSS-specific production, resource, template, documentation, and test files from `origin/feat/newstart-fss-complaint-helper-v1`.
- Deliberately excluded that branch's legacy `templates/newstart/index.html`; the later home rebuild remains responsible for the new quick-action entry.
- Recovered route: `/newstart/fss-helper`.
- Recovered APIs: `GET /api/newstart/fss/companion`, `POST /api/newstart/fss/compose`.
- Recovered protections: user-fact-only draft prompt, confirmation markers for missing facts, no complaint persistence, no complaint-body logging, no automatic submission, and official FSS handoff.

### FSS test gate

- Attempted the two recovered tests three times: normal Gradle daemon, `--no-daemon`, and `--no-daemon` with empty `org.gradle.jvmargs`.
- Every attempt stopped before compilation/test discovery with `java.io.IOException: Unable to establish loopback connection`.
- This is an environment/Gradle startup blocker, not a failing FSS test assertion. FSS test gate remains **not passed** until the local loopback issue is resolved and the recovered tests execute.

## Not Implemented

- A to Z page structure and contextual tool links.
- Core 5 action experiences.
- 2025 to 2026 official fact audit.
- Official-reference and Library audit.
- Latest-information pipeline audit.
- Responsive visual QA and regression test run.

## Official Sources

Not yet audited in this branch. Use the prescribed source priority and record each changing fact in `NEWSTART_ATOZ_2025_TO_2026_UPDATE_AUDIT.md`.

## Tests

- Passed: worktree Git verification only.
- Failed: none.

## Known Issues

- The main tree does not currently contain the FSS helper files found in the dedicated FSS branch. Reuse/integration must be assessed before implementation; do not rewrite the helper from scratch.
- Gradle cannot start in this environment because it cannot establish its required loopback connection. This blocks the mandatory FSS test gate and therefore blocks Phase 5–7 implementation under the approved instructions.

## Next Exact Step

Resolve the Gradle loopback startup issue, rerun `NewStartFssComplaintControllerTest` and `FssComplaintHelperServiceTest`, then commit the FSS recovery checkpoint. Only after the FSS test gate passes may Phase 5–7 implementation begin.

## Remaining Phases

4. A to Z structure
5. Official fact audit
6. Home typography/UI rebuild
7. Core five quick actions
8. A to Z contextual links
9–15. Official integrations, information pipeline, footer/privacy
16. Visual QA
17. Regression/build
18. Commit, push, PR, CI
