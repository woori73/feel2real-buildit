package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ChartPublicServiceKstTest {

  @Test
  void toKst_LocalDateTime과_Timestamp를_한국_시간으로_바꾼다() {
    // given: 서버 시계로 기록된 게시 시각
    LocalDateTime server = LocalDateTime.of(2026, 11, 9, 16, 30);

    // when
    Object fromLocal = ChartPublicService.toKst(server);
    Object fromTimestamp = ChartPublicService.toKst(Timestamp.valueOf(server));

    // then: 두 형식 모두 같은 한국 시간이 된다
    assertThat(fromLocal).isEqualTo(KstTime.toKst(server));
    assertThat(fromTimestamp).isEqualTo(KstTime.toKst(server));
  }

  @Test
  void toKst_null이나_다른_형식은_그대로_둔다() {
    // given / when / then
    assertThat(ChartPublicService.toKst(null)).isNull();
    assertThat(ChartPublicService.toKst("2026-11-09")).isEqualTo("2026-11-09");
  }
}
