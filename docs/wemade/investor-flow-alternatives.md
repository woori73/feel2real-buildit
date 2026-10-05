# 투자자별 수급 대체 수집경로 조사 (2026-10-05)

## 현황
- 네이버 증권 투자자 페이지(`m.stock.naver.com`)는 VM/서버 환경에서 bot 차단으로 수집 불가.
- 우회(헤더 위장, 프록시 회피 등)는 시도하지 않는다 — 정책상 금지.
- 현재 `/wemade` 수급 영역은 "데이터 없음"으로 표시하고, 기존 `WemadeInvestorFlow` 수집기는 graceful PARTIAL로 동작한다.

## 권장 대안: KRX OpenAPI (공식)

- **URL**: https://openapi.krx.co.kr
- **성격**: 한국거래소 공식 REST API. 스크래핑이 아니다.
- **비용**: 무료. 인증키 유효기간 발급일로부터 1년, 일 10,000회 호출 한도.
- **인증**: Bearer 토큰 (`Authorization: Bearer {KEY}`)
- **필요 절차 (NEEDS_OWNER)**:
  1. openapi.krx.co.kr 회원가입·로그인
  2. 마이페이지 → API 인증키 신청 (담당자 승인, 보통 1일 이내)
  3. **사용할 엔드포인트를 각각 별도 신청** (키만으로는 데이터 수신 불가)
     - `코스닥 일별매매정보` (112040 위메이드, 101730 위메이드맥스용)
     - 필요 시 `유가증권 일별매매정보`
  4. 발급 키를 운영 서버 환경변수 `KRX_AUTH_KEY`로 설정 (GitHub·로그 노출 금지)
- **JVM 클라이언트**: `krx-reader` (io.github.aaron-jang:krx-reader-core:0.1.0, MIT) — Kotlin/Java용 공식 OpenAPI 클라이언트. Spring Boot에서 그대로 사용 가능.
- **데이터 범위**: 일별매매정보에 투자자별(외국인·기관·개인 등) 매매 데이터 포함. 60일 제한 없이 과거 데이터 조회 가능 → 자체 장기 수급 DB 구축 목적에 부합.

## 검토 후 제외한 대안

| 대안 | 제외 사유 |
|---|---|
| 공공데이터포털(data.go.kr) 금융위원회 API | 주식시세정보 등 가격 데이터만 제공. 투자자별 매매동향 없음 |
| KRX 정보데이터시스템(data.krx.co.kr) | 2025-12-27부터 회원제 전환. API 없이 수동 다운로드만 가능 |
| pykrx | Python 스크래핑 라이브러리. Java 서버에 부적합, 이용약관 불명확 |
| 네이버 우회 수집 | bot 차단 우회는 정책상 금지 |

## 구현 계획 (키 발급 후)
1. `WemadeKrxFlowClient` 신규 생성 — `KRX_AUTH_KEY` 환경변수 전용, 키 미설정/호출 실패 시 조용히 비활성화
2. 장 마감 후 스케줄러에 `KRX → (폴백) 기존 수집기` 순서로 편입, source별 fetched/accepted/duplicate/error 기록 유지
3. 기존 `wemade_investor_flows` 테이블 재사용 (symbol별 저장, content_hash dedupe)
4. UI는 데이터가 들어오는 대로 "데이터 없음" → 실수급으로 자동 전환 (코드 변경 불필요)

## 참고
- krx-reader: https://github.com/aaron-jang/krx-reader
- KRX OpenAPI 신청 가이드: https://github.com/raccoonyy/pykrx-openapi/blob/HEAD/docs/krx-openapi.md
