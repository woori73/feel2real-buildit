package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto;

import java.util.List;

public class TestPrepDtos {

  public record ExamListItem(
      Long examId, String examCode, String kind, String title, String subtitle,
      String description, String difficulty, Integer timeLimitSec, long questionCount) {}

  /** koHelpJson: 해설란 한글 도움 자료(JSON 문자열, 없으면 null). 화면이 해설 버튼을 누를 때만 보여 준다. */
  public record RunQuestion(
      Long questionId, String section, String taskType, String prompt, String bodyText,
      String audioMode, List<String> choices, Integer orderNo, boolean autoScore, String koHelpJson) {}

  public record AttemptView(
      Long attemptId, String examCode, String kind, String title, String status,
      String deadlineAt, long remainingSec, List<RunQuestion> questions,
      java.util.Map<Long, String> savedAnswers, String bandDisclaimer) {}

  /** scaled: SAT 영역 추정 점수(200~800). TOEFL 등 다른 종류는 null. */
  public record SectionScore(
      String section, int totalCount, int autoCount, int answered, int correct,
      Double accuracy, Double band, Integer scaled) {}

  public record Scorecard(
      Long attemptId, String examTitle, String submittedAt, boolean overtime,
      Double overallBand, Double accuracy, List<SectionScore> sections,
      String bandDisclaimer, String kind, Integer totalScaled) {}

  public record ReviewItem(
      Long questionId, String section, String taskType, String prompt, String bodyText,
      String audioMode, List<String> choices, String yourAnswer, String correctAnswer,
      Boolean isCorrect, boolean autoScore, String explanation, Integer orderNo, String koHelpJson) {}

  public record ReviewView(
      Long attemptId, String examTitle, String kind, String submittedAt, boolean overtime,
      Double overallBand, Double accuracy, List<SectionScore> sections,
      List<ReviewItem> items, String bandDisclaimer, Integer totalScaled) {}

  public record AttemptSummary(
      Long attemptId, String examCode, String kind, String examTitle, String status,
      String startedAt, String submittedAt, Double overallBand, Double accuracy,
      int questionCount, Integer totalScaled) {}

  public record WrongItem(
      Long questionId, Long examId, String examCode, String examTitle, String kind,
      String section, String taskType, String promptShort, String lastWrongAt) {}

  public record LearningView(
      long totalAttempts, long submittedAttempts, long practiceAttempts, long vocabAttempts,
      Double bestBand, List<SectionScore> latestSectionScores, long wrongCount,
      List<WrongItem> wrongItems, String bandDisclaimer) {}

  public record StartAttemptRequest(String examCode, List<Long> questionIds) {}

  public record SaveAnswerRequest(Long questionId, String answerText) {}

  public record SubmitRequest(List<SaveAnswerRequest> answers) {}

  private TestPrepDtos() {}
}
