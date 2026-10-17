package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse.Period;
import java.time.LocalDate;
import java.util.List;

/**
 * 이벤트(섹션 노출·클릭·서비스 이동·흐름 시작) 통계. 페이지 조회(PV)와 별개 지표이며 서로 합산하지 않는다.
 * 사람(HUMAN)·비내부(관리자 제외) 이벤트만 집계한다.
 */
public record AdminAnalyticsEventsResponse(
    LocalDate eventsSince,
    int retentionDays,
    List<SectionRow> sections,
    List<ClickRow> clicks,
    List<EventRow> serviceMoves,
    List<EventRow> flowStarts,
    Period consultCompleted,
    Definitions definitions) {

  /** exposures = 노출된 페이지 방문 수(view_id), visitors = 노출 UV. */
  public record SectionRow(String key, String label, Period visitors, Period exposures) {}

  /** clicks = 클릭 횟수, visitors = 클릭 UV, ctr = 클릭한 방문 ÷ 섹션 노출 방문(30일, 섹션 없는 버튼은 null). */
  public record ClickRow(String key, String label, String sectionKey, String sectionLabel, Period visitors, Period clicks,
      Double ctr30, Double ctrToday, Double ctr7, Double ctrTotal) {}

  public record EventRow(String key, String label, Period visitors, Period counts) {}

  public record Definitions(String exposure, String click, String ctr, String separation, String retention) {}
}
