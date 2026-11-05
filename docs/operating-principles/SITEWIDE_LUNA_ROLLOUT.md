# 사이트 AI의 GPT-6 Luna 전환

2026-11-05. 관련: #1001, 선행 #999.

`SITEWIDE_LUNA_ENABLED=true`일 때 활성 기능의 서버 요청을 `gpt-6-luna` / OpenAI Responses API로 보낸다.
브라우저의 기존 모델 선호·API 키 입력은 이 설정을 우회하지 못한다.

| 경로 | 처리 |
|---|---|
| 입시상담·NewStart 상담 | 기존 검색·사례 우선 프롬프트 + 공유 Luna 전송부 |
| Academy 제작·편집, 출판 명령 계획 | 기존 명령 검증·승인 과정 + 공유 Luna 전송부 |
| Wiki 채팅·분석·문제 생성/변형 | Python 라우터 Luna 요청; JSON 후처리·Reference·유형 검증 유지 |
| Spring 문제 생성/박원장 도우미/문제 변형 | 공유 Luna 전송부; 기존 엔드포인트·결과 형식 유지 |
| Chart 조사 | Luna Responses + web_search, 출처 수집 유지 |
| 사진 OCR·채무표, Wiki 사진 추출 | Responses input_image, 기존 텍스트/JSON 후처리 유지 |
| 기존 Claude 튜터·시험지 추출/메타 추론 | 활성화돼 있는 경우 Luna로 호출; 기존 기능 비활성 스위치는 유지 |

임베딩, 이미지·영상·음성 생성과 외부 MCP 클라이언트는 텍스트 답변 모델 교체 대상이 아니다.
미사용 `MuseSparkToolsService` 실험 빈은 호출처가 없으므로 활성화하지 않는다.
전체 Luna 모드에서는 구 모델 A/B 비교를 차단하여 동일 모델을 다른 모델로 잘못 표시하지 않는다.
API 키·프롬프트·사진은 로그에 기록하지 않는다. Responses `store=false`, reasoning low.

배포는 기존 수동 Deploy to EC2의 `counseling_model=luna`를 사용한다. 기존 제한된 S3 키 경로를 재사용하고 키를 추가로 만들지 않는다.
키와 전환 플래그를 `/etc/f2r/env`, `/etc/buildit-wiki.env`에 함께 기록한 후 서비스를 재시작한다.
두 파일은 env rollback 스냅샷 대상이다. `meta` 선택 시 플래그를 false로 돌려 기존 라우팅으로 복귀한다.
키를 출력하거나 커밋하지 않는다.

검증: Python 라우팅·JSON·이미지·재시도 계약 7건, Java 전송/레거시 위임/문제 검증, 셸 활성·복귀·키 주입 방어.
운영 검증은 배포 후 상태 응답과 실제 호출의 모델 로그로 별도 확인한다.
