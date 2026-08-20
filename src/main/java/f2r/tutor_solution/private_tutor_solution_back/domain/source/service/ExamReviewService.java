package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationMetadata;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.StudentAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.StudentAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.score.entity.StudentScore;
import f2r.tutor_solution.private_tutor_solution_back.domain.score.repository.StudentScoreRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamAnalysisResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamConfirmRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamConfirmResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamReviewResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.PendingReviewSourceResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ProcessingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MVP §5.0 5~6단계 — 채점된 시험지(EXAM_GRADED)의 학생 검증/확정.
 *
 * <p>책임 분리 (Step 2/3 재작업 후):
 * <ul>
 *   <li>exams INSERT, questions INSERT, source ↔ exam 연결 — 분석 단계({@link SourceAnalysisService})가 담당</li>
 *   <li>메타 보정, student_answers INSERT, student_scores INSERT, COMPLETED 전이 — 본 서비스 confirm()이 담당</li>
 * </ul>
 *
 * <p>exam_id는 confirm 단계에서 절대 바꾸지 않는다 (학생 데이터 access 안정성 보장). 학생이 메타를 수정해도
 * exams row만 update하며, 다른 exam과의 중복은 추후 배치 dedup에서 정리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExamReviewService {

  private final SourceRepository sourceRepository;
  private final QuestionRepository questionRepository;
  private final StudentAnswerRepository studentAnswerRepository;
  private final StudentScoreRepository studentScoreRepository;
  private final UserRepository userRepository;
  private final StudentRepository studentRepository;
  private final ConversationRepository conversationRepository;
  private final ConversationSourceRepository conversationSourceRepository;
  private final ObjectMapper objectMapper;
  private final AuthenticatedUserResolver authResolver;

  /** 학생 대시보드 — 사용자가 업로드한 source 중 PENDING_REVIEW(시험지 검증 대기) 목록. */
  public List<PendingReviewSourceResponse> listPendingReview(Long userId) {
    return sourceRepository
        .findByUploadedByUserIdAndProcessingStatusOrderByCreatedAtDesc(
            userId, ProcessingStatus.PENDING_REVIEW)
        .stream()
        .map(PendingReviewSourceResponse::from)
        .toList();
  }

  public ExamReviewResponse review(Long sourceId) {
    Source source = findSourceForReview(sourceId);
    authResolver.verifyOwnsSource(source);  // 본인 자료 아니면 403
    ExamAnalysisResult result = ExamAnalysisResult.fromJson(source.getAnalysisResult(), objectMapper);
    if (result == null) {
      throw new IllegalStateException("분석 결과가 비어 있습니다: sourceId=" + sourceId);
    }
    return ExamReviewResponse.of(source, result);
  }

  @Transactional
  public ExamConfirmResponse confirm(Long sourceId, Long userId, ExamConfirmRequest request) {
    Source source = findSourceForReview(sourceId);
    authResolver.verifyOwnsSource(source);  // 본인 자료 아니면 403
    ExamAnalysisResult result = ExamAnalysisResult.fromJson(source.getAnalysisResult(), objectMapper);
    if (result == null) {
      throw new IllegalStateException("분석 결과가 비어 있습니다: sourceId=" + sourceId);
    }
    Exam exam = source.getExam();
    if (exam == null) {
      throw new IllegalStateException("source에 exam이 연결되지 않았습니다 (분석 단계 누락): sourceId=" + sourceId);
    }
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
    Student student = studentRepository.findByUser_UserId(userId)
        .orElseThrow(() -> new IllegalArgumentException("학생 정보를 찾을 수 없습니다: userId=" + userId));

    try {
      AuditorContextHolder.set(user.getLoginId());

      // 1) 학생 메타 수정 반영 (exam_id 유지, exams row만 update)
      applyMetaOverride(exam, request.meta());

      // 2) 기존 questions 로드 (Step 2 분석 단계에서 이미 INSERT됨)
      Map<Integer, Question> questionsByOrderNum = loadQuestionsByOrderNum(exam);

      // 3) student_answers INSERT + 채점 카운트 + 점수 합산 (학생 보정 point 반영)
      Map<Integer, ExamConfirmRequest.AnswerOverride> overrides = indexOverrides(request.answers());
      int totalQuestions = 0;
      int correctCount = 0;
      int totalPoints = 0;
      int earnedPoints = 0;
      for (ExamAnalysisResult.QuestionEntry q : result.questions()) {
        Question question = questionsByOrderNum.get(q.questionNo());
        if (question == null) {
          log.warn("question_no={}에 매칭되는 question이 없습니다 — answer skip (examId={})",
              q.questionNo(), exam.getExamId());
          continue;
        }
        ExamConfirmRequest.AnswerOverride override = overrides.get(q.questionNo());

        // 학생이 보정한 배점이 있으면 questions.point UPDATE (같은 exam을 공유하는 모든 학생에 영향)
        if (override != null && override.point() != null) {
          question.updatePoint(override.point());
        }

        Boolean isCorrect = override != null && override.isCorrect() != null
            ? override.isCorrect()
            : q.isCorrect();
        StudentAnswer answer = StudentAnswer.builder()
            .student(student)
            .question(question)
            .examId(exam.getExamId())
            .studentAnswer(q.studentAnswer())
            .build();
        if (isCorrect != null) {
          answer.grade(isCorrect);
        }
        studentAnswerRepository.save(answer);

        totalQuestions++;
        int point = question.getPoint() != null ? question.getPoint() : 0;
        totalPoints += point;
        if (Boolean.TRUE.equals(isCorrect)) {
          correctCount++;
          earnedPoints += point;
        }
      }

      // 4) student_scores INSERT — 배점이 한 문제라도 명시되면 점수 컬럼도 채움
      boolean hasPoints = totalPoints > 0;
      StudentScore score = studentScoreRepository.save(StudentScore.builder()
          .student(student)
          .exam(exam)
          .source(source)
          .totalQuestions(totalQuestions)
          .correctCount(correctCount)
          .totalPoints(hasPoints ? totalPoints : null)
          .earnedPoints(hasPoints ? earnedPoints : null)
          .build());

      // 5) 같은 묶음(같은 exam_id + 같은 학생) source 모두 COMPLETED 전이
      //    학생이 시험지 N장을 한 묶음으로 올렸으면 한 번의 confirm으로 전부 종료해야 한다.
      int batchSize = completeBatchSources(exam.getExamId(), user.getUserId());

      // 6) Phase 7 — 해당 source가 attach된 conversation들의 metadata 갱신
      String examScore = formatExamScore(correctCount, totalQuestions, earnedPoints, totalPoints, hasPoints);
      propagateExamScoreToConversations(sourceId, exam.getExamId(), examScore);

      log.info("시험지 묶음 일괄 COMPLETED: examId={}, userId={}, count={}",
          exam.getExamId(), user.getUserId(), batchSize);

      log.info("시험지 확정 완료: sourceId={}, examId={}, total={}, correct={}, points={}/{}",
          sourceId, exam.getExamId(), totalQuestions, correctCount,
          hasPoints ? earnedPoints : "-", hasPoints ? totalPoints : "-");

      return new ExamConfirmResponse(
          source.getSourceId(), exam.getExamId(), score.getScoreId(),
          totalQuestions, correctCount, /*dedupMatched*/ false);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /**
   * conversation metadata에 들어갈 점수 문자열을 만든다.
   * 배점이 있으면 "75/100 (8/10)" — 점수 + 정답률, 없으면 "8/10" — 정답률만.
   */
  static String formatExamScore(int correctCount, int totalQuestions,
                                int earnedPoints, int totalPoints, boolean hasPoints) {
    if (hasPoints) {
      return earnedPoints + "/" + totalPoints + " (" + correctCount + "/" + totalQuestions + ")";
    }
    return correctCount + "/" + totalQuestions;
  }

  // --- internal helpers ---

  /**
   * 같은 묶음(같은 exam_id + 같은 학생 uploadedBy)에서 아직 PENDING_REVIEW 상태인 source들을 모두 COMPLETED로 전이한다.
   * 학생이 시험지 7~8장을 한 묶음으로 올렸어도 검증 화면은 1회만 진입하므로, 확정 시 묶음 전체를 정리해야
   * 대시보드 "검증 대기" 카드가 깔끔히 사라진다. 같은 시험을 공유하는 다른 학생의 source는 영향받지 않는다.
   *
   * @return 전이한 source 개수
   */
  private int completeBatchSources(Long examId, Long userId) {
    List<Source> batch = sourceRepository.findByExamExamIdAndUploadedByUserIdAndProcessingStatus(
        examId, userId, ProcessingStatus.PENDING_REVIEW);
    for (Source s : batch) {
      s.updateProcessingStatus(ProcessingStatus.COMPLETED);
    }
    return batch.size();
  }

  /**
   * 학생이 보낸 메타 수정값을 exams row에 반영한다. exam_id는 절대 바꾸지 않는다.
   * 같은 exam을 공유하는 다른 학생 데이터도 함께 영향 받음 — 1차 단순화의 트레이드오프
   * (중복/충돌 정리는 추후 배치 dedup).
   */
  private void applyMetaOverride(Exam exam, ExamConfirmRequest.Meta meta) {
    if (meta == null) return;
    exam.updateMeta(
        meta.schoolName(),
        meta.grade(),
        meta.examType(),
        meta.examYear(),
        meta.examSemester(),
        buildDisplayName(meta));
  }

  private Map<Integer, Question> loadQuestionsByOrderNum(Exam exam) {
    List<Question> existing = questionRepository.findByExamIdOrderByOrderNumAsc(exam.getExamId());
    if (existing.isEmpty()) {
      throw new IllegalStateException(
          "exam에 questions가 없습니다 (분석 단계 누락): examId=" + exam.getExamId());
    }
    Map<Integer, Question> map = new HashMap<>();
    for (Question q : existing) {
      // 같은 order_num이 중복 존재하면 첫 번째만 사용
      map.putIfAbsent(q.getOrderNum(), q);
    }
    return map;
  }

  /**
   * Phase 7 — 시험지 source가 attach된 모든 conversation의 metadata를 갱신한다.
   * AI 시스템 프롬프트에 시험 점수가 노출되도록 sources 항목에 exam_id/is_exam/exam_score 부착.
   */
  private void propagateExamScoreToConversations(Long sourceId, Long examId, String examScore) {
    List<ConversationSource> links = conversationSourceRepository.findBySourceId(sourceId);
    if (links.isEmpty()) {
      return;
    }
    for (ConversationSource link : links) {
      try {
        Conversation conv = conversationRepository.findById(link.getConversationId()).orElse(null);
        if (conv == null) continue;
        String updated = ConversationMetadata
            .fromJson(conv.getMetadata(), objectMapper)
            .withSourceUpdated(sourceId, examId, examScore)
            .toJson(objectMapper);
        conv.updateMetadata(updated);
      } catch (Exception e) {
        log.warn("conversation metadata 갱신 실패 — skip: conversationId={}, sourceId={}, reason={}",
            link.getConversationId(), sourceId, e.getMessage());
      }
    }
  }

  private Source findSourceForReview(Long sourceId) {
    Source source = sourceRepository.findById(sourceId)
        .orElseThrow(() -> new IllegalArgumentException("source를 찾을 수 없습니다: " + sourceId));
    if (source.getContentKind() != ContentKind.EXAM_GRADED) {
      throw new IllegalStateException("채점된 시험지(EXAM_GRADED)가 아닌 source는 검증할 수 없습니다: sourceId=" + sourceId
          + ", contentKind=" + source.getContentKind());
    }
    if (source.getProcessingStatus() != ProcessingStatus.PENDING_REVIEW) {
      throw new IllegalStateException("검증 대기 상태가 아닙니다: sourceId=" + sourceId
          + ", status=" + source.getProcessingStatus());
    }
    return source;
  }

  private String buildDisplayName(ExamConfirmRequest.Meta meta) {
    StringBuilder sb = new StringBuilder();
    if (meta.schoolName() != null) sb.append(meta.schoolName()).append(' ');
    if (meta.grade() != null) sb.append(meta.grade()).append("학년 ");
    if (meta.examYear() != null) sb.append(meta.examYear()).append(' ');
    if (meta.examSemester() != null) sb.append(meta.examSemester()).append("학기 ");
    if (meta.examType() != null) sb.append(examTypeLabel(meta.examType()));
    String s = sb.toString().trim();
    return s.isEmpty() ? null : s;
  }

  private String examTypeLabel(ExamType type) {
    return switch (type) {
      case MIDTERM -> "중간고사";
      case FINAL -> "기말고사";
      case MOCK -> "모의고사";
      case OTHER -> "기타";
    };
  }

  /** question_no → 학생 보정 객체 매핑. isCorrect/point 중 적어도 하나가 있으면 등록된다. */
  private Map<Integer, ExamConfirmRequest.AnswerOverride> indexOverrides(
      List<ExamConfirmRequest.AnswerOverride> overrides) {
    Map<Integer, ExamConfirmRequest.AnswerOverride> map = new HashMap<>();
    if (overrides == null) return map;
    for (ExamConfirmRequest.AnswerOverride o : overrides) {
      if (o.questionNo() == null) continue;
      if (o.isCorrect() == null && o.point() == null) continue;
      map.put(o.questionNo(), o);
    }
    return map;
  }
}
