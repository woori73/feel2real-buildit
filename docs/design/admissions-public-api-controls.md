# 입시상담 공개 API 보완 — 스위치·일일 상한·IP 식별·LLM 비용 제한·이력 보존 설계

대상: `POST /api/admissions/chat`(비회원 permitAll). 이 문서는 **구현된 것(§1~§3)**과 **설계만 한 것(§4~§5)**을 구분한다.
원칙: 운영 기본값은 항상 **비활성**. 활성화·삭제 기능 켜기는 별도 승인 후에만 한다.

## 1. 구현됨 — 공개 API 스위치 (기본 OFF)
- `app.admissions.public-api.enabled` (환경변수 `APP_ADMISSIONS_PUBLIC_API_ENABLED`), 코드 기본값 `false`.
- OFF면 컨트롤러 첫 줄에서 `503 {"status":"error","errorMessage":"입시상담 AI는 아직 이용할 수 없습니다."}`를 돌려준다. IP 제한·일일 카운트·서비스(LLM)·DB 쓰기를 모두 거치지 않는다.
- 안내 화면(`/admissions/guide`)은 그대로 열린다(화면은 오류 메시지를 표시). SecurityConfig의 permitAll은 변경 없음 — 인가 규칙은 완화하지도 강화하지도 않았다.
- 켜는 법(승인 후): EC2 `/etc/f2r/env`에 `APP_ADMISSIONS_PUBLIC_API_ENABLED=true` 추가 후 재시작. 끄는 법: 값 삭제 또는 `false` 후 재시작.

## 2. 구현됨 — 전역 일일 요청 상한
- `app.admissions.rate-limit.daily-limit` (기본 **500**), 하루는 **KST 00:00** 기준. IP와 무관하게 하루에 허용하는 전체 요청 수.
- 순서: 스위치 → IP 제한(분당 10·시간당 60, 기존 유지) → 일일 상한 → 서비스. IP 제한에 걸린 요청은 일일 카운트를 쓰지 않는다. 상한 초과 시 429 + 안내.
- `daily-limit`이 1 미만이면 경고 로그 후 기본값 500 사용(설정 실수로 전면 차단·무제한이 되지 않게).
- 한계: 카운터는 JVM 메모리(인스턴스별, 재시작 시 초기화). 현재 단일 인스턴스 전제. 다중 인스턴스·재시작 내성이 필요하면 §4의 DB 카운터로 이전한다.
- 상한이 곧 AI 비용의 상한이다: LLM 호출은 요청당 primary 1회 + 기술적 실패 시 fallback 1회이므로 하루 최대 약 1,000회(대부분 근거 없음 거절은 LLM 호출 전 종료).

## 3. 구현됨 — 신뢰 프록시 CIDR 검증과 안전한 IP 식별
- `app.admissions.rate-limit.trusted-proxies`(CSV: IP 또는 CIDR). 기동 시 검증해 **형식 오류·과도하게 넓은 대역(IPv4 /8 미만, IPv6 /16 미만, 예: `0.0.0.0/0`, `::/0`)은 무시**하고 경고 로그에 목록을 남긴다. 무시된 항목은 XFF를 믿지 않는 쪽(안전 측)으로 동작한다. 앱 기동은 막지 않는다(환경변수 오타로 서비스 전체가 죽지 않게).
- **IP 식별 규칙 변경**: 기존 구현은 신뢰 프록시 뒤에서 XFF의 **가장 왼쪽 항목**을 썼다. 그 값은 클라이언트가 임의로 보낸 것이라(ALB/nginx는 실제 접속 IP를 오른쪽에 덧붙임) 위조하면 IP 제한을 우회할 수 있었다. 이제 오른쪽부터 읽으며 신뢰 프록시를 건너뛰고 처음 만나는 비신뢰 IP를 클라이언트로 본다. 해석 불가 항목을 만나면 remoteAddr로 폴백, 전부 신뢰 프록시면 가장 왼쪽을 쓴다.
- 운영 값은 ALB/nginx의 실제 대역으로 정해야 한다(운영 구성은 이번 단계에서 조회하지 않음). 미설정이면 XFF를 무시하고 remoteAddr를 쓰므로, 프록시 뒤에서는 모든 사용자가 한 IP로 보여 합산 한도에 걸린다(그래서 활성화 전에 반드시 설정).

