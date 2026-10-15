package f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto.WeaknessRadarResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.StudentAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.StudentAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.QuestionCategoryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeaknessAnalysisServiceTest {

  private ConversationRepository conversationRepository;
  private ConversationSourceRepository conversationSourceRepository;
  private SourceRepository sourceRepository;
  private StudentAnswerRepository studentAnswerRepository;
  private QuestionCategoryRepository questionCategoryRepository;
  private AuthenticatedUserResolver authResolver;
  private WeaknessAnalysisService service;

  private Subject subject;
  private Student student;
  private QuestionCategory vocab;
  private QuestionCategory grammar;
  private QuestionCategory reading;
  private QuestionCategory mem;
  private QuestionCategory other;

  @BeforeEach
  void setUp() {
    conversationRepository = mock(ConversationRepository.class);
    conversationSourceRepository = mock(ConversationSourceRepository.class);
    sourceRepository = mock(SourceRepository.class);
    studentAnswerRepository = mock(StudentAnswerRepository.class);
    questionCategoryRepository = mock(QuestionCategoryRepository.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    // 기본 — ownership 검증 통과 (deny 케이스 없음).
    service = new WeaknessAnalysisService(
        conversationRepository, conversationSourceRepository, sourceRepository,
        studentAnswerRepository, questionCategoryRepository, authResolver);

    subject = Subject.builder().nameKo("영어").nameEn("english").build();
    ReflectionTestUtils.setField(subject, "subjectId", 1);

    User user = User.builder().loginId("std").password("pw").name("길동").phone("010").build();
    ReflectionTestUtils.setField(user, "userId", 7L);
    student = Student.builder().user(user).schoolName("현대고").grade((byte) 2).build();
    ReflectionTestUtils.setField(student, "studentId", 99L);

    vocab   = category(101L, "vocabulary",   "어휘");
    grammar = category(102L, "grammar",      "문법");
    reading = category(103L, "reading",      "독해");
    mem     = category(104L, "memorization", "암기");
    other   = category(105L, "other",        "기타");
    when(questionCategoryRepository.findBySubject_SubjectId(1))
        .thenReturn(List.of(vocab, grammar, mem, reading, other));
  }

  @Test
  @DisplayName("analyzeForConversation — 첨부된 시험지가 없으면 5종 카테고리 모두 total=0 (radar empty 상태)")
  void no_sources_returns_zero_radar() {
    // given
    Conversation conv = sampleConversation(1L);
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
    when(conversationSourceRepository.findByConversationId(1L)).thenReturn(List.of());

    // when
    WeaknessRadarResponse res = service.analyzeForConversation(1L);

    // then — 5종 카테고리가 모두 들어 있고 전부 total=0
    assertThat(res.categories()).hasSize(5);
    assertThat(res.categories()).allSatisfy(c -> {
      assertThat(c.total()).isZero();
      assertThat(c.correct()).isZero();
      assertThat(c.accuracyPercent()).isEqualTo(0.0);
    });
    // exam_id가 없으면 답안 조회 자체를 안 함
    verify(studentAnswerRepository, never()).findForRadar(any(), any());
  }

  @Test
  @DisplayName("analyzeForConversation — 첨부 source가 있어도 exam 연결이 없으면 examIds가 비어 답안 조회 skip")
  void attached_sources_without_exam_skip_query() {
    // given
    Conversation conv = sampleConversation(1L);
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

    Source nonExamSource = sampleSource(50L, /*exam*/ null);
    when(conversationSourceRepository.findByConversationId(1L))
        .thenReturn(List.of(link(1L, 50L)));
    when(sourceRepository.findAllById(List.of(50L))).thenReturn(List.of(nonExamSource));

    // when
    WeaknessRadarResponse res = service.analyzeForConversation(1L);

    // then
    assertThat(res.categories()).hasSize(5);
    assertThat(res.categories()).allSatisfy(c -> assertThat(c.total()).isZero());
    verify(studentAnswerRepository, never()).findForRadar(any(), any());
  }

  @Test
  @DisplayName("analyzeForConversation — 시험지 답안이 있으면 카테고리별 정답률 집계 (어휘 2/3, 독해 1/3 등)")
  void aggregates_answers_by_category() {
    // given
    Conversation conv = sampleConversation(1L);
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

    Exam exam = sampleExam(500L);
    Source examSrc = sampleSource(50L, exam);
    when(conversationSourceRepository.findByConversationId(1L)).thenReturn(List.of(link(1L, 50L)));
    when(sourceRepository.findAllById(List.of(50L))).thenReturn(List.of(examSrc));

    // 어휘 3문항 (2 정답), 문법 1문항 (1 정답), 독해 3문항 (1 정답), 암기 1문항 (1 정답)
    List<StudentAnswer> answers = new ArrayList<>();
    answers.add(answer(student, q(vocab),   true));
    answers.add(answer(student, q(vocab),   true));
    answers.add(answer(student, q(vocab),   false));
    answers.add(answer(student, q(grammar), true));
    answers.add(answer(student, q(reading), true));
    answers.add(answer(student, q(reading), false));
    answers.add(answer(student, q(reading), false));
    answers.add(answer(student, q(mem),     true));
    when(studentAnswerRepository.findForRadar(eq(99L), eq(List.of(500L)))).thenReturn(answers);

    // when
    WeaknessRadarResponse res = service.analyzeForConversation(1L);

    // then — 카테고리별 정답률
    var byKey = res.categories().stream()
        .collect(java.util.stream.Collectors.toMap(
            WeaknessRadarResponse.CategoryStat::categoryNameEn, c -> c));

    assertThat(byKey.get("vocabulary").total()).isEqualTo(3);
    assertThat(byKey.get("vocabulary").correct()).isEqualTo(2);
    assertThat(byKey.get("vocabulary").accuracyPercent()).isEqualTo(66.7);

    assertThat(byKey.get("grammar").total()).isEqualTo(1);
    assertThat(byKey.get("grammar").accuracyPercent()).isEqualTo(100.0);

    assertThat(byKey.get("reading").total()).isEqualTo(3);
    assertThat(byKey.get("reading").correct()).isEqualTo(1);
    assertThat(byKey.get("reading").accuracyPercent()).isEqualTo(33.3);

    assertThat(byKey.get("memorization").total()).isEqualTo(1);
    assertThat(byKey.get("memorization").accuracyPercent()).isEqualTo(100.0);

    // 답안이 0개인 other도 응답에 포함되어 있어야 한다 (radar 축 5개 유지)
    assertThat(byKey).containsKey("other");
    assertThat(byKey.get("other").total()).isZero();
  }

  @Test
  @DisplayName("analyzeForConversation — 여러 시험지가 첨부되면 examIds를 dedup해서 한 번에 조회")
  void multiple_exam_sources_dedup_exam_ids() {
    // given
    Conversation conv = sampleConversation(1L);
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

    Exam exam1 = sampleExam(500L);
    Exam exam2 = sampleExam(501L);
    Source src1 = sampleSource(50L, exam1);
    Source src2 = sampleSource(51L, exam1);  // 같은 exam을 다른 source가 가리킴 (dedup 대상)
    Source src3 = sampleSource(52L, exam2);

    when(conversationSourceRepository.findByConversationId(1L)).thenReturn(List.of(
        link(1L, 50L), link(1L, 51L), link(1L, 52L)));
    when(sourceRepository.findAllById(List.of(50L, 51L, 52L)))
        .thenReturn(List.of(src1, src2, src3));
    when(studentAnswerRepository.findForRadar(eq(99L), any())).thenReturn(List.of());

    // when
    service.analyzeForConversation(1L);

    // then — examIds는 [500, 501] 두 개로 dedup
    verify(studentAnswerRepository).findForRadar(eq(99L), eq(List.of(500L, 501L)));
  }

  @Test
  @DisplayName("analyzeForConversation — student-owned guard가 거부하면 getStudent/답안 조회 전에 403")
  void student_owned_guard_denies_before_answer_query() {
    // given
    Conversation conv = sampleConversation(1L);
    when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
    doThrow(new AccessDeniedException("학생 소유 대화만 접근"))
        .when(authResolver).requireStudentOwnedConversationAccess(conv);

    // when & then
    assertThatThrownBy(() -> service.analyzeForConversation(1L))
        .isInstanceOf(AccessDeniedException.class);
    verify(conversationSourceRepository, never()).findByConversationId(any());
    verify(studentAnswerRepository, never()).findForRadar(any(), any());
  }

  @Test
  @DisplayName("analyzeForConversation — 존재하지 않는 conversationId면 IllegalArgumentException")
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
        .fileSize(1L)
        .sourceType(f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType.STUDENT_MATERIAL)
        .build();
    ReflectionTestUtils.setField(s, "sourceId", id);
    if (exam != null) s.linkToExam(exam);
    return s;
  }

  private Exam sampleExam(Long id) {
    Exam e = Exam.builder()
        .schoolName("현대고").grade((byte) 1).examType(ExamType.MIDTERM)
        .examYear((short) 2024).examSemester((byte) 1).subject(subject)
        .build();
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

  private Question q(QuestionCategory cat) {
    return Question.builder()
        .subject(subject).category(cat).examId(500L)
        .questionType(QuestionType.MULTIPLE_CHOICE)
        .questionText("Q").correctAnswer("1").orderNum(1).questionLabel("1")
        .build();
  }

  private StudentAnswer answer(Student student, Question question, boolean correct) {
    StudentAnswer a = StudentAnswer.builder()
        .student(student).question(question).examId(500L).studentAnswer("1").build();
    a.grade(correct);
    return a;
  }
}
