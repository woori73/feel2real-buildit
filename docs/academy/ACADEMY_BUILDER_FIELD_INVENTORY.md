# Academy Builder — Field Inventory

작성일: 2026-09-05
범위: `/tutor/academy_builder` (Academy Builder), 관련 하위 편집기(`lesson_product_editor`, `tutor_academy_videos`).
목적: 현재 Builder가 노출하는 모든 입력 필드를 조사해 A/B/C/D/E로 분류하고, 향후 UI 단순화·상세
설정 이동·AI 연결 우선순위를 정하는 근거로 쓴다. PR 본문에서 설명한 "우선순위 1~3" 작업(아바타
제거/영상 분리/이미지 크기)의 배경 조사이기도 하다.

분류 기준:

- **A** = 반드시 필요한 핵심 입력 (학원/원장/수업의 정체성)
- **B** = 선택 입력 (있으면 좋지만 없어도 페이지가 정상 작동)
- **C** = 거의 사용하지 않는 상세 입력
- **D** = 다른 입력과 중복되거나 파생 가능
- **E** = 내부 시스템용 / 사용자 노출 불필요

---

## 1. 학원 기본 정보 (`sec-hero`, 잠금 섹션)

| 화면 필드 | data key | API | DB | 공개 페이지 사용처 | 분류 | 기본 노출 | 상세 이동 | 숨김 시 보존 |
|---|---|---|---|---|---|---|---|---|
| 학원명 | `name` | `PUT /api/tutor/academy` | `academies.name` | GNB, 히어로 타이틀 | A | O | - | 폼 필수값이라 항상 전송 — 위험 없음 |
| 로고 | 파일 업로드 → `logoStorageKey` | `PUT /api/tutor/academy` (multipart) | `academies.logo_storage_key` | GNB 로고 | A | O | - | 파일 미첨부 시 기존 로고 유지(`isPresent(logo)` 가드, `AcademyService.java:261`) |
| 대표 과목 | `subjectIds` | 〃 | `academy_subjects` (M:N) | 히어로 서브텍스트, 검색 필터 | A | O | - | **전량 교체**(clear+addAll, `Academy.java:124-131`) — 매 저장마다 전체 목록을 다시 보내야 함 |
| 대상 학생(target groups) | `targetGroups` | 〃 | `academies.target_groups`(컬렉션) | 검색 필터 | A | O | - | 전량 교체, 위와 동일 |
| 지역 | `regions` | 〃 | `academy_regions` | 검색 필터, 학원 소개 | B | O | - | **DELETE 후 재INSERT**(`academyRegionRepository.deleteByAcademy_AcademyId` → `saveRegions`) — 요청에서 누락하면 전부 삭제됨 |
| 소개 한 줄 | `intro` | 〃 | `academies.intro` | 히어로 서브텍스트 | A | O | - | 필수값 |
| 공개 핸들(서브도메인) | `handle` | 〃 | `academies.handle` | 공개 URL | A | O | - | 값 없으면 기존 handle 유지(`resolveUpdateHandle`) |
| 커버 이미지 | 파일 업로드 | 〃 | `academies.cover_storage_key` | (현재 미노출 레거시 필드로 보임) | E | X | - | 로고와 동일 가드 |
| 헤더 타이틀(GNB 커스텀명) | `builderDraft.headerTitle` | 〃 (pageSettings 경유) | `page_settings` JSON | GNB | C | X (있으면 사용) | 상세 설정 | builderDraft 규칙 적용(§4) |
| 히어로 강조 문구 | `builderDraft.heroAccent` | 〃 | 〃 | 히어로 강조 텍스트 | C | X | 상세 설정 | 〃 |
| 히어로 통계(월 수강료/성적 향상/운영 연차) | `builderDraft.monthlyTuition` / `scoreImprovement` / `operationYears` | 〃 | 〃 | 히어로 통계 배지 | C | X(요청 시) | 상세 설정 | 〃 |

## 2. 대표 원장 소개 (`sec-principal`)

