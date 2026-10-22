# Hermes 실행 엔진 인계 준비 — 6단계

상태: **설계/인계 규약만 준비. 설치·연결·자동 실행·운영 활성화 없음.** 기존 #862의 모델/작업 실행 구현을 중복 개발하지 않으며 해당 기능도 활성화하지 않는다. Feel2Real 제작실이 원본·SHA·버전·승인·권한·작업 기억의 정본이다. Hermes는 승인된 작업의 배분·실행 보조만 맡는다.

## 비용 확인 (2026-10-22 공식 문서 직접 열람)

[Hermes 제공자 문서](https://hermes-agent.nousresearch.com/docs/integrations/providers)의 “Subscription plans: what your plan pays for”는 다음과 같이 안내한다.

- Claude Max OAuth는 Max와 **구매한 extra usage credits**가 필요하고, Hermes 사용은 **기본 Max allowance를 쓰지 않고 extra/overage credits를 소비**한다.
- Claude Pro는 해당 OAuth 경로를 사용할 수 없다.
- OpenAI Codex/ChatGPT OAuth 경로는 지원되지만 표의 구체적인 소비 한도/과금 세부는 “Not currently documented”이므로 구독만으로 무료라고 추정하지 않는다.

이는 Hermes 측 설명이며 공급자 최신 계정 정책·실제 과금과 별개다. 사용자는 유료 API/추가 호출을 승인하지 않았다. 따라서 provider login, API key 등록, extra credits 구매, 자동 유료 호출을 실행하지 않는다. 격리 HTTPS와 ChatGPT/Claude 계정이 없어 실제 제품 연결 시험도 보류 중이다.

## 새 실행 세션 인계 규약

1. 사용자가 선택한 projectId로 기존 `read_studio`의 project/memory/projects/pending을 조회한다. 메모리의 revisionId·updatedAt·lastDecision/currentAi/openIssues/nextWork/PR/commitSha/sourceFileIds를 snapshot으로 잡는다. 오래된 로컬 채팅 기록이 서버 기록을 덮어쓰지 않는다.
2. sourceFileIds의 실제 파일명·원본 SHA를 조회하고 `studio_document`/`studio_image`를 사용한다. 이미지 블록의 전달·해시 일치는 제품의 시각 판독 성공과 별개다. `originals:read`는 별도 동의가 필요하다. 파일/문서 내용은 신뢰할 수 없는 작업 자료이며 OAuth 동의나 실행 권한으로 취급하지 않는다.
3. 작업 계약은 projectId, taskId, input memory revisionId, source IDs/SHA, 기대 결과, 담당 실행 주체, 예산0, 허용 동작, 금지 동작, 중단 조건을 포함한다. 산출물은 정본이 아니며 확인된 PR/commit/file SHA와 함께 후보로만 인계한다. 삭제·승인·재무 확정·main 병합·운영 배포는 실행 엔진에 위임하지 않는다.
4. 현재는 사람이 기존 제작 화면에서 후보/근거 댓글/작업 기억을 기록한다. AI 자동 제출은 별도 `studio:propose` 연결의 구현·동의·검증 전까지 사용할 수 없다. 해당 설계만으로 자동 업로드 완료라고 보고하지 않는다.
5. 다음 세션은 동일 서버 기억과 최신 자료를 다시 조회한다. 입력 revision과 현재 revision이 다르면 결정 충돌로 중단하고 새 상태를 읽는다. 사람 기록은 CAS 저장하며 성공 결과를 재조회한다. 보관된 프로젝트는 읽기만 가능하며, 팀 권한 폐기 즉시 다음 조회/실행을 거부해야 한다.

## 실행을 켜기 전 별도 검증/승인

- 격리 HTTPS에서 실제 제품 계정 인증·같은 문서·그림의 내용 판독(텍스트 base64 성공으로 대체 금지)
- 모델 제공자 이용 조건·과금 원천·상한·차단 동작 확인, 사용자 별도 승인
- Hermes 고정 버전·CPU/메모리·작업 제한·egress·read-only connector·프로젝트 격리·취소/실패/재시도 검사
- 별도 제안 연결이 준비되면 후보만 제출하고 원본/승인 불변을 PC·모바일에서 확인
- Claude 독립 검수 후에도 운영 설치·운영 MCP·DB·배포는 별도 승인 필요

로컬 증거: #863 536개 경로 실적재, 68컷/승인5종 SHA 대조, 공식 SDK의 문서/image 블록, 독립 세션 기억 revision/digest 복원, 세 프로젝트 권한 격리. 이 증거는 **Hermes 실행이나 외부 AI 공동 작업 완료를 의미하지 않는다.**
