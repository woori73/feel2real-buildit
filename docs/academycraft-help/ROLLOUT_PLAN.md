# AcademyCraft 도움말 운영 적용 계획 (계획만 — 실행하지 않음)

> 작성일 2026-10-20 · 기준 BuildIt main `4cdc95a0`(#828 병합) · 운영 BuildIt `5343d785` · 운영 AcademyCraft `543cdfc8`.
> 이 문서는 계획이다. 홈페이지 게시, 운영 DB 지식 등록, 운영 배포, AWS 변경은 별도 승인 후에만 실행한다.

## 1. "/help/academycraft" 게시 준비
- 코드는 main에 있으나 **운영에는 없다**(BuildIt 운영 배포 전). 게시 = BuildIt 운영 배포 1회.
- 접근: ADMIN·TUTOR 로그인 필수, 비로그인은 로그인 안내. 공개(비로그인) 게시가 필요하면 SecurityConfig·문서 `visibility` 정책을 따로 정해야 한다(현재는 의도적으로 비공개).
- 진입 링크: 대시보드/AcademyCraft 화면에는 도움말 링크가 아직 없다. 게시 후 링크 추가는 별도 작은 PR(화면 변경)로 승인받는다.
- 게시 전 체크: ① 운영 화면과 12개 문서 재대조(라벨·권한) ② AI Agent·주간 시간표 배포 여부에 맞춰 문서 상태/적용버전 갱신 ③ noindex 유지 확인.

## 2. AI 비서 API 연결 상태
- `POST /api/help/academycraft/ask`: main 반영 완료, 운영 미배포.
- 외부 의존 없음: LLM·DB·AcademyCraft 호출이 없고 jar 안 `help/academycraft/*.md`만 읽는다 → **환경변수·키·DB 변경 불필요**. `buildit.help.academycraft.ai-official-statuses` 기본 `REVIEWED`(12개 모두 해당).
- 이 비서는 사용법 안내용이다. 업무 실행·조회를 하지 않는다(AI Agent 쓰기 승인과 무관, 별도 기능).

## 3. ADMIN/TUTOR 권한 검증 계획 (배포 후, 읽기 전용 확인 위주)
| 확인 | 기대 |
|---|---|
| 비로그인 `/help/academycraft` | 302 로그인 안내 |
| 비로그인·STUDENT `POST /ask` | 401 |
| ADMIN 12개 문서 | 200 |
| TUTOR `admin-guide` | 404, 나머지 200 |
| 질문 3종(학생 등록, 다른 학원 명단, 삭제해줘) | 근거 답변 / 조회 거절 / 실행 안 함 |
- 실제 운영 ADMIN·TUTOR 계정으로 사용자가 직접 확인하거나, 승인 하에 점검 계정을 쓴다. 운영 데이터 변경 없음(질문은 읽기).

## 4. 운영 배포 및 롤백
- 배포: 기존 수동 `Deploy to EC2`(신규 계정 425805163960) 1회. 이 변경은 신규 jar 리소스·컨트롤러·정적 파일뿐이며 DB 마이그레이션 없음.
- 같은 배포에 main의 P0(#825)·P1-b(#827)도 포함된다 → AcademyCraft #21/#20(AI Agent)은 AcademyCraft를 먼저 배포하는 순서 계획(`AcademyCraft/docs/agent-p1b-e2e-and-deploy.md`)을 따른다. **도움말만 따로 배포할 수는 없다**(단일 jar).
- 롤백: 기존 배포 워크플로의 이전 SHA(`5343d785`) 재배포. 도움말은 DB를 쓰지 않아 데이터 롤백 없음. 롤백 시 도움말 URL은 404가 되고 다른 기능은 이전 상태로 돌아간다.
- 배포 후 확인: `/actuator/health` 등 기존 헬스 게이트 + 3장 표.

## 5. 운영 DB 변경 필요 여부
- 도움말·AI 비서: **없음**(Flyway 신규 없음, 지식 데이터는 jar 내 파일이라 "운영 DB 지식 등록"도 불필요).
- 별도: AcademyCraft V6(`rea_agent_plans`)는 AI Agent 배포 시에만 필요(백업 후 별도 승인, 위 배포 계획 참조). 공지 소유권 개선(§별도 PR)은 스키마 변경 없이 가능하도록 제안했다.

## 6. 출결 한국 시간대 검증 방법
- 사실: AcademyCraft 출결은 `LocalDateTime.now()`(JVM 기본 시간대)로 시각을 저장한다. 운영 DB URL은 `serverTimezone=Asia/Seoul` 예시가 있으나 **JVM 기본 시간대가 UTC면 저장 시각이 9시간 어긋날 수 있다.** 현재 운영 값은 미확인.
- 읽기 전용 점검(승인 후): ① 호스트 `timedatectl`/`date`, 서비스 환경의 `TZ`·JVM `-Duser.timezone` 확인(SSM 읽기 전용 명령) ② DB `SELECT NOW(), @@session.time_zone`.
- 기능 점검(승인 후): 점검용 학원·학생에서 출결 1건 기록 → 화면 시각과 한국 시각을 대조 → 점검 데이터는 정정 이력과 함께 정리 방침을 사전 합의.
- 어긋나면: 코드 수정(`Clock`을 Asia/Seoul로 고정)을 별도 PR로 제안한다. 이 계획서 범위 밖.
