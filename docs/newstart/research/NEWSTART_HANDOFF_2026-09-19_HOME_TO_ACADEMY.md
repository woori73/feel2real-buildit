# NewStart Handoff

## 1. 목적
네이버 카페 "새출발기금 부실차주 라이브러리"의 질문게시판 자료를 조사하여
NewStart AI / AI+Human Thread / 사례 Knowledge 구축에 활용.

> **정정 (2026-09-19, 학원 PC)**: article_5994의 실제 breadcrumb이 `질문게시판(menuid=1)`임을 확인. 기존 '초보가이드(menuid=16)' 기록은 referrer menuid 오인. 이하 menuid=16 URL 패턴은 menuid=1로 읽을 것.

## 2. 조사 대상
- **카페**: 새출발기금 부실차주 라이브러리
- **URL**: https://cafe.naver.com/newhopero
- **게시판**: 질문게시판 (menuid=1, boardtype=L) — 정정済み
- **클럽 ID**: 31312978

## 3. 현재까지 한 작업

### PoC 검증 완료 (2026-09-19)
- ✅ 기존 Chrome 네이버 로그인 세션 재사용 성공
- ✅ Playwright MCP (via npx @playwright/mcp@latest --extension) 접근 성공
- ✅ cafe_main iframe 내부 콘텐츠 접근 성공
- ✅ 게시글 본문 원문 추출 성공
- ✅ 댓글 존재 여부 확인 성공 (댓글 0개)
- ✅ 첨부파일 탐지 성공 (첨부파일 없음)
- ✅ JSON/Markdown 저장 성공

### 수집 구조 검증
- 게시판 목록 페이지: https://cafe.naver.com/f-e/cafes/31312978/menus/1?viewType=L (정정: 기존 menus/16)
- 실제 기사 페이지: iframe(cafe_main) 내부에서 렌더링됨
- URL 패턴: https://cafe.naver.com/f-e/cafes/31312978/articles/{articleId}?boardtype=L&menuid=1&referrerAllArticles=false (정정: 기존 menuid=16)
- 페이지네이션: 게시글 하단 "'질문게시판' 게시판 글" 영역에 페이지 버튼 1~3 + "전체보기" 링크 확인 (2026-09-19)

## 4. 생성된 파일

### GitHub에 저장된 안전 자료 (PII 없음)
```
docs/newstart/research/
├── article_5994.json      # PoC 수집 결과 (JSON)
├── article_5994.md        # PoC 상세 보고서 (Markdown)
└── NEWSTART_HANDOFF_2026-09-19_HOME_TO_ACADEMY.md  # 이 파일
```

### 로컬에만 보관 (PII 포함 가능성 → GitHub 미저장)
```
C:\Users\sxxra\docs\newstart\cafe\raw\초보가이드\articles\
  └── (빈 디렉터리 - FAST COLLECTION 단계에서 수집 예정)
```

## 5. 수집된 게시글

| 글번호 | 제목 | 작성자 | 작성일 | 비고 |
|--------|------|--------|--------|------|
| 5994 | 햇살론 보증료 | lyrsky | 2026.09.16. | **PoC 완료 (본문 6문단, 댓글 0개)** |

**실제 저장된 수: 1개 (PoC만)**

## 6. 수집된 댓글

- 글번호 5994: 댓글 0개 확인
- **총 댓글 수: 0개**

## 7. 현재 수집 방식

| 요소 | 상세 |
|------|------|
| **브라우저** | Chrome (C:\Program Files\Google\Chrome\Application\chrome.exe) |
| **자동화 도구** | Playwright MCP (npx @playwright/mcp@latest --extension) |
| **세션 관리** | 사용자가 Chrome에서 직접 로그인한 세션 재사용 (자격증명 저장 안 함) |
| **접근 경로** | 목록 페이지 → 테이블 파싱 → articleId 추출 → 상세 페이지 iframe(cafe_main) 진입 → DOM 추출 |
| **대기 전략** | 페이지 로드 후 3-5초 대기 (동적 렌더링 고려) |
| **속도 제한** | 순차 요청, 요청당 2-5초 간격 |

## 8. 마지막 실패

**에러**: `504 Upstream idle timeout exceeded`

