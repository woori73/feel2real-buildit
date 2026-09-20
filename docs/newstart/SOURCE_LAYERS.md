# NewStart 출처 계층 설계

AI 답변 근거 우선순위. 모든 knowledge record는 `layer` 필드로 출처를 명시한다.

## 계층

| layer | 의미 | 예 | 우선순위 |
|-------|------|----|----------|
| OFFICIAL | 새출발기금 공식 홈페이지·공식 공지·공식 확인서 | data/newstart/official_* | 1 (최고) |
| COMMUNITY | 카페 사례·이용자 후기 | 추후 수집 DB | 2 (참고) |
| BOOK | 전자책·정리자료 | 추후 수집 DB | 3 (참고) |
| CASE | 사용자 상담 사례 | 추후 상담 기록 | 4 (참고) |
| AI_SYNTHESIS | AI가 여러 근거를 종합한 설명 | 답변 생성 시 | 5 (근거 아님) |

## 규칙

1. 공식 규정과 커뮤니티 사례가 충돌하면 OFFICIAL을 우선하고, 답변에서 "공식 규정상 … / 실제 이용자 사례에서는 …"으로 구분한다.
2. OFFICIAL record는 원문 요약이 아닌 `사실/규정/조건/절차 + 출처 URL` 구조를 유지한다.
3. 모든 OFFICIAL record 필수 필드: id, category, topic, content, sourceTitle, sourceUrl, effectiveDate, collectedAt, official, status.
4. 폐지 규정은 삭제하지 않고 status=superseded로 유지하며 현행처럼 답변하지 않는다.
5. 본 KB v1은 OFFICIAL만 포함한다. COMMUNITY/BOOK/CASE는 별도 파일·별도 id 네임스페이스로 추가하고 원본을 훼손하지 않는다.