| 화면 필드 | data key | API | DB | 공개 페이지 사용처 | 분류 | 기본 노출 | 상세 이동 | 숨김 시 보존 |
|---|---|---|---|---|---|---|---|---|
| 원장 이름 | `builderDraft.principalName` | `PUT /api/tutor/academy` | `page_settings.builderDraft` | 원장 카드 이름 | A | O | - | §4 규칙 |
| 원장 직함/학원명 | `builderDraft.principalRole` | 〃 | 〃 | 원장 카드 배지 | A | O | - | 〃 |
| 원장 소개문 | `builderDraft.principalDesc` | 〃 | 〃 | 원장 카드 본문 | A | O | - | 〃 |
| 원장 약력(가변 리스트) | `builderDraft.principalCareer*` | 〃 | 〃 | 원장 카드 리스트 | B | O | - | 〃 |
| 원장 한 줄 신념(quote) | `builderDraft.principalQuote` | 〃 | 〃 | 원장 카드 인용구 | B | O | - | 〃 |
| ~~원형 기본 아바타~~ | 없음(마크업 하드코딩) | - | - | - | **E** | **제거됨(이번 PR)** | - | DB 필드 아님 — 순수 UI 장식이라 데이터 보존 이슈 없음 |
| 원장 소개 영상(업로드) | `builderDraft.principalVideoId` | `PUT /api/tutor/academy/videos`(업로드) + `PUT /api/tutor/academy`(참조 저장) | `academy_videos` + `page_settings.builderDraft` | 원장 카드 옆 영상 박스 | B | O | - | 이번 PR에서 `purpose=DIRECTOR` 저장 추가(§ below) |
| 원장 소개 YouTube 링크 | `builderDraft.principalYoutubeUrl` | 〃 | 〃 | 〃 | B | O | - | 업로드 영상과 배타적 |
| 영상 방향(가로/세로) | `builderDraft.principalVideoOrientation` | 〃 | 〃 | 영상 박스 비율 | C | O(자동 감지) | - | 〃 |

## 3. 학습 철학 (`sec-philosophy`)

| 화면 필드 | data key | 분류 | 비고 |
|---|---|---|---|
| 철학 키워드 | `builderDraft.philosophyKeyword` | B | |
| 철학 타이틀 | `builderDraft.philosophyTitle` | B | |
| 철학 항목 1~N | `builderDraft.philosophyItem1..N` | B | 가변 리스트, §4 규칙 |

## 4. 대표 수업 (`sec-lessons`, `sec-featured`) — 이미 상당 부분 간소화됨

이전 작업에서 이미 "카드 라벨 / 수업명 / 한 줄 설명 / 이런 학생에게 / 핵심 특징 / 대상 / 대표
이미지·영상"으로 정리되어 있어, 이번 PR에서 구조를 다시 바꾸지 않았다(§8 지침 준수).

| 화면 필드 | data key(`LessonProduct`) | 분류 | 비고 |
|---|---|---|---|
| 카드 라벨(탭 짧은 이름) | `tabShortName` | A | |
| 수업명 | `name` | A | |
| 한 줄 설명 | `kicker` | A | |
| 이런 학생에게 | `learnerNeed` | A | |
| 핵심 특징 | `highlight` | A | 강조 박스로 렌더 |
| 대상 학년/학생 | `gradeLabel` / `tabGuidanceLabel` | A | `gradeLabel` 없으면 `tabGuidanceLabel`로 폴백 |
| 대표 이미지 | `mediaImageKey` | A | 이번 PR에서 표시 크기/비율 수정(§ below) |
| 대표 영상 | `mediaVideoId` | A | 이번 PR에서 `purpose=LESSON`으로 원장 영상과 분리 |
| OT 영상 | `otVideoId` | B | 수업 상세 진입 후 노출 |
| 요일(weekdays) | `weekdays` | B | **PR #172에서 이미 hidden-field 보존 버그 수정됨**(§5) |
| 가격/정원/일정 등 상세 필드 | `priceAmount/priceUnit/priceSummary/maxCapacity/currentCount/scheduleType/weeklyCount/startDate/endDate/durationWeeks/durationMonths/scheduleSummary` | C | 이미 "상세 설정"류로 편집기에서 분리 렌더 |
| 설명 1/2/3 | `explanation1/2/3` | C | 상세 설명 |
| 테마 | `theme` | C | 카드 색상 프리셋 |
| creationKey / authoringRevision | `creationKey`, `authoringRevision` | **E** | 낙관적 락·신규 여부 판별용 내부 필드, 사용자 비노출 |