**발생 시점**: 
- PoC 완료 후 FAST COLLECTION 모드 전환 중
- 게시판 목록 페이지(https://cafe.naver.com/f-e/cafes/31312978/menus/16?viewType=L) 로드 후
- 테이블 데이터 파싱 및 페이지네이션 확인 단계에서 발생

**원인 추측**: Playwright MCP 서버 측 타임아웃 (긴 대기 후 응답 지연)

**영향**: 
- 추가 게시글 수집 중단
- 이미 저장된 PoC 데이터(article_5994.json/.md)는 손실 없음

## 9. 아직 하지 못한 작업

- [ ] 초보가이드 게시판 전체 페이지 순회 (1페이지 ~ 마지막 페이지)
- [ ] 고정 공지 포함 전체 게시글 수집 (약 100-200개 예상)
- [ ] 각 게시글 본문/댓글/첨부파일 메타데이터 추출
- [ ] `index.json` / `collection_state.json` 체크포인트 생성
- [ ] 중단 후 재개 로직 구현
- [ ] CAPTCHA/세션만료/권한오류 발생 시 중단 로직
- [ ] PHASE 3: 이미지/첨부파일 실제 다운로드
- [ ] 전자책 원문과 카페 데이터 합성 (별도 PHASE)

## 10. 개인정보 처리

| 구분 | 처리 |
|------|------|
| **GitHub에 포함한 것** | PoC 결과물 (article_5994.json/.md) - 닉네임/작성일/제목/본문 텍스트만 포함, 실명/전화번호/계좌번호 등 민감정보 없음 |
| **의도적으로 제외한 것** | `cafe/raw/` 디렉터리 전체 - 향후 대량 수집 시 개인정보(실명, 전화번호, 계좌번호, 대출정보 등) 포함 가능성 |
| **.gitignore 처리** | `docs/newstart/.gitignore`에 `cafe/raw/` 추가하여 Git 추적 제외 |

**확인 사항**: PoC 원문(article_5994.json)에서 개인정보는 이미 네이버 측에서 `000`으로 마스킹되어 있음 (이름, 계좌번호, 예금주명). 하지만 향후 수집될 다른 글에는 마스킹되지 않은 정보가 있을 수 있으므로 raw 데이터는 GitHub에 올리지 않음.

## 11. 학원 PC에서 재개 방법

### 사전 조건
- Git 설치됨
- Node.js 18+ 설치됨 (Playwright MCP 실행용)
- Chrome 브라우저 설치됨
- 네이버 계정으로 Chrome에서 로그인済み

### 실행 명령어

```bash
# 1. Repository clone 또는 fetch
cd C:\Users\sxxra\feel2real-buildit
git fetch origin

# 2. Handoff branch로 이동
git checkout newstart/cafe-research-handoff-20260919
git pull --ff-only

# 3. 저장된 자료 확인
cat docs/newstart/research/article_5994.json
cat docs/newstart/research/NEWSTART_HANDOFF_2026-09-19_HOME_TO_ACADEMY.md

# 4. Playwright MCP 서버 시작 (별도 터미널)
npx -y @playwright/mcp@latest --extension

# 5. 브라우저에서 네이버 카페 로그인 확인
# Chrome에서 https://cafe.naver.com/newhopero 접속 → 로그인 상태 확인

# 6. 수집 재개 (새 터미널에서 OpenCode 실행 후)
# - FAST COLLECTION 모드로 초보가이드 게시판 전체 수집 계속
# - docs/newstart/cafe/raw/초보가이드/articles/ 에 JSON 저장
# - 체크포인트(index.json, collection_state.json) 활용하여 중단 지점부터 재개
```

### 로컬 raw 데이터 복원 (학원 PC에서)
GitHub에 저장하지 않은 raw 데이터가 필요한 경우:
```bash
# 집 PC에서 USB/클라우드로 복사해 온 경우
# 경로: C:\Users\sxxra\docs\newstart\cafe\raw\초보가이드\articles\
```

## 12. NEXT ACTION (학원 PC에서)

1. **Repository 동기화**: 위 11번 명령어로 handoff branch checkout
2. **자료 검증**: article_5994.json 내용 확인, 스키마 이해
3. **브라우저 세션 준비**: Chrome에서 네이버 카페 로그인 상태 확인
4. **Playwright MCP 연결**: `npx -y @playwright/mcp@latest --extension` 실행 후 OpenCode에서 연결
5. **FAST COLLECTION 재개**: 
   - 게시판 목록 1페이지부터 순회
   - 이미 수집된 5994는 SKIP (파일 존재 확인 후)
   - 각 글 순차 방문 → DOM 추출 → JSON 저장
   - 10개 단위로 중간 검증 (본문/제목/작성자/댓글/첨부파일 정상)
   - CAPTCHA/세션만료/권한오류 발생 시 즉시 중단 → 체크포인트 저장

---

**작성일**: 2026-09-19
**작성자**: Home PC (sxxra)
**대상**: Academy PC 이어받기