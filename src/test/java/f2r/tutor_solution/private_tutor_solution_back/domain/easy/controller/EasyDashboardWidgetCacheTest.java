package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardWidgetResponse;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EASY 위젯 응답 캐시 — 항목 수 상한·만료 제거")
class EasyDashboardWidgetCacheTest {

  private static final long TTL = 1_000L;

  private AtomicLong now;
  private EasyDashboardWidgetCache cache;

  private static EasyDashboardWidgetResponse response(String marker) {
    return new EasyDashboardWidgetResponse("1.0", "easy", marker, List.of());
  }

  @BeforeEach
  void setUp() {
    now = new AtomicLong(10_000L);
    cache = new EasyDashboardWidgetCache(3, TTL, now::get);
  }

  @Test
  @DisplayName("TTL 안에서는 저장한 응답을 그대로 돌려준다")
  void get_withinTtl_returnsStoredResponse() {
    // given
    cache.put("1:2026-10", response("a"));
    now.addAndGet(TTL - 1);

    // when
    var hit = cache.get("1:2026-10");

    // then
    assertThat(hit).isPresent();
    assertThat(hit.get().generatedAt()).isEqualTo("a");
  }

  @Test
  @DisplayName("TTL이 지나면 조회 시 비어 있고 항목도 제거된다")
  void get_afterTtl_returnsEmptyAndRemovesEntry() {
    // given
    cache.put("1:2026-10", response("a"));
    now.addAndGet(TTL);

    // when
    var hit = cache.get("1:2026-10");

    // then
    assertThat(hit).isEmpty();
    assertThat(cache.size()).isZero();
  }

  @Test
  @DisplayName("저장할 때 이미 만료된 항목은 한꺼번에 제거된다 (다시 조회되지 않아도)")
  void put_purgesExpiredEntriesNeverReadAgain() {
    // given: 서로 다른 키 2개를 넣고 아무도 다시 조회하지 않은 채 TTL이 지남
    cache.put("1:2026-01", response("a"));
    cache.put("2:2026-01", response("b"));
    now.addAndGet(TTL + 1);

    // when
    cache.put("3:2026-10", response("c"));

    // then
    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.get("1:2026-01")).isEmpty();
    assertThat(cache.get("3:2026-10")).isPresent();
  }

  @Test
  @DisplayName("항목 수가 상한에 닿으면 가장 오래된 항목부터 밀어낸다")
  void put_atMaxEntries_evictsOldestFirst() {
    // given
    cache.put("k1", response("1"));
    now.addAndGet(1);
    cache.put("k2", response("2"));
    now.addAndGet(1);
    cache.put("k3", response("3"));
    now.addAndGet(1);

    // when
    cache.put("k4", response("4"));

    // then
    assertThat(cache.size()).isEqualTo(3);
    assertThat(cache.get("k1")).isEmpty();
    assertThat(cache.get("k2")).isPresent();
    assertThat(cache.get("k3")).isPresent();
    assertThat(cache.get("k4")).isPresent();
  }

  @Test
  @DisplayName("같은 키를 다시 저장하면 새 응답으로 바뀌고 가장 최신 항목이 되며 개수는 늘지 않는다")
  void put_sameKey_replacesAndRefreshesWithoutGrowing() {
    // given
    cache.put("k1", response("old"));
    cache.put("k2", response("2"));
    cache.put("k3", response("3"));

    // when: k1을 갱신한 뒤 새 키가 들어오면 k2가 가장 오래된 항목이다
    cache.put("k1", response("new"));
    cache.put("k4", response("4"));

    // then
    assertThat(cache.size()).isEqualTo(3);
    assertThat(cache.get("k1").orElseThrow().generatedAt()).isEqualTo("new");
    assertThat(cache.get("k2")).isEmpty();
  }

  @Test
  @DisplayName("서로 다른 키를 아무리 많이 넣어도 크기는 상한을 넘지 않는다")
  void put_manyDistinctKeys_neverExceedsMax() {
    // when
    for (int i = 0; i < 10_000; i++) {
      cache.put("biz-" + i + ":2026-10", response("x"));
    }

    // then
    assertThat(cache.size()).isEqualTo(3);
  }

  @Test
  @DisplayName("기본 설정(최대 200개)에서 여러 스레드가 서로 다른 키를 동시에 넣어도 실행 중 어느 순간에도 200개를 넘지 않는다")
  void put_concurrentDefaultCache_neverExceeds200AtAnyObservedMoment() throws Exception {
    // given
    var shared = new EasyDashboardWidgetCache();
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    AtomicInteger maxObserved = new AtomicInteger();
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);

    // when: 모두 동시에 시작해 각자 다른 키 5,000개를 넣으면서 매번 크기를 관측한다
    for (int t = 0; t < threads; t++) {
      final int base = t * 100_000;
      pool.submit(() -> {
        start.await();
        for (int i = 0; i < 5_000; i++) {
          shared.put("biz-" + (base + i) + ":2026-10", response("x"));
          shared.get("biz-" + (base + i / 2) + ":2026-10");
          maxObserved.accumulateAndGet(shared.size(), Math::max);
        }
        return null;
      });
    }
    start.countDown();
    pool.shutdown();

    // then
    assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
    assertThat(maxObserved.get()).isLessThanOrEqualTo(EasyDashboardWidgetCache.DEFAULT_MAX_ENTRIES);
    assertThat(shared.size()).isEqualTo(EasyDashboardWidgetCache.DEFAULT_MAX_ENTRIES);
  }

  @Test
  @DisplayName("기본 상한은 200개, 수명은 5분이다")
  void defaults_are200EntriesAnd5Minutes() {
    assertThat(EasyDashboardWidgetCache.DEFAULT_MAX_ENTRIES).isEqualTo(200);
    assertThat(EasyDashboardWidgetCache.DEFAULT_TTL_MILLIS).isEqualTo(5 * 60 * 1000L);
  }

  @Test
  @DisplayName("clear는 모든 항목을 비운다")
  void clear_removesEverything() {
    // given
    cache.put("k1", response("1"));

    // when
    cache.clear();

    // then
    assertThat(cache.size()).isZero();
  }

  @Test
  @DisplayName("maxEntries가 1 미만이면 생성할 수 없다")
  void constructor_rejectsNonPositiveMax() {
    assertThatThrownBy(() -> new EasyDashboardWidgetCache(0, TTL, now::get))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
