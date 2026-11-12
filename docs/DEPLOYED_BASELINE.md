# 운영 배포 기준 (Production baseline)

운영에서 읽은 JAR과 배포 보관 증거를 기준으로 기록한다. main 최신 커밋은 배포 완료를 의미하지 않는다. 새 배포가 성공하면 관측 시각·전체 해시·실행 기록을 함께 갱신한다.

## 최신 배포 확인 — 2026-11-12 16:29:30 KST

| 항목 | 확인된 값과 근거 |
|---|---|
| 배포 커밋 | `f7c39a1f6a9600efb31e87e3c362becd813eb50e` — PR #1057, 제공된 이번 주 진행표의 예정 학생 우선 표시. PR #1056 기능 복구·요일 수정 포함 |
| 운영 배포 | [Deploy to EC2 #1149](https://github.com/woori73/feel2real-buildit/actions/runs/43174815662), attempt 1, success, 완료 2026-11-12 07:29:30 UTC |
| 검증 | PR [Build Check](https://github.com/woori73/feel2real-buildit/actions/runs/43173593054) 빌드·단위/계약·MySQL 성공, [브라우저 회귀](https://github.com/woori73/feel2real-buildit/actions/runs/43173593049) 성공, main [Build Check](https://github.com/woori73/feel2real-buildit/actions/runs/43174667794) 성공. PR과 병합 트리 `705b7a3205f776a39fdfc797a45f1172896c88e7` 일치 |
| JAR 산출물 SHA-256 | `f4406b8b5a9e99050895c1b9ccab882dfc1f464cd8747be615617b5606c2b042` — 배포 로그의 산출물 해시. 운영 파일을 별도로 다운로드한 해시 대조는 미실행 |
| 서버 확인 | 배포 SUCCESS·buildit-wiki still healthy 로그, 외부 HTTPS /actuator/health HTTP 200·status UP |
| 새 화면 코드 | /js/academy_home.js?v=20261112-plan HTTP 200, weekly-plan 연결 확인. SHA-256 `035531c7a1a8ce6606871f48ade4296767edfcef485b7ffb2377dd7e2082b6db`가 해당 소스 파일과 일치 |
| 운영 UI 확인 | 로그인한 /dashboard에서 전체 41명·검색 1명 확인. 제공 진행표 2026-11-09~15 기준 월1·화2·수4·목4·금2·토4·일0, 이번 주 일정 미등록 28명 표시. 원문 진행표와 목요일 4명 대조. 수정 창에서 원본의 화·목 선택 확인 후 닫음. 학생 실명·화면 캡처는 공개 저장소에 기록하지 않음 |
| 기능 | 기존 메뉴·자료·장부·접수 관리 직접 링크, 반 학생 배정 명단 자동 열기, 학생별 출결 이력 보기. 수정 요일은 V1110 별도 값으로 보존하고 원본 진행표·출결은 유지 |
| 입력값 | 수동 배포 target=new, 나머지 설정은 모두 unchanged로 실행 |
| /wemade 확인 | 내부 smoke는 CURL_FAILED non-blocking 경고. 별도 외부 HTTPS /wemade → /wemade/login HTTP 200 확인. 내부 smoke가 성공한 것으로 기록하지 않음 |
| 미검증 범위 | 운영 학생 요일 PUT 저장·원본 출결 쓰기는 미실행. 저장·재조회·충돌·미정·되돌리기는 합성 MySQL/브라우저 환경에서 검증 |

## 이전 배포 확인 — 2026-11-12 16:05:04 KST

- PR #1056 `2dcac70d3578c2488a45e0098a75b019e811601d`, [Deploy #1148](https://github.com/woori73/feel2real-buildit/actions/runs/43171837471) success. 기존 업무 링크와 학생·반·기록·요일 수정 복구, V1110 학생별 요일 수정값 추가.
- 해당 배포 후 실제 학생 41명, 기록 보기, 반 배정 명단 자동 열기, 수정 창을 확인했다. 처음에는 최근 4주 등원 기준으로 요일을 표시했으며, 이어서 원본 진행표에 예정 일정이 있음을 대조해 위 #1149에서 원본의 이번 주 계획을 우선 적용했다.
- 당시 PR [Build Check](https://github.com/woori73/feel2real-buildit/actions/runs/43170827500), [화면 회귀](https://github.com/woori73/feel2real-buildit/actions/runs/43170827400), [접수 회귀](https://github.com/woori73/feel2real-buildit/actions/runs/43170827409), main [Build Check](https://github.com/woori73/feel2real-buildit/actions/runs/43171783217) 모두 success.

## 이전 배포 확인 — 2026-11-12 00:18:28 KST

| 항목 | 확인된 값과 근거 |
|---|---|
| 배포 커밋 | `f88afe41266072b26cd78e09ae6810b97f48a2ed` — PR #1050, 맞춤문제 접수 관리자 작업 화면 |
| 운영 배포 | [Deploy to EC2 #1144](https://github.com/woori73/feel2real-buildit/actions/runs/43045834262), attempt 1, success, 완료 관측 2026-11-11 15:18:28 UTC |
| 검증 | PR의 build·mysql-integration-test·local-intake 성공(브라우저 27건), [main Build Check](https://github.com/woori73/feel2real-buildit/actions/runs/43040726772) 성공, 배포 전 unit/contract gate 성공 |
| 서버 확인 | SSM 최종 Success, f2r-app.service healthy, Spring health OK, buildit-wiki still healthy, 배포 SUCCESS. 외부 HTTPS /actuator/health HTTP 200 및 status UP 확인 |
| 배포 산출물 JAR SHA-256 | `43e79ed42b1e208802862a53c9b27da68957c8954c30e3329a560f650b719467` — 배포 산출물 해시, 운영 파일 별도 다운로드 대조는 미실행 |
| 새 정적 화면 코드 | /js/exam_admin.js?v=20261111-1 HTTP 200, 이메일 전용 저장 문구 확인. SHA-256 `8084b9053388073b91876b2500810f9a675bbc524339a30811c3cda8b100742f` |
| 목록 스크롤 | /css/exam_admin.css?v=20261111-1 HTTP 200, max-height:620px 확인. SHA-256 `44e612d432008c0ad92ac460de28d82bbdfdfad77cb9118fd0e2febecb15d7ba` |
| /wemade 확인 | 배포 내 내부 curl은 CURL_FAILED non-blocking 경고. 별도 외부 HTTPS 조회는 /wemade/login으로 복귀해 HTTP 200 확인. 내부 curl 검사가 성공한 것으로 기록하지 않음 |
| 설정 적용 로그 | MCP studio-chatgpt, admissions public on, agent media seven,suni 적용 로그 관측. 직전 설정과의 차이는 대조하지 않음 |
| 미검증 범위 | 로그인한 실제 접수 화면의 시각 검수, 운영 고객 접수/입금/전달 쓰기, 실제 메일 수신·휴대폰 푸시는 미실행. 운영 테스트 신청 및 테스트 메일을 생성하지 않음 |

## 이전 배포 확인 — 2026-11-11 12:58:37 UTC

| 항목 | 확인된 값과 근거 |
|---|---|
| 배포 커밋 | `daea3ee46096909eb303bab0217a4db6ac797e08` — PR #1048, 접수 이메일·문자 알림과 관리자 상세 연결 |
| 운영 배포 | [Deploy to EC2 #1143](https://github.com/woori73/feel2real-buildit/actions/runs/43023273524), success, 완료 2026-11-11 12:54:10 UTC |
| 검증 | [PR Build Check #2584](https://github.com/woori73/feel2real-buildit/actions/runs/43021763318), [접수 회귀 #74](https://github.com/woori73/feel2real-buildit/actions/runs/43021763328) 27개 브라우저 검사, [모바일/비서 회귀 #78](https://github.com/woori73/feel2real-buildit/actions/runs/43021763422), [main Build Check #2585](https://github.com/woori73/feel2real-buildit/actions/runs/43023090386) 모두 success |
| 배포 산출물 JAR SHA-256 | `9a09d2649d27cc76cb2d016f03abb86c7fc5730f596137b63e23ce8bbcf2ffec` — 배포 job의 빌드 산출물 해시이며 운영 파일을 별도로 다운로드해 대조한 값은 아님 |
| 서버 확인 | SSM Success, f2r-app.service healthy, Spring health OK, buildit-wiki still healthy, 배포 SUCCESS |
| 운영 화면 | `/admin/hub/exam-requests#notifications`에서 요청된 수신처·채널 사용을 비공개 저장. 새로고침 후 수신처 유지와 채널 체크 확인. 원문 수신처는 공개 저장소에 기록하지 않음 |
| 실제 발송 상태 | 이메일·문자 모두 `발송 서비스 연결 필요`. 네이버 앱 비밀번호, SOLAPI 등록 발신번호·API 인증정보가 아직 없어 외부 테스트 발송 및 실제 수신은 미확인. 활성화 완료로 간주하지 않음 |
| 관리자 링크 | 알림의 `?request={id}`로 해당 접수 상세 표시, 비로그인은 로그인 후 복귀, 일반 회원은 403. 합성 환경에서 검증했으며 운영에 테스트 신청은 만들지 않음 |

## 이전 배포 확인 — 2026-11-11 12:03:50 UTC

| 항목 | 확인된 값과 근거 |
|---|---|
| 배포 커밋 | `0736c93a9e8b79ca9aa3b4cbab67fabccd87797e` — PR #1046, 클라우드리뷰 무료신청 명칭 변경 |
| 운영 배포 | [Deploy to EC2 #1141](https://github.com/woori73/feel2real-buildit/actions/runs/43015598507), success, 완료 2026-11-11 11:59:41 UTC |
| 검증 | PR 빌드·신청 회귀·모바일/비서 회귀, [main Build Check](https://github.com/woori73/feel2real-buildit/actions/runs/43015476786) 모두 success |
| 배포 산출물 JAR SHA-256 | `f9dd56c236ca9b363f761fb515c426d35a8a241731c70ea27bcc5225948e289d` — 배포 job의 빌드 산출물 해시이며 운영 파일을 별도 다운로드해 대조한 값은 아님 |
| 운영 화면 | 홈페이지 무료 신청 버튼과 `/exam-service?sample=1` 제목·제출 버튼 ‘클라우드리뷰 무료신청’ 확인 |
| 반응형 확인 | 320/390px 지정(문서 가용 폭 305/375px), 가로 넘침 0, 로고·버튼·메뉴 겹침 없음. viewport 복원 완료 |

## 이전 배포 확인 — 2026-11-11 06:24:24 UTC

| 항목 | 확인된 값과 근거 |
|---|---|
| 배포 커밋 | `e35e2a78e8441d0ee162403da848df02e600ea11` — PR #1035, 학교별 적중률 블로그 명칭·링크 변경 |
| 운영 배포 | [Deploy to EC2 #1133](https://github.com/woori73/feel2real-buildit/actions/runs/42971069955), attempt 1, success, 완료 2026-11-11 06:21:03 UTC |
| 빌드 검증 | [main Build Check #2557](https://github.com/woori73/feel2real-buildit/actions/runs/42970869217) success. PR Build Check #2555·신청 회귀 #68·모바일/비서 회귀 #72 모두 success |
| 배포 산출물 JAR SHA-256 | `18dc4718e5c497b443966fd7cf2492eb76374eb77954e117ff4f4a5b1f4c9f31` — 배포 job 로그의 빌드 산출물 해시. 운영 파일을 별도 다운로드해 대조한 값은 아님 |
| 서버 확인 | 배포 SSM Success, f2r-app.service healthy, Spring health OK, buildit-wiki still healthy, 배포 SUCCESS |
| 운영 화면 확인 | 홈페이지 ‘학교별 시험 적중률 블로그’에서 ‘언북중3 2학기말 적중률 95% 블로그’와 ‘신구중3 적중률 96% 블로그’ 표시·href 확인 |
| 링크 | 언북중3 `224463820746`, 신구중3 `224472061838`, 기존 압구정중2 `224435388122` — 모두 `blog.naver.com/apgujungeduholic/` 원문 |

아래 2026-10-29 관측은 과거 증거를 보존한 기록이다. 이하의 ‘현재 JAR’·인프라 값·미배포/보류 표현은 당시 관측을 뜻하며, 이번 배포의 DB·백업·미디어 전체 상태를 재검증했다는 의미가 아니다.

## 과거 관측 기준 — 2026-10-29 02:21:39 UTC

| 항목 | 확인된 값과 근거 |
|---|---|
| 운영 JAR과 일치하는 배포 커밋 | `38f40090e49f6cd2641865ce538aebea033546ef` |
| 식별 방법 | 현재 JAR 전체 SHA-256이 아래 배포 보관 영수증과 일치. `app.jar.meta`는 없으므로 실행 마커로 확인한 커밋은 아님 |
| 현재 JAR | `/home/ec2-user/app.jar`, 234,270,368 bytes |
| 현재 JAR SHA-256 | `a28100f0bdbc419ea52adc3c76ffcc536a4157705b28838297eb0551907f7ca2` |
| 기존 배포 기록 | [Deploy to EC2 40656618382](https://github.com/woori73/feel2real-buildit/actions/runs/40656618382), 2026-10-28, attempt 1 |
| 읽기 전용 관측 | [40308339478 / attempt 4](https://github.com/woori73/feel2real-buildit/actions/runs/40308339478/attempts/4), job `123224544583`; 고정 SSM 문서 `F2R-ReadOnlyStatus` |
| 서비스 | active/running, NRestarts 0, 시작 2026-10-28 08:29:17 UTC, health HTTP 200, ALB 8080·8082 healthy |
| 연결 DB | `f2r-db-202610181331`, MySQL 8.4.8; Flyway 191건, 최대 V1096, 실패 0 |
| DB 백업 조회 | 7일 자동 백업, 암호화·삭제 보호 ON, latest restorable 2026-10-29 02:16:55 UTC; 2026-10-28 18:52:20.570 UTC 자동 스냅샷 available |

관측 이후 상태가 계속 동일하다는 보장은 아니다. 다음 배포 직전에 현재 JAR/서비스/백업을 다시 읽어 비교해야 한다. DB 연결·백업 메타데이터 PASS는 전체 데이터 무결성이나 실제 복원 시험 PASS와 다르다.

## 현재 JAR, 이전 JAR, 보관본 구분

| 대상 | 상태 |
|---|---|
| `app.jar.previous` | 234,264,626 bytes, SHA-256 `18ca78cb7743fbd9e94e055fa5c6a4cd6fb5ca1011885b63d866f96eaa1dcdc0`; df425588 복구본과 일치. **현재 38f40090의 백업이 아님** |
| 현재 버전 배포 보관본 | `s3://f2r-deploy-425805163960/releases/38f40090e49f6cd2641865ce538aebea033546ef/40656618382-1/app.jar`; 영수증의 크기·해시가 현재 JAR과 일치 |
| 보관본 검증 범위 | 2026-10-28 08:28:06 UTC 생성 영수증 재사용. S3 객체의 현재 존재·읽기 권한·새 격리 복원은 미검증 |
| PR914 고정 백업 | `/var/backups/f2r-pr914-df425588`; 2026-10-28 MediaAudit 검증 성공 기록 있음. 현재 버전 백업으로 혼용 금지 |
| 실제 복구 권한 | 현재 실행 주체의 S3 읽기·JAR 복원·서비스 재시작 권한은 미검증. 읽기 전용 SSM 성공으로 대체하지 않음 |

일반 배포는 [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md)와 `.github/workflows/deploy-ec2.yml`을 따른다. PR914 전용 자동화는 df425588 및 승인 UI 파일 3개에 고정되어 있으며, PR923 전체 JAR 배포나 38f40090 복구 경로로 사용할 수 없다. 고정 검사값을 바꾸거나 예전 배포 작업을 재실행하여 우회하지 않는다.

## 미디어 관측 범위

2026-10-28 [PR914 실행 40625991893](https://github.com/woori73/feel2real-buildit/actions/runs/40625991893)의 보존된 MediaAudit 출력에서 경로 `/home/ec2-user/agent-media`, 세븐·서수니 이미지/MP4 6개와 백업 검증을 확인했다. 파일별 해시는 [증거 JSON](operations/homepage-gates-20261029/evidence.json)의 `historicalMedia`에 보존한다.

이는 **과거 확인**이다. 현재 실제 경로·원본 해시·백업 해시, AI AGENT Studio 전체 자료의 백업 범위는 이번 연결에서 재조회하지 못했다. 기존 미디어 파일을 수정·삭제·복원하지 않았다.

## 2026-10-29 당시 다음 홈페이지 후보 (과거 기록)

- main 확인값: `71e33cdfc2f054dcf290c0731a24ecb923af4440` (PR923 병합, PR922 정책 연결 포함).
- [main Build Check 40805496826](https://github.com/woori73/feel2real-buildit/actions/runs/40805496826): build·MySQL PASS. 같은 소스 트리의 PR 모바일/비서 CI 48/48 PASS 재사용.
- [승인 미디어](home-exam/APPROVED-MEDIA-20261029.md): 새 MP4·PNG는 업로드 원본과 해시 일치. 운영에서 표시됐다고 판정하지 않는다.
- 신청·고객 계좌 공개는 OFF 유지가 요구된다. 코드 기본값은 false이나 **실제 운영 설정값은 미검증**이다.
- 현재 버전 대비 migration 파일 변경 없음. 이것만으로 DB 쓰기나 모든 데이터 무결성이 검증된 것은 아니다.
- 일반 홈페이지 배포와 신청 활성화의 조건을 분리한 [검증/차단 목록](operations/homepage-gates-20261029/README.md)을 따른다. 현재 둘 다 보류다.
- 일반 배포 workflow는 `workflow_dispatch` 전용이다. 이번 문서 변경·PR 생성·CI 실행으로 운영을 배포하지 않는다.

## 이전 문서 보존

2026-10-17의 e034492f 기준 문서는 [당시 원문](DEPLOYED_BASELINE-20261017.md)으로 바이트 그대로 보존했다. 그 문서의 ‘현재 운영’, ‘미배포 PR 목록’, 메모리/인스턴스 값은 **당시 기록이며 현재 상태가 아니다**. 후속 변경 이력을 모두 재구성했다고 주장하지 않는다.
