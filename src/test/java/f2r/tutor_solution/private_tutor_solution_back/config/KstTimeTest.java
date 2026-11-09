package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KstTimeTest {

  private final TimeZone original = TimeZone.getDefault();

  @AfterEach
  void restore() {
    TimeZone.setDefault(original);
  }

  @Test
  void toKst_addsNineHoursWhenServerIsUtc() {
    // given: 운영 EC2처럼 서버 기본 시간대가 UTC이고, 서버가 08:05로 기록한 시각
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    LocalDateTime stored = LocalDateTime.of(2026, 11, 9, 8, 5);

    // when: 화면용으로 바꾸면
    LocalDateTime shown = KstTime.toKst(stored);

    // then: 한국 시간 17:05가 된다
    assertThat(shown).isEqualTo(LocalDateTime.of(2026, 11, 9, 17, 5));
  }

  @Test
  void fromKst_turnsKoreanMidnightIntoServerTime() {
    // given: 서버 기본 시간대가 UTC이고 한국 날짜 11월 9일 0시
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    LocalDateTime kstMidnight = LocalDateTime.of(2026, 11, 9, 0, 0);

    // when: DB 비교용 서버 시각으로 바꾸면
    LocalDateTime server = KstTime.fromKst(kstMidnight);

    // then: 전날 15:00(UTC)이 된다
    assertThat(server).isEqualTo(LocalDateTime.of(2026, 11, 8, 15, 0));
  }

  @Test
  void conversionsAreIdentityWhenServerIsAlreadySeoulAndKeepNull() {
    // given: 서버 기본 시간대가 이미 서울
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Seoul")));
    LocalDateTime t = LocalDateTime.of(2026, 11, 9, 17, 5);

    // when / then: 값이 바뀌지 않고, 빈 값은 빈 값으로 남는다
    assertThat(KstTime.toKst(t)).isEqualTo(t);
    assertThat(KstTime.fromKst(t)).isEqualTo(t);
    assertThat(KstTime.toKst(null)).isNull();
    assertThat(KstTime.fromKst(null)).isNull();
  }
}
