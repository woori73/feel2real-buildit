# 보존 확인 (r6)
- 대상: deliver, deliver_r2, deliver_r3, deliver_r4, deliver_r5, panels (r6 작업 전 목록 `preserve_before_r6.md5`, 275개 파일)
- 결과: 작업 후 `md5sum -c` 275개 전부 OK, 불일치 0
- r14 원본(`src_r14`) 6개 파일 MD5 전부 OK
- 승인 시트: 업로드 원본과 `references/suni/SUNI_APPROVED_SHEET_original.png`가 바이트 동일, SHA-256 `461926ffdb3a391c6b31d55aa759a4716674264209947d9cf55c7732e7f23be6`
- 참고: preserve_before_r6.md5는 r5 폴더의 zip 포함 여부와 무관하게 위 폴더의 모든 파일을 포함
