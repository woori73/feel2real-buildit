package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stale snapshot 감지 회귀 테스트 (PHASE 4, 스키마 변경 없음).
 *
 * <ul>
 *   <li>snapshot이 없으면 재채점 대상이다.</li>
 *   <li>profile/evidence 변경이 snapshot보다 새로우면 재채점 대상이다.</li>
 *   <li>snapshot이 최신이면 대상이 아니다.</li>
 * </ul>
 */
class ChartStaleSnapshotTest {

  private final ChartSiteToolService service = new ChartSiteToolService(mock(JdbcTemplate.class));

  @Test
  void snapshotStale_noSnapshotRequiresRescore() {
    assertThat(ChartSiteToolService.snapshotStale(LocalDateTime.now(), null)).isTrue();
    assertThat(ChartSiteToolService.snapshotStale(null, null)).isTrue();
  }

  @Test
  void snapshotStale_dataNewerThanSnapshotRequiresRescore() {
    LocalDateTime snapshot = LocalDateTime.of(2026, 9, 14, 10, 0);
    LocalDateTime enriched = LocalDateTime.of(2026, 9, 18, 12, 0);

    assertThat(ChartSiteToolService.snapshotStale(enriched, snapshot)).isTrue();
  }

  @Test
  void snapshotStale_upToDateSnapshotRequiresNothing() {
    LocalDateTime snapshot = LocalDateTime.of(2026, 9, 18, 12, 0);

    assertThat(ChartSiteToolService.snapshotStale(
        LocalDateTime.of(2026, 9, 14, 10, 0), snapshot)).isFalse();
    assertThat(ChartSiteToolService.snapshotStale(snapshot, snapshot)).isFalse();
    assertThat(ChartSiteToolService.snapshotStale(null, snapshot)).isFalse();
  }

  @Test
  void serviceConstructsWithoutDb() {
    assertThat(service).isNotNull();
  }
}
