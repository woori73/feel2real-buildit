package f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 한 학생의 시험 점수 시계열 — line chart 데이터.
 *
 * <p>points는 confirmedAt 오름차순으로 정렬되어 있어 그대로 X축으로 사용 가능하다.
 * accuracyPercent는 점수 정보가 있으면 (earnedPoints / totalPoints * 100), 없으면 (correctCount / totalQuestions * 100) 반올림.
 * totalPoints/earnedPoints는 신규 데이터에만 채워지고, 구 데이터는 null이다 — 프론트가 둘 다 표시할지 정답률만 표시할지 판단.
 */
public record ScoreTimelineResponse(List<Point> points) {

  public record Point(
      Long examId,
      String examDisplayName,
      String examType,
      LocalDate examDate,
      int totalQuestions,
      int correctCount,
      Integer totalPoints,
      Integer earnedPoints,
      int accuracyPercent,
      LocalDateTime confirmedAt) {
  }
}
