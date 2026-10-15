package f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto.ScoreTimelineResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.score.entity.StudentScore;
import f2r.tutor_solution.private_tutor_solution_back.domain.score.repository.StudentScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 한 학생의 시험 점수 시계열 — line chart 데이터.
 *
 * <p>StudentScoreRepository.findTimelineByStudent로 가져온 row를 시점(연도/학기/시험종류) 기준으로
 * 정렬하고 accuracyPercent(%)와 함께 응답 record로 변환한다. 같은 시점에 학교가 달라 동률이 나는
 * 경우는 학생 확정(=업로드) 시각이 tiebreaker.
 */
@Service
@RequiredArgsConstructor
public class ScoreTimelineService {

  /**
   * 성적 변화 추이용 정렬 키.
   *
   * <ol>
   *   <li>examYear ASC (null은 끝으로)</li>
   *   <li>examSemester ASC (null은 끝으로)</li>
   *   <li>examType ordinal ASC (MIDTERM=0, FINAL=1, MOCK=2, OTHER=3 — enum 선언 순서)</li>
   *   <li>confirmedAt ASC — 같은 시점에 학교가 다를 때 업로드 순서 tiebreaker</li>
   * </ol>
   */
  private static final Comparator<StudentScore> TIMELINE_ORDER = Comparator
      .<StudentScore, Short>comparing(
          s -> s.getExam().getExamYear(),
          Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(
          s -> s.getExam().getExamSemester(),
          Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparingInt(s -> s.getExam().getExamType().ordinal())
      .thenComparing(StudentScore::getConfirmedAt);

  private final StudentScoreRepository studentScoreRepository;
  private final ConversationRepository conversationRepository;
  private final ConversationSourceRepository conversationSourceRepository;
  private final AuthenticatedUserResolver authResolver;

  @Transactional(readOnly = true)
  public ScoreTimelineResponse forStudent(Long studentId) {
    List<ScoreTimelineResponse.Point> points = studentScoreRepository.findTimelineByStudent(studentId).stream()
        .sorted(TIMELINE_ORDER)
        .map(this::toPoint)
        .toList();
    return new ScoreTimelineResponse(points);
  }

  /**
   * 특정 conversation에 첨부된 시험 결과만 반환한다.
   * 성적은 학생 계정 단위로 저장되지만, 화면 표시/분석 컨텍스트는 학습기록(conversation) 단위로 격리한다.
   */
  @Transactional(readOnly = true)
  public ScoreTimelineResponse forConversation(Long conversationId) {
    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationId));
    authResolver.requireStudentOwnedConversationAccess(conversation);

    List<Long> sourceIds = conversationSourceRepository.findByConversationId(conversationId).stream()
        .map(cs -> cs.getSourceId())
        .toList();
    if (sourceIds.isEmpty()) {
      return new ScoreTimelineResponse(List.of());
    }

    Long studentId = conversation.getStudent().getStudentId();
    List<ScoreTimelineResponse.Point> points = studentScoreRepository
        .findTimelineByStudentAndSources(studentId, sourceIds)
        .stream()
        .sorted(TIMELINE_ORDER)
        .map(this::toPoint)
        .toList();
    return new ScoreTimelineResponse(points);
  }

  private ScoreTimelineResponse.Point toPoint(StudentScore s) {
    int total   = s.getTotalQuestions() != null ? s.getTotalQuestions() : 0;
    int correct = s.getCorrectCount()    != null ? s.getCorrectCount()    : 0;
    Integer totalPoints  = s.getTotalPoints();
    Integer earnedPoints = s.getEarnedPoints();

    // 점수가 있으면 점수 기반, 없으면 정답률 기반으로 percent 계산
    int percent;
    if (totalPoints != null && totalPoints > 0) {
      int earned = earnedPoints != null ? earnedPoints : 0;
      percent = (int) Math.round((double) earned / totalPoints * 100.0);
    } else {
      percent = total > 0 ? (int) Math.round((double) correct / total * 100.0) : 0;
    }
    return new ScoreTimelineResponse.Point(
        s.getExam().getExamId(),
        s.getExam().getDisplayName(),
        s.getExam().getExamType() != null ? s.getExam().getExamType().name() : null,
        s.getExam().getExamDate(),
        total,
        correct,
        totalPoints,
        earnedPoints,
        percent,
        s.getConfirmedAt());
  }
}
