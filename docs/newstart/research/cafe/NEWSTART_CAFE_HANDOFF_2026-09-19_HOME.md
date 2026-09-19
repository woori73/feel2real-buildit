# NewStart Cafe Research Home Handoff — 2026-09-19

> 학원 PC(Academy PC)에서 집 PC(Home PC)로 네이버 카페 자료 수집 작업을 인계하기 위한 문서입니다.

---

## 1. GITHUB & BRANCH STATE

- **Repository:** woori73/feel2real-buildit
- **Branch:** `newstart/cafe-research-academy-20260919`
- **Commit SHA:** `51b383f0fd2cf660d3d5f3fb0201d4a03ee7e974`
- **Base Commit:** `d68bfa49967d720f5b59e5306f9ad40f908bb0df`

---

## 2. CURRENT COLLECTION STATUS

- **총 저장 Article 수:** **15건**
- **수집 완료 Article IDs:**
  - PoC (1건): `5994`
  - 1차 수집 (10건): `6008, 6006, 6004, 6002, 6001, 5997, 5993, 5988, 5987, 5986`
  - 2차 부분 수집 (4건): `5973, 5982, 5985, 5972`
- **마지막 체크포인트:** `SECOND_BATCH_MOBILE_COMPLETE_15_TOTAL`
  - `lastProcessed`: `5972`
  - `nextArticleIdToCollect`: `5971`
- **남은 수집 범위:** 초보가이드 게시판 2차 배치 잔여 약 36건 (2차 목표 40건 중 4건 완료) 및 이후 전체 게시글.

---

## 3. FILE SYSTEM & PRIVACY BOUNDARIES

### GitHub 저장 자료 (정제 & 마스킹 완료)
- **위치:** `docs/newstart/research/`
- **목록:**
  - `docs/newstart/research/cafe/collection_state.json` (수집 진행 상태 체크포인트)
  - `docs/newstart/research/cafe/BOARD_STRUCTURE.md` (게시판 구조 분석)
  - `docs/newstart/research/cafe/raw_text/*.json` (정제 텍스트 JSON 15건)
  - `docs/newstart/research/cafe/documents/*.md` (익명화 정리 마크다운 14건)
  - `docs/newstart/research/knowledge/*.md` (`CASE_INDEX.md`, `POLICY_CLAIMS_TO_VERIFY.md`, `QUESTION_TAXONOMY.md` 귀납적 지식 문서)

### Local-Only 자료 (GitHub 저장 금지)
- **위치:** `C:\Users\sxxra\docs\newstart\cafe\raw\`
- **사유:** 대량 수집 시 개인식별정보(실명, 전화번호, 계좌번호 등) 미마스킹 원본 포함 가능성 차단.
- **보호 조치:** `docs/newstart/.gitignore`에 `cafe/raw/` 등록되어 자동 제외됨.

---

## 4. READ-ONLY PRINCIPLE & PRIVACY COMPLIANCE

- **Cafe Write Actions:** **0건** (글 작성, 댓글 작성, 수정, 삭제, 좋아요 등 일체 없음)
- **계정 세션 정보:** 비밀번호, 쿠키, 세션 토큰, 브라우저 자격증명 등은 코드나 파일에 절대 저장되지 않음.
- **접근 방식:** 사용자가 직접 로그인한 Chrome 브라우저 세션을 Playwright MCP extension bridge로 읽기 전용 참조.

---

## 5. HOME PC COLLECTION RESUMPTION METHOD (집 PC 재개 방법)

### 사전 조건
1. Chrome 브라우저에서 네이버 카페 ([새출발기금 부실차주 라이브러리](https://cafe.naver.com/newhopero)) 로그인 세션 접속 확인.
2. Playwright MCP 서버 가동:
   ```bash
   npx -y @playwright/mcp@latest --extension
   ```

### 작업 순서
1. Repository fetch & checkout:
   ```bash
   git fetch origin
   git checkout newstart/cafe-research-academy-20260919
   ```
2. 수집 진행 상태 확인 (`collection_state.json`):
   - `lastProcessed`: 5972
   - 다음 수집 시작 ID: 5971
3. Playwright MCP 연결 후 `5971` 이하 미수집 articleId 순차 방문 및 JSON/Markdown 저장 진행.

---

## 6. FUTURE GOAL & PURPOSE

- 수집된 네이버 카페 Q&A 사례 지식 데이터는 향후 **전자책(E-Book) 원문 자료와 합성**하여 NewStart AI, AI+Human Thread, 사례 지식 라이브러리 구축의 핵심 지식 기반으로 활용될 예정입니다.

