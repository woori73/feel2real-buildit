# F2R 정형업무 과금 — Phase 1(안내문) 빌드 스펙 r1

2026-11-28 KST · r2 설계 → 구현 인계용 · 검수: 차원연산4(PASS) · 대상: 안내문 미터링 **관찰 전용(집행 없음)**

## 0. 범위 (딱 이것만)
- **안내문 1종**만. 저장당 **사용량 +1 관찰**. 한도 집행·예약·공급자 원가는 **Phase 2~4로 미룸**.
- 목표: "원장이 Claude에서 '학부모 안내문 만들어 F2R에 저장'을 하면, 초안 1개 저장 + 이번 달 사용량 1건이 정확히 올라간다. 재시도·중복에도 중복 차감 없음."

## 1. 스키마 (최소 2테이블, Flyway 신규 V — 현재 `feel2real_codi_staging` 스키마 버전 1144 → 다음 순차번호, WORKFLOW §4 준수)
```
codi_usage_counter        -- 계약·기간·업무별 카운터
  id PK
  academy_id        NOT NULL      -- Phase1 계약 대용(= 귀속 학원)
  period_start DATE NOT NULL       -- KST 월 경계
  period_end   DATE NOT NULL
  task_type    VARCHAR(32) NOT NULL -- 'PARENT_NOTICE'
  confirmed_qty BIGINT NOT NULL DEFAULT 0  -- >=0
  reserved_qty  BIGINT NOT NULL DEFAULT 0  -- Phase1 미사용(0 고정)
  plan_version  VARCHAR(32) NULL
  UNIQUE (academy_id, period_start, task_type)

codi_task_ledger          -- 업무 원장(감사·멱등)
  id PK
  task_id      CHAR(36) NOT NULL       -- UUID
  academy_id   NOT NULL
  task_type    VARCHAR(32) NOT NULL
  channel      VARCHAR(16) NOT NULL     -- 'MCP' | 'DASHBOARD'
  request_key  VARCHAR(100) NOT NULL    -- 안내문 결정적 id(= ParentNotice id)
  result_kind  VARCHAR(32) NOT NULL     -- 'PARENT_NOTICE_DRAFT'
  result_id    VARCHAR(100) NOT NULL    -- 저장된 초안 id
  qty          INT NOT NULL DEFAULT 1
  status       VARCHAR(16) NOT NULL     -- 'CONFIRMED'
  created_at   DATETIME NOT NULL
  UNIQUE (request_key)                  -- 같은 요청 중복 기록 차단
  UNIQUE (result_id)                    -- 결과 중복 차단
```
> 민감정보(문구 원문·학생 이름·사진) **저장 금지**. 식별은 id·키만.

## 2. 계측 훅 — `ParentNoticeService.save` (검수로 확인된 코드 기준)
현재 `save`는 **신규삽입·멱등재저장을 반환값으로 구분하지 않음**. 따라서:
1. `insertDraft(...)`의 **영향행수(=신규 1 / 기존 0)로 "최초 삽입" 판별**. (insertDraft가 dup PK에서 예외면, 예외를 잡아 "기존=0건"으로 처리하고 기존 row 반환.)
2. **최초 삽입일 때만**, **같은 `@Transactional` 안에서**:
   - `codi_task_ledger` 1행 insert (`task_id`=새 UUID, `academy_id`=아래 §3, `task_type='PARENT_NOTICE'`, `channel`=`source`(MCP/DASHBOARD), `request_key`=초안 id, `result_id`=초안 id, `qty=1`, `status='CONFIRMED'`).
   - `codi_usage_counter`의 (academy_id, 현재 KST월, 'PARENT_NOTICE') 행을 **UPSERT 후 `confirmed_qty += 1`**.
   - `codi_task_ledger`의 UNIQUE(request_key/result_id) 충돌 시 **카운터 증가 생략**(멱등 2중 안전망).
3. 멱등 재저장(영향행수 0)·내용충돌 409·목록조회·카톡공유 = **카운터 불변**.

## 3. 귀속(academy_id)
- `requireOwner`가 이미 `academy.context(principal)`로 ACADEMY_OWNER를 확인 → 그 **academyId**를 귀속으로 사용.
- 다학원 원장 등 **귀속 모호 시**: Phase1은 관찰 전용이므로 **`academy_id`를 기본학원으로 기록하되 `ambiguous` 플래그/로그** 남김(집행은 Phase 3 전에 학원 선택 UX 선행 — r2 §3.1).

## 4. 노출(관찰용)
- 원장 화면/대시보드에 **"이번 달 안내문 n건"** 1줄 표시(기존 안내문 목록 화면 재사용). 집행·차단 **없음**.

## 5. 검수 기준 (차원연산4) — 전부 통과해야 Phase 1 완료
1. 같은 `requestId`로 **MCP 2회 저장** → **초안 1개 · counter +1** (중복 차감 없음).
2. **같은 requestId·다른 내용** → **409 · counter 불변**.
3. **DASHBOARD vs MCP** 채널이 `codi_task_ledger.channel`에 정확히 구분 기록.
4. **저장 실패**(검증 에러, 권한 403 등) → **counter·ledger 불변**(원자성).
5. **귀속 정확**: counter/ledger의 `academy_id` = 원장의 학원. 다학원이면 ambiguous 처리.
6. **동시성**: 같은 요청 2건 동시 전송에도 ledger UNIQUE로 **정확히 1건·counter +1**.
7. **월 경계**: period_start/end가 KST 월 경계로 설정, 이전 달 카운터 불변.

주: given/when/then 단위테스트 필수(CLAUDE.md). Phase 1은 **anthropic.enabled 무관**(안내문은 F2R 서버가 모델을 호출하지 않는 저장 업무 — 공급자 원가는 Phase 4에서 `ClaudeChatService` 사용량 캡처로 추가).

## 6. 다음 단계
- Phase 2: 5업무 대조(r2 §5).  Phase 3: 한도 집행(예약→확정).  Phase 4: F2R 서버 키 호출 + 공급자 원가·마진(`ClaudeChatService.chat`에 `response.usage()` 캡처 추가 — 검수로 확인된 미구현 지점).

## 분담
- **구현**: Codex(또는 요청 시 연산4). 마이그레이션 번호·Entity·DOMAIN.md 동시 수정(스키마 3동기).
- **검수**: 연산4 — 위 §5 7항목 PASS/NEEDS_CHANGES.
