# MCP GET 부수효과·외부 비용 정적 조사 (자동 생성)

`scripts/ai-agent-studio/get-sideeffect-survey.py`가 생성했다. **후보 목록이며 안전성의 증명이 아니다.**

- 정책 경로: 405건 · 키워드 미검출: 313건 · 후보: 12건 · 정적으로 해석 불가(프록시/뷰/템플릿): 80건

검사 범위: 컨트롤러 GET 메서드 본문 + 호출하는 주입 서비스 메서드 본문(2단계). 리포지토리 `@Modifying`·AOP·이벤트 리스너·3단계 이상 호출은 보지 못한다. MCP는 추가로 읽기 전용 트랜잭션+rollback(Java)과 SQLite `mode=ro`(Wiki)로 DB 쓰기를 차단한다. 외부 HTTP·AI·파일 쓰기는 이 트랜잭션이 막지 못하므로 아래 후보를 사람이 검토해야 한다.

## 후보

| 경로 | 서비스 | 분류 | 근거 메서드 |
|---|---|---|---|
| `/api/academies/{handle}/midterm-campaign/live-videos` | wiki | 외부 HTTP | MidtermLiveVideoService.listPublic |
| `/api/admin/newstart/jipijigi/inventory` | newstart | 외부 AI 비용 | JipijigiInventoryService.build |
| `/api/chart/regions` | chart | DB·파일 쓰기 | ChartRankingService.regions |
| `/api/studio/files/{id}/text` | studio | DB·파일 쓰기 | StudioController.text |
| `/api/wemade/catalyst/events` | wemade | 외부 HTTP | WemadeCatalystService.catalysts |
| `/api/wemade/catalyst/policy` | wemade | 외부 HTTP | WemadeCatalystService.policy |
| `/api/wemade/events` | wemade | 외부 HTTP | WemadeEventService.eventsWithPriceReaction |
| `/api/wemade/gap/factors` | wemade | 외부 HTTP | WemadeGapService.factors |
| `/api/wemade/gap/timeline` | wemade | 외부 HTTP | WemadeGapService.timeline |
| `/api/wemade/ownership/persons` | wemade | 외부 HTTP | WemadeOwnershipService.persons |
| `/api/wemade/valuation/components` | wemade | 외부 HTTP | WemadeValuationService.components |
| `/sitemap.xml` | content | 외부 HTTP | SitemapController.sitemap |

## 정적으로 해석 불가 (업스트림/뷰)

| 경로 | 서비스 | 사유 |
|---|---|---|
| `/academies` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academies/{slug}` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academies/{slug}/posts` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academies/{slug}/posts/{postSlug}` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academy-posts` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/integration/context` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/integration/teachers` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/attendance` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/classes` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/classes/{classId}` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/consultations` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/consultations/{consultationId}` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/dashboard/stats` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/notices` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/notices/{noticeId}` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/students` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/students/{studentId}` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/academycraft/api/rea/{academyId}/timetable` | academy | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/academies` | members | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/analytics` | analytics | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/analyzes` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/dashboard` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/hub` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/landing` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/lesson-communities` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/members` | members | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/students` | members | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/wiki-analysis` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/admin/wiki-originals` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/analyzes` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/analyzes/{id}` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/api/admin/choonggi/posts` | choonggi | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/api/chart/educators/{id:\d+}` | chart | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/api/chart/rankings/{id:\d+}` | chart | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/api/dashboard/admissions/history` | admissions | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/chart/educators/{id:\d+}` | chart | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/community` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/community/posts/{postId}` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/exam_review` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/home` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/lessons` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/csat/exam-resources/{resource_id}/original` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/csat/exams` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/csat/exams/{exam_id}` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/csat/exams/{exam_id}/questions` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/csat/questions` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/csat/types` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/digital-twins` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/digital-twins/{twin_id}` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/exam-materials` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/exam-materials/{material_id}/body` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/exam-materials/{material_id}/html` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/exam-materials/{material_id}/json` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/exam-materials/{material_id}/original` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/final-deliverables` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/final-deliverables/{deliverable_id}/json` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/final-deliverables/{deliverable_id}/original` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/questions` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/questions/{question_id}` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/schools` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/students` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/students/{student_id}/wrong-materials` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/students/{student_id}/wrong-notes` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/textbook-materials` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/textbook-materials/{material_id}/content` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/textbook-materials/{material_id}/original` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/textbook-materials/{material_id}/questions` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/problem-generator/wiki/api/textbook-questions` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/stdt_dashboard` | learning | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/tutor/academy` | buildit | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/tutor/academy/inquiries` | buildit | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/tutor/academy/posts` | buildit | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/tutor/academy/videos` | buildit | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/tutor/lesson/{lessonId}` | content | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/tutor/lessons/{lessonId}/students` | learning | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/tutor_dashboard` | learning | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/wiki/board/{boardKey}` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/wiki/learning` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/wiki/learning/{id}` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |
| `/wiki/posts/{postId}` | wiki | 프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿 |

## 수동 검토 결과 (후보 12건, 2026-10-22 Claude)

| 경로 | 판정 | 근거 |
|---|---|---|
| `/api/academies/{handle}/midterm-campaign/live-videos` | 오탐 | `MidtermLiveVideoService.listPublic`은 DB 조회만 수행(외부 HTTP 없음) |
| `/api/admin/newstart/jipijigi/inventory` | 오탐 | 'embedding' 문자열은 안내 문구, count 조회만 수행 |
| `/api/wemade/**` 7건 | 오탐 | `sourceUrl` 필드 문자열. 외부 HTTP 호출 없음(저장 분석 자료) |
| `/api/chart/regions` | 오탐 | 집계 조회(키워드 우연 일치) |
| `/api/studio/files/{id}/text` | 무해 | ZIP 목록 미리보기용 임시 파일 생성 후 삭제(압축 해제 없음) |
| `/sitemap.xml` | 오탐 | URL 문자열 조립 |

이 조사 초기 판(정책 484건)은 NewStart 상담 열람 GET 2건(`/api/newstart/consultations/{id}`, `/api/admin/newstart/consultations/{id}`)에서 **GET이 읽음 상태를 save**하는 실제 쓰기를 찾았다. 이후 [GET_SAFETY_AUDIT.md](GET_SAFETY_AUDIT.md)의 별도 감사가 이 2건을 비롯해 쓰기·외부 호출 GET을 MCP 정책에서 제거했고, 현재 정책(문서 상단 재생성 건수 기준)에서는 후보에 나타나지 않는다.
외부 AI 비용(LLM·임베딩 호출)을 일으키는 GET 정책은 발견되지 않았다. 한계: 정적 해석 불가(프록시·뷰)는 이 조사에 포함되지 않았으며, 프록시 대상(AcademyCraft·Wiki Python)은 업스트림 코드를 별도로 확인해야 한다.

