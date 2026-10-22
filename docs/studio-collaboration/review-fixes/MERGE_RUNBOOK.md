# 제작 협업실 병합 준비 — 실행 전 최종 사용자 승인 필요

현재 authoritative main `ae5d1760b9ba76ca79ef5c6f906acc59e6c293b1`. 사용자 전달 Claude 독립 판정(#863/#864~#871 PASS, #862 중복 FAIL)을 수용한다. 아래는 **준비 절차**이며 아직 main 병합·기존 PR base 변경을 실행하지 않았다. 새 수정 PR은 #871 위에 쌓고 마지막에 병합한다. #862는 항상 제외한다.

## 순서와 base

| 순서 | PR/현재 base | 선행 병합 후 작업 |
|---|---|---|
| 1 | #863 / main | 원본536 파일만 merge commit으로 병합, 원본 blob/SHA 유지 |
| 2 | #864 / main | #863 병합 확인 후 최신 main과의 통합 CI 재검증 |
| 3 | #865 / feat/studio-readable-board | #864 merged=true 확인 후 base를 main으로 변경 |
| 4 | #866 / feat/studio-production-import | #865 merged=true 확인 후 main |
| 5 | #867 / feat/studio-mcp-media | #866 merged=true 확인 후 main |
| 6 | #868 / feat/studio-shared-memory | #867 merged=true 확인 후 main |
| 7 | #869 / feat/studio-project-workspaces | #868 merged=true 확인 후 main. **문서2개뿐, AI 쓰기 활성화 없음** |
| 8 | #870 / docs/studio-propose-contract | #869 merged=true 확인 후 main |
| 9 | #871 / docs/hermes-collaboration-handoff | #870 merged=true 확인 후 main |
| 10 | 이번 수정 PR / fix/studio-mcp-commit-sha | #871 merged=true 확인 후 main. 승인 보호/더 보기까지 포함한 전체 묶음 검증 |

모두 merge commit 방식이며 squash/rebase merge·force push·자동 병합·브랜치 삭제를 사용하지 않는다. 실제 병합 권한은 최종 사용자 승인 후 별도 실행한다. 기존 PR의 PASS는 당시 HEAD 기준이고 base가 바뀐 이후 통합 CI의 성공을 대신하지 않는다. 승인 보호 수정까지 main에 들어가기 전 중간 스택을 운영에 배포하지 않는다. 전체 병합 후에도 운영 배포/DB/MCP/유료 호출은 별도 승인 대상이다.

## 각 단계의 구체적 재검증

1. GitHub API로 현재 main SHA, 대상 PR HEAD/base/mergeable/draft와 선행 merged/merge_commit_sha를 다시 읽는다. PR HEAD가 검수한 값과 달라지면 차이를 검토한다. 로컬 origin/main만 신뢰하지 않는다.
2. 선행 PR이 실제 main에 병합된 뒤에만 후속 PR을 main으로 retarget한다. #864를 아직 병합하지 않은 #863 브랜치로 지금 변경하지 않는다. #863의 자료를 feature에 복사/삭제하거나 이력 재작성하지 않는다.
3. 변경된 base의 GitHub test-merge tree/commit을 확인한다. base 변경만으로 CI가 자동 재실행된다고 가정하지 않는다. 다음 main+PR 조합을 전용 검증 브랜치에 merge commit으로 준비하고 **배포가 없는** Build Check와 Studio/MCP 격리 CI를 재실행한다. 검증 브랜치/합쳐진 HEAD와 tested main SHA를 기록한다. workflow_dispatch ref와 실행 결과 head_sha가 일치해야 한다. main에 먼저 병합해 시험하지 않는다.
4. 예시(승인 후 GitHub ref를 재확인한 실행자가 수행):
   - `gh pr edit <번호> --repo woori73/feel2real-buildit --base main`
   - `gh workflow run build-check.yml --repo woori73/feel2real-buildit --ref <통합검증브랜치>`
   - `gh workflow run studio-mcp-check.yml --repo woori73/feel2real-buildit --ref <통합검증브랜치>`
   - 금융 변경이 생기면 financial-collector-check.yml도 같은 ref로 실행. 기존 성공 테스트는 영향이 없으면 반복하지 않는다.
5. 문서만인 #863/#869/#870은 원본 blob/허용 경로·schema/설정 diff를 검사한다. #869의 diff가 설계 문서 이외 경로를 포함하면 중단한다. 이후 전체 통합 조합에서 Build/MySQL/Flyway/Studio 보호 회귀를 확인한다.
6. V1094 SHA256 `3e1b5aab8393f878901d4216cc86ef8eb8b00bd71cf068d87cfe21352460abac`, checksum `-1031052192`; V1095 SHA256 `18d2c7f4b9ca3027216178f806a6ef389448f8ba8f089d5f746f2b9608ccec24`, checksum `-1470147673`. 격리 MySQL에서 full/upgrade/validate 성공, V1096~1098 또는 ai_collaboration/mcp_collaboration schema가 추가되지 않았음을 검사한다. 운영 DB를 조회하거나 repair하지 않는다.
7. 최신 실행 대상 CI PASS, 프로젝트/학원·사업체 권한 회귀, PC1440/모바일390 더 보기·승인 확인·취소·미확인 요청 거부·성공 재조회·감사 이력, 원본536경로/316파일/승인5종 SHA·기존 revision 불변을 확인한다.
8. 충돌이 있으면 원본/승인·문서 양쪽을 보존하는 해결 커밋을 추가하고 그 새 HEAD만 재검증한다. 기존 통과 결과를 덮어 PASS로 재사용하지 않는다. 각 단계의 merge SHA/HEAD/CI URL을 병합 기록에 남긴다.

수정 브랜치의 CI는 이 수정 코드 검증이며 미래 main 변경에 대한 검증을 미리 완료했다고 주장하지 않는다. 실제 제품 ChatGPT/Claude 접속·AI 직접 쓰기·Hermes 실행은 여전히 미검증/설계 범위다. 이 기능들을 켜는 것은 코드 병합 승인에 포함하지 않는다.
