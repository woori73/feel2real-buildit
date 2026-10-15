# TOP10 실제 차주 질문 — 3-소스 커버리지 매트릭스 (Agent 3)

- 작성: 2026-09-22 (Qoder/Agent 3 Knowledge Curation)
- 입력: `docs/newstart/research-official/comparison/top10_questions.json` (10문),
  `docs/newstart/research-official/ccrs/MANIFEST.md` (k01~k12+evidence),
  `docs/newstart/ebook/ebook_sections.json` (EBK-* 19절),
  `data/newstart-cafe/gamian/articles/` (구조화 CASE 11건),
  `docs/newstart/research/article_json/` (카페 원문 124건)
- 규칙: FOUND=그 소스에 실제 근거 텍스트가 존재 / PARTIAL=일부 하위개념만 / NOT_FOUND=확인된 근거 없음.
  **OFFICIAL FOUND라도 3-소스 동시 FOUND가 아니면 '완결'로 보지 않는다.** 값이 없는 칸은 추측하지 않고 NOT_FOUND.

| # | 질문 | OFFICIAL | EBOOK | CASE | 비고 |
|---|---|---|---|---|---|
| 1 | 부실차주인데 왜 개인워크아웃을 검토하는가 | FOUND (EV-NS-PROP-2026, EV-PW-PROP, concepts.json) | FOUND (EBK-2-2: 채권단 동의·상각 대기 vs 매입 후 통보, 투트랙) | FOUND (5988: 신복위 50% 안내 vs 기금 감면0%, 6010: "결정난 방식 변경 불가→버티다 개인워크아웃" 제보) | 3-소스 완결 |
| 2 | 재산이 있으면 어떻게 평가되는가 | FOUND (EV-NS-PROP-2026, EV-NS-PROP-HIST, MX-PROPERTY) | FOUND (EBK-7-1: 재산=시세−근저당−임대차보증금−지역공제) | FOUND (5904: 전세−공제+금융+차량0 산정, 5957: 공동명의 절반−담보, 5988: 재산가액 1억 산정·대납차감 거부, 6010: 아파트=부동산테크 시세) | 3-소스 완결. 단 EBK "감면 60~80%" vs EV-NS-PROP-2026 "30~80%" **CONFLICT_REVIEW** |
| 3 | 집 있으면 신청 불가인가, 감면액이 달라지는가 | FOUND (MX-PROPERTY, EV-NS-PROP-2026) | FOUND (EBK-7-1 서두: "자가 있어도 청산가치 계산하면 결과 달라짐") | FOUND (5988, 6010: 주택 보유 상태로 신청 접수·심사 진행, 감면 0% 판정) | 3-소스 완결 — 답은 '신청 가능·감면액 축소' 방향으로 3소스 일치 (SUPPORTS) |
| 4 | 자동차·예금·보험·가상자산·비상장주식 취급 | PARTIAL (MX-PROPERTY — 가상자산/비상장주식 명시 범위 확인 필요) | PARTIAL (EBK-9-3 차량/EBK-7-1 원칙만; 가상자산·비상장주식·보험 미수록) | PARTIAL (차량: 5862, 5904(4천만 이하 0원), 5957(2009년식 제외) / 주식·코인: 5947 댓글 "디테일하게 보는 추세", 5868 본문 "주식코인보험 없음" 질의 / 보험: 5948 흥국생명 채무(자산 아님)) | **갭**: 가상자산·비상장주식의 재산산정 기준 3-소스 모두 확정 근거 없음 → NEW COLLECTION 후보가 아니라 OFFICIAL 원문 재확인 필요 |
| 5 | 채무보다 재산이 많으면 어떻게 되는가 | FOUND (EV-NS-PROP-2026, EV-PW-PROP) | FOUND (EBK-7-2: 재산분은 100% 상환, 초과분만 감면) | FOUND (5988, 6010: 재산>채무 → 감면 0% 실측) | 3-소스 완결 |
| 6 | 재산 때문에 원금감면 효과가 작아질 수 있는가 | FOUND (EV-NS-PROP-2026, EV-PW-PROP) | FOUND (EBK-7-2 각주: "소득 적으면 감면율 높아도 실질 감면액 감소") | FOUND (5957: 재산가액 6,100만~9,100만은 감면 적용 안 됨 상환액 반영, 5988/6010 0%) | 3-소스 완결 |
| 7 | 새출발기금과 개인워크아웃은 재산을 각각 어떻게 반영하는가 | FOUND (EV-NS-PROP-2026 + EV-PW-PROP + MX-PROPERTY 비교) | PARTIAL (EBK-7-1은 기금측만; EBK-2-1은 개인회생 재산반영 비교. 워크아웃 재산산정 공식 없음) | PARTIAL (5988 댓글: "개인워크는 공제금액이 커서 나을 것" — 경험 진술, 공식 없음) | **반쪽 갭** — OFFICIAL 없으면 성립하지 않는 질문 |
| 8 | 새출발기금 제외 채무를 개인워크아웃에 포함할 수 있는가 | FOUND (EV-DEBT-SCOPE, MX-DEBT, EV-ROUTING) | FOUND (EBK-2-2: 통신채무·비협약 대출 → 워크아웃 가능) | NOT_FOUND (구조화 11건에 '기금 제외채무→워크아웃 포함' 결과확인 사례 없음) | EBOOK 단독 지원 진술 — CASE 검증 대기 |
| 9 | 통신요금이 섞이면 어떤 차이가 발생하는가 | FOUND (EV-TELECOM, MX-DEBT) | FOUND (EBK-2-2 + 부록: 저자 본인 개인워크아웃+통신채무 조정 실체험) | NOT_FOUND (수집 124건 내 통신채무 결과사례 미확인) | #8과 동일 구조의 갭 |
| 10 | 신청 후 워크아웃으로 변경/별도 진행, 반대의 경우도 가능한가 | FOUND (EV-ROUTING, EV-CREDIT-INFO) | PARTIAL (EBK-8-1/8-2는 우려→차주 전환만; 기금확정 후 워크아웃 역방향 미수록, EBK-2-2 '투트랙 별도 병행'만) | PARTIAL (순: 5884 실효→접수 5일~1주 실측 / 역: 5988 차주0%→신복위 재도전 진행중(결과 미확인), 6010 워크아웃 검토중) | 역방향 '성공' 결과확인은 아직 0건 — 추적 대상 |

