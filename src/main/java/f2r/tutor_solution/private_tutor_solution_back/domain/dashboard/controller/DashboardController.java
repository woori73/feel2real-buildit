package f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto.ScoreTimelineResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto.WeaknessRadarResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto.WrongAnswerResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.service.ScoreTimelineService;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.service.WeaknessAnalysisService;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.service.WrongAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 카드 데이터.
 *
 * <ul>
 *   <li>AI 자동채점 성취도 리포트 (radar) — 한 대화에 첨부된 시험지의 카테고리별 정답률</li>
 *   <li>성적 변화 추이 (line) — 한 대화에 첨부된 시험 점수 시계열.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class DashboardController {

  private final WeaknessAnalysisService weaknessAnalysisService;
  private final ScoreTimelineService scoreTimelineService;
  private final WrongAnswerService wrongAnswerService;
  private final AuthenticatedUserResolver authResolver;

  @GetMapping("/api/conversations/{id}/weakness")
  public WeaknessRadarResponse getWeakness(@PathVariable("id") Long id) {
    // conversationId 기반 — ownership 검증은 service 단에서 별도 (TODO).
    return weaknessAnalysisService.analyzeForConversation(id);
  }

  /**
   * 한 대화에 첨부된 시험지의 오답노트.
   * ownership 검증은 {@link WrongAnswerService#analyzeForConversation}에서 수행.
   */
  @GetMapping("/api/conversations/{id}/wrong-answers")
  public WrongAnswerResponse getWrongAnswers(@PathVariable("id") Long id) {
    return wrongAnswerService.analyzeForConversation(id);
  }

  /**
   * 한 대화에 첨부된 시험지의 점수 시계열.
   * ownership 검증은 {@link ScoreTimelineService#forConversation}에서 수행.
   */
  @GetMapping("/api/conversations/{id}/score-timeline")
  public ScoreTimelineResponse getConversationScoreTimeline(@PathVariable("id") Long id) {
    return scoreTimelineService.forConversation(id);
  }

  /**
   * 자기 학생의 시험 점수 시계열. path의 {@code studentId}는 무시되지 않고 ADMIN이 임퍼소네이트할 때만 의미가 있다 —
   * 학생이 자기와 다른 ID를 넣으면 403.
   */
  @GetMapping("/api/students/{studentId}/score-timeline")
  public ScoreTimelineResponse getScoreTimeline(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable("studentId") Long pathStudentId) {
    Long studentId = authResolver.resolveStudentId(principal, pathStudentId);
    return scoreTimelineService.forStudent(studentId);
  }
}
