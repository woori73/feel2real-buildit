# BuildIt S2·S3 준비 메모 (설계만 — 구현은 S1 검증 후 별도 승인)

S1(대화형 반복 수정)이 만든 확장 지점만 적는다. 코드·DB는 아직 없다.

## 공통 전제
- 모든 변경은 `AssistantPatchApplier`의 **패치 연산 → 서버 검증 → 작업본 저장**을 지난다. 공개본 직접 쓰기 금지.
- 되돌리기 스택(EDIT/UNDO 메시지)은 새 연산에도 그대로 쓰인다. 연산이 `undo` 정보만 채우면 된다.

## S2 — AI가 buildit2 디자인 자산을 고르고 조합
- 새 연산: `setVariant {sectionId, variantId}`, `setMotion {motionId|none}`. `AssistantDesignCatalog`에 허용 목록을 추가.
- 자산 목록: 새 DB 테이블 없이 **저장소 안 정적 JSON**(`variantId → 섹션, 설명, 필요 데이터 키`).
- 저장 위치: `builderDraft`의 `sectionVariant.<sectionId>`, `motion` 키 → `AcademyService` BUILDER_DRAFT_KEYS 허용 목록에 추가.
- 렌더: `academy_sections.js`가 해당 키를 읽어 CSS 클래스(`bi-` 접두)만 바꾼다. 새 렌더러 없음. 필요한 데이터(강사 등)가 없으면 변형을 적용하지 않고 이유를 `skipped`로 알린다.
- AI 프롬프트에는 목록의 id/설명만 넣는다(자산 코드 전달 없음).

## S3 — 이미지 업로드·교체도 작업본에서 (**구현됨**: PR `feat/assistant-image-staging`, V1064)
구현된 것: `academy_working_copy.staged_media_json` 예약, `POST /assistant/media`·`GET /assistant/media/{slot}`, `clearImage` 연산, 게시 때만 공개 슬롯 승격, 되돌리기(`mediaUndo`), 미리보기 전용 이미지 경로. 아래는 설계 당시 계획이다(키 이름·경로는 구현 기준으로 `API.md`/`DOMAIN.md` 참고). 관리자 작업본·AI 이미지 생성/검색은 범위 밖.

- 현재: `AcademyMediaService.uploadHeroMedia → academyService.updateHeroMedia → Academy.updateImageSlot`이 **공개 데이터를 즉시 변경**한다(작업본 우회).
- 제안: 업로드는 스토리지에 올리되 키를 작업본 draft(`mediaDraft.<slot>`)에만 기록하고, `publish` 시 `Academy` 슬롯으로 승격. 폐기·되돌리기 시 미승격 키는 정리 대상.
- 대화 연산: `setImage {slot, uploadId}` (업로드 ID는 소유자 검증 후 사용). 사용자가 올린 사진만 사용하며 임의 이미지 생성/검색은 하지 않는다.
- 선결 과제: 업로드 키의 소유권·정리(고아 파일) 정책, 용량/형식 검증은 기존 `AcademyMediaService` 규칙 재사용.

## 승인 전 하지 않는 것
레지스트리 DB, AI 자동 검색, 새 렌더러, 공개본 직접 쓰기.
