# 포스터 로딩 관측

PR #916은 PR #914 장애의 **조사 결과 보존용 Draft**다. 실패 구간(2026-10-28 03:29:33–03:30:38 UTC) 접근 로그는 접근 가능한 Actions 로그/아티팩트와 로컬 증거에서 발견하지 못했다. 고정 MediaAudit는 전체 해시만 제공한다. 이 환경에서 S3·CloudWatch·서버 로그의 직접 조회는 불가능하므로 **해당 과거 증거는 현재 확보 불가, 원인 미확정으로 조사 종료**한다. AWS 전체에 로그가 없다고 단정하지 않는다. 기존 증거를 덮어쓰거나 과거 장애의 수정 완료를 주장하지 않는다.

이 후속 변경은 Playwright 관측 기능이다. 운영 애플리케이션/미디어/DB/JAR/IAM 변경은 없고, HTTP 요청을 추가하거나 이미지 src·재시도·로딩 정책을 바꾸지 않는다. 운영 실행이나 배포는 별도 승인 범위다. 새 CI는 합성 이미지를 제공하는 loopback HTTP 서버만 사용한다.

## 수집 계약

`e2e/pr914-production/poster-observer.cjs`를 페이지 이동 전에 연결한다. 실제 공개 manifest와 두 캐릭터의 `poster.jpg`/`front.webp` 요청만 관측한다. 결과는 기존 release evidence 옆 `poster-observation.json`에 저장한다.

| 계층 | 기록 | 해석 한계 |
| --- | --- | --- |
| HTTP | 요청 ID·허용 경로·리다이렉트·상태·MIME·완료 여부·전송 오류 | 200 헤더가 디코딩 성공을 뜻하지 않음 |
| 시간 | requestStart/responseStart/responseEnd, 요청→헤더·fetch→완료 ms | 서버 처리 시간으로 단정하지 않음. 미관측 값은 null |
| 캐시 | 허용 Cache-Control 지시어·Age·transferSize·deliveryType·workerStart | 0-byte 전송이나 캐시 정책만으로 캐시 장애를 판정하지 않음. 출처 미확정은 unknown |
| 이미지 | src 지정 여부·정규화 경로·complete·자연 크기·가시성·화면 크기·load/error 이벤트 | 디코딩 완료와 실제 가시성을 별도로 기록 |
| 브라우저 | uncaught-exception / console-error / console-warning·발생 시각·메시지 SHA-256·제한된 소스 위치 | 원문·console 인자·스택은 저장하지 않음. 오류 존재만으로 이미지 실패 원인을 단정하지 않음 |
| 수집 상태 | 누락/용량 제한 카운터·DOM 스냅샷 불가 | 관측 실패를 운영 장애로 분류하지 않음 |

쿠키·인증 헤더·쿼리 문자열·본문·사용자 입력·페이지 HTML은 수집하지 않는다. 오류 텍스트는 해시만 남기므로 상세 오류 메시지가 필요하면 별도 승인된 현장 로그와 대조해야 한다. 이 설계는 원문 Console 로그를 완전하게 대체하지 않는다.

기본 상한: 요청/브라우저 오류/이미지 이벤트 각 200개, 스냅샷 20개, 스냅샷당 이미지 10개·ResourceTiming 200개. 잘린 개수를 표시한다. DOM이 닫히거나 1초 동안 응답하지 않아도 이미 수집한 HTTP 증거는 저장한다. 이 **관측용 1초 제한은 이미지 합격 기준이 아니다**.

## 오판 방지와 기존 검사 유지

- `not-requested`: src 미지정. manifest/스크립트 문제일 수 있으나 단정하지 않는다.
- `loading`: `complete=false`. 정상 비동기 로딩과 일시적 네트워크 지연을 포함한다. 자동 장애 알림을 내지 않는다.
- `loaded`: `complete=true && naturalWidth>0`. visible 여부를 따로 확인한다. 새 요청 기록이 없어도 이미 로드된 이미지를 실패로 처리하지 않는다.
- `not-decoded`: src가 있지만 complete=true, naturalWidth=0. HTTP 상태·전송 오류·image error 이벤트를 함께 검토한다. 서버 장애라는 자동 결론을 내지 않는다.
- 같은 문서/이미지가 loading 뒤 loaded가 되면 `loadedAfterObservedWait=true`를 남긴다. 다른 페이지 이동의 기록과 혼동하지 않는다.

기존 `expect.poll(...complete && naturalWidth > 0).toBe(true)`와 기본 **5000ms**는 그대로다. 완료 시점 스냅샷을 finally에서 추가할 뿐 기존 예외를 삼키지 않는다. 5초 검사 실패 뒤에 복구되더라도 원래 검사 결과는 FAIL로 유지하고, 관측 데이터는 늦은 완료를 기록한다. 영상 재생·신청 메뉴 6개·미인식 질문·안내 다시 보기·로그인 검사는 변경하지 않는다.

이번 코드에는 추가 grace period, 자동 재시도, 서버/캐시 원인 단정, 운영 알림/DB 저장이 없다. 향후 실제 승인된 운영 검사가 실행될 때 증거를 남기며, 이 PR 작성 과정에서 운영 검사를 새로 실행하지 않는다.

## 로컬 / CI

```bash
npm ci --prefix e2e/unified-assistant
cd e2e/unified-assistant && npx playwright install chromium && cd ../..
NODE_PATH="$PWD/e2e/unified-assistant/node_modules" \
  e2e/unified-assistant/node_modules/.bin/playwright test \
  --config e2e/poster-observation/playwright.config.cjs
```

설치된 브라우저를 쓰면 `CHROMIUM_PATH`를 지정한다. 테스트는 390px 합성 페이지에서 정상, 일시 지연, 실제 5초 초과 후 회복, HTTP 404, 손상된 200 본문, 전송 실패, 디코딩 가능한 MIME 불일치, 리다이렉트, 이미 로드된 이미지, 숨김/src 미지정, 브라우저 오류 구분·비밀값 미기록, 수집량 제한·페이지 종료, DOM 무응답을 검증한다. 서버가 본 이미지 요청 수도 확인해 관측기가 추가 fetch를 하지 않음을 검사한다.

결과/캡처는 `poster-observation-results/`에 저장되고 CI가 30일 보존한다. 합성 테스트 캡처를 실제 승인 캐릭터나 운영 화면으로 표시하지 않는다. 장애 원인 해결 검증과 이 관측 기능의 CI 통과는 별개다.
