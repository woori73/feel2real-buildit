package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AuthenticatedUserResolver — TUTOR/ADMIN 가드")
class AuthenticatedUserResolverTest {

  private StudentRepository studentRepository;
  private LessonApplicationRepository lessonApplicationRepository;
  private ConversationSourceRepository conversationSourceRepository;
  private ConversationRepository conversationRepository;
  private AuthenticatedUserResolver resolver;

  @BeforeEach
  void setUp() {
    studentRepository = mock(StudentRepository.class);
    lessonApplicationRepository = mock(LessonApplicationRepository.class);
    conversationSourceRepository = mock(ConversationSourceRepository.class);
    conversationRepository = mock(ConversationRepository.class);
    resolver = new AuthenticatedUserResolver(
        studentRepository, lessonApplicationRepository,
        conversationSourceRepository, conversationRepository);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("verifyAdminOrTutor — ADMIN 통과")
  void verifyAdminOrTutor_admin_passes() {
    // given
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(1L, "admin", Role.ADMIN.name()));

    // when & then
    resolver.verifyAdminOrTutor();
  }

  @Test
  @DisplayName("verifyAdminOrTutor — TUTOR 통과")
  void verifyAdminOrTutor_tutor_passes() {
    // given
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(2L, "tutor", Role.TUTOR.name()));

