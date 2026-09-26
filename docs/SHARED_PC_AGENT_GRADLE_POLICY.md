# Shared PC Agent / Gradle Policy

> 목적: 여러 AI Agent가 같은 PC에서 동시에 작업할 때 Gradle/JVM 경합으로 전체 작업이 느려지거나 멈추는 현상을 방지한다.

## 작업 시작 전 — 필수

모든 Agent는 구현·테스트를 시작하기 전에 다음 순서로 확인한다.

1. 루트 `AGENTS.md`를 읽는다.
2. 이 문서 `docs/SHARED_PC_AGENT_GRADLE_POLICY.md`를 읽는다.
3. 현재 repository / worktree / branch / git status를 확인한다.
4. 다른 Agent의 worktree를 reset / clean / delete 하지 않는다.
5. 다른 Agent가 heavy Gradle 작업을 실행 중인지 확인한다.

이 확인 없이 `compileJava`, `test`, `bootJar`, `build`, `bootRun`을 시작하지 않는다.

## 기본 원칙

**Gradle은 개발 중 반복 검증 도구가 아니라 최종 Release Gate로 사용한다.**

여러 Agent가 동시에 작업하더라도 heavy Gradle/JVM 작업은 **PC 전체에서 동시에 1개만** 실행한다.

Heavy task:
- `compileJava`
- `compileTestJava`
- `test`
- `bootJar`
- `build`
- `bootRun`
- 위 작업을 포함하는 Gradle task

## 개발 중

원칙적으로 매 수정마다 `./gradlew compileJava`를 실행하지 않는다.

우선 사용:
- git diff / diff stat
- 정적 코드 검토
- 변경 영역에 맞는 작은 contract/unit test
- JS 테스트
- TypeScript typecheck
- HTML/CSS 렌더/브라우저 QA
- 기존 lightweight 검증

HTML/CSS/JS/docs-only 변경이고 Java source를 변경하지 않았다면 `compileJava`는 원칙적으로 생략한다.

Java/Spring 변경도 개발 도중 반복 compile하지 않는다. 구현을 완료한 뒤 통합 검증 시 한 번 실행한다.

## Final Release Gate

PR / merge / deploy 직전에만:

1. 다른 Agent의 heavy Gradle/JVM 실행 여부 확인
2. heavy Gradle이 없을 때 한 Agent만 실행
3. 필요한 최소 `compileJava` 또는 해당 compile 수행
4. 필요한 targeted test 수행
5. PASS 결과를 release gate로 사용

전체 `build`가 필요하지 않다면 습관적으로 실행하지 않는다.

## Timeout / 느려짐 규칙

Gradle 명령이 5분 이상 무응답이어도 **같은 명령을 즉시 재실행하지 않는다.**

timeout 발생 시:
1. 기존 Gradle/JVM 프로세스가 남아 있는지 확인
2. 다른 Agent가 Gradle을 실행 중인지 확인
3. CPU / memory / disk 경합 가능성을 먼저 기록
4. 중복 실행하지 않는다
5. 필요하면 `TIMEOUT / INCOMPLETE`로 handoff한다

`--no-daemon`도 single-use JVM을 실행하므로 동시 실행 제한 대상이다.

timeout은 곧바로 코드 실패를 의미하지 않는다.

## 여러 Agent 동시 작업

병렬 허용:
- 코드 읽기/수정
- HTML/CSS/JS 작업
- 문서
- git diff
- 브라우저 QA
- 가벼운 targeted 검증

직렬 실행:
- Gradle compile/test/build/boot 계열
- 장시간 JVM 작업

각 Agent가 자기 작업 종료 시 각각 full Gradle build를 돌리지 않는다. 최종 통합/Release 담당 Agent가 대표로 검증한다.

## 퇴근 / Handoff

퇴근 hard stop이 가까우면 Gradle 완료를 기다리기 위해 퇴근을 늦추지 않는다.

결과가 나오지 않으면:
- 실행 명령
- 시작/timeout 상태
- worktree
- branch / HEAD
- 변경 파일
- 완료된 lightweight 검증
- 다음 세션 첫 단계

를 기록하고 STOP한다.

미완성 상태에서 반복 compile, reset, clean, worktree 삭제를 하지 않는다.

## 보고 형식

최종 보고에 해당되는 항목만 기록:

```
GRADLE HEAVY TASK:
RUN BY:
RESULT: PASS / FAIL / TIMEOUT / NOT RUN
TARGETED TEST:
LIGHTWEIGHT QA:
CONCURRENT GRADLE: NO / YES
RELEASE GATE: PASS / PENDING
```
