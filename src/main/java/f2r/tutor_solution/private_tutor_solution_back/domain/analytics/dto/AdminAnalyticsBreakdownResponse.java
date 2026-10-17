package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 Analytics 세분화(서비스·페이지·추세·전환). 모두 실제 수집된 값만 담는다 — 수집하지 않는 지표는
 * {@code collected=false}로 표시하고 숫자를 넣지 않는다. 사람 방문(HUMAN)만 집계하고 Bot 요청은 제외한다.
 */
public record AdminAnalyticsBreakdownResponse(
    LocalDate statsSince,
    List<Row> services,
    List<Row> pageGroups,
    List<String> pagesWithoutRoute,
    List<TopPath> topPaths,
    List<DayPoint> trend,
    String trendService,
    List<Conversion> conversions,
    Definitions definitions) {

  public record Period(long today, long recent7, long recent30, long total) {}

  /** key는 서비스/페이지 그룹 enum 이름, label은 화면 표시 이름. */
  public record Row(String key, String label, Period visitors, Period views) {}

  public record TopPath(String path, String service, long views30, long visitors30) {}

  public record DayPoint(LocalDate date, long visitors, long views) {}

  /** collected=false면 metric은 null이며 화면은 '미수집'으로 표시한다. */
  public record Conversion(String key, String label, boolean collected, Period metric, String note) {}

  public record Definitions(String uv, String pv, String bot, String additivity, String periods) {}
}
