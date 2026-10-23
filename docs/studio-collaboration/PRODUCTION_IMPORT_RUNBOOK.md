# 운영 536개 자료 적재 절차 (`import-production.py --production`)

**실행은 별도 승인 후에만 한다.** 도구의 기본 동작은 아무것도 쓰지 않는 dry-run이다.

## 안전장치

| 장치 | 동작 |
|---|---|
| 기본 dry-run | `--apply`가 없으면 GET만 보낸다(프로젝트 초기화 여부, 등록 경로 수, 이미 저장된 원본 수) |
| HTTPS·호스트 고정 | `https://feel2real.co.kr`(또는 `www.`)만, 경로·포트·계정 정보 금지, `--confirm-host`로 한 번 더 입력 |
| 운영 SHA 검증 | `--deployed-sha`가 ① 이 스크립트와 **같은 카탈로그 바이트**와 적재 API를 포함하고 ② GitHub의 **가장 최근 성공 `Deploy to EC2` 실행 SHA**와 같아야 진행 |
| 스냅샷 확인 | `--apply`에는 `--confirm-snapshot f2r-predeploy-YYYYMMDDHHMM-<sha8>` 필수. 이름의 SHA가 배포 SHA와 같고 24시간 이내여야 함. **존재 여부는 AWS에서 확인한 값을 사람이 넣는다**(도구는 AWS에 접근하지 않음) |
| 인증 | ADMIN 쿠키는 0600 파일로만. 명령행·영수증·상태 파일에 쓰지 않음 |
| 추가 보호 | HTTP redirect 거부, 적용 전 ADMIN/원본 프로젝트/보관 여부 확인, 전체 리비전 페이지 조회 후 기존 승인 포인터·파일·SHA 보존/승인5종 GET 재검증. 승인 해제된 정본은 자동 재승인하지 않음 |
| 중복 방지 | 서버가 프로젝트 내 SHA로 중복 제거, 도구도 기존 저장 파일 목록을 먼저 조회해 없는 것만 올림 |
| 재시작 | `--state` 파일에 "저장 바이트까지 재검증한" 파일만 기록. 중단 후 같은 명령을 다시 실행하면 검증된 파일은 건너뜀 |
| 부분 상태 | 등록 경로 수가 0도 536도 아니면 중단(자동 복구 시도 없음). 536이면 아무 것도 쓰지 않고 종료 |
| 원자 등록 | 316개 검증 후 `source-import`를 **재시도 없이 1회** 호출(서버 트랜잭션). 실패하면 다시 실행하면 됨(0이면 재등록, 536이면 종료) |

## 순서 (승인 후)

1. 배포 완료, `/ai-agent-studio`에서 ADMIN이 프로젝트 개설.
2. 배포 직전 스냅샷 이름 기록(`f2r-predeploy-...`), 콘솔에서 `available` 확인.
3. 원본 추출: `git fetch origin 09d62dee…` 후 `python3 scripts/ai-agent-studio/import-production.py --extract-to <dir> --receipt <dry.json>`.
4. ADMIN으로 운영 로그인, 쿠키를 `{"adminCookie":"<이름=값>"}` 형태로 0600 파일에 저장.
5. dry-run:
   ```sh
   python3 scripts/ai-agent-studio/import-production.py --production --source <dir> \
     --base-url https://feel2real.co.kr --confirm-host feel2real.co.kr \
     --deployed-sha <배포 SHA> --auth-file <0600 파일> --state <state.json> --receipt <dry-prod.json>
   ```
   확인: `toUpload`(처음이면 316), `alreadyStored`.
6. 적용: 같은 명령에 `--apply --confirm-snapshot <스냅샷 이름>` 추가. 중단되면 같은 명령을 다시 실행.
7. 사후 확인(읽기): 등록 536 / 고유 316 / 승인 캐릭터 5(MANIFEST SHA 일치) / 콘티 승인 0 / 원작 대조 0/68. 영수증을 보관하고 쿠키 파일은 삭제.

## 시험

`python3 -m unittest scripts/ai-agent-studio/test_import_production.py` — 로컬 가짜 HTTPS Studio API로 9건: dry-run 무쓰기, 위험 대상 거부, 스냅샷 형식·SHA·시각, 카탈로그 없는 배포 SHA, 0600 권한, 미초기화 프로젝트, 100번째 업로드 실패 후 재시작(중복 0·등록 1회·쿠키 비노출), 부분 등록 중단, GitHub 배포 기록 불일치. Studio/MCP CI에 포함.
실제 앱을 HTTPS로 앞세운 종단 시험은 하지 않았다(엔드포인트·SHA 재검증은 기존 로컬 모드와 같은 서버 API를 쓴다).
