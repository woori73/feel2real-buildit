---
id: AC-HELP-010
slug: ai-assistant
title: AI 비서 사용법
category: AI
order: 10
audience: ALL
visibility: STAFF
status: REVIEW
featureState: DEV_DONE_UNDEPLOYED
appliesTo: 개발 완료·미배포 (AcademyCraft PR #20 병합, PR #21·BuildIt #827 검토 중)
lastModified: 2026-10-20
lastVerified: 2026-10-20
source: docs/agent-approval.md, docs/agent-p1b-e2e-and-deploy.md, 로컬 E2E 112건(2026-10-20)
related: admin-guide, tutor-guide, faq
---
AI 비서는 도움말을 찾아 안내합니다. 기본 동작은 설명·검색·화면 안내이며 데이터를 직접 바꾸지 않습니다.

## 현재 상태 (중요)
- 도움말 안내 AI: 이 도움말 문서를 근거로 답합니다. 근거가 없으면 모른다고 답합니다.
- AI Agent 쓰기 기능(계획 → 미리보기 → 승인 → 실행): **개발 완료·미배포**입니다. 운영 화면에서는 아직 사용할 수 없습니다.

## 쓰기 승인 방식 (배포 후 적용 예정)
1. AI가 변경 계획을 만들면 서버가 저장하고 미리보기를 보여줍니다.
2. 승인 권한자가 미리보기를 확인하고 승인합니다.
3. 승인한 계획만 한 번 실행됩니다. 계획은 10분 뒤 만료됩니다.
- 승인과 실행은 원장(OWNER)만 가능하고, 강사는 초안 작성까지입니다.
- 자동 재시도는 없습니다.

## 주의사항
- 배포 전에는 "AI가 대신 등록해 준다"고 안내하지 않습니다. 직접 해당 메뉴에서 처리하세요.
- AI 답변이 틀려 보이면 해당 도움말 문서를 직접 확인하세요.
