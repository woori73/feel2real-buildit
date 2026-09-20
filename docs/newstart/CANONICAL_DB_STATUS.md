# NewStart Canonical DB 상태 (2026-09-20)

## 현재 상태: SOURCE DATA READY / PARTIAL

| Source | 상태 | 비고 |
|--------|------|------|
| OFFICIAL_NEWSTART | ✅ READY | 공식 홈페이지 전수 수집, 58 records, CURRENT/SUPERSEDED 관리 |
| OFFICIAL_FSC | ❌ NOT YET | 금융위원회 자료 미수집 (별도 Agent 담당) |
| BOOK | ❌ NOT YET | 전자책/정리자료 미수집 (별도 작업) |
| COMMUNITY | ❌ NOT YET | 카페 데이터 미수집 (별도 Agent, 마지막 수집 진행 중) |
| CASE | ❌ NOT YET | 상담 사례 미수집 (향후 상담 기록에서 생성) |

## Canonical DB 구축 상태

**CANONICAL DB BUILD: NOT YET COMPLETE**

Canonical DB는 위 5개 source가 모두 수집 완료된 후 별도 단계에서 구축한다.

### 구축 순서
1. OFFICIAL_NEWSTART (완료) + OFFICIAL_FSC → 공식 규정 통합
2. + BOOK → 실무 설명/판단 로직 보강
3. + COMMUNITY + CASE → 실제 사례/경험 보강
4. 우선순위: 공식 최신자료 > 전자책 > 실제 사례

## Current Feed Status (Latest Links)

| Source | Record Count | Verification Status |
|--------|-------------|---------------------|
| OFFICIAL_NEWSTART | 3 | ✅ VERIFIED (공식 홈페이지 대조 완료) |
| NEWSTART_CAFE | 0 | ❌ REMOVED (로그인 필요로 검증 불가) |
| OFFICIAL_CCRS | 0 | ❌ REMOVED (로그인 필요로 검증 불가) |

**Note**: NEWSTART_CAFE 3건, OFFICIAL_CCRS 2건은 네이버 카페 로그인 필요로 실시간 검증 불가하여 production feed에서 제거함. 향후 관리자가 직접 검증한 글만 추가 가능.

## JSON Single Source of Truth

**Canonical Path**: `data/newstart/feeds/latest_links.json`

**Deploy Copy**: `src/main/resources/static/data/newstart/feeds/latest_links.json` (Spring Boot static serving용)

두 파일은 **동일해야 함**. 배포 시 validation 필요.