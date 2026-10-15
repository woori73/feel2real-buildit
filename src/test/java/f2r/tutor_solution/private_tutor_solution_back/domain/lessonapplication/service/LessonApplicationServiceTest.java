package f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto.LessonApplicationResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplication;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LessonApplicationServiceTest {

  private LessonApplicationRepository lessonApplicationRepository;
  private LessonProductRepository lessonProductRepository;
  private StudentRepository studentRepository;
  private ConversationRepository conversationRepository;
  private LessonApplicationService service;

  private final JwtTokenProvider.AuthenticatedPrincipal studentPrincipal =
      new JwtTokenProvider.AuthenticatedPrincipal(10L, "student", Role.STUDENT.name());
  private final JwtTokenProvider.AuthenticatedPrincipal tutorPrincipal =
      new JwtTokenProvider.AuthenticatedPrincipal(20L, "tutor", Role.TUTOR.name());

  @BeforeEach
  void setUp() {
    lessonApplicationRepository = mock(LessonApplicationRepository.class);
    lessonProductRepository = mock(LessonProductRepository.class);
    studentRepository = mock(StudentRepository.class);
    conversationRepository = mock(ConversationRepository.class);
    service = new LessonApplicationService(
        lessonApplicationRepository,
        lessonProductRepository,
        studentRepository,
        conversationRepository);
  }

  @Test
  void apply_approvesApplication_whenCapacityRemainsAndConversationIdNull() {
    // given
    Student student = student();
    LessonProduct lesson = lesson(2, 1);
    Conversation autoConv = conversation(student, "중3 영어 내신반 학습 대화");
    when(studentRepository.findByUser_UserId(10L)).thenReturn(Optional.of(student));
    when(lessonProductRepository.findForUpdateByLessonId(100L)).thenReturn(Optional.of(lesson));
    when(lessonApplicationRepository.findByStudent_StudentIdAndLesson_LessonId(30L, 100L)).thenReturn(Optional.empty());
    when(conversationRepository.save(any(Conversation.class))).thenReturn(autoConv);
    when(lessonApplicationRepository.save(any(LessonApplication.class))).thenAnswer(invocation -> {
      LessonApplication application = invocation.getArgument(0);
      ReflectionTestUtils.setField(application, "applicationId", 500L);
      return application;
    });

    // when
    LessonApplicationResponse response = service.apply(studentPrincipal, 100L, null);

    // then
    assertThat(response.applicationId()).isEqualTo(500L);
    assertThat(response.status()).isEqualTo(LessonApplicationStatus.APPROVED);
    assertThat(lesson.getCurrentCount()).isEqualTo(2);
    verify(conversationRepository).save(any(Conversation.class));
  }

  @Test
  void apply_linksExistingConversation_whenConversationIdProvided() {
    // given
    Student student = student();
    LessonProduct lesson = lesson(2, 1);
    Conversation existing = conversation(student, "기존 대화");
    ReflectionTestUtils.setField(existing, "conversationId", 77L);
    when(studentRepository.findByUser_UserId(10L)).thenReturn(Optional.of(student));
    when(lessonProductRepository.findForUpdateByLessonId(100L)).thenReturn(Optional.of(lesson));
    when(lessonApplicationRepository.findByStudent_StudentIdAndLesson_LessonId(30L, 100L)).thenReturn(Optional.empty());
    when(conversationRepository.findById(77L)).thenReturn(Optional.of(existing));
    when(lessonApplicationRepository.save(any(LessonApplication.class))).thenAnswer(invocation -> {
      LessonApplication app = invocation.getArgument(0);
      ReflectionTestUtils.setField(app, "applicationId", 500L);
      return app;
    });

    // when
    LessonApplicationResponse response = service.apply(studentPrincipal, 100L, 77L);

    // then
    assertThat(response.applicationId()).isEqualTo(500L);
    verify(conversationRepository, never()).save(any(Conversation.class));
    verify(conversationRepository).findById(77L);
  }

  @Test
  void apply_throwsAccessDenied_whenConversationBelongsToDifferentStudent() {
    // given
    Student student = student();
    LessonProduct lesson = lesson(2, 1);
    // different student's conversation
    Student otherStudent = student();
    ReflectionTestUtils.setField(otherStudent, "studentId", 99L);
    Conversation otherConv = conversation(otherStudent, "다른 학생 대화");
    ReflectionTestUtils.setField(otherConv, "conversationId", 88L);
    when(studentRepository.findByUser_UserId(10L)).thenReturn(Optional.of(student));
    when(lessonProductRepository.findForUpdateByLessonId(100L)).thenReturn(Optional.of(lesson));
    when(lessonApplicationRepository.findByStudent_StudentIdAndLesson_LessonId(30L, 100L)).thenReturn(Optional.empty());
    when(conversationRepository.findById(88L)).thenReturn(Optional.of(otherConv));

    // when & then
    assertThatThrownBy(() -> service.apply(studentPrincipal, 100L, 88L))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("본인 대화");
    verify(lessonApplicationRepository, never()).save(any());
  }

  @Test
  void apply_throwsAccessDenied_whenConversationIsTutorOwned() {
    // given
    Student student = student();
    LessonProduct lesson = lesson(2, 1);
    User tutor = User.builder()
        .loginId("other-tutor")
        .password("pw")
        .name("강사")
        .role(Role.TUTOR)
        .build();
    ReflectionTestUtils.setField(tutor, "userId", 20L);
    Conversation tutorOwned = Conversation.forTutor(tutor, "강사 기록", null);
    ReflectionTestUtils.setField(tutorOwned, "conversationId", 89L);
    when(studentRepository.findByUser_UserId(10L)).thenReturn(Optional.of(student));
    when(lessonProductRepository.findForUpdateByLessonId(100L)).thenReturn(Optional.of(lesson));
    when(lessonApplicationRepository.findByStudent_StudentIdAndLesson_LessonId(30L, 100L)).thenReturn(Optional.empty());
    when(conversationRepository.findById(89L)).thenReturn(Optional.of(tutorOwned));

    // when & then
    assertThatThrownBy(() -> service.apply(studentPrincipal, 100L, 89L))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("학생 소유 대화");
    verify(lessonApplicationRepository, never()).save(any());
  }

  @Test
  void apply_autoCreatesConversationWithLessonName_whenConversationIdNull() {
    // given
    Student student = student();
    LessonProduct lesson = lesson(2, 1);
    when(studentRepository.findByUser_UserId(10L)).thenReturn(Optional.of(student));
    when(lessonProductRepository.findForUpdateByLessonId(100L)).thenReturn(Optional.of(lesson));
    when(lessonApplicationRepository.findByStudent_StudentIdAndLesson_LessonId(30L, 100L)).thenReturn(Optional.empty());
    when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
    when(lessonApplicationRepository.save(any(LessonApplication.class))).thenAnswer(inv -> inv.getArgument(0));

    // when
    service.apply(studentPrincipal, 100L, null);

    // then — verify conversation saved with lesson name in title
    var captor = org.mockito.ArgumentCaptor.forClass(Conversation.class);
    verify(conversationRepository).save(captor.capture());
    assertThat(captor.getValue().getTitle()).isEqualTo("중3 영어 내신반 학습 대화");
    assertThat(captor.getValue().getStudent()).isEqualTo(student);
  }

  @Test
  void apply_throwsConflict_whenApplicationAlreadyApproved() {
    // given
    Student student = student();
    LessonProduct lesson = lesson(2, 1);
    LessonApplication application = application(lesson, student, LessonApplicationStatus.APPROVED);
    when(studentRepository.findByUser_UserId(10L)).thenReturn(Optional.of(student));
    when(lessonProductRepository.findForUpdateByLessonId(100L)).thenReturn(Optional.of(lesson));
    when(lessonApplicationRepository.findByStudent_StudentIdAndLesson_LessonId(30L, 100L)).thenReturn(Optional.of(application));

    // when & then
    assertThatThrownBy(() -> service.apply(studentPrincipal, 100L, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("이미 신청");
    assertThat(lesson.getCurrentCount()).isEqualTo(1);
  }

  @Test
  void apply_reactivatesCanceledApplication_whenCapacityRemains() {
    // given
    Student student = student();
    LessonProduct lesson = lesson(2, 0);
    LessonApplication application = application(lesson, student, LessonApplicationStatus.CANCELED);
    ReflectionTestUtils.setField(application, "canceledAt", LocalDateTime.now());
    Conversation autoConv = conversation(student, "중3 영어 내신반 학습 대화");
    when(studentRepository.findByUser_UserId(10L)).thenReturn(Optional.of(student));
    when(lessonProductRepository.findForUpdateByLessonId(100L)).thenReturn(Optional.of(lesson));
    when(lessonApplicationRepository.findByStudent_StudentIdAndLesson_LessonId(30L, 100L)).thenReturn(Optional.of(application));
    when(conversationRepository.save(any(Conversation.class))).thenReturn(autoConv);

    // when
    LessonApplicationResponse response = service.apply(studentPrincipal, 100L, null);

    // then
    assertThat(response.status()).isEqualTo(LessonApplicationStatus.APPROVED);
    assertThat(response.canceledAt()).isNull();
    assertThat(lesson.getCurrentCount()).isEqualTo(1);
  }

  @Test
  void apply_throwsWhenLessonIsFull() {
    // given
    Student student = student();
    LessonProduct lesson = lesson(1, 1);
    when(studentRepository.findByUser_UserId(10L)).thenReturn(Optional.of(student));
    when(lessonProductRepository.findForUpdateByLessonId(100L)).thenReturn(Optional.of(lesson));
    when(lessonApplicationRepository.findByStudent_StudentIdAndLesson_LessonId(30L, 100L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.apply(studentPrincipal, 100L, null))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    verify(lessonApplicationRepository, never()).save(any());
  }

  @Test
  void cancel_cancelsApplication() {
    // given
    Student student = student();
    LessonProduct lesson = lesson(2, 1);
    LessonApplication application = application(lesson, student, LessonApplicationStatus.APPROVED);
    when(lessonApplicationRepository.findById(500L)).thenReturn(Optional.of(application));
    when(lessonProductRepository.findForUpdateByLessonId(100L)).thenReturn(Optional.of(lesson));

    // when
    LessonApplicationResponse response = service.cancel(studentPrincipal, 500L);

    // then
    assertThat(response.status()).isEqualTo(LessonApplicationStatus.CANCELED);
    assertThat(lesson.getCurrentCount()).isZero();
  }

  @Test
  void listTutorApplications_rejectsStudentRole() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal = studentPrincipal;

    // when & then
    assertThatThrownBy(() -> service.listTutorApplications(principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("강사 권한");
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private LessonApplication application(LessonProduct lesson, Student student, LessonApplicationStatus status) {
    LessonApplication application = LessonApplication.builder()
        .lesson(lesson)
        .student(student)
        .status(status)
        .appliedAt(LocalDateTime.now())
        .build();
    ReflectionTestUtils.setField(application, "applicationId", 500L);
    return application;
  }

  private Conversation conversation(Student student, String title) {
    return Conversation.builder()
        .student(student)
        .title(title)
        .build();
  }

  private LessonProduct lesson(int maxCapacity, int currentCount) {
    User tutor = User.builder()
        .loginId("tutor")
        .password("pw")
        .name("강사")
        .role(Role.TUTOR)
        .build();
    ReflectionTestUtils.setField(tutor, "userId", 20L);
    Academy academy = Academy.builder()
        .tutor(tutor)
        .slug("english-academy")
        .name("영어학원")
        .logoStorageKey("logo.jpg")
        .intro("소개")
        .status(AcademyStatus.PUBLISHED)
        .build();
    ReflectionTestUtils.setField(academy, "academyId", 40L);
    LessonProduct lesson = LessonProduct.builder()
        .academy(academy)
        .name("중3 영어 내신반")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(maxCapacity)
        .currentCount(currentCount)
        .scheduleType(ScheduleType.WEEKLY)
        .scheduleSummary("주3회")
        .status(LessonStatus.PUBLISHED)
        .build();
    ReflectionTestUtils.setField(lesson, "lessonId", 100L);
    return lesson;
  }

  private Student student() {
    User user = User.builder()
        .loginId("student")
        .password("pw")
        .name("학생")
        .role(Role.STUDENT)
        .build();
    ReflectionTestUtils.setField(user, "userId", 10L);
    Student student = Student.builder()
        .user(user)
        .schoolName("현대고")
        .grade((byte) 2)
        .build();
    ReflectionTestUtils.setField(student, "studentId", 30L);
    return student;
  }
}
