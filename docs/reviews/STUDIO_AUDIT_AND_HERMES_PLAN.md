# 제작 협업실 완성도 감사 및 Hermes 연동 설계

기준: main `ae5d1760`(#854 `83c65ffd` → #853 `657a0135` → #855 `ae5d1760` 병합 후). 운영 미배포.
검수 방식: 코드 정독(`StudioController/Service`, `agent_studio.js`, `templates/studio`, `domain/mcp/*`, V1095) + 저장소에 있는 캡처·시험 결과. **이 감사에서 실제 ChatGPT·Claude 연결, 실제 EP01 등록, 운영 접속은 하지 않았다.**

## 1. 제작 협업실 기능 감사

| # | 항목 | 판정 | 근거(코드) / 부족한 점 |
|---|---|---|---|
| 1 | 프로젝트별 공동 자료 보관소 | **PARTIAL** | 파일(pdf/html/json/md/zip/png/mp4, ≤100MiB)·SHA-256·버전 보관은 동작. 프로젝트가 `ai-agent-part1` 1개로 **하드코딩**(`StudioService.PROJECT`) — 다른 작품/프로젝트 생성 불가 |
| 2 | 사람 팀원 접근 권한 | **PARTIAL** | VIEWER/EDITOR/APPROVER, ADMIN은 자동 APPROVER, 무권한 403. 팀원 추가는 ADMIN이 **숫자 user_id를 직접 입력**(초대·이메일 검색·알림 없음) |
| 3 | 콘티 68컷 가독성 열람 | **PARTIAL (약함)** | 번호 1~68 버튼 격자 + 컷 상세(제목·본문·대사·연출 텍스트). **컷 이미지+설명 보드 없음**, 이전/다음 컷 이동·키보드 이동 없음 |
| 4 | 이미지 갤러리·확대·비교 | **MISSING** | 이미지 미리보기는 '원본 파일' 탭의 단일 `<img>`뿐. 갤러리·썸네일·확대·원본 대 수정본 이미지 비교·캐릭터 갤러리 없음. 버전 비교는 **텍스트 필드 좌우 나열**만 |
| 5 | HTML·MD·PDF·ZIP 미리보기 | **PARTIAL** | PDF=서버 텍스트 추출(100쪽·20만 자), ZIP=목록만, MD/HTML/JSON=**원문 `<pre>` 표시(렌더링 안 함, 안전 설계)**. 읽기 좋은 HTML 렌더·PDF 페이지 뷰어 없음 |
| 6 | AI 산출물 업로드·버전·SHA | **PARTIAL** | 업로드·SHA·새 버전·복원(새 revision 생성, 기존 불변)은 PASS. 그러나 **AI가 직접 올리는 경로 없음**(MCP는 읽기 전용). 'AI 제안' 출처는 사람이 폼에서 선택하는 라벨이며 AI 모델·작성자·근거 필드 없음 |
| 7 | 원본·후보·승인 정본 구분 | **PASS** | origin(ORIGINAL/AI_PROPOSAL/USER_EDIT) 배지 + `studio_items.approved_id` 포인터만 변경, 승인·반려 이력. 승인본 vs 미승인 화면 구분 확인 |
| 8 | 사람·AI 댓글 및 검수 | **PARTIAL** | 승인/반려 + 메모 2000자(이력에 기록)만 있음. **컷별 댓글 스레드·수정 요청·AI 검수 의견 없음** |
| 9 | 프로젝트별 작업 기억 | **MISSING** | 마지막 결정·열린 이슈·다음 작업을 저장하는 테이블·화면·MCP 도구 없음. '작업 이력'은 이벤트 로그일 뿐 |
| 10 | 자동 인계·승인 대기함 | **MISSING** | 승인 대기함·담당 AI 지정·인계 큐 없음 (`mcp_pending`은 OAuth 동의용). 승인 대기는 목록에서 사람이 '미승인'을 찾아야 함 |

보안·불변성(승인 포인터만 변경, 원본 SHA 불변, 고아 파일 정리, 읽기 전용 MCP)은 이전 검수에서 PASS.
PC 1440/모바일 390 캡처는 저장소에 있으나 **실제 EP01 자료가 아닌 합성 자료**로 촬영된 것이다.

## 2. ChatGPT·Claude 커넥터 (#855 MCP)

MCP 도구: `list_catalog`, `read_<서비스>`, `search`, `fetch` (모두 읽기 전용, 쓰기·업로드·승인 도구 없음). 파일 원본은 JSON 안의 **base64 문자열 청크(≤64KiB)** + chunkSha256으로 반환.

| 항목 | 판정 | 설명 |
|---|---|---|
| ChatGPT에서 실제 제작실 자료 조회 | **NOT TESTED** | 공개 HTTPS·계정 필요. 서버 측 OAuth/프로토콜은 합성 시험 통과 |
| Claude에서 동일 자료 조회 | **NOT TESTED** | 동일 |
| 이미지 원본 AI 판독 | **MISSING(설계상)** | 현재 이미지는 base64 **텍스트**로만 전달 → 모델이 그림을 보는 것이 아님. MCP `image` 콘텐츠 블록(≤수십~100KB 파생본)으로 바꿔야 함. 이마저 Claude는 연결된 이슈 보고(호출 턴에 이미지가 모델 컨텍스트에 안 들어감, anthropics/claude-ai-mcp #731·#643)가 있고, ChatGPT는 커스텀 MCP의 이미지 블록 지원 문서를 찾지 못함 → **둘 다 실측 전까지 미확정** |
| 파일명·메타데이터 조회 vs 이미지 판독 | 구분 필요 | 메타(파일명·SHA·크기·버전·승인 상태)·텍스트 자료는 현재도 조회 가능(미실측). 그림 내용 판독은 별개 기능이며 위와 같이 미구현·미검증 |
| 기존 구독 포함·별도 과금 | 부분 확인 | MCP 서버는 AI API를 호출하지 않음(서버 비용 없음). Claude 커스텀 커넥터: Free(1개)·Pro·Max·Team·Enterprise 안내([Claude 도움말](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp)). ChatGPT: 자료마다 상충 — Business/Enterprise/Edu는 공식, **Plus/Pro 개인 계정의 커스텀 MCP 허용 여부는 확인 못함**(제3자 글이 서로 충돌). OpenAI 공식 도움말은 접속이 막혀 직접 확인하지 못함 → **사용자가 ChatGPT 앱의 커넥터 추가 화면에서 직접 확인 필요** |
| 별도 동의 연결로 후보 첨부·제안 등록 | **MISSING** | 쓰기 scope·사용자 확인 UI·서버 엔드포인트 모두 없음. 읽기 전용 MCP와 **분리된 별도 연결**로 설계해야 함 |

## 3. Hermes 연동 설계(안, 설치·구현 안 함)

**먼저 확인된 제약(조사, 공식 문서 직접 열람은 네트워크 차단으로 실패, 검색 요약 기준):**
- Hermes는 ChatGPT/Codex OAuth, Claude OAuth, Nous Portal OAuth, 원격 MCP OAuth를 지원한다고 문서에 기재([Hermes 제공자 문서](https://hermes-agent.nousresearch.com/docs/integrations/providers) 검색 요약).
- **Claude 구독을 Hermes 같은 제3자 도구에 쓰는 경로는 제한적**: Hermes 문서 요약상 Claude Max OAuth는 *구매한 추가 사용 크레딧이 있어야* 작동하고 기본 Max 한도는 쓰지 않으며 Pro는 미지원. Anthropic 정책은 2026년 중 여러 차례 바뀌었고 자료들이 서로 엇갈림([VentureBeat](https://venturebeat.com/technology/anthropic-reinstates-openclaw-and-third-party-agent-usage-on-claude-subscriptions-with-a-catch), [Hermes+Claude Max 분석](https://vincentvandeth.nl/blog/hermes-agent-claude-subscription-oauth)). → **"기존 Claude Max 구독만으로 추가 과금 없이 Hermes가 Claude 검수 수행"은 현재 보장되지 않는다(NOT VERIFIED, 위험).**
- ChatGPT: OpenAI는 Plus/Pro 구독으로 제3자 도구를 쓰는 'Sign in with ChatGPT'를 제공(플랜 한도 내, 앱별 주간 상한)([The New Stack](https://thenewstack.io/sign-in-with-chatgpt/)). 서버(헤드리스) 운영 지원 여부는 확인 못함.

**권장 구조:**
```
운영 EC2(기존 인스턴스, 신규 AWS 리소스 없음 — 별도 승인 후)
 ├ 기존 f2r 앱(#855 제작실: 정본 DB·승인·권한·SHA)  ← 시스템 오브 레코드
 └ Hermes(별도 사용자·프로세스·읽기 전용 접근, 메모리 상한)
     ├ 개발(Codex): ChatGPT 구독 로그인 — 개발 PR 작성만
     └ 검수(Claude): 구독 한도 내 경로가 확정될 때까지 '사람이 Claude 앱/Code로 실행'
```
역할 분담(제안):
- **#855 제작실 담당(정본):** 파일·SHA·버전·승인 포인터·권한·감사 이력·승인 대기함·프로젝트 작업 기억(신규 구현 필요). 승인은 사람만.
- **Hermes 담당(보조):** 에이전트 실행·스케줄·세션 메모, 제작실 MCP를 읽기 전용으로 조회해 요약·초안 생성, 결과는 '후보 제안'으로만 제출. 정본·승인 상태를 Hermes 저장소에 두지 않는다.
- 개발→검수→수정→승인 흐름: Codex 개발(구독) → PR → Claude 검수(구독 한도 내 가능 경로 확인 필요) → 수정 → **사용자 승인**. 자동 병합·배포 없음.

미검증/남은 질문: Hermes 설치 방식·메모리 요구량·EC2 사양 적합성, Claude 구독 사용 허용 범위(이용약관·추가 사용 크레딧), OpenAI 헤드리스 로그인. 모두 **운영 EC2 설치 전 별도 승인 + 격리 시험 필요**.

## 4. 최소 완성 경로(사용자가 파일을 수동으로 옮기지 않기)

목표: AI 산출물이 **업로드 없이** 제작실에 후보로 들어오고, 사람은 화면에서 보고 승인만.
1. **(P0) 컷 보드 UI:** 컷 이미지+설명 카드, 확대 모달, 이전/다음, 원본 vs 수정본 이미지 비교, 승인본 배지. 이미지 썸네일 파생본 생성(서버, 저장소 읽기만).
2. **(P0) 읽기용 렌더:** MD/HTML 안전 렌더(sandbox iframe 또는 sanitize), PDF 뷰어.
3. **(P0) MCP 이미지 판독:** `image` 블록으로 ≤100KB 파생본 반환 → **ChatGPT·Claude 각각 실측**(실패 시 서명된 단기 URL 대안).
4. **(P1) 후보 제안 쓰기 경로:** 별도 OAuth scope(`studio:propose`) + 사용자 확인 + AI_PROPOSAL 전용·승인 불가·SHA/모델/작성 AI/근거 필드·한도.
5. **(P1) 컷별 댓글·수정 요청, 승인 대기함, 프로젝트 작업 기억(마지막 결정·열린 이슈·다음 작업).**
6. **(P2) 다중 프로젝트·초대(이메일)·알림.**
7. 위 1~3 완료 후 격리 HTTPS에서 실제 연결·EP01 시험(`ISOLATED_HTTPS_CONNECTION_RUNBOOK.md`).

## 5. 운영 보호 준수

운영 SHA·DB·ACTIVE 자료·MCP·AWS 리소스 변경 없음, Hermes 설치 없음, 유료 모델 API 호출 없음(웹 검색·문서 열람만).
