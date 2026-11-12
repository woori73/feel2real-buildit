# CAFE HANDOFF 2026-10-03 (학원 PC → 집 PC)

- 작성: 2026-10-03 늦은 저녁, 학원 PC
- 범위: 네이버 카페 `새출발기금 부실차주 라이브러리`(`https://cafe.naver.com/newhopero`, CAFE ID 31312978) 구조개편 사전조사 + Phase 1 실행 설계
- 이 문서의 목적: **집 PC에서 새로 조사하지 않고, 오늘 확인한 상태 그대로continuation.**

---

## 1. 오늘 완료한 작업

| # | 작업 | 산출물 |
|---|---|---|
| 1 | 카페 구조 감사(읽기 전용) — 15개 게시판 5,323건, 전체공지 3건, NewStart 라우트·권한, 디자인 토큰 | `docs/newstart/CAFE_CURRENT_STRUCTURE_2026-10-03.md` (운영자 READY 승인) |
| 2 | 390px/PC 스크린샷 5종 + 3198 모바일 본문 합성 캡처 | `docs/newstart/cafe-audit-2026-10-03/shots/` |
| 3 | 게시판 통계·키워드·Jaccard 집계 | `…/boards_table.txt` |
| 4 | 증상별 대표글(articleId·조회) 전수 추출 | `…/index_candidates.txt` |
| 5 | 전체공지 3건 본문·링크·inbound 의존도 실측 + contentHtml 원문 백업 | `…/ref_report.txt`, `…/notices3_report.txt`, `…/raw_backup/*.json` |
| 6 | NewStart 상담 URL·로그인 체인·감사회원 정책 코드/HTTP 실측 | PLAN §6, §14 |
| 7 | 카페 관리 능력 실측(메뉴 그룹/구분선/링크, 대문 HTML, 스킨, 가입 환영글, 배너 관리 부재) | PLAN §0 |
| 8 | **Phase 1 최종 변경 설계(FINAL PLAN)** 12개 요구 항목 + 예상 변경 건수 | `docs/newstart/CAFE_PHASE1_FINAL_PLAN.md` |
| 9 | 2026-10 운영 메시지 보존 | `docs/newstart/CAFE_2026_10_VISION_MESSAGE.md` |
| 10 | GitHub 인계 branch + PR(미병합) | 아래 §11–§13 |

## 2. 실측 완료 항목 (VERIFIED)

- 모바일 HOME 390px: `section#MyCafeGateSink.MyCafeGate` — 자식 0·앵커 0, 배경은 네이버 프리셋 `img_cafe_skin05.png` 640×484, `html.cafeSkin3` → **대문 배너는 클릭되지 않는 이미지**
- 모바일 HOME 전체공지 노출: **0건**. 하단 탭 `전체공지`(`?tab=notice`)에서만 3건
- 게시판 공지(판 상단 고정): 게시판 목록에서는 노출, HOME에서는 미노출
- 전체공지 슬롯: 3건, 순서 제어 가능
- 메뉴 관리도구(`/ca-fe/admin/cafes/31312978/menu-management`): **그룹 제목 / 구분선 / 링크** + 순서·들여쓰기·접기·드래그&드롭·ctrl 다선택. 링크 hint: "카페에 필요한 링크를 '메뉴'로 추가합니다."
- PC 대문 HTML(`Gate.nhn?gate.clubid=31312978&m=viewEditorIframe` → `textarea[name=content]`): 원문 732자, **`<img>` 2개 / `<a>` 0개**, 래퍼 `div.img_tit_area > div.cafe_default_gate`
- PC 대문 HTML 직접 편집: 가능(`checkHtmlMode()` 분기 존재). 하이퍼링크 입력: 가능(`<legend>하이퍼링크</legend>` + 텍스트 input)
- PC 대문 이미지 실측: **960×1431**(`표지2.png`, id userImg4047537) / **656×1632**(`표지_바로_뒷면.png`, id userImg6579785)
- '배너 관리'(클릭 배너 등록) 메뉴: `ManageHome.nhn` 전수 검색에서 **없음**
- 게시글 영구 링크: `https://cafe.naver.com/newhopero/3198` → 200, 셸이 `#cafe_main` iframe을 `ArticleRead.nhn?articleid=3198&clubid=31312978`로 지정
- NewStart: `/newstart` 200 / `/newstart/consultations` **302 → `/newstart/login?next=%2Fnewstart%2Fconsultations`** / `/newstart/login` 200 / `thanks-guide`·`chat`·`fss-helper` 302
- 권한 정책: 1:1 상담 = **로그인 회원 전체**(감사회원 요건 없음, 2026-10 코드), 지피지기 Agent = **THANKS_MEMBER(감사회원) + ADMIN 전용** → **본 개편에서 변경 없음**
- 의존도: 3287 inbound **0건**(방 주소 `gFl6FS8h`는 3774/3799/3835/3850에 잔존), 4446 inbound **0건**, 3198 inbound **10건**
- 3198 본문: 스마트스토어 앵커 3곳(동일 URL 1종), 이미지 5장, NewStart 링크 **0건**, 감사회원 전환은 하단 주의사항 1줄
- 디자인 토큰: `#1558d6` `#0b2f73` `#eef3ff` `#101828` `#dfe6ef` `#f5f7fa`, 카드 radius 14px, 버튼 ≥44px, Pretendard/"Noto Sans KR"

