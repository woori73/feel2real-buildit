package f2r.tutor_solution.private_tutor_solution_back.domain.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto.WrongAnswerResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.repository.ExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.StudentAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.StudentAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class WrongAnswerServiceTest {

  private ConversationRepository conversationRepository;
  private ConversationSourceRepository conversationSourceRepository;
  private SourceRepository sourceRepository;
  private ExamRepository examRepository;
  private StudentAnswerRepository studentAnswerRepository;
  private AuthenticatedUserResolver authResolver;
  private WrongAnswerService service;

  private Subject subject;
  private Student student;
  private QuestionCategory grammar;
  private QuestionCategory reading;

  @BeforeEach
  void setUp() {
    conversationRepository = mock(ConversationRepository.class);
    conversationSourceRepository = mock(ConversationSourceRepository.class);
    sourceRepository = mock(SourceRepository.class);
    examRepository = mock(ExamRepository.class);
    studentAnswerRepository = mock(StudentAnswerRepository.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    service = new WrongAnswerService(
        conversationRepository, conversationSourceRepository, sourceRepository,
        examRepository, studentAnswerRepository, authResolver);

    subject = Subject.builder().nameKo("영어").nameEn("english").build();
    ReflectionTestUtils.setField(subject, "subjectId", 1);

    User user = User.builder().loginId("std").password("pw").name("길동").phone("010").build();
    ReflectionTestUtils.setField(user, "userId", 7L);
    student = Student.builder().user(user).schoolName("현대고").grade((byte) 2).build();
    ReflectionTestUtils.setField(student, "studentId", 99L);

    grammar = category(102L, "grammar", "문법");
    reading = category(103L, "reading", "독해");
  }

  @Test
  @DisplayName("첨부된 시험지가 없으면 exams 빈 리스트 반환 + 답안 조회 skip")
  void no_sources_returns_empty() {
    // given
    Conversation conv = sampleConversation(1L);
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
    when(conversationSourceRepository.findByConversationId(1L)).thenReturn(List.of());

    // when
    WrongAnswerResponse res = service.analyzeForConversation(1L);

    // then
    assertThat(res.exams()).isEmpty();
    verify(studentAnswerRepository, never()).findWrongAnswersForExams(any(), any());
  }

  @Test
  @DisplayName("첨부된 source가 exam 연결 없으면 답안 조회 skip")
  void attached_sources_without_exam_skip() {
    // given
    Conversation conv = sampleConversation(1L);
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
    when(conversationSourceRepository.findByConversationId(1L)).thenReturn(List.of(link(1L, 50L)));
    when(sourceRepository.findAllById(List.of(50L))).thenReturn(List.of(sampleSource(50L, null)));

    // when
    WrongAnswerResponse res = service.analyzeForConversation(1L);

    // then
    assertThat(res.exams()).isEmpty();
    verify(studentAnswerRepository, never()).findWrongAnswersForExams(any(), any());
  }

  @Test
  @DisplayName("시험 1개, 오답 2개 — 시험 그룹 1개에 wrongCount=2, totalCount=시험 전체 답안 수")
  void groups_wrong_answers_by_exam_with_total_count() {
    // given
    Conversation conv = sampleConversation(1L);
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

    Exam exam = sampleExam(500L);
    when(conversationSourceRepository.findByConversationId(1L)).thenReturn(List.of(link(1L, 50L)));
    when(sourceRepository.findAllById(List.of(50L))).thenReturn(List.of(sampleSource(50L, exam)));
    when(examRepository.findAllById(List.of(500L))).thenReturn(List.of(exam));

    Question q1 = q(500L, grammar, 1, "1");
    Question q2 = q(500L, reading, 2, "2");
    when(studentAnswerRepository.findWrongAnswersForExams(eq(99L), eq(List.of(500L))))
        .thenReturn(List.of(answer(q1, 500L, "B", false), answer(q2, 500L, "C", false)));
    when(studentAnswerRepository.countByStudentAndExams(eq(99L), eq(List.of(500L))))
        .thenReturn(List.<Object[]>of(new Object[]{500L, 10L}));

    // when
    WrongAnswerResponse res = service.analyzeForConversation(1L);

    // then
    assertThat(res.exams()).hasSize(1);
    WrongAnswerResponse.ExamGroup group = res.exams().get(0);
    assertThat(group.examId()).isEqualTo(500L);
    assertThat(group.examDisplayName()).isEqualTo("현대고 1학년 2024 1학기 중간고사");
    assertThat(group.wrongCount()).isEqualTo(2);
    assertThat(group.totalCount()).isEqualTo(10);
    assertThat(group.questions())
        .extracting(WrongAnswerResponse.WrongQuestion::categoryNameEn)
        .containsExactly("grammar", "reading");
  }

  @Test
  @DisplayName("여러 시험에 오답이 흩어져 있으면 시험별 그룹으로 분리")
  void splits_groups_per_exam() {
    // given
    Conversation conv = sampleConversation(1L);
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

    Exam examA = sampleExam(500L);
    Exam examB = sampleExam(501L);
    when(conversationSourceRepository.findByConversationId(1L))
        .thenReturn(List.of(link(1L, 50L), link(1L, 51L)));
    when(sourceRepository.findAllById(List.of(50L, 51L)))
        .thenReturn(List.of(sampleSource(50L, examA), sampleSource(51L, examB)));
    when(examRepository.findAllById(List.of(500L, 501L))).thenReturn(List.of(examA, examB));

    when(studentAnswerRepository.findWrongAnswersForExams(eq(99L), eq(List.of(500L, 501L))))
        .thenReturn(List.of(
            answer(q(500L, grammar, 1, "1"), 500L, "x", false),
            answer(q(501L, reading, 1, "1"), 501L, "y", false)));
    when(studentAnswerRepository.countByStudentAndExams(eq(99L), eq(List.of(500L, 501L))))
        .thenReturn(List.<Object[]>of(new Object[]{500L, 5L}, new Object[]{501L, 8L}));

    // when
    WrongAnswerResponse res = service.analyzeForConversation(1L);

    // then
    assertThat(res.exams()).hasSize(2);
    assertThat(res.exams())
        .extracting(WrongAnswerResponse.ExamGroup::examId)
        .containsExactlyInAnyOrder(500L, 501L);
  }

  @Test
  @DisplayName("ownership 위반이면 AccessDeniedException — 답안 조회 시도 안 함")
  void ownership_violation_blocked() {
    // given
    Conversation conv = sampleConversation(1L);
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
    doThrow(new AccessDeniedException("not yours"))
        .when(authResolver).requireStudentOwnedConversationAccess(conv);

    // when & then
    assertThatThrownBy(() -> service.analyzeForConversation(1L))
        .isInstanceOf(AccessDeniedException.class);
    verify(conversationSourceRepository, never()).findByConversationId(any());
    verify(studentAnswerRepository, never()).findWrongAnswersForExams(any(), any());
  }

  @Test
  @DisplayName("존재하지 않는 conversationId면 IllegalArgumentException")
  void unknown_conversation_throws() {
    when(conversationRepository.findById(999L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.analyzeForConversation(999L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // --- helpers ----------------------------------------------------

  private Conversation sampleConversation(Long id) {
    Conversation c = Conversation.builder().student(student).title("test").build();
    ReflectionTestUtils.setField(c, "conversationId", id);
    return c;
  }

  private Source sampleSource(Long id, Exam exam) {
    Source s = Source.builder()
        .uploadedBy(student.getUser())
        .originalFileName("x.pdf").storageKey("/x").fileType("PDF")
        .fileSize(1L).sourceType(SourceType.STUDENT_MATERIAL).build();
    ReflectionTestUtils.setField(s, "sourceId", id);
    if (exam != null) s.linkToExam(exam);
    return s;
  }

  private Exam sampleExam(Long id) {
    Exam e = Exam.builder()
        .schoolName("현대고").grade((byte) 1).examType(ExamType.MIDTERM)
        .examYear((short) 2024).examSemester((byte) 1).subject(subject)
        .displayName("현대고 1학년 2024 1학기 중간고사").build();
    ReflectionTestUtils.setField(e, "examId", id);
    return e;
  }

  private ConversationSource link(Long convId, Long sourceId) {
    return ConversationSource.builder().conversationId(convId).sourceId(sourceId).build();
  }

  private QuestionCategory category(Long id, String en, String ko) {
    QuestionCategory c = QuestionCategory.builder()
        .subject(subject).categoryNameEn(en).categoryNameKo(ko).build();
    ReflectionTestUtils.setField(c, "categoryId", id);
    return c;
  }

  private Question q(Long examId, QuestionCategory cat, int orderNum, String label) {
    Question q = Question.builder()
        .subject(subject).category(cat).examId(examId)
        .questionType(QuestionType.MULTIPLE_CHOICE)
        .questionText("Q").correctAnswer("A").orderNum(orderNum).questionLabel(label)
        .build();
    ReflectionTestUtils.setField(q, "questionId", (long) (examId * 100 + orderNum));
    return q;
  }

  private StudentAnswer answer(Question question, Long examId, String studentAnswer, boolean correct) {
    StudentAnswer a = StudentAnswer.builder()
        .student(student).question(question).examId(examId).studentAnswer(studentAnswer).build();
    a.grade(correct);
    return a;
  }
}
