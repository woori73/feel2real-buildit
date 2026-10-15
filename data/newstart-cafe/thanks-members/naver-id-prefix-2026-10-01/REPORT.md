# NewStart 감사멤버 네이버 ID 접두부 보완 수집 보고 (2026-10-01)

## 1. 목적

기존 503명 감사멤버 명부(`roster-2026-10-01/`)에는 네이버 로그인 ID가 저장되어 있지 않았다.
본 보완 수집은 **기존 503명 전체를 재수집하지 않고**, 화면에 노출되는 네이버 ID **접두부**만
추가로 수집해 `cafeMemberTag` 기준으로 기존 명부에 연결한다.

## 2. 수집 결과

```
ROSTER           = 503
PREFIX CAPTURED  = 503
TAG MATCHED      = 503
UNMATCHED        = 0
DUP PREFIX TAGS  = 0
ROSTER w/o PREFIX= 0
```

- 소스: Cafe Studio 감사멤버 관리 화면 내부 API
  `apis.cafe.naver.com/cafe-manage/cafe-manage/v1/cafes/31312978/members/list?...&memberLevel=150`
- 페이지 1·2·3 = 200 + 200 + 103 = 503.
- `cafeMemberTag`(memberTag)를 확정 연결키로 사용, 기존 503명과 1:1 대응.

## 3. 중요 — 접두부는 전체 네이버 ID가 아니다

- Cafe Studio 는 네이버 로그인 ID 를 마스킹해 노출한다. API 필드 `maskedMemberId` 예: `ssgw****`.
- 화면/DOM 및 API 어디에도 **전체 네이버 ID 는 존재하지 않는다.**
- `naverIdPrefix` 는 `maskedMemberId` 에서 후행 `*` 만 제거한, 화면에 실제 노출되는 접두부다.
  - 예: `ssgw****` → `ssgw`, `to****` → `to` (길이 2 또는 4).
- 같은 접두부가 여러 네이버 ID 에 존재할 수 있다(503명 중 distinct prefix = 477 → 26건 중복).
  따라서 **접두부 단독으로는 확정 식별자가 아니며, 이 값만으로 `THANKS_MEMBER` 를 자동 부여하지 않는다.**
- 네이버 OAuth 가 반환하는 `response.id`(`user_social_accounts.provider_user_id`)는 애플리케이션별
  stable id 이고 네이버 로그인 ID 가 아니다. 접두부와도 서로 다른 값이다.

## 4. 파일 구성

```
naver-id-prefix-2026-10-01/
└── naver-id-prefix.json   (snapshotDate, capturedAt, sourceUrl, 503 records)
```

record 필드: `cafeMemberTag`, `nickname`, `maskedMemberId`, `naverIdPrefix`.

## 5. 운영 DB 반영

- `V1016__add_naver_id_prefix_to_thanks_roster.sql` — `newstart_thanks_roster` 에 nullable
  `naver_id_prefix` 컬럼 추가 후, `(snapshot_date, cafe_member_tag)` 로 503건 갱신.
- `V1015` / RAW 503 / `roster.json` 은 수정하지 않는다.
- 기존 원칙대로 이 단계에서 `THANKS_MEMBER` grant 는 일절 부여하지 않는다.
