package f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto.WeaknessRadarResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.StudentAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.StudentAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.QuestionCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 한 대화에 첨부된 시험지(들)에서 학생의 카테고리별 정답률을 집계해 radar chart 데이터를 만든다.
 *
 * <p>흐름:
 * <ol>
 *   <li>conversation → 학생 + 첨부 source 목록</li>
 *   <li>첨부 source 중 exam 연결된 것들의 exam_id를 모은다.</li>
 *   <li>student_answers JOIN questions(JOIN category) — 학생 + 해당 exam_ids 범위로 조회.</li>
 *   <li>카테고리 시드 5종(vocabulary/grammar/memorization/reading/other)을 빠짐없이 채워서 응답.</li>
 * </ol>
 *
 * <p>시험지가 한 건도 없거나 카테고리 매핑된 답안이 0건이면 모든 축이 0%인 radar를 반환한다 (UI가 "데이터 없음" 표시).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeaknessAnalysisService {

  /** MVP 영어 고정. */
  private static final int ENGLISH_SUBJECT_ID = 1;

  private final ConversationRepository conversationRepository;
  private final ConversationSourceRepository conversationSourceRepository;
  private final SourceRepository sourceRepository;
  private final StudentAnswerRepository studentAnswerRepository;
  private final QuestionCategoryRepository questionCategoryRepository;
  private final AuthenticatedUserResolver authResolver;

  @Transactional(readOnly = true)
  public WeaknessRadarResponse analyzeForConversation(Long conversationId) {
    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new IllegalArgumentException(
            "conversation not found: " + conversationId));
    authResolver.requireStudentOwnedConversationAccess(conversation);
    Long studentId = conversation.getStudent().getStudentId();

    // 1) 첨부된 source 중 exam_id가 있는 것들만 추림
    List<ConversationSource> attached = conversationSourceRepository.findByConversationId(conversationId);
    List<Long> sourceIds = attached.stream().map(ConversationSource::getSourceId).toList();
    List<Source> sources = sourceIds.isEmpty()
        ? List.of()
        : sourceRepository.findAllById(sourceIds);
    List<Long> examIds = sources.stream()
        .map(Source::getExam)
        .filter(Objects::nonNull)
        .map(Exam::getExamId)
        .distinct()
        .toList();

    // 2) 카테고리 시드 5종을 키로 카운터 초기화 (LinkedHashMap으로 순서 보존)
    List<QuestionCategory> categories = questionCategoryRepository.findBySubject_SubjectId(ENGLISH_SUBJECT_ID);
    Map<String, int[]> counters = new LinkedHashMap<>();
    for (QuestionCategory c : categories) {
      counters.put(c.getCategoryNameEn(), new int[]{0, 0});  // [total, correct]
    }

    // 3) 첨부 시험이 있고 답안이 있으면 집계
    if (!examIds.isEmpty()) {
      List<StudentAnswer> answers = studentAnswerRepository.findForRadar(studentId, examIds);
      for (StudentAnswer sa : answers) {
        QuestionCategory cat = sa.getQuestion().getCategory();
        if (cat == null) continue;
        int[] tc = counters.get(cat.getCategoryNameEn());
        if (tc == null) continue;  // 시드 외 카테고리 (이론상 없음)
        tc[0]++;
        if (Boolean.TRUE.equals(sa.getIsCorrect())) tc[1]++;
      }
    }

    // 4) 응답 변환 — 시드 순서 그대로
    List<WeaknessRadarResponse.CategoryStat> stats = new ArrayList<>(categories.size());
    for (QuestionCategory c : categories) {
      int[] tc = counters.get(c.getCategoryNameEn());
      double accuracy = tc[0] > 0
          ? Math.round(((double) tc[1] / tc[0]) * 1000.0) / 10.0
          : 0.0;
      stats.add(new WeaknessRadarResponse.CategoryStat(
          c.getCategoryNameEn(),
          c.getCategoryNameKo(),
          tc[0],
          tc[1],
          accuracy));
    }
    log.debug("weakness radar: conversationId={}, studentId={}, examIds={}, stats={}",
        conversationId, studentId, examIds, stats);
    return new WeaknessRadarResponse(stats);
  }
}