    // when & then
    resolver.verifyAdminOrTutor();
  }

  @Test
  @DisplayName("verifyAdminOrTutor — STUDENT 거부")
  void verifyAdminOrTutor_student_denied() {
    // given
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(3L, "student", Role.STUDENT.name()));

    // when & then
    assertThatThrownBy(() -> resolver.verifyAdminOrTutor())
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("verifyOwnsConversation — TUTOR가 APPROVED 담당 학생 대화면 통과")
  void verifyOwnsConversation_tutor_approvedStudent_passes() {
    // given
    Long tutorUserId = 10L;
    Long conversationId = 300L;
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(tutorUserId, "tutor", Role.TUTOR.name()));
    when(lessonApplicationRepository.existsApprovedTutorConversation(tutorUserId, conversationId))
        .thenReturn(true);

    Conversation conv = mock(Conversation.class);
    Student student = mock(Student.class);
    when(conv.getConversationId()).thenReturn(conversationId);
    when(conv.getStudent()).thenReturn(student);
    when(conv.getTutorUser()).thenReturn(null);

    // when & then
    resolver.verifyOwnsConversation(conv);
  }

  @Test
  @DisplayName("requireStudentOwnedConversationAccess — TUTOR assigned student-owned conversation read 통과")
  void requireStudentOwnedConversationAccess_tutor_approvedConversation_passes() {
    // given
    Long tutorUserId = 10L;
    Long conversationId = 301L;
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(tutorUserId, "tutor", Role.TUTOR.name()));
    when(lessonApplicationRepository.existsApprovedTutorConversation(tutorUserId, conversationId))
        .thenReturn(true);

    Conversation conv = mock(Conversation.class);
    Student student = mock(Student.class);
    when(conv.getConversationId()).thenReturn(conversationId);
    when(conv.getStudent()).thenReturn(student);
    when(conv.getTutorUser()).thenReturn(null);

    // when & then — legacy verifyOwnsConversation과 동일한 student-owned read guard 경로가 통과해야 한다.
    resolver.requireStudentOwnedConversationAccess(conv);
  }

  @Test
  @DisplayName("verifyOwnsConversation — TUTOR가 APPROVED 신청 없는 학생이면 거부")
  void verifyOwnsConversation_tutor_noApprovedStudent_denied() {
    // given
    Long tutorUserId = 10L;
    Long conversationId = 999L;
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(tutorUserId, "tutor", Role.TUTOR.name()));
    when(lessonApplicationRepository.existsApprovedTutorConversation(tutorUserId, conversationId))
        .thenReturn(false);

    Conversation conv = mock(Conversation.class);
    Student student = mock(Student.class);
    when(conv.getConversationId()).thenReturn(conversationId);
    when(conv.getStudent()).thenReturn(student);
    when(conv.getTutorUser()).thenReturn(null);

    // when & then
    assertThatThrownBy(() -> resolver.verifyOwnsConversation(conv))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("verifyOwnsSource — STUDENT가 본인 대화에 첨부된 타인 업로드 자료(강사 시험대비)를 열람 가능")
  void verifyOwnsSource_student_conversationScoped_passes() {
    // given
    Long studentUserId = 50L;
    Long sourceId = 77L;
    Long conversationId = 300L;
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(studentUserId, "student", Role.STUDENT.name()));

    Source source = mock(Source.class);
    when(source.getSourceId()).thenReturn(sourceId);
    User uploader = mock(User.class);
    when(uploader.getUserId()).thenReturn(999L);  // 강사가 업로드
    when(source.getUploadedBy()).thenReturn(uploader);

    ConversationSource cs = mock(ConversationSource.class);
    when(cs.getConversationId()).thenReturn(conversationId);
    when(conversationSourceRepository.findBySourceId(sourceId)).thenReturn(java.util.List.of(cs));

    Conversation conv = mock(Conversation.class);
    Student student = mock(Student.class);
    User studentUser = mock(User.class);
    when(studentUser.getUserId()).thenReturn(studentUserId);
    when(student.getUser()).thenReturn(studentUser);
    when(conv.getStudent()).thenReturn(student);
    when(conversationRepository.findById(conversationId)).thenReturn(java.util.Optional.of(conv));

    // when & then — 업로더 본인이 아니어도 본인 대화 첨부면 통과
    resolver.verifyOwnsSource(source);
  }

  @Test
  @DisplayName("verifyOwnsSource — STUDENT가 본인 대화에 없는 타인 자료면 403")
  void verifyOwnsSource_student_notInOwnedConversation_denied() {
    // given
    Long studentUserId = 50L;
    Long sourceId = 88L;
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(studentUserId, "student", Role.STUDENT.name()));

    Source source = mock(Source.class);
    when(source.getSourceId()).thenReturn(sourceId);
    User uploader = mock(User.class);
    when(uploader.getUserId()).thenReturn(999L);
    when(source.getUploadedBy()).thenReturn(uploader);
    when(conversationSourceRepository.findBySourceId(sourceId)).thenReturn(java.util.List.of());

    // when & then
    assertThatThrownBy(() -> resolver.verifyOwnsSource(source))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("verifyOwnsLearningMaterial — 본인 학생 통과")
  void verifyOwnsLearningMaterial_student_owner_passes() {
    // given
    Long studentUserId = 50L;
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(studentUserId, "student", Role.STUDENT.name()));

    LearningMaterial material = mock(LearningMaterial.class);
    Student student = mock(Student.class);
    User user = mock(User.class);
    when(material.getStudent()).thenReturn(student);
    lenient().when(student.getStudentId()).thenReturn(300L);
    when(student.getUser()).thenReturn(user);
    when(user.getUserId()).thenReturn(studentUserId);

    // when & then
    resolver.verifyOwnsLearningMaterial(material);
  }

  @Test
  @DisplayName("verifyOwnsLearningMaterial — 다른 학생 거부")
  void verifyOwnsLearningMaterial_other_student_denied() {
    // given
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(50L, "student", Role.STUDENT.name()));

    LearningMaterial material = mock(LearningMaterial.class);
    Student student = mock(Student.class);
    User user = mock(User.class);
    when(material.getStudent()).thenReturn(student);
    lenient().when(student.getStudentId()).thenReturn(300L);
    when(student.getUser()).thenReturn(user);
    when(user.getUserId()).thenReturn(99L);

    // when & then
    assertThatThrownBy(() -> resolver.verifyOwnsLearningMaterial(material))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("verifyOwnsLearningMaterial — TUTOR가 APPROVED 담당 학생이면 통과, 아니면 거부")
  void verifyOwnsLearningMaterial_tutor_approvedStudent_check() {
    // given
    Long tutorUserId = 10L;
    Long studentId = 300L;
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(tutorUserId, "tutor", Role.TUTOR.name()));

    LearningMaterial material = mock(LearningMaterial.class);
    Student student = mock(Student.class);
    when(material.getStudent()).thenReturn(student);
    when(student.getStudentId()).thenReturn(studentId);

    when(lessonApplicationRepository.existsApprovedTutorStudent(tutorUserId, studentId))
        .thenReturn(true);

    // when & then — APPROVED 신청 있으면 통과
    resolver.verifyOwnsLearningMaterial(material);

    // given — APPROVED 신청 없음
    when(lessonApplicationRepository.existsApprovedTutorStudent(tutorUserId, studentId))
        .thenReturn(false);

    // when & then — 거부
    assertThatThrownBy(() -> resolver.verifyOwnsLearningMaterial(material))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("resolveStudentId — TUTOR가 APPROVED 담당 학생 지정하면 그대로 반환")
  void resolveStudentId_tutor_approvedStudent_returned() {
    // given
    Long tutorUserId = 10L;
    Long studentId = 300L;
    JwtTokenProvider.AuthenticatedPrincipal p =
        new JwtTokenProvider.AuthenticatedPrincipal(tutorUserId, "tutor", Role.TUTOR.name());
    when(lessonApplicationRepository.existsApprovedTutorStudent(tutorUserId, studentId))
        .thenReturn(true);

    // when
    Long result = resolver.resolveStudentId(p, studentId);

    // then
    assertThat(result).isEqualTo(studentId);
  }

  @Test
  @DisplayName("resolveStudentId — TUTOR가 target 없이 호출하면 거부")
  void resolveStudentId_tutor_without_target_denied() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal p =
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name());

    // when & then
    assertThatThrownBy(() -> resolver.resolveStudentId(p, null))
        .isInstanceOf(AccessDeniedException.class);
  }


  @Test
  @DisplayName("student-shaped guard — tutor-owned conversation은 getStudent NPE 전에 403")
  void studentOwnedGuard_denies_tutorOwnedConversation_beforeStudentAccess() {
    // given
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name()));
    Conversation conv = mock(Conversation.class);
    User tutor = mock(User.class);
    when(conv.getStudent()).thenReturn(null);
    when(conv.getTutorUser()).thenReturn(tutor);

    // when & then
    assertThatThrownBy(() -> resolver.requireStudentOwnedConversationAccess(conv))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("학생 소유");
  }

  @Test
  @DisplayName("tutor-owned guard — 소유 강사만 통과하고 ADMIN/non-owner는 거부")
  void tutorOwnedGuard_allowsOnlyOwningTutor() {
    // given
    Long ownerTutorUserId = 10L;
    Conversation conv = mock(Conversation.class);
    User tutor = mock(User.class);
    when(conv.getStudent()).thenReturn(null);
    when(conv.getTutorUser()).thenReturn(tutor);
    when(tutor.getUserId()).thenReturn(ownerTutorUserId);

    // owning tutor passes
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(ownerTutorUserId, "tutor", Role.TUTOR.name()));
    resolver.requireTutorOwnedConversationManage(conv);

    // non-owner tutor denied
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(11L, "other", Role.TUTOR.name()));
    assertThatThrownBy(() -> resolver.requireTutorOwnedConversationManage(conv))
        .isInstanceOf(AccessDeniedException.class);

    // admin denied: admin UI/API for tutor-owned records is out of scope
    putPrincipal(new JwtTokenProvider.AuthenticatedPrincipal(1L, "admin", Role.ADMIN.name()));
    assertThatThrownBy(() -> resolver.requireTutorOwnedConversationManage(conv))
        .isInstanceOf(AccessDeniedException.class);
  }

  private void putPrincipal(JwtTokenProvider.AuthenticatedPrincipal principal) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, "n/a", java.util.List.of()));
  }
}