## 5. 대표 수업 선택(`sec-featured`) / 미니 강의(`sec-minilectures`)

| 화면 필드 | data key | 분류 | 비고 |
|---|---|---|---|
| 대표 수업으로 노출할 카드 선택 | `builderDraft.featuredLessons`(id 목록) | B | opt-in 아님(항상 노출 가능) |
| 미니 강의 목록(영상 재사용) | `builderDraft.miniLectures`(JSON: `{v,t,n,g}[]`) | C | opt-in, 기본 OFF. 영상은 `academy_videos` 재사용 |
| AI Native 프로세스 문구 | `builderDraft.aiProcess*` | C | 고정 마케팅 섹션, 텍스트만 편집 가능 |

## 6. 외부 연동 / 상담 / Footer

| 화면 필드 | data key | 분류 | 비고 |
|---|---|---|---|
| 블로그/스토어/네이버맵 URL | `pageSettings.blogUrl/storeUrl/naverMapUrl` | B | URL 유효성 검사(`validateUrl`) |
| 히어로 YouTube URL | `pageSettings.heroYoutubeUrl` | B | |
| 상담 연락처(전화/이메일) | `pageSettings.businessInfo.phone/email` | A | 상담 CTA의 실제 연결 채널 |
| 사업자 정보(상호/대표/주소/등록번호 등) | `pageSettings.businessInfo.*` | A(법적 필수) | 통신판매업 표시 의무 — `sec-footer`는 잠금 섹션 |
| 섹션 노출 토글 | `pageSettings.sectionVisibility` | E(시스템) | Builder 좌측 패널 전용 |
| 섹션 순서 | `pageSettings.sectionOrder` | E(시스템) | 〃 |

## 7. 영상 관리(`tutor_academy_videos.html`, Builder 외부 페이지)

| 화면 필드 | data key | 분류 | 비고 |
|---|---|---|---|
| 영상 파일 | multipart `video` | A | |
| **영상 용도(신규, 이번 PR)** | multipart `purpose` (`DIRECTOR`\|`LESSON`) | **A(신규)** | `academy_videos.purpose` 컬럼 신설(V963). 레거시 행은 `NULL` = 용도 미상으로 두 목록 모두에 노출 |

---

## 향후 AI 연결 우선순위 (참고)

A급 텍스트 필드(원장 소개문, 학습 철학 항목, 수업 한 줄 설명, 핵심 특징) 순으로 §9(AI Roadmap)의
"설명해줘/작성해줘/다듬어줘" 액션을 붙이는 것이 ROI가 가장 높다 — 이 필드들이 "학원마다 다시
써야 하는데 문장력 부담이 큰" 지점이기 때문이다. C/D/E급 필드는 AI 연결 우선순위에서 제외한다.

---

## §4. builderDraft 저장 규칙 (모든 `builderDraft.*` 필드 공통)

`Academy.pageSettings.builderDraft`는 `Map<String,String>` 화이트리스트 방식이다
(`AcademyService.validateBuilderDraft` — 허용 key 목록에 없으면 저장 자체가 거부된다). 저장
경로가 두 개라 규칙이 다르다:

1. **`PUT /api/tutor/academy/page-settings`**(부분 업데이트) — `builderDraft`는 요청에 **아예 받지
   않는다**. 항상 `current.builderDraft()`를 그대로 유지한다(`AcademyService.java:301`). 즉 이
   엔드포인트로는 builderDraft를 절대 바꿀 수 없다 — 의도된 설계다.
2. **`PUT /api/tutor/academy`**(전체 업데이트) — 프론트(`academy_builder.js:academyUpdateRequest`)가
   `Object.assign({}, academy.pageSettings)`로 현재 pageSettings 전체를 복사해 보내므로, 실제로는
   항상 builderDraft 전체가 함께 전송된다(주석 `academy_builder.js:3840-3841`에 명시).

## §5. 발견한 hidden-field 데이터 유실 위험 (감사 결과)