## 3. 아직 미확인 항목 (UNVERIFIED — 가정 금지, 실행일 1단계에서 읽기 전용 확인)

1. 메뉴 '링크' 추가 시 **외부 URL 입력 UI**가 뜨는지(추가 시도는 `visInputs=[]`였고 **취소**로 원상복구, 저장 안 함)
2. 메뉴 그룹/구분선/링크의 **모바일 서랍 렌더**(접힘 여부·노출 순서)
3. 대문/썸네일 이미지의 **관리 화면 권장 규격·용량 한도**(780×590 제안은 skin05 비율 실측 기반)
4. 카페 소개 **100byte의 문자/바이트 기준**(한글 50자 vs 100B)
5. 비회원(비로그인) 첫 화면 — 매니저 세션으로만 실측
6. 초보가이드(16) 열람 권한 등급 기준
7. 전자책 가격·재고·리뷰(문구에 미기재)
8. 글 수 5,323(실측) vs 5,324(지시문) 1건 차이 → 실행일 목록 API 재실측
9. NewStart 상담 화면 내부 상태값(대기/답변완료)
10. 카페 articleId 57과 NewStart `/newstart/updates/57`의 구분([내 문제 찾기] 2번 항목 링크 치환 필요)

## 4. 카페에 실제 변경한 항목

**0건.**

- 메뉴 저장하기 클릭하지 않음(링크 추가 시도는 취소, 트리 15개 원형 확인)
- 공지 수정·전체공지 재지정·게시글 삭제·이미지 업로드·대문 교체·스킨 변경 **전부 미실행**
- NewStart 코드/DB/배포/PR **0건** (오늘 작업은 읽기 전용 + 로컬 문서 작성 + docs branch push)

## 5. 아직 변경하지 않은 항목 (Phase 1 실행 시에만)

- 전체공지 순서 재편(3198 1순위 / [내 문제 찾기] 2순위 / 4446 3순위)
- 3198 in-place 개편(블록 A 상단 5단계 + 블록 B 카카오 방 흡수)
- [내 문제 찾기] 신규 안내글 게시(공지사항 menuId 14)
- 3287 전체공지 해제 → 삭제(게이트: 의존 링크 재스캔 0건 확인 후)
- 메뉴 그룹 7 / 구분선 / 게시판 위치 15 / 이름 변경 6
- 모바일 대문 배너·카페 아이콘·카페 소개·가입 환영글·PC 대문 앵커 2개
- 예상 변경 건수 총계 **33~38건**, 되돌릴 수 없는 것 **1건(3287 삭제)** → PLAN §13

## 6. 현재 공지 상태 (그대로)

| 슬롯 | articleId | 제목 | 조회/댓글 | 상태 |
|---|---|---|---|---|
| 전체공지 1 | **3287** | 단톡방 2기 \<감사회원방\> | 930 / 93 | 그대로(삭제 결정만 기록, **미실행**) |
| 전체공지 2 | **3198** | \<새출발 2억 2천 탕감…\> 출시 했습니다! | 3,188 / 376 | 그대로(개편 원고만 작성) |
| 전체공지 3 | **4446** | 전자책 상담방 오픈! \<비번 수시변경\> | 1,125 / 204 | 그대로(3198과 중심 링크 중복 0건 → 통합 불필요 판정) |

