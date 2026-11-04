# 입시상담 · NewStart 지피지기 GPT-6 Luna 전환

사용자 요청(2026-11-04): 입시상담과 NewStart 지피지기의 답변 모델을 `gpt-6-luna`로 전환한다.

## 연결

공통 `OpenAiLunaLlmTransport`는 OpenAI Responses API만 호출한다. 각 서비스의 검색, 프롬프트, 출처, 권한과 상담 이력은 기존대로 유지한다. 지피지기의 전자책·운영자 경험·실제 사례 우선 원칙은 변경하지 않는다.

- 서버 비밀 환경변수: `OPENAI_API_KEY` (채팅·Git·프론트에 저장하지 않음).
- 입시상담 선택: `APP_ADMISSIONS_LLM_PRIMARY=openai`.
- 지피지기 선택: `NEWSTART_LLM_PRIMARY=openai` (application.yaml 매핑).
- 모델: `gpt-6-luna`, reasoning low, 최대 출력 4096토큰, 저장 요청 false, 요청 timeout 90초.
- 성공 응답의 input/cached input/output 토큰 수만 로그에 기록한다. 키·질문·근거·답변·reasoning은 기록하지 않는다.
- 정상 시 1회. 기술적 실패만 기존 OpenRouter fallback 1회. 키 부재·인증 오류는 fallback 없이 통제된 실패.

## 적용 순서와 현재 한계

이 PR은 선택 경로를 추가한다. 키 없는 배포로 상담이 중단되지 않게 기존 기본값은 유지한다. 실제 전환은 GitHub OPENAI_API_KEY secret 등록 후 Deploy to EC2의 counseling_model=luna 수동 배포로 키와 두 선택값을 적용한다. 상담 모델 기본값은 unchanged다. 기존 환경변수에 meta가 명시돼 있으면 반드시 교체해야 한다.

API 계정의 크레딧 확인·Playground Luna 응답은 완료됐지만 서버 API 키의 등록 여부 및 운영 요청은 아직 검증하지 않았다. 이 변경을 운영 전환 완료로 보고하지 않는다.

## 검증

단위 테스트: Responses 요청의 모델·프롬프트 보존·저장 비활성·출력 상한, 최종 답변만 추출, 불완전/잘못된 응답 거절, 키 부재, 두 클라이언트의 openai 선택을 검증한다. 외부 모델 API 호출 없이 로컬 HTTP 모형을 사용한다.

배포 env helper 셸 테스트 통과(전환·복귀·600 권한·잘못된 키 무변경). 현재 작업 환경은 Java 17이며 Gradle 배포본 다운로드가 Network is unreachable로 실패했다. 전체 프로젝트가 요구하는 JDK 21/Gradle 환경의 테스트는 미실행이다. 병합 전 CI 확인 필요.

운영 검증: 두 서비스 각각 개인정보 없는 질문 1회 → 서버 로그 provider=openai-luna/model=gpt-6-luna 확인 → 출처와 기존 상담 규칙 확인 → OpenAI usage 반영 후 실제 비용 확인. 비용은 reasoning을 포함한 사용량을 기준으로 계산한다.

롤백: 입시상담 `APP_ADMISSIONS_LLM_PRIMARY=meta`, 지피지기 `NEWSTART_LLM_PRIMARY=meta`로 되돌리고 재시작한다.
