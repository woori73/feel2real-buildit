package f2r.tutor_solution.private_tutor_solution_back.config;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 서버가 기록한 시각을 화면용 한국 시간으로 바꾼다(2026-11-09 대표 지시: 위키·관리자 통계는 한국 시간으로).
 *
 * <p>{@code LocalDateTime.now()}·{@code @CreatedDate}·{@code @CreationTimestamp}로 저장된 값은 서버 기본
 * 시간대(운영 EC2 = UTC)의 벽시계다. 저장값은 그대로 두고, 화면에 내보낼 때와 한국 날짜 기준으로 셀 때만 바꾼다.
 * 서버 기본 시간대를 읽어 계산하므로 서버가 이미 한국 시간이면 값이 바뀌지 않는다.
 * 이미 한국 시간으로 기록하는 칸(방문·다운로드 기록, 인스타 자동 피드 등)에는 쓰지 않는다.
 */
public final class KstTime {

  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  private KstTime() {}

  /** 서버 기본 시간대 벽시계 → 한국 시간 벽시계. */
  public static LocalDateTime toKst(LocalDateTime serverTime) {
    return convert(serverTime, ZoneId.systemDefault(), ZONE);
  }

  /** 한국 시간 벽시계(예: 한국 날짜 0시) → 서버 기본 시간대 벽시계. DB 비교 조건에 쓴다. */
  public static LocalDateTime fromKst(LocalDateTime kstTime) {
    return convert(kstTime, ZONE, ZoneId.systemDefault());
  }

  static LocalDateTime convert(LocalDateTime value, ZoneId from, ZoneId to) {
    return value == null ? null : value.atZone(from).withZoneSameInstant(to).toLocalDateTime();
  }
}