### 5-1. `weekdays` — 이미 수정됨 (PR #172, 참고용으로 남김)

간소화된 수업 편집 화면에는 요일 체크박스가 없는데, `readForm()`이 `[name="weekdays"]:checked`를
무조건 조회해 빈 배열을 반환 → 저장 시 기존 요일이 삭제됐다. `lesson_product_editor.js:186-198`에서
컨트롤이 아예 없으면 필드 자체를 건너뛰도록 고쳤다(`if (weekdayInputs.length === 0) continue;`).
백엔드 `LessonProduct.updateAuthoringDetails()`(`LessonProduct.java:249,305-306`)는 여전히
`weekdays`를 매번 전량 교체(clear+addAll)하므로, 프론트가 필드를 생략하지 않는 한 항상 안전하다 —
**프론트 쪽 가드가 유일한 방어선**이라는 점을 새 폼 필드를 추가할 때마다 기억해야 한다.

### 5-2. `builderDraft` 전체 유실 위험 — 이번 PR에서 신규 발견 및 수정

`AcademyService.normalizePageSettingsForWrite()`(전체 업데이트 `PUT /api/tutor/academy` 경로)는
수정 전에는 `requested.builderDraft()`를 **null 여부와 무관하게 그대로** 사용했다 — 부분 업데이트
엔드포인트(`updatePageSettings`)가 명시적으로 `current.builderDraft()`를 지키는 것과 대칭이 맞지
않았다.

- **왜 지금까지 사고가 안 났는가**: 이 경로의 유일한 실사용 caller인 `academy_builder.js`가 항상
  `Object.assign({}, academy.pageSettings)`로 현재 builderDraft 전체를 복사해 보내기 때문에, 실제로
  null이 전달된 적이 없다.
- **왜 위험한가**: 이 서비스 메서드를 호출하는 새 caller(관리자 도구, 모바일 클라이언트, 향후
  리팩토링으로 payload를 다시 조립하는 코드 등)가 하나라도 `pageSettings.builderDraft`를
  생략/null로 보내면, 원장 소개·철학·대표수업 선택·업로드 영상 참조 등 **builderDraft에 저장된
  모든 값이 한 번에 사라진다** — `weekdays`보다 훨씬 큰 반경의 사고다.
- **조치**: `AcademyService.java`의 `normalizePageSettingsForWrite()`에 `updatePageSettings()`와
  동일한 null-fallback을 추가했다(`requested.builderDraft() == null && current != null ?
  current.builderDraft() : requested.builderDraft()`). 현재 유일한 caller의 동작은 전혀 바뀌지
  않는다(항상 non-null을 보내므로) — 순수한 방어적 하드닝이다.
- **회귀 테스트**: `AcademyServiceTest.update_preservesExistingBuilderDraftWhenFullRequestOmitsIt`
  추가(기존 `update_preservesExistingBusinessInfoWhenFullRequestOmitsIt`과 동일한 패턴).

### 5-3. `subjects` / `targetGroups` / `regions` — 이미 전량 교체 방식(회귀 아님, 다만 주의 필요)

`Academy.updateDetails()`는 `subjects`/`targetGroups`를 매번 clear+addAll 하고, `regions`는
DELETE 후 재INSERT한다. Builder UI가 이 세 필드를 폼에서 완전히 숨기는 시나리오가 생기면(§6 단순화
검토 시) 저장 때마다 빈 배열이 전송되어 기존 값이 삭제될 수 있다 — **UI 단순화 작업 시 이 세
필드는 "안 보여도 기존 값을 그대로 다시 실어 보내는" 방식으로만 숨겨야 한다.** 이번 PR에서는 세
필드 모두 기본 노출(A급)로 유지해 이 위험을 만들지 않았다.

### 5-4. 신규 `academy_videos.purpose` — 설계 시점부터 유실 위험 없게 설계

레거시 영상 행(`purpose IS NULL`)의 실제 용도를 파일명 등으로 추측해 일괄 마이그레이션하지
않았다(V963 마이그레이션 주석 참고). 대신 애플리케이션 레이어에서 `purpose IS NULL`을 "두 목록
모두에 노출"로 처리해, 과거 업로드가 어느 목록에서도 사라지지 않게 했다.
