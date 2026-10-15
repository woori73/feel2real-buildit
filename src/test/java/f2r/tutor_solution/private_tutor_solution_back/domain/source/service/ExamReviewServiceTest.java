package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationMetadata;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.StudentAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.StudentAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.score.entity.StudentScore;
import f2r.tutor_solution.private_tutor_solution_back.domain.score.repository.StudentScoreRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamAnalysisResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamConfirmRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamConfirmResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamReviewResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ProcessingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Step 3 단순화 후 ExamReviewService 단위 테스트.
 *
 * <p>새 confirm 흐름의 책임:
 * <ul>
 *   <li>exams 메타 update (exam_id는 유지)</li>
 *   <li>기존 questions(분석 단계에서 INSERT됨) 로드해서 student_answers/student_scores INSERT</li>
 *   <li>source COMPLETED 전이 + conversation metadata propagate</li>
 * </ul>
 *
 * <p>exam INSERT, questions INSERT, dedup 처리는 모두 분석 단계({@link SourceAnalysisService})가 담당하므로
 * 본 테스트에서 다루지 않는다.
 */
class ExamReviewServiceTest {

  private SourceRepository sourceRepository;
  private QuestionRepository questionRepository;
  private StudentAnswerRepository studentAnswerRepository;
  private StudentScoreRepository studentScoreRepository;
  private UserRepository userRepository;
  private StudentRepository studentRepository;
  private ConversationRepository conversationRepository;
  private ConversationSourceRepository conversationSourceRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private ExamReviewService service;

  @BeforeEach
  void setUp() {
    sourceRepository = mock(SourceRepository.class);
    questionRepository = mock(QuestionRepository.class);
    studentAnswerRepository = mock(StudentAnswerRepository.class);
    studentScoreRepository = mock(StudentScoreRepository.class);
    userRepository = mock(UserRepository.class);
    studentRepository = mock(StudentRepository.class);
    conversationRepository = mock(ConversationRepository.class);
    conversationSourceRepository = mock(ConversationSourceRepository.class);
    AuthenticatedUserResolver authResolver = mock(AuthenticatedUserResolver.class);
    // 기본 — ownership 검증은 통과 (별도 deny 케이스 없음).
    service = new ExamReviewService(
        sourceRepository,
        questionRepository, studentAnswerRepository, studentScoreRepository,
        userRepository, studentRepository,
        conversationRepository, conversationSourceRepository,
        objectMapper,
        authResolver);
  }

  // --- review (GET) ---

  @Test
  @DisplayName("review — PENDING_REVIEW + EXAM_GRADED source의 analysis_result를 그대로 반환")
  void review_returns_analysis_result() {
    // given
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, sampleExam(42L));
    when(sourceRepository.findById(11L)).thenReturn(Optional.of(source));

    // when
    ExamReviewResponse response = service.review(11L);

