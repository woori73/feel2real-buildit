# Coverage Audit — 새출발기금 공식 홈페이지 (2026-09-20)

## 내부 페이지

| 페이지 | URL | 상태 | 비고 |
|--------|-----|------|------|
| 메인 Index | /Index.do | COVERED | 제도안내 전문 + 팝업 5종 수집 |
| 제도안내 | /Contents/Process_guid.do | COVERED | |
| 절차안내 | /Contents/Prod.do | COVERED | |
| 추가 자격심사 | /Contents/Objc.do | COVERED | |
| 맞춤프로그램 | /Contents/Findmatchprogram.do | COVERED | 분기 로직 분석 완료 (match_program.json) |
| 제도설명 영상 | /Contents/archive.do | COVERED | YouTube 5종 링크 수집 (영상 내용 미시청) |
| FAQ | /Faq/List.do p1~5 | COVERED | Q1~Q43 전수 |
| 공지사항 | /Board/List.do p1~5 | COVERED | 49건 목록 |
| 상담센터 | /Cntr/List.do | PARTIAL | 탭/대표센터 구조만, 전체 지점 덤프 안 함 (LIVE LOOKUP 원칙) |
| 협약기관 조회 | /Safc/List.do | PARTIAL | 구조+기준일+총건수만, 3,534건 덤프 안 함 (LIVE LOOKUP 원칙) |
| 진행상태 확인 | /Rslt/Rslt.do | PARTIAL | 본인인증 게이트 확인 (로그인 영역 진입 불가 — 정책상 제외) |
| 공시사실 조회 | /Pbnc/Pbnc.do | PARTIAL | 본인인증 게이트 확인 (동일 사유) |
| 영상안내 공지 | /Board/View.do?idx=18 | COVERED | 제목 확인 |
| 신청 플로우 | JS 플로우 | NOT_RELEVANT | 본인인증 필요, 수집 제외. 신청 링크로 연결 |

## 공지사항 49건 분류 (VIEWED = 49/49)

### KB_INCLUDE (19건 — knowledge 반영)

| idx | 제목 | 게시일 | 반영 record |
|-----|------|--------|-------------|
| 52 | 제도개선 시행('26.3.25) | 26-03-25 | notice_001 등 CURRENT |
| 54 | 비상장주식 제출 | 26-04-30 | notice_002 CURRENT |
| 56 | 지원체계 보완 | 26-06-26 | notice_003 CURRENT |
| 41 | 제도개선 시행('25.9.22) | 25-09-19 | notice_004 CURRENT |
| 42 | 9.22 소급적용 신청 | 25-09-23 | notice_004 CURRENT |
| 47 | 사회취약 소급 | 25-11-17 | notice_005 CURRENT |
| 50 | 가상자산 제출 | 26-01-09 | notice_006 CURRENT |
| 55 | 재기지원 확대 | 26-05-26 | rehab_001 CURRENT |
| 40 | 지원제외업종 기준 | 25-08-25 | notice_007 CURRENT |
| 19 | 추가감면 안내 | 24-09-12 | principal_003 CURRENT |
| 30 | 공공정보 해제 신청 | 25-03-27 | public_001 CURRENT |
| 7 | 제출서류 및 발급기관 | 23-02-06 | documents_* CURRENT (추가분과 통합) |
| 28 | 중개형 실효차주 미신청 담보대출 지원 | 25-02-07 | notice_008 CURRENT (신규) |
| 15 | 부채증명원 발급 신청 | 24-01-24 | documents_005 CURRENT (신규) |
| 46 | 새도약론 채무조정 이행확인서 | 25-11-14 | documents_006 CURRENT (신규) |
| 26 | 동의서 양식 2종 | 24-11-27 | documents_007 CURRENT (신규) |
| 21 | 종합대책 확대·조기시행(9.12) | 24-09-11 | notice_009 SUPERSEDED (25.9.22로 대체, 첨부 미파싱) |
| 11 | 지원대상 확대 | 23-12-12 | notice_010 SUPERSEDED (후속 확대로 대체) |
| 17 | 성실상환자 신용회복 지원 | 24-03-21 | 이미지 9장 — IMPORTANT UNPARSED (OCR 필요) |

### REFERENCE_ONLY (10건)

idx1 신청전필독(본문 비어있음, 22-08-03) / idx4 협약리스트(xlsx 스냅샷, LIVE 조회 우선) / idx5 확약서 매뉴얼(PC절차) / idx24 채무조정소개(본문 미노출) / idx39 사업정리도우미(마감·본문없음) / idx38 경영환경개선(마감·본문없음) / idx37 노란우산(본문없음) / idx31 은행권지원(외부링크 위임) / idx18 영상안내 / idx20 개편예고(9월 시행 예고, 1줄)

### NOT_RELEVANT (20건)

수기·공모전·이벤트: idx8,10,14,23,25,27,44,48,9,16,29,35,36 / 이전·개소: idx13,22,34,45,53 / 종료된 2023 프로그램: idx6 / 방송후기: idx9(중복 아님, 위 포함)
