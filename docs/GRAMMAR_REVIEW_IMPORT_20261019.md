# 문법 문제은행 검토본 등록 계약 및 잔여 작업

기준 main: `5c14bb8533601bf78866a20e94a4b89f324924e7` / 기존 PR #786, #802.

## 이번 코드

- 기존 `difficulty=EASY|MEDIUM|HARD` 검색 API를 화면의 기본·응용·심화 필터에 연결. 학년·태그·형식과 AND 검색. 목록·상세에 난이도 표시.
- 오프라인 어댑터는 JSONL을 검증하고 기존 API용 **미승인 초안**과 전체 메타데이터 sidecar를 생성. 네트워크·DB 쓰기 코드 없음.
- external_id → external_ref, 선택지 id → 순서, 정답 id → answer, 난이도 한국어 → enum. 기존 grade → school_level 유지.
- 23유형을 V1090의 14개 태그 이름으로 명시 매핑. 세부 concept_code와 권장 학년은 sidecar에 원형 그대로 보존. 실제 운영 tags 응답과의 대조는 아직 미실시.
- 기존 manifest와 동일 ID/동일 레코드는 skip, 동일 ID/다른 레코드는 conflict로 분리하며 덮어쓰기 안 함. 기존 API의 정규화 발문 충돌도 사전에 거절.

## 아직 지원되지 않는 부분

기존 수입기는 `recommended_grade`, `concept_code`, 원문↔개선본 대응, 구조화된 오답 해설을 전부 보존하지 않는다. sidecar가 이를 보존해도 사이트가 저장·검색하는 것은 아니다. `registration_ready=false`를 유지한다.

후속 구현은 기존 `auto_classification` JSON을 이용한 검토 메타데이터 보존 또는 별도 nullable 프로필 확장 중 선택해 진행한다. 원래 school_level/grade를 수정하지 않고 recommendedGrade 별도 검색·응답·편집 필드를 제공해야 한다. 판정 미확정(null)을 고학년으로 강제 배치하지 않는다. 새 API 계약·DTO·검수 상태·수정 이력·문제집 원고 내보내기를 함께 검증한다.

현재 `(material_id, external_ref)`는 material 없는 수입에서 외부키 전역 멱등성을 보장하지 않는다. 수정본 업로드는 별도 버전 등록 및 기존 레코드 대조 정책을 먼저 구현해야 한다. 현재 어댑터 manifest는 운영 DB 대조를 대신하지 않는다.

`GrammarBankAccessGuard`는 ADMIN·modu만 허용하지만 서비스 내부에는 ADMIN/TUTOR 역할 검사도 남아 있다. modu의 실제 역할·소유권에 따른 수입/편집/검수 접근은 실계정 또는 격리 테스트로 검증해야 한다. 이를 해결하려 modu에 ADMIN을 부여하거나 TUTOR 전체로 접근을 넓히지 않는다.

## 검증 및 한계

- Python adapter fixture 단위 테스트 5건, 실제 1,016개 메타데이터 roundtrip/정답/중복·충돌 검증 4건 통과.
- 오프라인 검토 HTML은 Node DOM 모형으로 28개 학년×난이도 조합, 보류 표시·정답 분리·초기화 통과.
- 브라우저 실행 파일이 없어 실제 PC/390px 렌더링·인쇄 시각 QA는 미실시.
- 전체 repository checkout/JDK/MySQL 환경이 없어 Spring 부팅·프론트 번들·MySQL 통합검증은 미실시. PR은 초안으로 유지한다.
- 운영 사이트는 접근 도구에서 열람 실패. 운영 ADMIN/modu 실사용·데이터 등록 상태는 확인하지 않았다.
- main 병합·운영 DB 등록/수정·배포·AWS 변경은 별도 승인 대상이다.
