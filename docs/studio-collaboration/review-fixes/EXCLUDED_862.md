# #862 병합 제외와 고유 기능 후보

사용자가 전달한 Claude 독립 검수 판정: #863 및 #864~#871 PASS, #862 FAIL(중복 구현). 이 결정을 수용하며 main의 #855 `studio_*`가 자료·리비전·승인·권한·작업 기억의 정본이다. #862의 커밋/브랜치/PR/검증 이력은 보존하고 PR 본문에 대체·병합 제외를 기록했다. 열린 Draft를 유지하며 병합/브랜치 삭제를 하지 않는다.

조사 대상은 #862 최신 HEAD `a93792d08ea1cb3cde1150ac9031c52aa1adb7a9`이다. 이전 버전의 실행기 설명이 아니라 이 HEAD의 README/Controller/MCP/Github 구현을 대조했다. 해당 PR의 별도 모델 API 실행기는 현재 README 기준 존재하지 않는다. 실제품 AI 판독/파일 전송은 그 PR도 NOT TESTED이며 SDK 시험을 실제품 검증으로 승격하지 않는다.

## 가져오지 않는 구현

프로젝트/멤버/파일/SHA/자료532+인계4/콘티/문서 뷰어/승인 후보/공동 기억/이미지 MCP는 #855·#864~#871의 기존 기능과 겹친다. `/ai-collaboration`과 별도 테이블을 두 번째 정본으로 도입하지 않는다. V1096~V1098을 복사하거나 #862 브랜치를 merge/cherry-pick하지 않는다.

- V1096: ai_collaboration_budget_days/tasks/events/artifacts
- V1097: ai_collaboration_workspaces/workspace_members/workspace_events/files/uploads/chunks/submissions, mcp_collaboration_bindings, task.workspace_id
- V1098: ai_collaboration_imports/sources/storyboard_cuts

이 목록은 기존 스튜디오의 파일/승인 이력을 옮기거나 지우는 지시가 아니다. 적용된 V1094/V1095와 기존 이력은 그대로 유지한다.

## 고유 기능과 후속 PR 제안 (미구현·비활성)

| #862의 고유 기능·코드 근거 | 정본 스튜디오와 차이 | 별도 PR 제안·조건 |
|---|---|---|
| 선택된 AI만 참여하는 작업 상태·handoffInbox, CHANGES_REQUESTED→선택 Codex→Claude, PAUSED/CANCELLED/BLOCKED/인계상한 (`CollaborationService`, `CollaborationMcp`) | currentAi는 작업 메모이며 실행 상태기계/선택된 AI 큐가 아님 | `[FEAT] Studio 작업 담당자 선택·인계 큐` — 기존 project/revision/event 정본 재사용, 실행/유료 호출 없이 먼저 설계 |
| grant×AI×프로젝트 binding과 별도 append 동의 (`CollaborationMcp`, OAuth 변경) | 현재 MCP는 읽기 전용, #869는 분리된 studio:propose 설계만 | `[FEAT] Studio 별도 후보 제안 연결` — #869 resource/audience 분리 검수 후 구현. 기존 OAuth/SCOPES로 collaboration:append를 가져오지 않음 |
| 32KiB 청크/32MiB 후보 업로드·재시도/원본 SHA/클라이언트 전송 도우미 (`CollaborationFiles`, `collaboration-transfer.py`) | 기존 Studio는 웹 multipart 및 SHA 중복 검사; 재개형 AI 업로드 없음 | 위 후보 연결의 별도 첨부 PR — 저장 상한/TTL/새 객체 롤백, 기존 파일·SHA·승인 불변, 사용자 확인 필수. 외부 앱 파일 접근 능력은 별도 실측 |
| 고정 저장소 PR/HEAD/check-runs 스냅샷 (`CollaborationGithub`, POST github-sync) | 현재 기억은 PR/SHA 수동 기록과 링크 | `[FEAT] Studio PR·CI 읽기 스냅샷` — 조회 전용 토큰/고정 repo/응답 한도, 외부 GET 비용·데이터 저장은 POST 명시, 댓글/병합/실행/배포 권한 없음 |
| 작업 단위 event 타임라인·최근200개 Markdown export (`CollaborationController`) | 컷별 스레드·프로젝트 기억 버전은 있으나 작업 실행 타임라인/export 없음 | `[FEAT] Studio 작업 이력 내보내기` — 기존 event paging/권한 재사용, 200건 잘림 표시 또는 전체 cursor 내보내기, 비밀/PII 제외 |
| 작업/자료/기억을 묶은 통합 검색 (`CollaborationWorkspaces`, Sources) | 현재 revision 검색과 기억/댓글/파일 조회가 분리됨 | `[FEAT] Studio 프로젝트 자료·기억 검색` — 기존 SQL/프로젝트 ACL로 먼저 구현, 유료 임베딩 없음 |

이는 향후 범위 제안이다. 이번 수정에서는 위 기능을 구현/활성화하거나 별도 schema를 도입하지 않는다. 사용자 우선순위가 정해지면 기존 정본을 확장하는 별도 PR로 제출한다.

근거 원본: [README](https://github.com/woori73/feel2real-buildit/blob/a93792d08ea1cb3cde1150ac9031c52aa1adb7a9/docs/collaboration/README.md), [Controller](https://github.com/woori73/feel2real-buildit/blob/a93792d08ea1cb3cde1150ac9031c52aa1adb7a9/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/collaboration/CollaborationController.java), [MCP](https://github.com/woori73/feel2real-buildit/blob/a93792d08ea1cb3cde1150ac9031c52aa1adb7a9/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/collaboration/CollaborationMcp.java), [GitHub snapshot](https://github.com/woori73/feel2real-buildit/blob/a93792d08ea1cb3cde1150ac9031c52aa1adb7a9/src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/collaboration/CollaborationGithub.java).