    // then
    assertThat(response.sourceId()).isEqualTo(11L);
    assertThat(response.processingStatus()).isEqualTo("PENDING_REVIEW");
    assertThat(response.analysisResult().questions()).hasSize(2);
  }

  @Test
  @DisplayName("review — PENDING_REVIEW가 아닌 source면 IllegalStateException")
  void review_rejects_non_pending_review() {
    // given
    Source source = examGradedSource(11L, ProcessingStatus.COMPLETED, sampleExam(42L));
    when(sourceRepository.findById(11L)).thenReturn(Optional.of(source));

    // when & then
    assertThatThrownBy(() -> service.review(11L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("검증 대기 상태가 아닙니다");
  }

  @Test
  @DisplayName("review — GENERAL 자료는 검증 대상 아님")
  void review_rejects_general_kind() {
    // given
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, sampleExam(42L));
    source.updateContentKind(ContentKind.GENERAL);
    when(sourceRepository.findById(11L)).thenReturn(Optional.of(source));

    // when & then
    assertThatThrownBy(() -> service.review(11L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("채점된 시험지");
  }

  @Test
  @DisplayName("review — EXAM_BLANK(채점 흔적 없는 시험지)도 검증 대상 아님")
  void review_rejects_exam_blank_kind() {
    // given
    Source source = examGradedSource(12L, ProcessingStatus.PENDING_REVIEW, sampleExam(42L));
    source.updateContentKind(ContentKind.EXAM_BLANK);
    when(sourceRepository.findById(12L)).thenReturn(Optional.of(source));

    // when & then
    assertThatThrownBy(() -> service.review(12L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("채점된 시험지");
  }

  // --- confirm (POST) — 정상 흐름 ---

  @Test
  @DisplayName("confirm — 정상 흐름: 기존 questions에 매핑된 student_answers + student_scores를 INSERT 하고 COMPLETED로 전이")
  void confirm_inserts_answers_and_score_then_completes() {
    // given
    Exam exam = sampleExam(42L);
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();
    List<Question> existingQuestions = List.of(
        existingQuestion(101L, 1, "Q1", QuestionType.MULTIPLE_CHOICE),
        existingQuestion(102L, 2, "Q2", QuestionType.SHORT_ANSWER));

    stubCommon(source, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(42L)).thenReturn(existingQuestions);
    stubInsertSaves();

    // when
    ExamConfirmResponse response = service.confirm(11L, user.getUserId(), sampleRequest());

    // then
    assertThat(response.examId()).isEqualTo(42L);
    assertThat(response.totalQuestions()).isEqualTo(2);
    assertThat(response.correctCount()).isEqualTo(1);
    assertThat(source.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);

    verify(studentAnswerRepository, times(2)).save(any());
    verify(studentScoreRepository).save(any());

    // student_answers는 기존 questions를 그대로 참조
    ArgumentCaptor<StudentAnswer> answerCaptor = ArgumentCaptor.forClass(StudentAnswer.class);
    verify(studentAnswerRepository, times(2)).save(answerCaptor.capture());
    assertThat(answerCaptor.getAllValues())
        .extracting(a -> a.getQuestion().getQuestionId())
        .containsExactly(101L, 102L);
  }

  // --- confirm — 학생 메타 수정 반영 ---

  @Test
  @DisplayName("confirm — 학생이 메타를 수정하면 exam_id는 유지된 채 exams row만 update된다")
  void confirm_applies_meta_override_to_exam_without_changing_id() {
    // given — 학생이 학년을 3 → 2로 수정
    Exam exam = sampleExam(42L);
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();
    stubCommon(source, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(42L)).thenReturn(List.of(
        existingQuestion(101L, 1, "Q1", QuestionType.MULTIPLE_CHOICE),
        existingQuestion(102L, 2, "Q2", QuestionType.SHORT_ANSWER)));
    stubInsertSaves();

    ExamConfirmRequest request = new ExamConfirmRequest(
        new ExamConfirmRequest.Meta("00중학교", (byte) 2, ExamType.FINAL, (short) 2025, (byte) 2),
        List.of());

    // when
    ExamConfirmResponse response = service.confirm(11L, user.getUserId(), request);

    // then — exam_id 그대로, exams row만 update
    assertThat(response.examId()).isEqualTo(42L);
    assertThat(exam.getGrade()).isEqualTo((byte) 2);
    assertThat(exam.getExamType()).isEqualTo(ExamType.FINAL);
    assertThat(exam.getExamYear()).isEqualTo((short) 2025);
    assertThat(exam.getDisplayName()).contains("00중학교", "2학년", "2025", "2학기", "기말고사");
  }

  // --- confirm — answer override 적용 ---

  @Test
  @DisplayName("confirm — answer override가 LLM 추출값보다 우선 적용된다")
  void confirm_applies_answer_overrides() {
    // given
    Exam exam = sampleExam(42L);
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();
    stubCommon(source, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(42L)).thenReturn(List.of(
        existingQuestion(101L, 1, "Q1", QuestionType.MULTIPLE_CHOICE),
        existingQuestion(102L, 2, "Q2", QuestionType.SHORT_ANSWER)));
    stubInsertSaves();

    // override: 1번을 false로, 2번을 true로 뒤집기
    ExamConfirmRequest request = new ExamConfirmRequest(
        sampleRequest().meta(),
        List.of(
            new ExamConfirmRequest.AnswerOverride(1, false, null),
            new ExamConfirmRequest.AnswerOverride(2, true, null)));

    // when
    ExamConfirmResponse response = service.confirm(11L, user.getUserId(), request);

    // then — override 적용된 합산
    assertThat(response.totalQuestions()).isEqualTo(2);
    assertThat(response.correctCount()).isEqualTo(1); // 2번만 정답
  }

  // --- confirm — 배점 보정 + 점수 합산 ---

  @Test
  @DisplayName("confirm — 학생이 보정한 point가 questions.point에 반영되고 StudentScore에 점수 합계가 저장된다")
  void confirm_applies_point_override_and_saves_total_earned_points() {
    // given — 1번(맞음, 3점), 2번(틀림, 5점). 학생이 2번 배점을 5→7로 수정
    Exam exam = sampleExam(42L);
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();
    Question q1 = existingQuestion(101L, 1, "Q1", QuestionType.MULTIPLE_CHOICE, 3);
    Question q2 = existingQuestion(102L, 2, "Q2", QuestionType.SHORT_ANSWER,    5);
    stubCommon(source, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(42L)).thenReturn(List.of(q1, q2));
    stubInsertSaves();

    // analysis_result: 1번 정답, 2번 오답. 학생 보정 — 2번 배점만 7로 수정
    ExamConfirmRequest request = new ExamConfirmRequest(
        sampleRequest().meta(),
        List.of(new ExamConfirmRequest.AnswerOverride(2, null, 7)));

    // when
    service.confirm(11L, user.getUserId(), request);

    // then — questions.point 반영
    assertThat(q2.getPoint()).isEqualTo(7);
    assertThat(q1.getPoint()).isEqualTo(3); // 보정 없음 — 그대로

    // StudentScore — total=10(3+7), earned=3(1번만 정답)
    ArgumentCaptor<StudentScore> scoreCap = ArgumentCaptor.forClass(StudentScore.class);
    verify(studentScoreRepository).save(scoreCap.capture());
    StudentScore saved = scoreCap.getValue();
    assertThat(saved.getTotalQuestions()).isEqualTo(2);
    assertThat(saved.getCorrectCount()).isEqualTo(1);
    assertThat(saved.getTotalPoints()).isEqualTo(10);
    assertThat(saved.getEarnedPoints()).isEqualTo(3);
  }

  @Test
  @DisplayName("confirm — 모든 문제 배점이 0이면 StudentScore의 점수 컬럼은 NULL (배점 미명시 시험)")
  void confirm_no_points_means_null_score_columns() {
    // given — 두 문제 모두 point=0
    Exam exam = sampleExam(42L);
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();
    stubCommon(source, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(42L)).thenReturn(List.of(
        existingQuestion(101L, 1, "Q1", QuestionType.MULTIPLE_CHOICE, 0),
        existingQuestion(102L, 2, "Q2", QuestionType.SHORT_ANSWER,    0)));
    stubInsertSaves();

    // when
    service.confirm(11L, user.getUserId(), sampleRequest());

    // then
    ArgumentCaptor<StudentScore> scoreCap = ArgumentCaptor.forClass(StudentScore.class);
    verify(studentScoreRepository).save(scoreCap.capture());
    StudentScore saved = scoreCap.getValue();
    assertThat(saved.getTotalPoints()).isNull();
    assertThat(saved.getEarnedPoints()).isNull();
    // count 컬럼은 여전히 채워짐 (정답률 폴백용)
    assertThat(saved.getTotalQuestions()).isEqualTo(2);
    assertThat(saved.getCorrectCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("confirm — 점수가 있으면 conversation metadata의 exam_score는 \"earned/total (correct/totalQ)\" 형식")
  void confirm_propagates_point_based_exam_score_to_conversation() {
    // given
    Exam exam = sampleExam(77L);
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();
    stubCommon(source, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(77L)).thenReturn(List.of(
        existingQuestion(101L, 1, "Q1", QuestionType.MULTIPLE_CHOICE, 3),
        existingQuestion(102L, 2, "Q2", QuestionType.SHORT_ANSWER,    7)));
    stubInsertSaves();

    ConversationMetadata preMetadata = ConversationMetadata.initial(student, 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(11L, "exam.pdf"));
    Conversation conv = Conversation.builder()
        .student(student)
        .title("새 대화")
        .metadata(preMetadata.toJson(objectMapper))
        .build();
    ReflectionTestUtils.setField(conv, "conversationId", 100L);
    when(conversationSourceRepository.findBySourceId(11L)).thenReturn(List.of(
        ConversationSource.builder().conversationId(100L).sourceId(11L).build()));
    when(conversationRepository.findById(100L)).thenReturn(Optional.of(conv));

    // when
    service.confirm(11L, user.getUserId(), sampleRequest());

    // then — exam_score는 "3/10 (1/2)" — 1번만 정답이라 earned 3점
    ConversationMetadata postMetadata = ConversationMetadata.fromJson(conv.getMetadata(), objectMapper);
    assertThat(postMetadata.sources().get(0).examScore()).isEqualTo("3/10 (1/2)");
  }

  @Test
  @DisplayName("formatExamScore — 배점 있으면 점수+정답률, 없으면 정답률만")
  void formatExamScore_branches() {
    // given / when / then — 점수 있음
    assertThat(ExamReviewService.formatExamScore(8, 10, 25, 30, true))
        .isEqualTo("25/30 (8/10)");
    // 점수 없음 (배점 미명시 시험)
    assertThat(ExamReviewService.formatExamScore(8, 10, 0, 0, false))
        .isEqualTo("8/10");
  }

  // --- confirm — question_no 매칭 안 되는 케이스 ---

  @Test
  @DisplayName("confirm — 기존 questions에 없는 question_no는 skip (LLM 추출 문항이 더 많은 경우)")
  void confirm_skips_unmatched_question_no() {
    // given — LLM은 2문항 추출, 기존 questions는 1문항만
    Exam exam = sampleExam(42L);
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();
    stubCommon(source, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(42L)).thenReturn(List.of(
        existingQuestion(101L, 1, "Q1", QuestionType.MULTIPLE_CHOICE)));
    stubInsertSaves();

    // when
    ExamConfirmResponse response = service.confirm(11L, user.getUserId(), sampleRequest());

    // then — 매핑된 1문항만 처리
    assertThat(response.totalQuestions()).isEqualTo(1);
    assertThat(response.correctCount()).isEqualTo(1);
    verify(studentAnswerRepository, times(1)).save(any());
  }

  // --- confirm — 사전조건 검증 ---

  @Test
  @DisplayName("confirm — source에 exam이 연결되어 있지 않으면 IllegalStateException (분석 단계 누락)")
  void confirm_rejects_source_without_exam() {
    // given
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, /*exam*/ null);
    when(sourceRepository.findById(11L)).thenReturn(Optional.of(source));

    // when & then
    assertThatThrownBy(() -> service.confirm(11L, 7L, sampleRequest()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exam이 연결되지 않");
  }

  @Test
  @DisplayName("confirm — exam은 있지만 questions가 비어 있으면 IllegalStateException (분석 단계 누락)")
  void confirm_rejects_when_no_questions() {
    // given
    Exam exam = sampleExam(42L);
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();
    stubCommon(source, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(42L)).thenReturn(List.of());

    // when & then
    assertThatThrownBy(() -> service.confirm(11L, user.getUserId(), sampleRequest()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("questions가 없습니다");
  }

  // --- confirm — conversation metadata 갱신 ---

  @Test
  @DisplayName("confirm — attach된 conversation의 metadata에 exam_score가 기록된다 (Phase 7)")
  void confirm_propagates_exam_score_to_conversation_metadata() {
    // given
    Exam exam = sampleExam(77L);
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();
    stubCommon(source, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(77L)).thenReturn(List.of(
        existingQuestion(101L, 1, "Q1", QuestionType.MULTIPLE_CHOICE),
        existingQuestion(102L, 2, "Q2", QuestionType.SHORT_ANSWER)));
    stubInsertSaves();

    // attach 상태 mock
    ConversationMetadata preMetadata = ConversationMetadata.initial(student, 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(11L, "exam.pdf"));
    Conversation conv = Conversation.builder()
        .student(student)
        .title("새 대화")
        .metadata(preMetadata.toJson(objectMapper))
        .build();
    ReflectionTestUtils.setField(conv, "conversationId", 100L);

    when(conversationSourceRepository.findBySourceId(11L)).thenReturn(List.of(
        ConversationSource.builder().conversationId(100L).sourceId(11L).build()));
    when(conversationRepository.findById(100L)).thenReturn(Optional.of(conv));

    // when
    ExamConfirmResponse response = service.confirm(11L, user.getUserId(), sampleRequest());

    // then — conversation.metadata에 exam_score 기록됨
    assertThat(response.totalQuestions()).isEqualTo(2);
    assertThat(response.correctCount()).isEqualTo(1);
    ConversationMetadata postMetadata = ConversationMetadata.fromJson(conv.getMetadata(), objectMapper);
    ConversationMetadata.SourceSummary updated = postMetadata.sources().get(0);
    assertThat(updated.sourceId()).isEqualTo(11L);
    assertThat(updated.isExam()).isTrue();
    assertThat(updated.examId()).isEqualTo(77L);
    assertThat(updated.examScore()).isEqualTo("1/2");
  }

  @Test
  @DisplayName("confirm — 같은 묶음(같은 exam_id + 같은 학생)의 PENDING_REVIEW source들이 일괄 COMPLETED로 전이된다")
  void confirm_completes_all_sources_in_same_batch() {
    // given — 학생이 시험지 3장을 한 묶음으로 올림 (같은 exam_id=42)
    Exam exam = sampleExam(42L);
    Source clicked = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Source second = examGradedSource(12L, ProcessingStatus.PENDING_REVIEW, exam);
    Source third = examGradedSource(13L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();

    stubCommon(clicked, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(42L)).thenReturn(List.of(
        existingQuestion(101L, 1, "Q1", QuestionType.MULTIPLE_CHOICE),
        existingQuestion(102L, 2, "Q2", QuestionType.SHORT_ANSWER)));
    stubInsertSaves();
    // 묶음 일괄 전이 — 학생이 11번 클릭했지만 12, 13도 같이 COMPLETED 되어야 함
    when(sourceRepository.findByExamExamIdAndUploadedByUserIdAndProcessingStatus(
        42L, user.getUserId(), ProcessingStatus.PENDING_REVIEW))
        .thenReturn(List.of(clicked, second, third));

    // when
    service.confirm(11L, user.getUserId(), sampleRequest());

    // then — 묶음 전체가 COMPLETED
    assertThat(clicked.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
    assertThat(second.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
    assertThat(third.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);

    // student_answers / student_scores 는 한 번만 INSERT (묶음 전체에 대한 한 회 채점)
    verify(studentAnswerRepository, times(2)).save(any());  // 2문항
    verify(studentScoreRepository, times(1)).save(any());
  }

  @Test
  @DisplayName("confirm — source가 attach된 conversation이 없으면 metadata 갱신 호출 자체를 안 한다")
  void confirm_skips_metadata_update_when_no_attached_conversation() {
    // given
    Exam exam = sampleExam(42L);
    Source source = examGradedSource(11L, ProcessingStatus.PENDING_REVIEW, exam);
    Student student = sampleStudent();
    User user = student.getUser();
    stubCommon(source, user, student);
    when(questionRepository.findByExamIdOrderByOrderNumAsc(42L)).thenReturn(List.of(
        existingQuestion(101L, 1, "Q1", QuestionType.MULTIPLE_CHOICE),
        existingQuestion(102L, 2, "Q2", QuestionType.SHORT_ANSWER)));
    stubInsertSaves();
    when(conversationSourceRepository.findBySourceId(11L)).thenReturn(List.of());

    // when
    service.confirm(11L, user.getUserId(), sampleRequest());

    // then
    verify(conversationRepository, never()).findById(any());
  }

  // --- helpers ---

  private void stubCommon(Source source, User user, Student student) {
    when(sourceRepository.findById(source.getSourceId())).thenReturn(Optional.of(source));
    when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
    when(studentRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(student));
    // 묶음 일괄 COMPLETED 전이 — 기본은 클릭한 source 자신 1개만 포함 (단일 source 묶음).
    // 여러 source 묶음 시나리오는 개별 테스트에서 stub override.
    when(sourceRepository.findByExamExamIdAndUploadedByUserIdAndProcessingStatus(
        anyLong(), anyLong(), eq(ProcessingStatus.PENDING_REVIEW))).thenReturn(List.of(source));
  }

  private void stubInsertSaves() {
    when(studentAnswerRepository.save(any())).thenAnswer(inv -> {
      StudentAnswer a = inv.getArgument(0);
      ReflectionTestUtils.setField(a, "answerId", System.nanoTime());
      return a;
    });
    when(studentScoreRepository.save(any())).thenAnswer(inv -> {
      StudentScore s = inv.getArgument(0);
      ReflectionTestUtils.setField(s, "scoreId", 1234L);
      return s;
    });
  }

  /** PENDING_REVIEW + EXAM_GRADED source. exam이 null이면 source.exam도 null (사전조건 검증 케이스용). */
  private Source examGradedSource(Long id, ProcessingStatus status, Exam exam) {
    ExamAnalysisResult result = new ExamAnalysisResult(
        new ExamAnalysisResult.ExamMeta(
            "00중학교", (byte) 3, ExamType.MIDTERM,
            (short) 2026, (byte) 1, 1),
        null,
        List.of(
            new ExamAnalysisResult.QuestionEntry(
                1, "1", "Q1", QuestionType.MULTIPLE_CHOICE,
                List.of("①a", "②b"), "②", "②", true, 0, null, null),
            new ExamAnalysisResult.QuestionEntry(
                2, "2", "Q2", QuestionType.SHORT_ANSWER,
                List.of(), "apple", "aple", false, 0, null, null)),
        null, 2, 1);

    Source source = Source.builder()
        .uploadedBy(sampleUser(1L))
        .originalFileName("exam.pdf")
        .storageKey("/tmp/exam")
        .fileType("PDF")
        .fileSize(1L)
        .sourceType(SourceType.STUDENT_MATERIAL)
        .build();
    ReflectionTestUtils.setField(source, "sourceId", id);
    source.updateContentKind(ContentKind.EXAM_GRADED);
    source.updateProcessingStatus(status);
    source.updateAnalysisResult(result.toJson(objectMapper));
    if (exam != null) source.linkToExam(exam);
    return source;
  }

  private ExamConfirmRequest sampleRequest() {
    return new ExamConfirmRequest(
        new ExamConfirmRequest.Meta("00중학교", (byte) 3, ExamType.MIDTERM,
            (short) 2026, (byte) 1),
        List.of());
  }

  private Student sampleStudent() {
    User user = sampleUser(7L);
    Student student = Student.builder()
        .user(user)
        .schoolName("00중학교")
        .grade((byte) 3)
        .parentName("홍부모")
        .parentPhone("010")
        .build();
    ReflectionTestUtils.setField(student, "studentId", 5L);
    return student;
  }

  private User sampleUser(Long userId) {
    User user = User.builder()
        .loginId("std1").password("pw").name("홍길동")
        .phone("010").role(Role.STUDENT).build();
    ReflectionTestUtils.setField(user, "userId", userId);
    return user;
  }

  private Subject sampleSubject() {
    Subject subject = Subject.builder().nameKo("영어").nameEn("English").build();
    ReflectionTestUtils.setField(subject, "subjectId", 1);
    return subject;
  }

  private Exam sampleExam(Long examId) {
    Exam exam = Exam.builder()
        .schoolName("00중학교").grade((byte) 3).examType(ExamType.MIDTERM)
        .examYear((short) 2026).examSemester((byte) 1).build();
    ReflectionTestUtils.setField(exam, "examId", examId);
    return exam;
  }

  private Question existingQuestion(Long id, int orderNum, String text, QuestionType type) {
    return existingQuestion(id, orderNum, text, type, 0);
  }

  private Question existingQuestion(Long id, int orderNum, String text, QuestionType type, int point) {
    Question q = Question.builder()
        .subject(sampleSubject())
        .questionType(type)
        .questionText(text)
        .correctAnswer("②")
        .orderNum(orderNum)
        .point(point)
        .build();
    ReflectionTestUtils.setField(q, "questionId", id);
    return q;
  }
}