- 게시판: 공지사항 = **menuId 14**(글 17건 전부 매니저 작성)
- 최신 일반 공지(고정 아님): 6039(9/28), 6069(10/1), 6071(10/2), 6089(10/2)

## 7. 현재 메뉴 상태 (그대로)

- PC 메뉴 평면 **15개**, 순서 그대로: menuId 1,2,3,4,5,6,7,9,11,13,14,15,16,22,23
- 그룹 없음, 구분선 없음, 링크 메뉴 없음, 게시판 추가/삭제/이름 변경 없음
- 합계 5,323건(자유 2,957 / 질문 957 / 신청 356 / 부실우려 249 / 자격·연체·추심 216 / 임시안→확정안 167 / 초보가이드 93 / 담보 89 / 재취업 95 / 재기를꿈꾼다 40 / 감면율예측 37 / 하랑랑 32 / 공지 17 / 지피지기 13 / 추심민원샘플 5)
- 트리 원형 증거: 마지막 항목 = `감면율과 확정금액 예측 게시판`(menuId 22 위치), `li` 15개

## 8. 백업 위치

| 자산 | 위치(이 repo) |
|---|---|
| 전체공지 3건 contentHtml 원문(전체) | `docs/newstart/cafe-audit-2026-10-03/raw_backup/notices_backup_full.json` |
| 3198 gw/v4 전체 응답 | `…/raw_backup/art3198_full.json` |
| 3건 원문 축소본 | `…/raw_backup/notices3.json` |
| PC 대문 HTML 원문 732자 | `…/raw_backup/pc_gate_html_backup_20261003.txt` |
| 게시판·글 인덱스(nick 제거 scrub) | `…/menu_data_scrubbed.json` |
| 재현 스크립트 | `…/scripts/analyze_*.py` |

학원 PC 원본(미push): `C:\Users\VIEW LIFE\Documents\Qoder\2026-10-03\d61b10f3\audit_raw\menu_data.json`(nick 포함) — **쿠키/세션은 어디에도 저장하지 않음.**

## 9. screenshots 위치

`docs/newstart/cafe-audit-2026-10-03/shots/`
- `mobile_home_390.png`(HOME, 공지 0건 실증) · `mobile_notice_tab_390.png`(전체공지 탭 3건)
- `art3198_mobile_390_stitched.png`(3198 본문 390×1659, 3프레임 합성) · `art3198_panel_top.png`
- `pc_menu_crop.png`(15개 메뉴) · `newstart_home_390.png` · `newstart_login_390.png` · `newstart_mobile_metrics.json`
- `skin05_naver_preset_gate.png`(640×484 = 모바일 대문 배경 실증)
- 미포함(로컬): `pc_cafe_menu.png`(1.2MB 원본), `a3198_p1..p3.png`, `step1/step2*.png`

## 10. FINAL PLAN 위치

`docs/newstart/CAFE_PHASE1_FINAL_PLAN.md` — 요구 12개 항목 전부 포함:
1 BEFORE→AFTER 모바일 HOME / 2 15개 게시판→그룹 매핑표(KEEP·RENAME·ARCHIVE, 글 수·menuId·이유) / 3 전체공지 BEFORE→AFTER(+3287 게이트, 4446 판정) / 4 articleId 3198 개편 원고(블록 A·B) / 5 [내 문제 찾기] 완성 원고 / 6 NewStart 1:1 상담 CTA 문구 / 7 전자책→감사회원 CTA 문구 / 8 배너 정확한 문구 / 9 배너 권장 크기·구성 / 10 실제 변경 순서 / 11 롤백 / 12 변경하지 않을 항목 + §13 예상 변경 건수

## 11. Git branch

- `handoff/naver-cafe-phase1-20261003` (base `main` = `47615c17`)
- 리포: `woori73/feel2real-buildit` (PRIVATE)

## 12. commit SHA

