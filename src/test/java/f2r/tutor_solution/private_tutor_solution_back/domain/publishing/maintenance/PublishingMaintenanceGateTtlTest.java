package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

/** OPEN 시간 제한(TTL): 가짜 시계로 만료 시점을 제어한다. */
class PublishingMaintenanceGateTtlTest {
  private static final long SEC = TimeUnit.SECONDS.toNanos(1);
  @TempDir Path dir;
  AtomicLong now = new AtomicLong(1_000 * SEC);
  PublishingMaintenanceGate gate;

  PublishingMaintenanceGate gate(long ttlSeconds) {
    gate = new PublishingMaintenanceGate(dir.resolve("state").toString(), ttlSeconds, now::get);
    return gate;
  }

  @AfterEach void cleanup() { if (gate != null) gate.close(); }

  @Test void enterBeforeExpiryStillWorks() {
    // given: TTL 10초로 OPEN
    gate(10).resume();
    // when: 9초 경과 후 진입
    now.addAndGet(9 * SEC);
    // then: 정상 진입
    assertDoesNotThrow(() -> { try (var scope = gate.enter()) { assertEquals(1, gate.status().activeOperations()); } });
    assertEquals(PublishingMaintenanceGate.State.OPEN, gate.status().state());
  }

  @Test void enterAfterExpiryIsRefusedAndGateClosesWhenIdle() {
    // given
    gate(10).resume();
    // when: TTL 경과 후 진입 시도
    now.addAndGet(10 * SEC);
    // then: 503, 유휴 상태이므로 곧바로 CLOSED
    var e = assertThrows(ResponseStatusException.class, gate::enter);
    assertEquals(503, e.getStatusCode().value());
    var status = gate.status();
    assertEquals(PublishingMaintenanceGate.State.CLOSED, status.state());
    assertTrue(status.singleProcessDrained());
    assertNull(status.openRemainingSeconds());
  }

