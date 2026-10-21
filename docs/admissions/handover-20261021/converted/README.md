# 변환 시드 v3.2 — 연결 관계와 운영 관리자 실행 절차 (2026-10-21)

## 1. 산출물과 원본 7개의 관계

| 파일 | 역할 |
|---|---|
| `seed-v3.2-ops.json` | `AdmissionsDataLoader.loadSeed` 형식. 대학 10 · 공식자료 20 · 전형 116 · 일정 72 · 학과 0 |
| `convert_seed.py` | 원본 → v3.2 재현 스크립트 (`python3 convert_seed.py <handover-20261021 폴더> <출력>`, 결과가 동일함을 확인) |

원본 연결: 대학·공식자료 20건 ← `draft-seed-2027-v3.1-20261021.json` / 전형·일정 ← `recruitment-2027-extracted.json`(13) + `…addendum-20261021.json`(서강대 정시 부분본은 제외) + `…addendum2-20261021.json`(5, 서강대 정시 완전본 포함) / `evidence-pages.json`·`changes2028.json`은 이 시드에 넣지 않았다(아래 §5).

v3.1 대비 정정: 서강대 수시 자료명 "논술가이드북(모집요강 전체 아님)", 고려대 수시 "(세종캠퍼스)" 표기(자료명·전형명·일정 구분), 중앙대 수시 "사설 미러본·공식 원본 미확보"(내용 행 없음), 경희대 정시 링크를 기관 주소로, 경희대 정시 수능최저는 "확인되지 않음(원문 p.12 대조 필요)".

## 2. 기존 API 조사 결과 (코드 기준, `AdmissionsAdminController`)

| 항목 | 결과 |
|---|---|
| 인증 | `/api/admin/**` 전체 ADMIN 전용(`SecurityConfig`), BuildIt 로그인 쿠키. CSRF 비활성 |
| `POST /api/admin/admissions/load/seed` | body `{"seedJson":"<시드 JSON 문자열>"}`. 구조·대학코드 오류는 전체 롤백(422), 미지원 학년도 행만 건너뜀. 전부 DRAFT로 적재 |
| 재실행 안전성 | 대학명 / 자료 제목 / 대학+학년도+전형명 / 대학+학년도+일정 제목+날짜로 중복 방지. 같은 시드를 다시 넣으면 0건 추가(H2 검증) |
| 공식자료 승인 | `GET /materials`, `POST /materials/{id}/review` `{"action":"review"}` → `{"action":"approve"}` 두 번(DRAFT→REVIEW→ACTIVE). **하위 전형·일정 행은 함께 바뀌지 않는다** |
| 전형·일정·학과 승인 | **개별·일괄 승인 API 없음** |
| 검색 근거 조건 | 전형·일정·학과 행이 ACTIVE이고 출처 자료도 같은 학년도 ACTIVE여야 근거가 된다 |
| `POST /index/reload` | ADMIN, 임베딩 테이블을 다시 읽는 작업. 운영은 임베딩 서비스 미설정·ACTIVE 지식 0건이라 사실상 변화 없음 |
| `POST /load/knowledge-base` | `question_types`만 읽어 학년도 2027·확정·GUIDE 역할로 고정 적재. 사실 지식·2028 자료는 등록 불가 |

H2 실검증(실제 로더·검색 코드, v3.2 시드): 공식자료 20건만 ACTIVE → "수시 원서접수 일정" 근거 **0건**, 서울대 수시 전형 비교 근거 **0건** / 일정 72건까지 ACTIVE → 근거 **8개 대학** / 전형 116건까지 ACTIVE → 서울대 전형 근거 **8건**.

**결론: 기존 API만으로는 적재까지만 가능하고 전형·일정 승격은 불가능하다.** 코드 수정이 필요하다(제안은 PR 보고 참고).

## 3. 관리자 수동 실행 절차 (비밀번호·쿠키·토큰은 어디에도 저장하지 않는다)

0. (관리자) RDS 스냅샷을 만든다.
1. (관리자) 운영 사이트에 ADMIN으로 로그인한 브라우저 탭에서 개발자도구 콘솔을 연다.
2. (관리자) 적재 — 시드는 이 폴더의 raw 파일을 읽어 그대로 전달한다.
```js
const url='https://raw.githubusercontent.com/woori73/feel2real-buildit/<브랜치>/docs/admissions/handover-20261021/converted/seed-v3.2-ops.json';
const seedJson=await (await fetch(url)).text();
const r=await fetch('/api/admin/admissions/load/seed',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json'},body:JSON.stringify({seedJson})});
console.log(r.status, await r.json());   // 기대: 200, universities 10, materials 20, types 116, schedules 72, status LOADED
```
3. (Claude) `admissions-diag` 실행으로 DRAFT 20/116/72 적재를 확인한다 — 읽기 전용.
4. (관리자) 공식자료 승인: `GET /api/admin/admissions/materials?academicYear=2027&status=DRAFT`로 id를 확인한 뒤 자료별로 `review` → `approve`. 중앙대 수시는 승인하지 않는다.
5. (관리자) 전형·일정 승인: **승인 API 추가 PR 병합·배포 후** 해당 API로 진행.
6. (관리자) `POST /api/admin/admissions/index/reload`.
7. (Claude) `admissions-diag`로 ACTIVE 건수·출처 연결을 확인하고 질문 6건을 검증한다(공개 API·회원 경로 사전조건 포함).

## 4. 구분
- 관리자만 가능: 로그인이 필요한 2·4·5·6단계, 스냅샷.
- Claude가 가능: 변환·로컬 검증, 코드 수정·PR, 3·7단계(읽기 전용 진단).

## 5. 2028학년도 자료 (`changes2028.json` 8항목) — 별도 관리
기존 `load/knowledge-base`로는 등록할 수 없다(학년도 2027·GUIDE 고정, `question_types` 형식만 읽음). 등록하려면 지식 항목(FACT) 적재 경로가 필요하다. 구분 방안: 학년도 2028·`year_confirmed`는 항목의 확정/예정 값 그대로, 출처 URL·자료명 필수, 2027 항목과 섞이지 않게 `dedup_key` 접두 `y2028_`, 상태는 DRAFT 적재 후 개별 검수.
