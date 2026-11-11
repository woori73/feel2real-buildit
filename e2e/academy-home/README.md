# 학원 홈 · 출결 · 학생 · 정보 로컬 E2E

합성 테스트 자료만 쓴다(학생 이름 `테스트가`·`신규가`·`동명나` 등, 연락처 `010-0000-00xx`). 운영 DB·S3·운영 자료를 쓰지 않는다.

1. [통합 비서 로컬 E2E](../unified-assistant/README.md) 2~4단계대로 **새** 테스트 DB(`f2r_unified_test`, `ac_unified_test`)와 두 앱을 띄운다. AcademyCraft는 이 작업 브랜치의 `frontend/`를 `--spring.web.resources.static-locations`로 지정하고, 바인딩에 다중 학원 관리자 한 줄을 더한다.
   `ACADEMYCRAFT_BUILDIT_BINDINGS='[{"userId":5,"role":"ADMIN","builditAcademyId":3,"academyId":7,"memberId":101},{"userId":5,"role":"ADMIN","builditAcademyId":1,"academyId":8,"memberId":104},{"userId":7,"role":"TUTOR","builditAcademyId":3,"academyId":7,"memberId":102},{"userId":3,"role":"TUTOR","builditAcademyId":1,"academyId":8,"memberId":103}]'`
2. 시드: `pip install python-docx reportlab` 후 `MYSQL="mysql -uroot -p... -h127.0.0.1 -P3308" bash e2e/academy-home/seed.sh`
3. 실행: `node e2e/academy-home/run.cjs` (Playwright 필요, `EVIDENCE_DIR`로 캡처 폴더 지정). 결과는 `results.json`.

테스트는 출결 기록·정정, 학생 정보 수정, 명단 연결·제외 확인, 학생 1명 등록(초안 승인)을 실제로 저장한다. 다시 돌리려면 1단계부터 새 DB로 시작한다.
