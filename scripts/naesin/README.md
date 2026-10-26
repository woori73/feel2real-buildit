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
