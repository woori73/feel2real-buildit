# Academy 발행 API 설계 노트 (LLM 미래 대비)

> 대상: `POST /api/tutor/academy` 계열 write 경로 + 랜딩 발행 폼 + 미래 LLM 콘텐츠 생성.
> 성격: 설계 결정 + **열린 결정** 기록. 계약 상세는 [API.md](API.md), 도메인은 [DOMAIN.md](DOMAIN.md), 플로우는 [FLOWS.md](FLOWS.md).
> 배경: `feature/academy-siyan-publish` 브랜치 리뷰(로고 필수→선택, 랜딩 발행 추가)에서 도출.

## 1. 확정된 방향

### 1-1. write 동사는 하나 — create로 수렴
- academy 저장은 **단일 action(`AcademyService.create`)** 으로 모은다. caller(인간 랜딩폼 / 미래 LLM)별로 엔드포인트를 쪼개지 않는다.
- 이유: **미래 LLM은 서버 밖 "클라이언트"** 로서 내용을 만들어 create를 호출한다(§2). caller가 늘어도 write 동사가 하나여야 MCP/tool 노출도 하나로 깨끗하다. caller별 엔드포인트는 tool 표면을 N개로 번식시킨다.
- **✅ 수렴 완료** (feature/academy-create-converge): 별도 `POST /api/tutor/academy/publish`(`publishFromForm`)는 create 본문 복붙이라 **삭제**. `create`가 content negotiation으로 둘 다 소비한다.
  - `multipart/form-data`(`createAcademy`) — 인간 파일 업로드(로고/커버).
  - `application/json`(`createAcademyJson`) — 랜딩폼 + 미래 LLM 클라이언트. 로고 생략 → 기본 로고 폴백.
  - 같은 경로 `POST /api/tutor/academy` + 단일 서비스 `create()`. 중복 발행은 **409 Conflict**로 통일. → write action 1개 = 미래 MCP tool 1개.

### 1-2. 로고는 선택, 기본 로고는 "empty-state 폴백" (placeholder 아님)
- **서버 데모 폴백 원칙**: 서버가 값을 채워주는 건 **"DB NOT NULL인데 입력은 선택"인 필드에 한한다.** 현재 그 조건에 맞는 건 `logo_storage_key`(스키마 필수, 입력 선택) **하나뿐**.
  - 나머지 선택값(cover 등)은 비면 그대로 null. **필수값도 아닌 걸 서버가 임의로 채우지 않는다.**
  - 텍스트 필수값(name·intro·subjects·targetGroups·regions)은 클라이언트가 반드시 입력 → 데모 폴백 없음. "데모 동작"은 클라가 실제 텍스트를 넣고 **로고 이미지만** 서버 기본값을 쓰는 형태다.
- 로고 미첨부 시 `resolveLogoStorageKey`가 `loadDefaultLogoJpeg()`로 기본 로고를 저장해 공개 `/logo` 계약 유지.
- **기본 로고 = 정적 에셋.** `src/main/resources/static/img/demo_logo.png`를 읽어 JPEG로 재인코딩해 저장한다(공개 로고 서빙이 `image/jpeg`라 바이트도 JPEG로 맞춤). 매번 동일한 상수 이미지를 자바2D로 그리던 방식(구 `generateDefaultLogoJpeg`)은 낭비라 제거했다.
- ⚠ 이 기본 로고는 **"LLM으로 교체될 placeholder"가 아니다.** 입력이 아무것도 없을 때의 **영구 폴백**이다. 미래 LLM은 이 에셋을 대체하지 않고 **우회**한다(진짜 로고를 만들어 입력으로 넘기면 "입력 있음" 경로를 탄다).
- 데모 로고 문구/디자인을 바꾸려면 `demo_logo.png` 파일만 교체하면 된다(코드 수정 불필요).

## 2. 미래 LLM 통합 — seam은 서버 안에 없다

- 미래에 로고 이미지/소개 내용을 LLM으로 생성한다. **단, LLM은 서버 내장 생성기가 아니라 클라이언트다.**
- 흐름(확정): `LLM이 내용·이미지 생성 → 기존 create를 JSON으로 호출 → 저장`. 서버는 LLM을 몰라도 된다.
- 귀결: 서버는 **지금 상태로 미래 준비 완료**. 나중에 서버 안에 LLM 훅을 심는 게 아니라, LLM 클라이언트가 호출할 **깨끗한 JSON create tool** 하나만 있으면 된다.
- 현재 단계(MVP): LLM 생성 없음. **static demo 값** + 기본 로고 폴백만.

## 3. 열린 결정 (미정 — 확정 금지)

> 아직 정하지 않았으므로 코드/문서에 특정 방식을 못박지 않는다.

- **LLM을 어디서·어떻게 호출해 로고/내용을 만들지 미정.** 후보(방향만, 결정 아님):
  - 프론트/별도 클라이언트가 Anthropic API 직접 호출 후 create(JSON)로 전달.
  - 별도 "generate" 서비스/엔드포인트가 초안(이미지+내용) 생성 → 사용자가 검토 후 create.
  - MCP tool로 노출( generate / persist 분리 여부 포함) — 상세 미정.
- 결정 시 재검토할 것: generate와 persist를 tool로 분리할지, 초안 검토(휴먼 게이트) 넣을지, 이미지 생성 모델·비용·캐싱.
- **불변 제약(무엇을 정하든 지킬 것)**: §1-1 write 동사 단일화, §2 서버 LLM-agnostic(서버가 생성기를 내장하지 않음).

## 4. 정리 실행 내역
1. ✅ `publishAcademy` + `publishFromForm` 삭제.
2. ✅ `createAcademyJson`(JSON consume) 추가 → 단일 `create()` 라우팅. 중복 409 통일.
3. 랜딩폼 전용 발행 흐름은 현재 BUILD IT 제품 방향 기준으로 다시 검토한다.
4. ✅ 기본 로고 문구 `"AI LOGO READY"` 제거(정적 에셋 `demo_logo.png`).
5. ✅ `publishFromForm` 테스트 → `create` JSON 케이스로 이관 + 컨트롤러 JSON 바인딩 테스트.
6. ✅ [API.md](API.md)/[FLOWS.md](FLOWS.md) `/publish` 제거 동기화.