## 4. 설계 — LLM 호출 횟수·비용 제한 (미구현)
현재 상한: 일일 요청 500 → LLM 호출 최대 ≈1,000(primary+fallback). 추가로 필요한 것:
1. **LLM 호출 전용 일일 상한** `app.admissions.llm.daily-calls`(제안 400): 거절(근거 없음)은 세지 않고 실제 `AdmissionsLlmClient.complete` 호출만 센다. 초과 시 LLM 없이 "근거 목록만" 제공하거나 429.
2. **fallback 호출 별도 상한**(제안 하루 100): OpenRouter 과금 보호. 초과 시 fallback 없이 통제된 오류.
3. **세션당 상한**(제안 세션 토큰당 하루 20회): 같은 상담 흐름의 과다 사용 방지.
4. **토큰/비용 추정 기록**: 답변당 입력 근거 글자 수(상한 12×400자)·출력(상한 4,000자)로 호출당 최대 토큰을 계산해 일 예상 비용 상한을 문서화. 단가는 운영 계약값 확인 후.
5. **영속 카운터**: 재시작·다중 인스턴스에 대비해 `admissions_usage_daily(day, requests, llm_calls, fallback_calls)` 테이블(새 V 파일, 별도 승인)에 원자적 증가(`INSERT ... ON DUPLICATE KEY UPDATE`).
6. **알림**: 일일 상한 80% 도달 시 ADMIN 알림(기존 NewStart 알림 채널 재사용 검토).
7. **자동 차단 스위치**: 상한 도달 시 당일은 스위치 OFF와 같은 503으로 전환.
검증 방법(구현 시): 시간 가짜 클럭 단위 테스트 + MockitoBean LLM으로 호출 횟수 검증.

## 5. 설계 — 상담 이력 90일 보존·자동 삭제 (미구현, 활성화는 별도 승인)
대상 테이블 `admissions_counseling_sessions`(질문·답변·`student_profile`(학년·희망 대학/전공·성적 요약)). 인덱스 `idx_admissions_session_created(created_at)`가 이미 있어 기간 삭제가 가능하다.
1. **정책**: `created_at`이 90일 지난 행을 삭제한다. 개인정보 최소화를 위해 보존 기간 안에서도 `student_profile`은 세션 종료 후 필요 없으면 비운다(별도 논의).
2. **구현 형태**: 스케줄러(`@Scheduled`, 매일 03:30 KST) + 설정 `app.admissions.retention.enabled`(기본 **false**), `retention-days`(기본 90), `batch-size`(기본 500), `dry-run`(기본 true).
3. **안전장치**: 배치 삭제(`DELETE ... WHERE created_at < ? ORDER BY session_id LIMIT ?` 반복)로 락·복제 지연 최소화, 1회 실행 최대 건수 상한, dry-run은 대상 건수만 로그, 삭제 건수·기준일·소요시간을 로그에 남기고 질문·답변 원문은 로그에 쓰지 않는다.
4. **활성화 절차**: ① dry-run으로 건수 확인 ② 소유자 승인 ③ 삭제 전 스냅샷 ④ `enabled=true, dry-run=false`. 삭제는 되돌릴 수 없으므로 스냅샷 없이 켜지 않는다.
5. **고지**: 개인정보 처리방침/화면 안내에 보존 기간(90일)과 입력 항목을 명시(법무 확인 필요).
6. **테스트(구현 시)**: 91일/89일 경계, 배치 상한, dry-run 무삭제, 비활성 시 스케줄러 미실행을 given/when/then으로.

## 6. 운영 전 체크
- [ ] 공개 API는 OFF 상태로 배포하고 `/api/admissions/chat`이 503인지 확인
- [ ] 활성화 전: 운영 trusted-proxies CIDR 확정, 일일 상한 값 확정, 지식베이스 데이터 적재·승인, LLM 키·한도 확인
- [ ] §4 영속 카운터·LLM 상한은 활성화 전 구현 여부를 결정(미구현이면 일일 요청 상한 500이 유일한 비용 상한)
- [ ] §5 보존·삭제 구현 및 활성화는 별도 승인
