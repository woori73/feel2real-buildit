# Agent3 TRUE GAP Collection — 2026-09-22 PM (Academy Continue)

> Base: `origin/main` 1cd88f64 (Merge PR #367) + `origin/docs/newstart-agent3-handoff-20260922` ecf58119
> PR #368: OPEN, merge PROHIBITED (untouched). 5568/4451 reinvestigation PROHIBITED. 5920 source reinvestigation NOT prioritized.
> Corpus: `docs/newstart/research/article_json/` 124 files on origin/main (articleId 5737~6010) + morning HANDOFF spot-check records (5920/5987/5988/5752 READ ONLY).
> Method: JSON-corpus keyword census + per-article provenance read. No cafe re-access, no raw dump, no PII.
> Rule: grades only within source support. CASE subject NOT assumed to be the article author — attributed as "article states" / "commenter states".
> Agent4 artifacts: untouched (separate worktree; this branch contains no discovery/ untracked files).

## 0. Census result (124 JSONs, keyword presence by articleId)

- 금감원 / 금융감독원 / 민원: **0 hits** (no occurrence in any of 124 JSONs)
- 국민신문고(신문고): **5988 only**
- 지급명령 / 이의신청: **5752, 5987 only**
- 등기: 5752, 5855, 5879 · 압류: 5752, 5862, 5867, 5878, 5971 · 본안소송: **5752 only**
- Credit (EN): **0 hits** · 신용정보: 5743, 5856, 5997 · KCB: 5749 only
- 주담대: 5947 only · HF: **0 hits** · 버팀목/전세만기: 5968 only · 디딤돌: 5868, 5883
- 연장: 5868, 5957, 5968, 5971 · 근저당: 5869, 5901, 6010
- 추심: 5745, 5856, 5971, 5987, 5988, 6002 · 위탁: 5855, 5856 · 양도: 5743, 5750, 5866, 5901

## 1. Priority 1 — 5987 지급명령 → 이의신청 이후 후속 결과

- SOURCE: CAFE / ARTICLE: 5987 / BOARD: 질문게시판 / DATE: 2026.09.14 / COMMENTS: 2 / VIEWS: 59 (JSON collected 2026-09-19)
- ARTICLE STATES: payment-order received while fund application stuck at document-omission stage (no receipt number yet); asks what to do. Title claims objection filed ("이의 신청했는데요") but body does not confirm filing completion or court step.
- COMMENTS: 1 peer short advice ("file the objection first" gist, non-operator) + 1 empty (sticker suspected). No operator guidance. No 15-day expression. Photo of order attached (auxiliary, no OCR).
- CHAIN: omission → order received → objection claimed (title) → peer comment → [no court procedure] → [no fund consultation] → [no final outcome]
- GRADE: **RESULT_PARTIAL** (title-claimed action only, outcome unknown). NOT RESULT_CONFIRMED.
- GAP: post-objection result ABSENT. COURT full-chain via 5987 = 0.

## 2. Priority 2 — 5988 국민신문고/캠코 재연락 이후 실제 결과

- SOURCE: CAFE / ARTICLE: 5988 / BOARD: 자유게시판 / DATE: 2026.09.15 / COMMENTS: 5 / VIEWS: 143 (JSON collected 2026-09-19)
- ARTICLE STATES: 2024-말 counseling → 25-초 concern-type 2-month payment → 25-10-말 lapse → delinquent-type application → interim-plan 0 → 26-05 notice of 0% (property value > debt) → 26-06-09 welfare-credit visit (50% mentioned) → 26-09-08 public-petition filing → fund callback pending. Dispute: request to deduct daughter-paid ~8천만 as co-share refused. Author comment 09-17: additional evidence to be submitted.
- COMMENTS: 2 peer + 2 author follow-ups (evidence details), 0 operator. Cited third-party rates in body (70% etc.) are unverified personal claims — MUST NOT be treated as criteria.
- CHAIN: concern → lapse → delinquent → interim 0 → 0% notice → petition → callback pending → [no 2nd-round outcome]
- GRADE: **RESULT_PARTIAL** (ongoing). NOT RESULT_CONFIRMED.
- GAP: post-petition / callback result ABSENT.

## 3. Priority 3 — FSS full-chain (추심 → 금감원 민원 → 상대기관 반응 → 최종 해결)

- Corpus census: 금감원/금융감독원/민원 = 0/124 JSONs.
- NOTE on prior knowledge doc: CASE_INDEX describes a "민원" discussion for article 5856, but article_5856 JSON on origin/main has comments=0 and body only about 40-day delinquency + collection-delegation notice. The discussion is NOT supported by the JSON corpus — preserved as CONFLICT/pipeline-note, NOT as source fact.
- Candidate-adjacent (not FSS): 5855事務위탁예정 notice (0 comments, UNRESOLVED); 5745 peer collection-response talk (no outcome); 5856 delegation question (UNRESOLVED, comments 0 in JSON).
- GRADE: **FSS full-chain RESULT_CONFIRMED = 0**. No 상대기관 반응, no 최종 해결 in corpus.
- FOR AGENT2: do NOT canonicalize any FSS chain; keep OFFICIAL_VERIFY queue open.

## 4. Priority 4 — COURT full-chain (지급명령/이의신청 이후 최종 결과)

| article | board/date | states | comments | grade |
|---|---|---|---|---|
| 5752 | 자유/2026.07.27 | asks whether objection requires receipt of registered mail + seizure timing; 2 failed door-deliveries | 1 peer: "count 2 weeks after receipt; if unreceived → main-action suit; after finality seizure in ~1–3 weeks" (peer, NOT operator) | **UNRESOLVED_CASE** (peer guidance only, no outcome). 15-day expression ABSENT; 2-week peer mention only |
| 5987 | 질문/2026.09.14 | §1 above | 1 peer + 1 empty | **RESULT_PARTIAL** (action claimed, no outcome) |
| 5878 | 자유/2026.08.24 | account seized over guarantee-insurance debt; asks what document releases it after adjustment + durations | 1 generic encouragement (no document/duration) | **UNRESOLVED_CASE** |
| 5971 | 부실차주신청/2026.09.12 | asks whether store-deposit seizure blocks regional deduction (3 sub-questions) | 0 | **UNRESOLVED_CASE** |
| 5862 | 질문/2026.08.20 | savings-bank business loan turned out vehicle-secured; asks about disposal/scrap ban + interest | 1 unanswered question | **UNRESOLVED_CASE** |
| 5867 | 자유/2026.08.21 | delinquent-type applied last Aug → call ~1yr later ("30%") + account-info re-submission | 4 (timeline only) | **RESULT_PARTIAL** (timeline data only; pre-value unknown → predictionVsActual UNKNOWN) |

- GRADE: **COURT full-chain RESULT_CONFIRMED = 0**. 15-DAY SOURCE = 0 (2-week peer mention 1 only). No payment-order → final-resolution chain.

## 5. Priority 5 — Credit4U (actual screen/state reading, not simple question)

- 5920 (NOT in JSON corpus; morning READ-ONLY spot-check): bond-transfer then Credit4U delinquency-info deletion timing variance question; comments 0; no answer, no outcome → **UNRESOLVED_CASE** (per HANDOFF, no re-investigation this round).
- 5997 (질문/2026.09.17, comments 1): article states Toss credit alerts show gradual card-delinquency deletion + fund debt registration BEFORE manager assignment; asks whether registration precedes signing. Operator-account reply: "call the fund; varies per person." → **RESULT_PARTIAL** (real state-change observation via Toss alerts, NOT Credit4U screen) + **OPERATOR_GUIDANCE** (thin, call-to-confirm).
- 5743 (자유/2026.07.25, comments 20): peer advice "check long-delinquency code at Korea Credit Information Service" (1 line, no screen steps) → discussion only, **UNRESOLVED_CASE** for guide purposes.
- 5856 (질문/2026.08.20): names 3 collection-delegation firms (credit-information companies as collectors, NOT Credit4U screen) → question only, **UNRESOLVED_CASE**.
- GRADE: **Credit4U detailed guide = ABSENT** in corpus. Closest state-reading case is 5997 (Toss alerts). No RESULT_CONFIRMED.

## 6. Priority 6 — 주담대/HF (연장 성공/실패/조건부/정상유지, actual cases)

- HF / 주택금융공사: **0 hits** → guide and cases ABSENT.
- Actual extension outcomes (success/failure/conditional/normal): **0 RESULT_CONFIRMED**.

| article | theme | states | comments | grade |
|---|---|---|---|---|
| 5968 | 버팀목 전세만기 12/3 연장 timing (자유/2026.09.11) | asks whether to hold concern-type vs move to delinquent-type before maturity | 1 peer ("credit will bottom out") | **UNRESOLVED_CASE** |
| 5868 | 디딤돌 (감면예측/2026.08.22) | apartment loan balance ~2.78억, normal repayment planned; asks deposit-as-property / house-car protection / post-extension handling if application spans 2027→2028 | 0 | **UNRESOLVED_CASE** (3 questions, no answer) |
| 5957 | 주담보 농협7700+SC9700 + credit debt ~1.747억 (감면예측/2026.09.09) | co-owned apartment, welfare-credit 75–77% estimate thread | operator-account estimate 75–77% (AI-assisted, reference-only) + author follow-ups | **OPERATOR_GUIDANCE** (prediction only) + result **UNRESOLVED** |
| 5947 | 주담대 same-bank concern + pre-application sale plan (감면예측/2026.09.05) | plans to sell apartment before applying | operator-account: pre-application disposal risks non-approval (consult first); peer concurs | **OPERATOR_GUIDANCE** (risk consult, NOT official criteria) + **PENDING** (phone consult planned, no outcome) |
| 5749 | 사업자주택담보 1억 수정조정 (질문/2026.07.27) | adjustment notice received; monthly 150+60 vs income 230; asks property-bar + life-restriction | 1 comment: property>debt → little effect + warning (role flag non-operator in JSON — do NOT upgrade) | **UNRESOLVED_CASE** (guidance-role unclear) |
| 5883 | 디딤돌+후순위 사업자담보 (자유/2026.08.25) | full profile posted, asks rate | 0 | **UNRESOLVED_CASE** |
| 5901 | 근저당 9000 사해행위 수용불가 (자유/2026.08.27) | 26-01 applied → 26-03 interim 0% → pre-signing hold: post-application mortgage deemed concealment; 0% final-note deferred, seeks workout | 1 (note sent, no substance) | **RESULT_PARTIAL** (0% final-note presented, signing deferred — failure-adjacent, preserved) |
| 6010 | 근저당 (부실차주신청/2026.09.19) | property-value-high 0% pre-signing; considers mortgage top-up vs workout | 2 operator-account: apt uses market-price site (villa uses public price); decided method unchangeable; "hold out then workout" | **OPERATOR_GUIDANCE** + **ONGOING** (no final) |

## 7. Follow-ups (§4 of morning HANDOFF — re-read, no re-access)

| target | article/date | result |
|---|---|---|
| 6010 | 부실차주 근저당 문의 / 2026.09.19 / comments 5 | **ONGOING** + OPERATOR_GUIDANCE (×2). Pre-signing 0%, alternatives sought. No final |
| 5904 | 감면율 궁금 / 2026.08.27 / comments 4 | **OPERATOR_GUIDANCE** (prediction 40% at income 8천 / 67–70% at 3천; AI-assisted predictor assumptions, reference-only). Actual **NOT tracked** → predictionVsActual UNKNOWN |
| 5948 | 감면율 궁금 / 2026.09.06 / comments 2 | **OPERATOR_GUIDANCE** (prediction 60–62%, property-0 assumption). Sub-question (bank distinction) unanswered. Actual NOT tracked |
| 5957 | 감면율 예측 / 2026.09.09 / comments 4 | **OPERATOR_GUIDANCE** (provisional 75–77% + co-ownership half-share note, AI-flagged). Actual NOT tracked |
| 5868 | 감면율 조언 / 2026.08.22 / comments 0 | **UNRESOLVED_CASE** (3 questions, no answer) |
| 5968 | 전세만기 / 2026.09.11 / comments 1 | **UNRESOLVED_CASE** (peer 1-line, no outcome) |
| 5947 | 감면율+매매 / 2026.09.05 / comments 3 | **OPERATOR_GUIDANCE** (disposal-risk consult) + **PENDING** (phone consult planned). No outcome |

## 8. Other chains preserved (success/failure/partial — for Agent2 context, NOT gap fills)

- 5866 (자유/2026.08.21, comments 22): article states 03-25 delinquent application (after 90-day delinquency) → 08-21 completion call, 90% reduction, <5 months. → **RESULT_CONFIRMED** (standard-approval success; already in CASE_CHAIN_RESULT).
- 5952 (자유/2026.09.08, comments 1): article states 90% signed last month, monthly ~19,000 repayment started; asks whether incoming family money causes clawback → success **RESULT_CONFIRMED** + new question **UNRESOLVED**.
- 6002 (자유/2026.09.18, comments 2): 06-15 delinquency → 09-17 long-delinquency-code registered → fund visit pending; benefit maintained → **RESULT_PARTIAL**.
- 5743 (자유/2026.07.25, comments 20): ARTICLE-author chain = applied → acceptance done → interim-plan in ~2 weeks → **RESULT_PARTIAL**. COMMENTER sub-chain (peer states own 03-03→03-24 interim 74%→05 transfer-notice→06 takeover-notice→07-14 manager→07-22 final signing) = **THIRD_PARTY RESULT_CONFIRMED** — separated, NOT merged into article-author chain.
- 5750 (부실차주신청/2026.07.27, comments 2): 1st signing done, 2 guarantee-insurance bonds transferred 29th → asks weeks-to-e-sign → peer guess "early Sep" → **RESULT_PARTIAL** (1st done, 2nd pending).
- 5745 (자유/2026.07.26, comments 5): peer collection-response talk (small-payment vs hold) → discussion only, **UNRESOLVED_CASE**.
- 5855 (질문/2026.08.20, comments 0): delegation-notice received while interim-plan pending + added-bond visibility question → **UNRESOLVED_CASE** (contains 등기 mention).
- 5879 (자유/2026.08.24, comments 0): concern-type 3rd month note (등기 keyword only) → **UNRESOLVED_CASE**.
- 5878, 5971, 5862, 5869, 5883: as tabled above — all **UNRESOLVED_CASE** (failures/pending preserved, none upgraded).

## 9. Agent2 canonicalization handoff

- READY for canonicalization: §§1–2 (5987/5988 chains+grades), §7 table (7 follow-ups), §6 table (8 mortgage rows), §§4–5 (court/credit negatives with supporting IDs).
- DO NOT canonicalize: any FSS chain; any COURT final-resolution; any Credit4U detailed guide; any HF/mortgage-extension outcome; 5904/5948/5957 predictions as criteria (keep as OPERATOR_GUIDANCE/prediction, predictionVsActual UNKNOWN); peer comments as OPERATOR_GUIDANCE (5752 2-week, 5749 property>debt — role unclear or peer).
- CONFLICT preservation: C-01~C-04 per morning HANDOFF unchanged; 90% cases (5866/5952) vs any upper-limit official text stays CONFLICT_REVIEW (this round adds no new evidence).
- Provenance keys for all rows: SOURCE=CAFE / CAFE_KEY=newhopero / CLUB=31312978 / ARTICLE=<id> / BOARD / DATE / ROLE (article-author vs commenter vs operator-account) / COMMENTS_n / COLLECTED=per-JSON lastVerifiedAt. No nicknames, no contacts, no accounts, no raw dumps in this file.

## 10. Compliance

- No 5568/4451/5920-source reinvestigation. No cafe re-access. No Knowledge DB ingest / ACTIVE / production write / application code change.
- Sanitized derivative only. No PII. No credentials. No merge/deploy from this branch.
- Branch: `docs/newstart-agent3-truegap-20260922-pm`. PR #368 untouched (still OPEN, no merge).
