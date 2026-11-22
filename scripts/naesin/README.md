# 내신 라이브러리 이관 도구

운영 DB 적재·배포는 별도 사용자 승인 후에만 실행한다. 기본 실행은 파일 검증만 하는 dry-run이다. 기존 AI AGENT Studio 및 원본·승인 포인터는 변경하지 않는다.

```bash
python3 scripts/naesin/library_inventory.py --inventory-dir /private/census --originals /private/originals --output /private/import.json
python3 scripts/naesin/import_library.py --manifest /private/import.json --originals /private/originals --base-url http://127.0.0.1:8080 --report /private/dry-run.json
```

격리 서버 검증은 인증된 관리자 토큰을 파일로 공급하고 `--execute --token-file /private/operator-token --journal /private/isolated-journal.json`을 추가한다. 운영은 별도 승인 및 실제 배포 SHA 대조 후 `--base-url https://feel2real.co.kr --production-authorized --deployment-sha <40자리 실제 배포 SHA>`를 명시한다. 환경마다 별도 journal을 사용한다. 권한 미확인 자료는 내부 검수용이며 이 도구는 승인/게시 API를 호출하지 않는다.

- 파일명·폴더로 확정하지 못한 학교급, 본문 일부/ZIP 내부 목록으로만 추정한 학교급, 지원하지 않는 형식과 무결성 오류는 자동 등록 보류.
- 원본 바이트는 변경하지 않는다. SHA가 같아도 다른 학교·학교급·학년·시험 범위 프로젝트에는 별도 연결하며 동일 프로젝트의 바이트만 재사용한다. 별칭 및 원본·정답·수정본 제안 관계는 인계 기록에 보존한다.
- 매 업로드 후 원본을 다시 다운로드하여 SHA를 확인한다. 실패·진행 기록은 journal에 남겨 재시도하며, 자동으로 원본·실패 리비전을 삭제하지 않는다.
- 대용량은 raw-files 스트리밍 경로(문서384MiB, 텍스트20MiB, 미디어100MiB)를 사용한다. 운영 프록시 상한과 디스크 여유는 배포 후 실제 파일로 검증한다. 전역 multipart 설정은 변경하지 않는다.
- HWP 추출은 선택 설정 `app.naesin.kordoc-cli=/absolute/path/dist/cli.js`, `app.naesin.kordoc-node=node`로 Kordoc을 사용한다. 검증 버전은 4.24.6, 소스 커밋 `4f3460354f9ddee72e659b4e1a189cd621e1ece9`. 설치하지 않으면 기존 Java HWP5 추출을 유지한다. 추출 결과와 실패 기록을 재사용하며 원본은 재저장하지 않는다.
- 인계 리비전 자동 재열람은 실제 Claude 구독 앱과 Work의 운영 인계 완료를 뜻하지 않는다. 배포·등록 후 각 앱에서 같은 프로젝트의 최신 원본·리비전·승인 포인터를 읽고 이어 저장하는 별도 실증이 필요하다.

```bash
python3 -m unittest discover -s scripts/naesin -p 'test_*.py' -v
```

원본, 조사 목록, 인증 토큰, 이관 journal은 Git에 커밋하지 않는다.

## 내신 예상문제 출제 검수·시험지 조립

Claude 코드 세션에서 "신구중 2학년 기말 40문항"처럼 한 줄로 시킬 때 쓰는 두 스크립트다. 사용법 전체는 [docs/exam/NAESIN_CODE_SESSION.md](../../docs/exam/NAESIN_CODE_SESSION.md).

```bash
python scripts/naesin/exam_check.py r12.json            # 문항 수·배점 합계·정답 번호 쏠림·지문 중복·양식
python scripts/naesin/exam_build.py r12.json --out-dir out --asset-dir figs   # 검수 통과 시 A4 문제지·정답해설 HTML
python -m unittest discover -s scripts/naesin -p 'test_exam_*.py' -v
```

- 입력은 내신위키 저장 계약(`question_contract.TransformQuestion`)과 같은 문항 JSON이라, 검수를 통과한 파일을 `save_generated_questions`에 그대로 넘길 수 있다.
- 문제지는 `exam_paper.py`가 감독 승인본(신구중2 11회, `static/naesin/singu-m2-2026-2/r11`)과 같은 편집으로 만든다: 명조 글꼴, 1쪽 왼쪽 위 F2R 로고, `[12~14] 다음 글을 읽고…` 묶음 머리말과 지문 상자, 발문 끝 `(2점)`·`(정답 2개/4점)`, 부정어 굵은 밑줄, 표 선택지, 그림, `<보 기>` 상자. 정답·해설지는 `problem-generator/wiki-backend-src/backend/weekly_paper.py`를 쓴다. 검수에서 고칠 것이 나오면 시험지를 만들지 않는다.
- 문항 표기: `stem` 첫 줄이 발문, 그 아래 줄은 예문 상자. `(선택지 표: ⓐ | ⓑ | ⓒ)` 줄은 선택지를 표로(선택지는 `ⓐ: in / ⓑ: with / ⓒ: for`), `[[img:/naesin/…/fig-q09-1.webp]]` 줄은 그림(저장소 `static` 아래 같은 경로 → `--asset-dir`의 같은 파일 이름), `<보 기>` 줄은 상자 제목. 태그는 `<u>`만 쓴다. 배점·정답 개수·부정어 밑줄은 스크립트가 붙이므로 발문에 적지 않는다(이미 적힌 옛 표기는 한 번만 찍는다).
- 단 나누기는 문제지 HTML 안의 스크립트가 브라우저에서 실제 높이를 재어 한다(문항·지문 상자는 갈라지지 않는다). 크롬에서 열어 인쇄하거나 `chrome --headless=new --print-to-pdf`로 PDF를 만들고, 쪽마다 눈으로 확인한다.
- 문항 JSON과 만든 시험지는 저장소 밖 작업 폴더에 둔다. 기출 원본·학생 정보는 커밋하지 않는다.
