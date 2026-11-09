package f2r.tutor_solution.private_tutor_solution_back.config;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 서버 기본 시간대(2026-11-09 대표 결정: 모든 자료를 한국 시간으로 저장·표시).
 *
 * <p>운영 EC2의 OS 시간대는 UTC라서 {@code LocalDateTime.now()}·{@code @CreationTimestamp}·
 * {@code @CreatedDate}가 모두 UTC 벽시계로 저장됐다. 애플리케이션 시작 직후 JVM 기본 시간대를
 * 서울로 고정하고, DB 세션 시간대도 같은 값으로 맞춘다(application-*.yaml의
 * {@code spring.datasource.hikari.connection-init-sql}). 기존 UTC 자료는 Flyway V1104가 보정한다.
 */
public final class ServerTimeZone {

  public static final String ID = "Asia/Seoul";
  public static final ZoneId ZONE = ZoneId.of(ID);

  private ServerTimeZone() {}

  /** JVM 기본 시간대를 서울로 바꾼다. 스프링 컨텍스트를 만들기 전에 호출해야 한다. */
  public static void applyDefault() {
    TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
  }
}
