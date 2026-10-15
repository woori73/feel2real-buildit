package f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto;

import java.util.List;

/** 공개 교육자 검색 결과 페이지. */
public record ChartPublicEducatorPage(
    List<ChartPublicEducatorSummary> content,
    long totalElements,
    int totalPages,
    int page,
    int size
) {
  public static ChartPublicEducatorPage of(List<ChartPublicEducatorSummary> content, long totalElements,
      int page, int size) {
    int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
    return new ChartPublicEducatorPage(content, totalElements, totalPages, page, size);
  }
}
