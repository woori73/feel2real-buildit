package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

/**
 * FEEL2REAL Hub 공통 위젯 API 스키마 v1.
 *
 * <p>읽기 전용. 기존 EASY 비즈니스 로직을 재사용하고 스키마만 변환한다.
 * 미구현 데이터(전년동월 대비, 주간 집계, 세금 예상)는 포함하지 않는다.
 */
public record EasyDashboardWidgetResponse(
    String version,
    String service,
    String generatedAt,
    List<Widget> widgets
) {
  public record Widget(
      String id,
      String title,
      String type,
      Object data,
      String link,
      Integer refreshIntervalSec,
      String error,
      String dataAsOf
  ) {}

  public record StatCardsData(
      List<StatCard> cards
  ) {}

  public record StatCard(
      String label,
      long value,
      String unit,
      String trend,
      String trendDirection
  ) {}

  public record ListData(
      List<ListItem> items,
      String emptyText
  ) {}

  public record ListItem(
      String title,
      String subtitle,
      String meta
  ) {}
}
