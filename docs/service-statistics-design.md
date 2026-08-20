# 서비스 통계 및 랜딩 사회적 증거 설계

> 상태: 일부 구현 — 학원별 세션 기준 일별 방문 집계·공개/Admin 표시(V941)
> 상태: 2026-08 문서 정리 기준, 오래된 이슈 번호와 분리해 현재 통계 설계 참고 문서로 유지한다.

## 1. 목표와 원칙

`/` 랜딩에 실제 서비스 이용 근거를 보여주고, 운영자가 유입→빌더→가입→발행 흐름을 판단할 수 있는 지표 정의를 고정한다.

- 공개 숫자는 DB 또는 자체 1st-party 집계를 SoT로 사용한다. GA4 값을 랜딩 요청마다 조회하지 않는다.
- GA4는 유입 경로와 행동 퍼널, Search Console은 검색 노출 성과에 사용한다.
- 방문자는 실제 사람이 아니라 브라우저/세션 단위 추정치다. 공개 문구에는 `방문 횟수`처럼 측정 단위를 명시한다.
- 데모·테스트·관리자 데이터, 알려진 봇, 정적 파일 요청을 집계에서 제외한다.
- 숫자가 작거나 표본이 불안정하면 숨기며 임의로 부풀리지 않는다.

## 2. 랜딩 공개 지표

| 지표 | 정의/계산 | 데이터 원천 | 현재 가능 여부 |
|---|---|---|---|
| 운영 중인 학원 홈페이지 | `Academy(PUBLISHED, visible)`, 내부/데모 제외 | `academies` | 가능 |
| 공개 수업 | 공개 Academy에 속한 `LessonProduct(PUBLISHED, visible)` | `lesson_products`, `academies` | 가능 |
| 이용 지역 | 공개 Academy의 정규화된 `city` distinct 수 | `academy_regions` | 정규화 후 가능 |
| 최근 30일 신규 홈페이지 | 공개 Academy 중 `created_at >= now-30d` | `academies` | 가능. 물리 삭제 건은 제외됨 |
| 공개 콘텐츠 | 공개 블로그 글과 READY 학원 영상(유형별 표시) | `academy_blog_posts`, `academy_videos` | 가능 |
| 학원 페이지 누적 방문 | HTTP 세션·학원·일자별 1회인 공개 학원 방문 횟수 합계 | `academy_daily_visits` | 구현. 내부/데모 분류 제외는 후속 |
| 홈페이지 완성시간 | 성공 사용자의 `academy_published - builder_started` 중앙값(P50) | 신규 제품 이벤트 | 계측 필요 |
| 상담 연결 | 상담 버튼 클릭 또는 제출 완료의 순 세션 수 | 신규 제품 이벤트 | 상담 기능 이후 |

초기 권장 조합은 `운영 중인 홈페이지`, `공개 수업`, `이용 지역`이다. 방문 데이터가 충분히 쌓이면 `학원 페이지 누적 방문`을 추가한다.

## 3. 내부 KPI

### 전환 퍼널

- 랜딩→빌더 시작률 = `builder_started` 순 세션 / `/` 순 세션
- 빌더→가입 완료율 = `sign_up` 사용자 / `builder_started` 사용자
- 가입→발행률 = `academy_published` 사용자 / `sign_up` 사용자
- 24시간 내 발행률 = 빌더 시작 후 24시간 내 발행 사용자 / 빌더 시작 사용자
- 홈페이지→상담 전환율 = 상담 완료 순 세션 / 학원 페이지 순 세션

### 활성도와 채택

- 주간 활성 학원(WAA): 7일 내 홈페이지 수정, 수업/글/영상 등록, 신청 확인 중 하나를 수행한 distinct Academy
- 수업 등록률: 공개 수업이 1개 이상인 공개 Academy / 공개 Academy
- 블로그·영상 사용률: 해당 공개 콘텐츠가 1개 이상인 공개 Academy / 공개 Academy
- 4주 유지율: 가입 코호트 중 4주차에도 유효 행동이 발생한 Academy 비율

`updated_at`은 시스템 변경도 포함하므로 활성 사용자 판정에 직접 사용하지 않는다.

### 수업/학생 성과

- 활성 연결 학생: `lesson_applications.status=APPROVED`의 distinct student
- 신청 취소율: 같은 신청 코호트의 `CANCELED / 전체 신청`
- 정원 충족률: 수업별 `APPROVED 신청 수 / max_capacity`; `current_count`는 정합성 검증용으로 비교한다.

