package f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 인스타 자동 피드의 모든 시각은 서울 시간으로 저장·비교한다(서버 기본 시간대와 무관). */
public final class SnsTime {
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  private SnsTime() {}

  public static LocalDateTime now() {
    return LocalDateTime.now(ZONE);
  }
}
