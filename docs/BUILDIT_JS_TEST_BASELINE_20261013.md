# BuildIt JS 단위 테스트 기준선 (2026-10-13)

`src/test/js/*.test.js` 91개 파일을 Node(`node --test`)로 돌린 결과다. 이 테스트들은 지금까지 **CI에서 실행되지 않아** 일부가 코드 변경을 따라가지 못했다.
이번 변경(AI 제작/기존 학원 화면 모드 분리) 전후로 **같은 35건이 실패**하며, 새 실패는 없다(main `89b81b54` 기준과 동일 목록).

## CI 실행 범위

`build-check.yml`의 `build` 잡은 현재 **통과하는 AI 제작 관련 파일만** 명시적으로 실행한다.

```bash
node --test src/test/js/assistant_wait.test.js src/test/js/academy_adopt.test.js \
  src/test/js/assistant_home_contract.test.js src/test/js/academy_public_hero_cycle.test.js
```

아래 실패 파일은 고치기 전까지 CI에 넣지 않는다(전체를 넣으면 CI가 항상 빨갛게 된다). 고친 파일은 위 목록에 추가한다.

## 실패 35건의 원인 (16개 파일)

| 파일 | 실패/전체 | 원인 분류 |
|---|---|---|
| `academy_ai_assist_button_render.test.js` | 3/11 | 렌더 결과 계약 변경(학습 철학 AI 버튼 구조) — 코드가 앞서가 테스트가 낡음 |
| `academy_ai_suggestion.test.js` | 1/9 | 기대 값 불일치(AI 제안 target 목록이 코드와 달라짐) — 낡은 계약 |
| `academy_brand.test.js` | 1/5 | 브랜드 프리셋 정의 변경 — 낡은 기대 값 |
| `academy_builder_consult_contact.test.js` | 1/12 | 빌더 JS 캐시버스팅 문자열(`academy_builder.js?v=20261011-1`)이 최신 버전과 다름 — 낡은 계약 |
| `academy_builder_future_sections.test.js` | 1/12 | vm 가짜 `window`에 `setTimeout`이 없음 — 하네스 결함 |
| `academy_builder_p0.test.js` | 1/26 | 디자인 프리셋 수가 5종에서 10종으로 늘었는데 테스트는 5종 기대 — 낡은 계약 |
| `academy_builder_preview_fields.test.js` | 1/2 | 원장 사진 렌더 구조 변경(태블릿 프레임) — 낡은 계약 |
| `academy_featured_selector.test.js` | 7/10 | 대표 학교 카드(featured-schoolcards) 마크업/CSS가 바뀜 — 낡은 계약(7건) |
| `academy_hero_polish.test.js` | 1/7 | 원장 사진 렌더 조건 변경 — 낡은 계약 |
| `academy_intro_summary.test.js` | 2/16 | `.canvas-frame` CSS 문자열 변경 — 낡은 계약 |
| `academy_public_consult_behavior.test.js` | 5/5 | vm 컨텍스트에 `URLSearchParams` 전역이 없음 — 하네스 결함(5건 전부) |
| `academy_public_mobile.test.js` | 1/7 | 모바일 헤더 구조 변경 — 낡은 계약 |
| `academy_reveal.test.js` | 1/14 | reveal 구현에 타이머/IntersectionObserver가 추가돼 "사용 금지" 단언이 깨짐 — 낡은 계약 |
| `academy_storefront_sections.test.js` | 2/8 | 스토어프론트 편집 앵커(`data-builder-draft="featuredItem1"`) 변경 — 낡은 계약 |
| `academy_video_first_play.test.js` | 3/3 | vm 컨텍스트에 `URLSearchParams` 전역이 없음 — 하네스 결함(3건 전부) |
| `api_archive_aware_url.test.js` | 4/4 | 테스트가 `document` 전역을 가정하는데 Node에는 없음 — 하네스 결함(4건 전부) |

### 분류 요약

- **하네스 결함 (테스트 코드가 실행 환경을 잘못 가정)**: `academy_public_consult_behavior`(5), `academy_video_first_play`(3), `api_archive_aware_url`(4), `academy_builder_future_sections`(1) — 합 13건. `vm` 컨텍스트에 `URLSearchParams`/`window.setTimeout`/`document`가 없어서 앱 코드가 아니라 테스트 준비 단계에서 실패한다. 컨텍스트에 전역을 주입하면 고쳐진다(`academy_public_hero_cycle.test.js`가 같은 방식으로 주입한다).
- **낡은 계약 (코드가 의도적으로 바뀌었는데 테스트가 안 따라감)**: 나머지 22건 — 마크업·CSS 문자열, 캐시버스팅 버전, 프리셋 개수(5→10) 등을 정규식/문자열로 고정해 둔 단언. 코드 변경 이력을 보고 기대 값을 갱신하거나 폐기를 결정해야 한다.

## 이번 변경과의 관계

- 새로 추가한 JS 테스트(`assistant_wait` 7건, `academy_adopt` 7건)와 `academy_public_hero_cycle`(3건), `assistant_home_contract`(5건)는 모두 통과하며 CI에서 실행된다.
- 이 문서의 35건은 별도 정리 작업으로 다루며, 이번 PR 범위에서 고치지 않는다.
