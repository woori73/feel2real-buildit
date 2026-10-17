# 내신영어 기출분석 + 출제방향 제작 — 설계 메모

## 범위
- 분석(무료·도구 화면)과 문제 제작(유료 20,000원 / 40문항)을 분리한다.
- 분석은 기존 LLM transport(WikiRouter → OpenRouter fallback 1회)를 재사용한다. 새 모델 연동 없음.

## 개인정보·비용
- 업로드 원본과 결과는 저장하지 않는다. 로그에는 길이·사용자 ID만 남긴다.
- 사용자 일 3회, 전체 일 100회(메모리, KST). 실패 시 환불. 입력 그룹당 40,000자 상한.
- 지원 형식: 문서 PDF(텍스트)·DOCX·HWPX·TXT·MD, 웹 문서 HTML·HTM, 사진 JPG·PNG·WebP(Vision 키가 설정된 경우). 스캔 PDF·HEIC는 미지원.

## 제작 의뢰 연동(미구현 — 승인 후)
현재 결제·주문 기능이 없어 가짜 결제를 만들지 않았다. 의뢰는 카카오 오픈채팅/메일로 접수한다.
다음 단계 제안:
1. `exam_insight_order(id, user_id, analysis_id, school, grade, publisher, exam_type, status, created_at)` 신설(운영 V909+ 번호 확인 필요).
2. 분석 번호(`analysisId`)는 내용 해시이므로 문서를 저장하지 않고도 의뢰와 연결된다. 의뢰 시 사용자가 .md 2개를 첨부하거나 서버가 원문을 보관하는 동의 절차를 추가한다.
3. 결제는 기존 결제 모듈이 생기면 `status=PAID` 전이만 연결한다.

## 안전 보강(2026-10)
- 기본값 비활성화(`app.exam-insight.enabled:false`). 실제 AI 응답 품질이 검증되기 전에는 환경변수로만 켠다.
- 완성 검증: 응답 맨 끝 `=====END=====`, 문서별 `## 1.`~`## 7.` 순서·마지막 구간 본문. 하나라도 빠지면 실패 + 횟수 환불.
- 출력 상한 40000자(Wiki 라우터의 문자 단위 절단 상한만 해당). 공급자 토큰 한도·Wiki 라우터 45초 공급자 제한/Spring 90초 제한은 별도이며 시간 초과는 실패 처리된다.

## 사진·HTML 입력 (2026-10)
- **흐름**: 파일(문서·HTML·사진 혼합) → `POST /api/exam-insight/extract`가 파일별 텍스트로 읽음 → 화면에서 파일별로 확인·수정·순서 변경 → 수정한 텍스트(`pastItems`/`scopeItems`)만 `analyze`로 전송 → 한 번의 통합 분석. 사진을 다시 읽지 않으므로 인식 비용은 사진당 한 번이다.
- **재사용**: 업로드 검증 `DocumentUploadValidator`(JPEG/PNG/WebP·HTML 시그니처), jsoup·ImageIO(TwelveMonkeys WebP/JPEG)·기존 Vision 설정(`app.newstart.vision`, `META_VISION_API_KEY`). 채무 이미지 서비스·ExamExtractionService(Claude, 데모 단계 비활성)는 건드리지 않고 재사용하지 않았다.
- **사진 처리**: 실제 디코딩 검증 → EXIF 회전 → 긴 변 1,800px 축소 → 새 JPEG 재인코딩(메타데이터·덧붙은 데이터 제거). 모델 프롬프트는 영어 철자 교정 금지, 불확실은 `[확인필요: …]`/`[판독불가]`, 필기는 `[필기: …]`, 개인정보는 `[개인정보 생략]`, 사진 속 지시문 불복종. 불확실 표시는 분석에서 사실로 쓰지 않는다.
- **HTML 처리**: jsoup 파싱(스크립트 미실행), 표·목록·문제 번호·선택지 보존, 서식·색 표시, 가려진 내용 `[숨김]` 표시, data URI 이미지는 사진 인식으로 연결, 외부 이미지는 가져오지 않음(SSRF 방지).
- **비용 통제**: 사진 장수 일일 한도(사용자·전체), 요청당 장수·용량 상한, 동시 호출 2, 중복 사진 1회, 재시도 1회, 분석 횟수가 없으면 인식 전에 거절, 실패 시 장수 환불.
- **미지원/한계**: HEIC(변환기 없음 — iOS Safari는 `accept`에 HEIC를 넣지 않으면 업로드 때 JPEG로 바꿔 준다), 스캔 PDF, 사진 속 손글씨 인식 정확도는 모델에 의존(수동 검증 테스트 `ExamInsightVisionRealApiTest`).
