package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardWidgetResponse;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
  @DisplayName("여러 스레드가 동시에 넣어도 상한을 지키고 예외가 없다")
  void put_concurrent_staysBounded() throws Exception {
    // given
    var bounded = new EasyDashboardWidgetCache(50, TTL, now::get);
    ExecutorService pool = Executors.newFixedThreadPool(8);

    // when
    for (int t = 0; t < 8; t++) {
      final int base = t * 10_000;
      pool.submit(() -> {
        for (int i = 0; i < 2_000; i++) {
          bounded.put("k" + (base + i), response("x"));
          bounded.get("k" + (base + i / 2));
        }
      });
    }
    pool.shutdown();

    // then
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    assertThat(bounded.size()).isLessThanOrEqualTo(50);
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
