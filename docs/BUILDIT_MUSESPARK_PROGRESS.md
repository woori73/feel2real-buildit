# BuildIt AI Muse Spark 전환 (2026-10-12)

> **상태**: 구현 완료, 단위 테스트 통과, 운영 미배포
> **브랜치**: `buildit-ai-agent/muse-spark-20261012` (main 851577b4 기준, PR #601 병합 후)

## 1. 목표

BuildIt의 AI 홈페이지 최초 생성 및 자연어 수정에 Muse Spark(`muse-spark-1.3-contributor`)를 기본 모델로 사용.

## 2. 라우팅 정책

| 순서 | 모델 | 경로 |
|------|------|------|
| 1순위 | `muse-spark-1.3-contributor` | Meta API (NewStart 검증済) |
| 폴백 | `qwen/qwen3.7-flash` → `ox-alpha` → `openrouter/free` | 기존 Wiki AI Router 체인 |

**동작**:
- Muse Spark 기술적 실패(`fallbackEligible=true`, 타임아웃·연결 오류 등) 시에만 Qwen 체인으로 폴백
- 설정 오류 등 비기술적 실패 시 폴백하지 않고 실패 반환
- 홈페이지 최초 생성과 자연어 수정에 동일한 정책 적용

## 3. 변경 파일

### `MuseSparkAcademyAiProvider.java` (신규)

- `AiProvider` 구현체, `@Primary`로 등록
- NewStart의 `MetaMuseSparkLlmTransport` 빈을 주입받아 재사용 (연결 방식·인증 설정 동일)
- NewStart 코드는 수정하지 않음
- 폴백: 기존 `WikiRouterAcademyAiProvider`에 위임
- 로깅: 성공·실패·타임아웃·폴백 여부 기록 (API 키·프롬프트 내용·개인정보 미기록)

### 영향 범위

- ✅ `domain/academy`의 `AiProvider` 주입처 (CreateService, EditService, GuideService, ChatService 등)
- ❌ 문제생성기 (`WikiAiRouterClient` 직접 사용, `AiProvider` 미사용)
- ❌ NewStart (`MetaMuseSparkLlmTransport` 빈 공유만, 코드 미수정)
- ❌ 클로드의 시각 편집기·Proposal·저장 로직

## 4. 검증 결과

### 단위 테스트 (5/5 통과)

| 케이스 | 결과 |
|--------|------|
| Muse Spark 성공 → 폴백 없음 | PASS |
| 기술적 실패 → Qwen 폴백 | PASS |
| 비기술적 실패 → 폴백 없음, 실패 반환 | PASS |
| 폴백도 실패 → 결과 전파 | PASS |
| name()/isReady() | PASS |

### 미검증 항목 (운영 환경 필요)

| 항목 | 상태 | 사유 |
|------|------|------|
| Muse Spark 실제 호출 성공 | ⏳ | API 키가 운영 서버 환경변수에만 존재 |
| AI 최초 생성 E2E | ⏳ | 운영 인증 필요 |
| 자연어 수정 E2E | ⏳ | 운영 인증 필요 |
| 응답 형식 (JSON·changes[]·Proposal 계약) | ⏳ | 실제 호출 후 검증 필요 |
| NewStart AI 영향 없음 | ✅ | 코드 미수정으로 보장 |

## 5. 기존 조사 결과 (참고)

BuildIt AI의 기존 라우팅 (`provider=auto`):
- `POST /api/ai/complete` → Python `ai_router.generate(allow_noncritical_fallback=True)`
- `qwen/qwen3.7-flash` → `stealth/ox-alpha` → `openrouter/free`
- `muse-spark-1.3-contributor`는 기존에 NewStart 전용이었음

## 6. 병합·배포

- PR #601·#602와 별도 브랜치로 진행 (통합 방해 없음)
- **운영 배포 전 사용자 보고 후 승인 필요**
