package f2r.tutor_solution.private_tutor_solution_back.domain.question.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto.WrongAnswerResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.repository.ExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.StudentAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.StudentAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 대화에 첨부된 시험지에서 학생이 틀린 문제를 시험 단위로 묶어 반환한다.
 *
 * <p>radar(카테고리별 정답률)와 짝을 이룬다 — radar로 "어느 카테고리가 약점인지" 보고,
 * 본 응답으로 "어떤 문제를 어떻게 틀렸는지" 본다.
 *
 * <p>시험지가 한 건도 없거나 오답이 0건이면 빈 리스트를 반환한다 (UI가 "오답 없음" 표시).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WrongAnswerService {

  private final ConversationRepository conversationRepository;
  private final ConversationSourceRepository conversationSourceRepository;
  private final SourceRepository sourceRepository;
  private final ExamRepository examRepository;
  private final StudentAnswerRepository studentAnswerRepository;
  private final AuthenticatedUserResolver authResolver;

  @Transactional(readOnly = true)
  public WrongAnswerResponse analyzeForConversation(Long conversationId) {
    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new IllegalArgumentException(
            "conversation not found: " + conversationId));
    authResolver.requireStudentOwnedConversationAccess(conversation);
    Long studentId = conversation.getStudent().getStudentId();

    List<Long> examIds = resolveAttachedExamIds(conversationId);
    if (examIds.isEmpty()) {
      return new WrongAnswerResponse(List.of());
    }

    Map<Long, Exam> examMap = examRepository.findAllById(examIds).stream()
        .collect(java.util.stream.Collectors.toMap(Exam::getExamId, e -> e));

    Map<Long, Long> totalByExam = new java.util.HashMap<>();
    for (Object[] row : studentAnswerRepository.countByStudentAndExams(studentId, examIds)) {
      totalByExam.put((Long) row[0], (Long) row[1]);
    }

    List<StudentAnswer> wrongs =
        studentAnswerRepository.findWrongAnswersForExams(studentId, examIds);

    Map<Long, List<WrongAnswerResponse.WrongQuestion>> wrongsByExam = new LinkedHashMap<>();
    for (StudentAnswer sa : wrongs) {
      wrongsByExam
          .computeIfAbsent(sa.getExamId(), k -> new ArrayList<>())
          .add(toWrongQuestion(sa));
    }

    List<WrongAnswerResponse.ExamGroup> groups = new ArrayList<>(wrongsByExam.size());
    for (Map.Entry<Long, List<WrongAnswerResponse.WrongQuestion>> entry : wrongsByExam.entrySet()) {
      Long examId = entry.getKey();
      Exam exam = examMap.get(examId);
      String displayName = exam != null ? exam.getDisplayName() : ("시험 #" + examId);
      int total = totalByExam.getOrDefault(examId, 0L).intValue();
      groups.add(new WrongAnswerResponse.ExamGroup(
          examId,
          displayName,
          entry.getValue().size(),
          total,
          entry.getValue()));
    }

    log.debug("wrong-answers: conversationId={}, studentId={}, examIds={}, wrongCountTotal={}",
        conversationId, studentId, examIds, wrongs.size());
    return new WrongAnswerResponse(groups);
  }

  private List<Long> resolveAttachedExamIds(Long conversationId) {
    List<ConversationSource> attached = conversationSourceRepository.findByConversationId(conversationId);
    List<Long> sourceIds = attached.stream().map(ConversationSource::getSourceId).toList();
    if (sourceIds.isEmpty()) return List.of();
    return sourceRepository.findAllById(sourceIds).stream()
        .map(Source::getExam)
        .filter(Objects::nonNull)
        .map(Exam::getExamId)
        .distinct()
        .toList();
  }

  private WrongAnswerResponse.WrongQuestion toWrongQuestion(StudentAnswer sa) {
    Question q = sa.getQuestion();
    QuestionCategory cat = q.getCategory();
    return new WrongAnswerResponse.WrongQuestion(
        q.getQuestionId(),
        q.getQuestionLabel(),
        q.getOrderNum(),
        q.getQuestionText(),
        q.getQuestionType() != null ? q.getQuestionType().name() : null,
        q.getChoices(),
        q.getCorrectAnswer(),
        sa.getStudentAnswer(),
        q.getExplanation(),
        cat != null ? cat.getCategoryNameEn() : null,
        cat != null ? cat.getCategoryNameKo() : null,
        q.getPoint());
  }
}
