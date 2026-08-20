package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

class AlbAccessStatisticsSchedulerIsolationTest {

  @Test
  void givenAlbPollingEnabled_whenScheduled_thenItDoesNotUseVideoTaskScheduler() throws Exception {
    // given
    Scheduled scheduled = AlbAccessStatisticsWorker.class.getMethod("poll")
        .getAnnotation(Scheduled.class);

    // when / then
    assertThat(scheduled.scheduler()).isEqualTo("albAccessStatisticsScheduler");
  }
}
