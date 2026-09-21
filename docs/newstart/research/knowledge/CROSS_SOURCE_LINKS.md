# OFFICIAL ↔ EBOOK ↔ CASE 크로스소스 연결표 (Agent 3)

- 작성: 2026-09-22. 관계 어휘: `SUPPORTS` / `CONTRADICTS` / `NOT_COMPARED` / `CONFLICT_REVIEW` / `EMPIRICAL_EVIDENCE`(CASE가 일반 명제를 실측 검증) / `EMPIRICAL_EXCEPTION`(CASE가 명제 범위를 벗어나는 관측).
- 원칙: **어떤 소스 내용도 타 소스에 맞추어 삭제·수정하지 않는다.** 관계 기록만 한다. 미확정은 NOT_COMPARED.
- ID 출처: OFFICIAL = ccrs k01~k12 + EV-*/MX-* (research-official), EBOOK = EBK-* (ebook_sections.json), CASE = articleId (data/newstart-cafe/gamian/articles 또는 research/article_json).

## 1. OFFICIAL ↔ EBOOK
| OFFICIAL | EBOOK | 관계 | 근거/주의 |
|---|---|---|---|
| EV-NS-PROP-2026 (재산·감면 30~80%) | EBK-2-1, EBK-7-1/7-2 (60~80%) | CONFLICT_REVIEW | 상한 수치 불일치. 조건(시점·가구·차상위) 차이 가능성은 미확인 |
| k02 (부실우려 연체하한 10일 vs 31일) | EBK-6-2 ("10일 이상 연체") , EBK-5-1 (납부일 다음날 기산/90일 장기) | CONFLICT_REVIEW | EBK는 제3의 서술. 확정 규칙 사용 금지 동일 적용 |
| k05 (중개형 원금감면 한정 서술) | EBK-3-1 (85% 빌드업), EBK-7-2 | SUPPORTS(scope 분기) | EBK 서술은 매입형(부실차주) 기준 — k05 scope와 혼용 금지 |
| k09 (전환·창구분리) | EBK-8-1/8-2/8-3 | SUPPORTS | 미서명 5일취소·실효 5회차 경로는 OFFICIAL에 없는 운영자 실무 — 확장 서술 |
| k11 (실효·재조정) | EBK-8-1 | SUPPORTS | — |
| k04 (대상채무·제외) | EBK-2-2 (통신·비협약 제외), EBK-9-1/9-2 (담보 처리) | SUPPORTS | — |
| k07 (신청절차·추심중단) | EBK-3-2 (우편물 방어), EBK-6-1 (금감원 민원) | SUPPORTS | EBK6 추심전략은 OFFICIAL 미수록 실무 — CONTRADICTS 아님 |
| MX-PROPERTY | EBK-7-1, EBK-9-3 | SUPPORTS | 전기차 무배기량 공백은 MX 대비 EBK 고유 서술 |
| EV-TELECOM | EBK-2-2 | SUPPORTS | — |
| CCRS 전체 | EBK-4-1, EBK-APX | NOT_COMPARED | 비교 대상 아님(태도/연혁) |