| 커밋 | SHA | 내용 |
|---|---|---|
| docs 보존 | `f651467195d3ca223ced2cdca4d3e2c1250f7a8a` | 감사·FINAL PLAN·VISION·백업·캡처 (28 files in PR) |
| handoff 문서 | `f7c9c30177d2410cf069f2ab0dafbdaca7980812` | `CAFE_HANDOFF_2026-10-03.md` 최초본 |
| base | `47615c17` | `origin/main`(PR #474 머지 직후) |

검증(2026-10-03 학원 PC): `git ls-remote origin refs/heads/handoff/naver-cafe-phase1-20261003` = `f7c9c301…` = 로컬 HEAD **일치**.
(§12를 실측값으로 갱신한 커밋이 이 위에 하나 더 쌓이며, 그때 HEAD SHA는 달라진다 — branch name으로 받는 것이 안전하다.)

## 13. PR 번호/URL

- **PR #475** — `https://github.com/woori73/feel2real-buildit/pull/475`
- 제목: `docs(newstart): Naver Cafe Phase 1 audit and home handoff`
- 실측(학원 PC): `state=OPEN` · `base=main` · `head=handoff/naver-cafe-phase1-20261003` · `mergeable=MERGEABLE` · 변경 파일 **28개**
- 상태: **OPEN, MERGE하지 않음**(목적은 인계·기록 보존)

## 14. 집 PC에서 첫 번째로 할 작업

1. `git clone` 또는 기존 clone에서 `git fetch && git checkout handoff/naver-cafe-phase1-20261003`
2. **읽기 순서**: `docs/newstart/CAFE_HANDOFF_2026-10-03.md` → `docs/newstart/CAFE_PHASE1_FINAL_PLAN.md` →(필요 시)`CAFE_CURRENT_STRUCTURE_2026-10-03.md`
3. **새로 조사하지 말 것.** 5,323건 재크롤링 불필요(§8 인덱스로 충분).
4. PLAN §10의 **1단계(UNVERIFIED 4종 읽기 전용 확인)**만 수행: 메뉴 링크 URL 입력 / 메뉴 그룹 모바일 렌더 / 대문 업로드 규격 / 카페 소개 byte 기준. **저장하기 클릭 금지.**
5. 4종 확인 결과를 PLAN §13 건수에 반영(불가 항목은 삭제)하고, **운영자 승인 후** 실행: 글 → 공지 → 메뉴 → 이미지 순서, 각 단계 백업·스크린샷, **3287 삭제는 마지막.**
6. 실행 후 390px 공동검수(수치 통과만으로 마감하지 않음).

---

## 15. 2026-10-03 학원 PC 1차 실행 기록 (메뉴 개편만 적용)

운영자 지시로 **메뉴/게시판 구조만** 실제 변경했다. 대문·로고·대표이미지·그래픽·공지 슬롯·게시글 이동·삭제는 **0건**.

### 15-1 적용 결과 (라이브 실측: `SideMenuList?cafeId=31312978`, `ManageMenuList.json`)

| 순서 | menuId | 종류 | 메뉴명 | indent |
|---|---|---|---|---|
| 1 | 23 | 게시판(B) | 지피지기 Agent + NewStart | false |
| 2 | 14 | 게시판(B) | 공지사항 | false |
| 3 | 1 | 게시판(B) | 인사·등업·질문게시판 | false |
| 4 | 16 | 게시판(B) | 초보가이드 (이름 변경 없음) | false |
| 5 | **24** | **그룹(F)** | 과거 게시판 | false |
| 6~16 | 13,15,11,6,7,2,3,4,5,9,22 | 게시판(B) | 자유게시판 … 감면율과 확정금액 예측 게시판 | **true** |

- **기존 menuId 15개 전부 불변** 확인: `23,14,1,16,13,15,11,6,7,2,3,4,5,9,22`. 신규는 그룹 제목 **24** 1개뿐.
- 이름 변경 2건(23, 1). 나머지 13개는 원문 그대로(이중 공백까지 유지).
- 게시판별 글 접근: `cafe2/ArticleListV3dot1.json?...menuid=13/1` → 글 정상 반환. 게시글 영구 링크 4건 200 — 3198(댓글 376·조회 3,188·noticeType N), 3287(93·930), 4446(204·1,125), 5526(3).
- PC `#cafe-menu` DOM: 상위 4개 + `과거 게시판`(링크 없는 그룹 헤더) + 하위 11개. href는 `search.menuid={원 menuId}` 유지.
- 모바일: `SideMenuList`의 `listOrder/indent/menuType=F`로 상위 5개 구조가 **실제 반영**됨을 데이터로 확인. (패널 hidden이라 390px 시각 QA는 미수행 → 집 PC 공동검수 항목)

### 15-2 카페 저장 메커니즘 (VERIFIED — 집 PC에서 재사용 가능)

- 관리도구: `https://cafe.naver.com/ca-fe/admin/cafes/31312978/menu-management` (Vue2 + Vuex + Apollo).
- 상태: `store.state.admin.menuManagement.menuList[]` = `{id, code, name, indented, folded, hidden, isHidden, isSelected}`. 코드: 게시판 `NORMAL`, 그룹 제목 `GROUP`, 저장 후 서버가 실 menuId 부여(임시 -1 → 24).
- 저장 API: **POST `https://apis.naver.com/cafe-web/cafe-mobile/ManageMenuListSave.json`** → 200 후 `ManageMenuList.json` 재조회. 읽기: GET `ManageMenuList.json?cafeId=`.
- 패널 hidden(실 포터 불가)에서도 합성 이벤트로 전 과정 수행 가능:
  - 행 선택: `li` 안의 **`a`**에 pointerdown/mousedown/pointerup/mouseup/click (li에 직접 클릭하면 선택 안 됨). 다중 선택은 `ctrlKey:true`로 반복 → `selection.menus`에 누적.
  - 이름 변경: 선택 후 `.set_area input.ipt_type`에 value setter + `input`/`change` 이벤트 → `menuList[].name` 반영.
  - 순서/들여쓰기: `.edit_btn_area li.bt_v3`(위로), `bt_v5`(들여쓰기) 등의 **내부 `a`**에 동일 이벤트 세트.
  - 그룹 제목 추가: 관리도구 `그룹 제목` li의 `a` 클릭 → `selection.lastSelectionIsMenuType=true` → `추가` `a` 클릭 → 선택 행 **다음 위치**에 `GROUP` 삽입.
  - 저장: `a.btn_type_ny3`.
- 주의: `.set_area`에는 권한/숨기기/좋아요 컨트롤이 함께 있으므로 **`input.ipt_type` 외 건드림 금지**(저장 시 전체 config가 반영될 수 있음).

### 15-3 article 3198 상태

- 이 창에서 Phase 1 블록 2개(선두 `text`, 후미 `text`)를 제거하고 등록 → 11 components, contentHtml 26,949자.
- 백업 `audit_raw/art3198_full.json`의 26,906자와 대조: **개행(`
`) 43자 차이를 제외하고 본문 동일**. 개행 제외 문자열 길이 26,778로 일치, FNV/역순 FNV/가중합 6종 해시 전부 일치(anchors 7·imgs 5·`<b>`46·fs16 27·smartstore 9 동일).
- 제목·SmartStore 링크(`smartstore.naver.com/newlibrary/products/12879870964`)·ogLink·댓글 376·조회 3,188·noticeType `N`·articleId/URL 보존 확인.
- 이후 운영자가 19:42 직접 편집·저장했으므로 **현재 라이브 본문은 운영자본이 우선**이며, 이 창에서는 그 뒤로 카페 쓰기 없음(메뉴 관리도구 저장만 수행).

### 15-4 미결(집 PC 2차)

모바일 대문/로고/대표이미지/그래픽, 초보가이드(16) 라벨 여부(1차에서는 이름 변경 안 함), 질문·자유게시판 역할 통합에 따른 게시판 공지 문구, 전체공지 슬롯, 3287 정리, 390px 공동검수.

> **핵심 문구**: "인간 지피지기 + 인공지능 = 지피지기 Agent" — 카페는 지피지기 Agent 중심으로 진화하고, NewStart는 개인 채무상황 정리·1:1 비공개상담·개인화 서비스로 연결한다. 지피지기 Agent는 **감사회원 + ADMIN 전용** 정책을 유지한다.
