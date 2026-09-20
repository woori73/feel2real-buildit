# /newstart UI 적용안 (공식 KB 기반, UI 수정 전 제안)

> 원칙: 공식 신청·조회 기능은 NewStart가 대행하지 않고 공식 링크로 연결한다.

## USER-FACING 후보 (화면 노출)

| # | 블록 | 데이터 근거 | 액션 |
|---|------|-------------|------|
| 1 | 새출발기금 대상 확인 | eligibility_001~004, delinquency_001~002 | Findmatchprogram 공식 링크로 연결 |
| 2 | 부실차주 / 부실우려차주 차이 | delinquency_001~002 | — |
| 3 | 어떤 채무가 포함되는가 | debt_001~003, exclusion_001~002 | 협약기관 조회 링크 |
| 4 | 감면·상환 조건 | principal_001~003, interest_001~002, repayment_001~002 | — |
| 5 | 신청 절차 | application_001~004, review_001 | 공식 신청 링크 |
| 6 | 신청 후 진행과정 | collection_001~002, credit_001, public_001, cancellation_001 | 진행상태 확인 링크 |
| 7 | 필요 서류 | documents_001 + official_documents.json | 제출서류 공지 링크 |
| 8 | 공식 신청하기 | official_links.json (application) | 외부 링크 |
| 9 | 진행상태 확인 | official_links.json (Rslt) | 외부 링크 |
| 10 | 상담센터 | Cntr 링크 + 콜센터 1660-1378 | 외부 링크 |
| 11 | 최근 제도 변경 | notice_001~003 (26.3.25 / 비상장주식 / 26.6.25) | 공지사항 링크 |
| 12 | FAQ | faq_* + Q&A 전수 | FAQ 링크 |
| 13 | 사칭 주의 | fraud_001~002 | — |

## AI KNOWLEDGE (화면 미노출, 검색용)

- FAQ Q1~Q43 구조화, 예외조건(한도 개별적용·대출선택·상계예외), 세부규정(취약계층·추가감면·소급), 제출서류 발급처, 공지 49건 목록+핵심 12건 본문, 제도변경 이력·충돌표.

## AI retrieval 연결 설계

- JSON 파일(knowledge/links/documents)을 그대로 로드해 category + questionAliases 키워드 매칭으로 1차 검색, effectiveDate 최신 우선.
- 답변 템플릿: "공식 규정상 … (출처: {sourceTitle}, {sourceUrl})".
- 변경 감지: NOTICES.md 폴링 규칙 참조.
- 다음 단계: 백엔드 `/newstart` API 또는 정적 JSON 서빙 방식 결정 후 연결 (본 작업 범위 밖).