## 요약
- 3-소스 완결: Q1, Q2, Q3, Q5, Q6 (5/10)
- OFFICIAL+EBOOK / CASE 갭: Q8, Q9 — **감면율게시판·자유게시판에 제외채무 처리 결과 사례가 없는 것이 갭의 실체**
- OFFICIAL 단독 의존: Q7 (재산산정 비교는 공식자료 없인 성립 불가)
- 역방향 전환(Q10)·감면 상회(90% 사례 vs 공식 80% 상한 서술)는 CONFLICT_REVIEW/추적 항목

## 미해결 충돌 등재 (삭제·수정 금지, 관계만 기록)
1. **감면율 상한**: EV-NS-PROP-2026 30~80% ↔ EBK-2-1/EBK-7 "60~80%" ↔ CASE 5866·5952 확정 90% (추가감면 배경 미확인) → CONFLICT_REVIEW
2. **부실우려 연체하한**: k02 (10일 vs 31일 evidence 병존) ↔ EBK-6-2 '10일 이상 연체' 서술 → third position, CONFLICT_REVIEW
3. **전세보증금 차감**: EBK-7-1 "원칙적 부채(전액 차감)" ↔ 본문의 "상담원마다 안 된다는 분들" 진술 → 같은 EBOOK 내 양론 보존
4. **공동명의 산정 순서**: 5957 운영자 "시세 절반−담보 절반, 공제는 그대로" vs "본인 명의 담보 전체공제 가능성" → 미확정, 결과 확인 후 CASE 추가

관련: `CROSS_SOURCE_LINKS.md`, `EBOOK_INDEX.md`, `CAFE_INVENTORY.md`, `CASE_INDEX.md`
