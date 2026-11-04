# Cafe thanks member → NewStart roster

Verified `감사멤버` observations from Cafe 31312978 are appended to the existing
`newstart_thanks_roster` table. Existing history and linked accounts are preserved.
A stable Studio member tag prevents duplicate registration. A nickname/ID-prefix
collision is held for review; a masked ID never grants an account entitlement.

## Deployment state (2026-10-31)

- Sandboxed server Chromium installed; private ec2-user profile.
- Six local and server tests pass; production roster columns checked read-only.
- `f2r-cafe-roster-sync.timer` active every five minutes; empty queue verified.
- **Studio observation producer and grade-change executor are NOT active.**
  The dedicated server browser still needs manager sign-in and Cafe identity
  verification. No customer roster insertion or continuous end-to-end sync has
  been verified. The timer alone does not mean the Cafe connection is complete.

## Files and credentials

Install Python files under `/opt/f2r-cafe-runtime`; use the dedicated Python 3.11
Playwright virtualenv. Install the existing `scripts/operations/a1-precheck.py`
credential adapter at `/opt/f2r-ebook/a1-precheck.py` (root 0600).
The DB adapter reads `/etc/f2r/env`, using a private RAM-backed MySQL client file.
No credentials, browser profile, screenshots, real observations, ebook or email
body belong in this public repository.

The root consumer reads verified JSON observations from
`/home/ec2-user/f2r-cafe-browser/roster-outbox` (ec2-user 0700), archives successful
receipts under `/var/lib/f2r-ebook/roster-synced` (root 0700), and leaves failed
items in place. Status exposes counts only. Systemd restricts writable paths.

An observation requires cafe ID, the actual Studio 12-character member tag,
observed nickname and ID prefix, grade `감사멤버`, member level 150, a verification
actor and a timezone-aware observation timestamp less than 24 hours old. An
optional grade-change date must come from the page. Never infer identity from
buyer name, nickname or email alone. A stale observation requires fresh checking.

## Remaining activation work

1. Complete user-controlled manager authentication in the dedicated profile.
2. Inspect the actual Studio member rows and grade dialog; implement and verify
   the visible UI producer, including manually upgraded members, pagination,
   uniqueness checks, session-expiry holds and safe order identity matching.
3. Validate an actual, operator-approved member observation and its resulting
   roster row. Do not create a synthetic production member to test the pipeline.
4. Connect verified grade receipts to order fulfillment; retain payment/claim
   rechecks, duplicate-send protection and delivery-state audit.

Run tests: `python -m unittest discover -s scripts/cafe-runtime -p 'test_*.py'`.
Stop consumer: `systemctl disable --now f2r-cafe-roster-sync.timer`.

## 2026-10-31 후속: 주문 기반 연결 활성화

위 초기 구축 기록 이후 전용 매니저 로그인·운영진 본인 인증·세션 재사용을 실제 확인했다.
`order_member.py`는 전체 아이디 검색 완료 후 결과 1명과 고유 태그를 검증하고,
감사멤버 미만 등급만 UI에서 변경·재조회한다. 독립적인 전체 카페 일괄 수집기는
여전히 비활성이다. 신규 주문은 ebook 연결기가 회원 확인·명부 동기화·발송을 조정한다.
운영 명부의 utf8mb4_unicode_ci 비교 충돌을 수정했으며, 운영자가 확인한 기존 수동
명부 1건은 비공개 이전 기록을 보존하고 실제 고유 태그에 연결했다. 반복 동기화는
같은 명부 ID를 반환했다. 실제 고객 정보와 세션 파일은 저장소에 포함하지 않는다.

새로운 유료 주문의 전체 실발송은 아직 관측하지 않았다. 상세 조건과 예외는
`scripts/ebook/INTEGRATION.md`를 따른다.