현재 신청은 즉시 APPROVED되는 흐름이므로 승인율은 유의미한 KPI로 사용하지 않는다. 학생·시험 성과는 개인정보와 인과관계 오해 위험이 있어 랜딩 공개 대상에서 제외한다.

## 4. 이벤트와 집계 경계

최소 이벤트 이름:

```text
landing_view
landing_start_clicked
builder_started
sign_up
academy_published
academy_page_view
manual_opened
contact_opened
consultation_started
consultation_completed
```

- `academy_published`, 신청·상담 완료처럼 성공 여부가 중요한 이벤트는 서버 트랜잭션 성공 후 기록한다.
- CTA·입력 시작처럼 브라우저에서만 알 수 있는 행동은 클라이언트 이벤트로 기록한다.
- 이벤트에는 이메일, 전화번호, 이름, 로그인 ID를 포함하지 않는다.
- 익명 방문 중복 제거가 필요하면 1st-party UUID의 HMAC 해시만 저장하고 원본 IP는 식별자로 보존하지 않는다.
- 원본 이벤트는 보존기간을 정하고, 공개 숫자는 `metric_date + metric_key + scope_id` 단위의 일별 집계에서 읽는다.
- 랜딩 렌더는 집계 스냅샷을 5~15분 캐시하며 외부 분석 API를 직접 호출하지 않는다.

## 5. 데이터 품질 선행조건

- `edu-holic` 등 데모와 운영 테스트를 hard-coded handle/login ID가 아닌 `is_internal` 또는 동등한 분류값으로 제외한다.
- `서울`/`서울시`처럼 혼재할 수 있는 `academy_regions.city`를 코드 기반으로 정규화한다.
- 공개 지표마다 집계 시작일과 최근 계산 시각을 기록한다.
- 순 방문, 전환율, 중앙값은 최소 표본 임계값을 충족할 때만 공개한다.
- 물리 삭제되는 Academy 때문에 현재 테이블만으로 역대 누적 개설 수를 복원하지 않는다. 누적 발행은 `academy_published` 이벤트를 SoT로 삼는다.

## 6. 외부 도구 역할

- GA4: 페이지뷰, 세션, 유입 채널, UTM, 디바이스, 랜딩 행동 퍼널. 현재 제품에서는 1st-party 집계를 우선하고 GA4는 보조 분석 채널로 취급한다.
- Search Console: 검색 노출, 클릭, CTR, 검색어, 페이지별 자연검색 성과.
- PageSpeed/CrUX: 공개 학원 페이지의 모바일 성능과 Core Web Vitals. 운영 품질 대시보드용이며 랜딩 사회적 증거와 분리한다.
- ALB/nginx 접근 로그: 장애 조사와 원시 요청 검증 보조. 사람 수의 SoT로 사용하지 않는다. 단,
  빠른 운영 관찰용 **요청 수**는 성공한 학원 서브도메인 루트 GET만 별도 집계할 수 있으며 사람·순방문자·세션으로 명명하지 않는다.

### 6.1 ALB 요청 기반 운영 통계

- `{handle}`은 변경 가능하므로 요청 시점의 handle 이력을 통해 immutable `academy_id`로 귀속한다.
- `academy_access_statistics.total_request_count`는 누적 요청 수,
  `academy_access_daily_statistics`는 `Asia/Seoul` 날짜별 요청 수다.
- S3 ALB 로그 객체별 처리 원장을 남겨 재시작·재처리 시 중복 가산을 막는다.
- IP, user-agent, 로그인 ID는 저장하지 않으며 이 통계를 랜딩의 사용자 수 근거로 재사용하지 않는다.

## 7. 구현 순서

1. 데모/테스트 제외 기준과 지역 코드 정규화 확정
2. DB에서 계산 가능한 공개 지표 조회 및 `LandingStatisticsService` 추가
3. 랜딩 통계 블록과 작은 표본 숨김 정책 적용
4. 랜딩 퍼널 이벤트 추가 여부는 현재 BUILD IT 성장 지표 정의 후 다시 결정
5. 1st-party 방문/제품 이벤트와 일별 집계 도입
6. 데이터 검증 후 누적 방문·완성시간·상담 성과 순으로 공개
7. Search Console/PageSpeed 및 관리자 대시보드는 별도 작업으로 분리
