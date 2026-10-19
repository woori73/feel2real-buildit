# 문법 문제은행 검토본 등록 계약 및 인계

기준 main: `ca601a217119397377f87f68f8b37dcd4383f9e9`. 기존 초안 #804의 난이도 필터·어댑터를 재사용해 서버 보존 경로를 보완했다. 새 DDL·운영 데이터 변경은 없다.

## 데이터 계약

- 기존 `grade` → `school_level`은 유지한다. 권장 학습 학년은 `recommendedGrade=M1..H3`로 별도 조회·수정한다. UNKNOWN은 분류 해제, null PATCH는 변경 없음이다. 문장 길이로 학년을 결정하지 않는다.
- 수입 레코드 `review_metadata`는 최종 검토 JSONL 한 행 전체다. 기존 `auto_classification` JSON의 `review_metadata`에 불변 스냅샷으로 저장하므로 original_ids, concept_code, 원래 grade/difficulty, recommended_concept_code, 보기별 해설·검수 근거·원문 대응 등이 손실되지 않는다.
- 현재 권장 학년은 JSON 루트 `recommended_grade`, 변경 전후·사용자·시각은 `review_history`다. 스냅샷은 수입 당시 값이고 이후 편집한 현재 문항과 구분한다.
- 권장 학년·난이도·태그·기존 학년을 AND 검색한다. 목록·상세에 권장 학년과 난이도를 표시하고 원래 학년도 상세에 남긴다. 검색/검수 목록은 50개씩 페이지 이동하며 선택함·문제집 제작을 재사용한다.
- 문제집 JSON은 `schoolLevel,recommendedGrade,difficulty,externalRef,reviewMetadata`를 함께 반환한다. MD/HTML 원고 제목은 권장 학년·난이도·기존 학년을 구분한다.
- 본문·권장 학년·태그 수정은 NEEDS_REVIEW로 전환한다. 태그 수정만으로 승인하지 않는다. 본문 수정 시 content_hash도 갱신하며 같은 발문의 다른 문항과 충돌하면 거절한다.

## 재수입 및 접근

검토본 수입은 영어 subject 행에 트랜잭션 잠금을 잡고 external_ref를 자료 구분 없이 대조한다. 동일 ID·동일 스냅샷은 중복 skip, 동일 ID·다른 스냅샷 또는 같은 발문·다른 ID는 실패 보고 후 덮어쓰지 않는다. 새 등록의 발문·정답·선택지도 스냅샷과 일치해야 한다. 기존 CSV/JSON 경로의 해시·자료 내 외부 ID 검사는 유지한다. 검토본 수입 경로 이외의 DB 직접 변경에 대한 전역 UNIQUE 제약을 새로 만든 것은 아니다.

컨트롤러와 수입·편집·검수 서비스 모두 기존 GrammarBankAccessGuard(ADMIN 또는 실제 modu user_id)를 적용한다. TUTOR 전체로 확대하거나 modu 역할을 변경하지 않는다. 선택함·문제집의 소유자 격리도 유지한다.

## 학원 PC 재현

1. 최종 패키지를 새 폴더에 풀고 index.html에서 학년·난이도·개념 검색과 해설·인쇄를 확인한다.
2. `python prepare_grammar_review_import.py final_questions_1016.jsonl registration`으로 미전송 API 초안과 동일 메타데이터 sidecar를 생성한다. 기존 manifest가 있으면 `--existing-manifest`로 대조하되 운영 DB 대조를 대신하지 않는다.
3. 최신 코드에서 `python3 -m unittest discover -s scripts/grammar-review`, wiki-src에서 `npm ci` 후 `npm test -- --environment jsdom src/components/GrammarBank.test.tsx`와 `npm run build:grammar-items`를 실행한다.
4. JDK21에서 `bash ./gradlew test --tests '*GrammarBank*Test' --tests '*GrammarReviewMetadataTest' --tests '*GrammarDesignCraftServiceTest'`를 실행한다.
5. 격리 MySQL 8.4의 `127.0.0.1:13316/grammar_review_test`에 빈 테스트 스키마를 준비한다. 이 테스트는 create-drop을 사용하므로 운영 DB를 연결하지 않는다. `GRAMMAR_MYSQL_TEST=true`, `GRAMMAR_REVIEW_IMPORT_JSON=<등록 초안 JSON 절대 경로>` 환경변수를 지정하고 `bash ./gradlew test --tests '*GrammarReviewMySqlTest'`로 1,016개 통합 검증을 실행한다. 기본 스위트에서는 이 테스트를 건너뛴다. 별도로 동시 수입·ADMIN/modu 실계정 검색·편집·내보내기와 실제 운영 tags 응답을 확인한다.

## 승인 경계와 남은 검증

독립분사구문 WG-0459는 사용자 지정에 따라 권장 고1이다. 1,016문항 구조 검사·AI 문법 검토와 외부 전문가 독립 감수, 학습자 실측 난이도는 별개이며 후자는 미실시다.

운영 `/grammar-items`는 비로그인 브라우저에서 로그인 복귀 URL로 이동하는 것을 확인했다. 인증된 운영 화면·운영 DB 내용은 확인하지 않았다. 브라우저 환경에서 로컬 서버는 연결되지 않아 실제 PC/모바일/인쇄 시각 검증은 남아 있다. 서버 관련 단위 테스트 23건, React 화면 테스트 2건, Python 패키지 검증 9건과 별도 어댑터 fixture 5건, 전용 Vite 번들이 통과했다. 이 환경의 Mockito self-attach가 지원되지 않아 실행 시 캐시된 mockito-core를 test JVM의 명시적 javaagent로 지정했다(제품 코드·보안 설정 변경 없음). 격리 MySQL 8.4.6은 UNIX socket 없이 같은 테스트 프로세스의 로컬 TCP로 구동했다. 실제 등록 초안 1,016개 전체 저장·불변 스냅샷 왕복·권장 고1 38개 검색·권장 학년/난이도/태그 복합 검색·전체 재수입 1,016개 skip·권장 학년 해제 후 미확정 검색이 통과했다. 운영 마이그레이션 전체·동시 수입 부하·실계정 테스트를 대체하지 않는다. 실계정 검증과 병합·배포·업로드 승인 전에는 `registration_ready=false`다.

main 병합·운영 배포·DB 등록/수정/덮어쓰기·AWS 변경은 별도 승인을 받은 뒤 진행한다. 이번 작업은 이 행동들을 실행하지 않는다.