  @Test void runningOperationSurvivesExpiryAndGateClosesWhenItEnds() throws Exception {
    // given: 진행 중인 operation과 CLI 하나
    gate(10).resume();
    Process process;
    try (var root = gate.enter()) {
      process = new ProcessBuilder("sh", "-c", "sleep 0.3").start();
      PublishingMaintenanceGate.trackCurrentProcess(process);
      // when: 작업 중에 TTL 만료
      now.addAndGet(11 * SEC);
      gate.tick();
      // then: DRAINING, 활성 작업은 그대로, 새 요청은 거절, 같은 operation 내부 재진입은 허용
      var status = gate.status();
      assertEquals(PublishingMaintenanceGate.State.DRAINING, status.state());
      assertEquals(1, status.activeOperations());
      assertThrows(ResponseStatusException.class, () -> inOtherThread(gate::enter));
      try (var nested = gate.enter()) { assertEquals(1, gate.status().activeOperations()); }
    }
    // then: CLI가 실제로 끝나야 CLOSED
    assertEquals(PublishingMaintenanceGate.State.DRAINING, gate.status().state());
    process.waitFor();
    assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> {
      while (!gate.status().singleProcessDrained()) Thread.sleep(10);
    });
    assertEquals(PublishingMaintenanceGate.State.CLOSED, gate.status().state());
  }

  @Test void tickClosesAnIdleExpiredGateAndPersistsClosedMarker() throws Exception {
    // given
    gate(5).resume();
    assertEquals("OPEN", Files.readString(dir.resolve("state/state")));
    // when: 요청 없이 시계만 진행되고 스케줄러가 tick 호출
    now.addAndGet(5 * SEC);
    gate.tick();
    // then
    assertEquals("CLOSED", Files.readString(dir.resolve("state/state")));
    assertEquals(PublishingMaintenanceGate.State.CLOSED, gate.status().state());
  }

  @Test void tickIsIdempotentWhenAlreadyClosedOrOpen() {
    // given: 한 번도 열리지 않은 gate와 아직 만료되지 않은 gate
    gate(10);
    // when/then
    for (int i = 0; i < 3; i++) { gate.tick(); }
    assertEquals(PublishingMaintenanceGate.State.CLOSED, gate.status().state());
    gate.resume();
    for (int i = 0; i < 3; i++) { gate.tick(); }
    assertEquals(PublishingMaintenanceGate.State.OPEN, gate.status().state());
  }

  @Test void resumeAfterExpiryStartsAFreshDeadline() {
    // given: 만료로 닫힌 gate
    gate(10).resume();
    now.addAndGet(10 * SEC);
    gate.tick();
    assertEquals(PublishingMaintenanceGate.State.CLOSED, gate.status().state());
    // when: 다시 resume하고 6초 경과
    gate.resume();
    now.addAndGet(6 * SEC);
    // then: 새 deadline 기준 4초 남음, 아직 OPEN
    assertEquals(4L, gate.status().openRemainingSeconds());
    now.addAndGet(4 * SEC);
    assertEquals(PublishingMaintenanceGate.State.CLOSED, gate.status().state());
  }

  @Test void manualDrainClearsTheDeadline() {
    // given
    gate(10).resume();
    // when
    var drained = gate.drain();
    // then: 응답에 남은 시간이 없고, 시간이 지나도 상태가 바뀌지 않는다
    assertNull(drained.openRemainingSeconds());
    now.addAndGet(100 * SEC);
    gate.tick();
    assertEquals(PublishingMaintenanceGate.State.CLOSED, gate.status().state());
  }

  @Test void explicitZeroMeansNoLimit() {
    // given: TTL 0(명시)
    gate(0).resume();
    // when: 아주 오랜 시간이 지나도
    now.addAndGet(10_000_000L * SEC);
    gate.tick();
    // then: OPEN 유지, 남은 시간 표시 없음
    var status = gate.status();
    assertEquals(PublishingMaintenanceGate.State.OPEN, status.state());
    assertNull(status.openRemainingSeconds());
    assertDoesNotThrow(() -> { try (var scope = gate.enter()) { } });
  }

  @Test void statusShowsRemainingSecondsRoundedUpOnlyWhileOpen() {
    // given: TTL 100초
    gate(100);
    assertNull(gate.status().openRemainingSeconds()); // CLOSED
    gate.resume();
    assertEquals(100L, gate.status().openRemainingSeconds());
    // when: 30.5초 경과
    now.addAndGet(30 * SEC + SEC / 2);
    // then: 69.5초 → 올림 70
    assertEquals(70L, gate.status().openRemainingSeconds());
  }

  @Test void defaultConstructorUses1800SecondsAndRealClock() {
    // given: 시계를 주입하지 않은 기본 생성자
    gate = new PublishingMaintenanceGate(dir.resolve("state").toString());
    // when
    gate.resume();
    // then
    long remaining = gate.status().openRemainingSeconds();
    assertTrue(remaining >= 1799 && remaining <= 1800, "remaining=" + remaining);
    assertEquals(1800, PublishingMaintenanceGate.DEFAULT_OPEN_TTL_SECONDS);
  }

  @Test void blankNegativeOrInvalidTtlFallsBackToDefaultAndOnlyExplicitZeroDisables() {
    // given/when/then
    assertEquals(1800, PublishingMaintenanceGate.parseTtlSeconds(null));
    assertEquals(1800, PublishingMaintenanceGate.parseTtlSeconds(""));
    assertEquals(1800, PublishingMaintenanceGate.parseTtlSeconds("  "));
    assertEquals(1800, PublishingMaintenanceGate.parseTtlSeconds("abc"));
    assertEquals(1800, PublishingMaintenanceGate.parseTtlSeconds("-5"));
    assertEquals(0, PublishingMaintenanceGate.parseTtlSeconds("0"));
    assertEquals(0, PublishingMaintenanceGate.parseTtlSeconds(" 0 "));
    assertEquals(600, PublishingMaintenanceGate.parseTtlSeconds("600"));
  }

  @Test void restartIsAlwaysClosedEvenIfTheMarkerSaidOpen() {
    // given: TTL이 남은 OPEN gate를 정상 종료
    gate(1000).resume();
    gate.close();
    // when: 같은 디렉터리로 재시작
    try (var restarted = new PublishingMaintenanceGateTest.AutoGate(dir.resolve("state"))) {
      // then
      assertEquals(PublishingMaintenanceGate.State.CLOSED, restarted.gate.status().state());
      assertNull(restarted.gate.status().openRemainingSeconds());
      assertThrows(ResponseStatusException.class, restarted.gate::enter);
    }
  }

  // ---- 경쟁 상태 ----

  @Test void admissionsRacingTheExpiryNeverEnterAfterExpiryAndInFlightWorkCompletes() throws Exception {
    // given: 8개 스레드가 계속 진입을 시도하는 OPEN gate
    gate(10).resume();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    AtomicBoolean expired = new AtomicBoolean();
    AtomicInteger enteredAfterExpiry = new AtomicInteger();
    AtomicInteger completed = new AtomicInteger();
    CountDownLatch go = new CountDownLatch(1);
    List<Future<?>> tasks = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      tasks.add(pool.submit(() -> {
        go.await();
        for (int n = 0; n < 2000; n++) {
          boolean afterExpiry = expired.get();
          try (var scope = gate.enter()) {
            if (afterExpiry) enteredAfterExpiry.incrementAndGet();
            Thread.onSpinWait();
            completed.incrementAndGet();
          } catch (ResponseStatusException closed) {
            // 만료 이후의 거절은 정상
          }
        }
        return null;
      }));
    }
    go.countDown();
    // when: 진행 도중에 시계를 TTL 너머로 이동
    Thread.sleep(5);
    now.addAndGet(11 * SEC);
    expired.set(true);
    for (var task : tasks) task.get(10, TimeUnit.SECONDS);
    pool.shutdownNow();
    // then: 만료를 확인한 뒤 시작한 진입은 0건, 활성 작업은 모두 정상 종료, 최종 CLOSED
    assertEquals(0, enteredAfterExpiry.get());
    assertTrue(completed.get() > 0);
    var status = gate.status();
    assertEquals(0, status.activeOperations());
    assertEquals(PublishingMaintenanceGate.State.CLOSED, status.state());
    assertTrue(status.singleProcessDrained());
  }

  @Test void tickDrainAndEnterHammeredTogetherEndClosedWithoutLeaks() throws Exception {
    // given
    gate(1).resume();
    ExecutorService pool = Executors.newFixedThreadPool(9);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<?>> tasks = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      tasks.add(pool.submit(() -> {
        go.await();
        for (int n = 0; n < 1500; n++) { try (var scope = gate.enter()) { } catch (ResponseStatusException ignored) { } }
        return null;
      }));
    }
    tasks.add(pool.submit(() -> { go.await(); for (int n = 0; n < 1500; n++) gate.tick(); return null; }));
    tasks.add(pool.submit(() -> { go.await(); for (int n = 0; n < 1500; n++) gate.status(); return null; }));
    tasks.add(pool.submit(() -> { go.await(); for (int n = 0; n < 50; n++) gate.drain(); return null; }));
    // when: 동시에 시계도 진행
    go.countDown();
    for (int i = 0; i < 20; i++) { now.addAndGet(SEC / 4); Thread.sleep(1); }
    for (var task : tasks) task.get(10, TimeUnit.SECONDS);
    pool.shutdownNow();
    // then: 예외 없이 끝나고 활성 작업 0, CLOSED(수동 drain이 있었으므로 TTL 상태와 무관)
    var status = gate.status();
    assertEquals(0, status.activeOperations());
    assertEquals(PublishingMaintenanceGate.State.CLOSED, status.state());
  }

  private static void inOtherThread(Callable<?> call) throws Throwable {
    ExecutorService single = Executors.newSingleThreadExecutor();
    try {
      single.submit(call).get(3, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      throw e.getCause();
    } finally {
      single.shutdownNow();
    }
  }
}
