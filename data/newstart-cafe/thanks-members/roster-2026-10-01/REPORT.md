# NewStart 오픈 전 감사멤버 전체 명부 수집 보고 (2026-10-01)

## 1. 최종 보고

```
CAFE THANKS MEMBERS RAW = 503
UNIQUE THANKS MEMBERS   = 503
TOTAL PAGES             = 3
DUPLICATES              = 0

SOURCE                  = https://studio.cafe.naver.com/@31312978/member/list?page=1&perPage=200&activityStopFilter=ALL&memberLevel=150
COLLECTION STATUS       = COMPLETE

OPERATING DB SAVED      = NO
DB TABLE                = (해당 없음 — 감사회원 사전 명부를 담는 기존 테이블/승인된 적재 경로 없음)
DB ROWS                 = 0

CAFE IDENTIFIER         = cafeMemberTag (Cafe Studio 멤버 태그, 예: gn5wjzdu86bk)
NAVER LOGIN IDENTIFIER  = user_social_accounts.provider_user_id (네이버 result.id, stable identity)
DIRECT MATCH POSSIBLE   = NO
EXISTING CROSSWALK      = NO
AUTO-GRANT READY        = NO
BLOCKER                 = Cafe 멤버 태그 ↔ 네이버 OAuth subject 를 확정 연결하는 식별키/crosswalk 부재,
                          그리고 오픈 전 감사회원 명부를 보존할 승인된 운영 DB 대상 테이블/적재 경로 부재

PREVIOUS QODER LIST FOUND = NO
PREVIOUS COUNT          =
CURRENT COUNT           = 503
DIFF CONFIRMED          = NO

SOURCE FILE             = data/newstart-cafe/thanks-members/roster-2026-10-01/roster.json
REMOTE BRANCH           = data/newstart-thanks-roster-20261001
```

기존 `increments/2026-10-01.json`(21건 PARTIAL)은 그대로 보존했으며, 신규회원 21명으로 간주하지 않았다.
21건 전부가 이번 503명 명부 안에 포함된다(중복 21, 명부에만 있는 추가 0). 따라서 `503 + 21` 같은 합산은 하지 않는다.

## 2. 수집 방법

- 사용자가 로그인해 둔 Naver Cafe Studio 세션에서 `memberLevel=150`(감사멤버) 필터를 유지한 채 페이지 1→3을 순회했다.
- 각 페이지에서 화면에 보이는 행(`div[class*="group/frozen"]`)의 텍스트와 `멤버 태그`(cafeMemberTag)만 추출했다.
- 마스킹된 네이버 로그인 ID(예: `ssgw****`)는 추출하지 않고 저장하지 않았다.
- 페이지당 50행 단위로 즉시 `raw/` 아래 JSON 청크로 저장했고(부분 수집이어도 보존), 이후 `roster.json`으로 합쳤다.
- 페이지 4는 존재하지 않는다(페이지네이션은 1,2,3).

### 페이지별 원본 행 수

| page | perPage | rows |
|------|---------|------|
| 1 | 200 | 200 |
| 2 | 200 | 200 |
| 3 | 200 | 103 |

합계 RAW = 503, 고유 cafeMemberTag = 503, 중복 0, 태그 없는 행 0.

- 첫 회원: `ssg` (`gn5wjzdu86bk`)
- 마지막 회원: `현이와덕이` (`kxd25pyuzt03`)

## 3. 보존된 원본 필드

`raw/page-XX-chunk-YY.json` 각 청크와 `roster.json`은 아래 화면 필드를 그대로 보존한다.

`nickname`, `grade`, `cafeMemberTag`, `activityStop`, `nicknameChanged`, `memo`,
`gradeChangedDate`, `autoLevelExcluded`, `accumulatedReward`, `lastVisitDate`,
`joinedDate`, `visitCount`, `postCount`, `commentCount`, `likeCount`

주의:

- `gradeChangedDate` 는 **가장 최근 등급 상승/강등일**이며, 감사멤버가 된 최초 날짜가 아니다. 신규 감사멤버 판정 근거로 쓰지 않는다.
- `cafeMemberTag` 는 Cafe 화면 식별자다. 네이버 로그인 ID와 동일하지 않다.

## 4. 파일 구성

```
roster-2026-10-01/
├── raw/
│   ├── page-01-chunk-01.json ... page-01-chunk-04.json   (각 50행)
│   ├── page-02-chunk-01.json ... page-02-chunk-04.json   (각 50행)
│   └── page-03-chunk-01.json, page-03-chunk-02.json (50행), page-03-chunk-03.json (3행)
├── roster.json     (합본 명부 + 통계 + 캡처 메타)
├── manifest.json   (건수/중복/청크 목록/해시)
└── REPORT.md       (이 문서)
```

## 5. 운영 DB 저장 상태

`OPERATING DB SAVED = NO`.

- 기존 `newstart_member_grants`(`V1011`)는 `users.user_id` FK에 `capability`(예: `THANKS_MEMBER`)를 붙이는 **권한 부여 테이블**이다. 감사회원 사전 명부(Cafe 태그 기준)를 담는 테이블이 아니다.
- 감사회원 사전 명부를 보존할 기존 테이블/승인된 적재 경로를 찾지 못했다. 새 스키마 생성이나 raw SQL 적재는 이번 작업 범위 밖으로 남겨 두었다.
- 오픈 전이므로 존재하지 않는 `users.user_id`를 만들거나 실제 회원에게 grant를 부여하지 않았다.

## 6. 오픈 후 권한 연결 준비 상태

- NewStart 는 네이버 OAuth 로그인을 사용하며, 회원 식별은 `user_social_accounts`(`provider=NAVER`, `provider_user_id` = 네이버 `result.id`)로만 한다(이메일 자동 병합 없음).
- Cafe Studio 에서 확보 가능한 식별자는 `cafeMemberTag` 뿐이고, 네이버 `result.id` 와의 확정 매핑을 제공하는 컬럼/테이블/crosswalk 는 저장소에 없다.
- 닉네임(`display_nickname` 또는 카페 닉네임)만으로는 매칭하지 않는다.

따라서 오픈 후 "네이버 로그인 → 감사멤버 명부 확정 일치 → THANKS_MEMBER 부여" 를 자동화하려면, Cafe 멤버 태그와 네이버 OAuth subject 를 연결할 확정 키(운영자가 신뢰할 수 있는 결합 절차 포함)가 먼저 정의되어야 한다.

## 7. 기존 21건 increment 비교

`increments/2026-10-01.json`(PARTIAL, 21건)은 수정/삭제하지 않았다.

- increment 21건 ∩ 이번 명부 503명 = 21건
- increment 에만 있고 이번 명부에 없는 회원 = 0건
- 이번 명부 인원에 +21 하지 않는다.