## 2. EBOOK ↔ CASE
| EBOOK | CASE | 관계 | 근거 |
|---|---|---|---|
| EBK-7-1 재산 공식 | 5904, 5948, 5957 | EMPIRICAL_EVIDENCE | 운영자 예측이 동일 공식(시세−담보−지역공제, 차량 4천만 이하 0) 사용 |
| EBK-7-2 "재산분 100% 상환, 초과분만 감면" | 5957(상환식), 5988·6010(감면 0%) | EMPIRICAL_EVIDENCE | 재산≥채무 시 감면 소멸 실측 |
| EBK-7-2 "감면율의 지배 요인은 소득" | 5904 (소득 8천→40% vs 3천→67~70%) | EMPIRICAL_EVIDENCE | 동일 채무·재산에서 소득 기준만으로 예측 반전 |
| EBK-7-3 사해행위·명의변경 금지 | 5947 (신청 전 부동산매매 계획 → 운영자 예측 거부) | EMPIRICAL_EVIDENCE | '처분 자체'가 문제, 사용처 무관(댓글 제보) |
| EBK-9-2 차량담보 별도 상환 | 5862 (차량번호 조건의 저축은행대출 담보화) | EMPIRICAL_EVIDENCE | 담보 분류 시 원금감면 제외 실측 |
| EBK-9-3 배기량·노후차 예외 | 5957 (2009년식 차량 재산 제외) | EMPIRICAL_EVIDENCE | — |
| EBK-8-1/8-2 전환 경로 | 5884 (실효→장기연체등록→접수 5일~1주 실측) | EMPIRICAL_EVIDENCE | EBK '1~2주' 서술과 부합(하단 5일 관측 추가) |
| EBK-8-1 (5회차 미납 실효) | 5948 (10/20 실효 예정), 5868 (미납5회 실효 계획) | SUPPORTS | 계획 단계 |
| EBK-3-1 (85% = 기본75%+국취10%) | 5866, 5952 (90% 확정) | CONFLICT_REVIEW | 90% 도달 경로(추가감면 근거) 원문 미확인 — 80% 상한 서술(EV-NS-PROP)과 3각 불일치 |
| EBK-6-1 금감원 민원 전략 | 5988 (국민신문고→담당자 재연락) | SUPPORTS(유사 구제경로) | 민원=금감원, 신문고=국민신문고로 상이 — 동일시 금지 |
| EBK-5-1 연체기산 | 5866 (90일 후 직행) | EMPIRICAL_EVIDENCE | — |

## 3. OFFICIAL ↔ CASE
| OFFICIAL | CASE | 관계 | 근거 |
|---|---|---|---|
| EV-NS-PROP-2026 상한 80% | 5866, 5952 (90%) | EMPIRICAL_EXCEPTION + CONFLICT_REVIEW | 확정안 90% 관측. 배경(취약계층 추가감면 등) 미확인 |
| k09/k11 전환·실효 | 5884, 5988, 5948 | EMPIRICAL_EVIDENCE | 전환 파이프라인 실사용 확인 |
| k02 연체하한 논쟁 | (직접 근거 없음) | NOT_COMPARED | 사례에 '부실우려 10/31일' 분기 관측 미수 |
| EV-ROUTING (변경·병행) | 5988(기금0%→신복위 재도전), 6010(워크아웃 검토) | SUPPORTS(방향) / 결과 미확인 | 逆전환 성공 사례 아직 0 |
| MX-DEBT/EV-DEBT-SCOPE 제외채무 | 5948 (비협약 소액 채무 별도 합산) | NOT_COMPARED | 워크아웃 편입 결과는 미추적 |
| k05 매입형 수치 | 5904·5948·5957 운영자 예측 공식 | EMPIRICAL_EVIDENCE | 매입형(부실차주) 산식과 동일한 구조 |

## 4. CASE ↔ CASE
| 쌍 | 관계 | 내용 |
|---|---|---|
| 5866(5개월 확정) ↔ 5867(1년 대기) | SIMILAR-분산 | 동일 제도 내 처리기간 편차 — 평균화 금지, 양쪽 보존 |
| 5904(운영자 40%) ↔ 5957(운영자 75~77%) | CONTRADICTS(조건차) | 상이 감면율 — 소득·연령·재산 조건 차이로 설명 가능하나 확정 아님 |
| 5988(대납 지분차감 거부) ↔ 6010(시세 기준 고수) | SIMILAR | 재산 산정 방식 관철(유연성 없음) 공통 주제 |
| 5884(5일) ↔ EBK-8-3 타임라인(10개월) | SIMILAR-경로차 | 원장초기화 여부·시기에 따른 편차 |

## 5. 운영 메타 관찰 (RAG 신뢰도용)
- 5957에서 운영자가 **"인공지능으로 전자책 내용과 사례를 바탕으로 만든 것"**임을 명시 — 감면율게시판 예측은 EBOOK+CASE 파생 2차 지식. OFFICIAL과 동급 신뢰로 쓰면 안 됨(confidence 낮게).
- 5952에서 차주 본인이 "+챗지피티의 도움" 진술 — 커뮤니티 advice의 AI 순환 가능성 기록.

관련: `TOP10_3SOURCE_MATRIX.md`, `EBOOK_INDEX.md`, `CASE_INDEX.md`, `../ebook/ebook_sections.json`
