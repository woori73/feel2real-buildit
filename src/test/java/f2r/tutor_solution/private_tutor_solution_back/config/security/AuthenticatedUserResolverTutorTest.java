package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedUserResolverTutorTest {

  private LessonApplicationRepository lessonApplicationRepository;
  private StudentRepository studentRepository;
  private AuthenticatedUserResolver resolver;

  private static final Long TUTOR_USER_ID = 10L;
  private static final Long STUDENT_ID = 200L;
  private static final Long STUDENT_USER_ID = 201L;
  private static final Long CONVERSATION_ID = 300L;

  @BeforeEach
  void setUp() {
    lessonApplicationRepository = mock(LessonApplicationRepository.class);
    studentRepository = mock(StudentRepository.class);
    resolver = new AuthenticatedUserResolver(
        studentRepository,
        lessonApplicationRepository,
        mock(f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository.class),
        mock(f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository.class));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void requireTutor_allowsTutorPrincipal() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(1L, "tutor", Role.TUTOR.name());

    // when & then
    assertThatCode(() -> resolver.requireTutor(principal)).doesNotThrowAnyException();
  }

  @Test
  void requireTutor_rejectsStudentPrincipal() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(1L, "student", Role.STUDENT.name());

    // when & then
    assertThatThrownBy(() -> resolver.requireTutor(principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("강사 권한");
  }

  @Test
  void verifyOwnsAcademy_allowsOwningTutor() {
    // given
    User tutor = User.builder()
        .loginId("tutor")
        .password("pw")
        .name("강사")
        .role(Role.TUTOR)
        .build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);
    Academy academy = Academy.builder()
        .tutor(tutor)
        .slug("academy")
        .name("Academy")
        .logoStorageKey("logo")
        .intro("intro")
        .build();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name()), null));

    // when & then
    assertThatCode(() -> resolver.verifyOwnsAcademy(academy)).doesNotThrowAnyException();
  }

  @Test
  void verifyOwnsAcademy_rejectsNonOwnerTutor() {
    // given
    User tutor = User.builder()
        .loginId("owner")
        .password("pw")
        .name("소유자")
        .role(Role.TUTOR)
        .build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);
    Academy academy = Academy.builder()
        .tutor(tutor)
        .slug("academy")
        .name("Academy")
        .logoStorageKey("logo")
        .intro("intro")
        .build();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new JwtTokenProvider.AuthenticatedPrincipal(11L, "other", Role.TUTOR.name()), null));

    // when & then
    assertThatThrownBy(() -> resolver.verifyOwnsAcademy(academy))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("자기 Academy");
  }

  // ─── P4 동등성 테스트 매트릭스 ──────────────────────────────────────────────
  // 구 동작(tutor_student_map) vs 신 동작(lesson_applications) 동등성 검증.
  // verifyOwnsConversation을 대표 케이스로 사용 (resolveStudentId/verifyOwnsLearningMaterial도 동일 헬퍼 사용).

  @Test
  @DisplayName("P4-1: APPROVED 신청 존재 → 통과 (구: map.active=true 통과와 동등)")
  void p4_approvedApplication_passes() {
    // given
    putTutorPrincipal(TUTOR_USER_ID);
    when(lessonApplicationRepository.existsApprovedTutorConversation(TUTOR_USER_ID, CONVERSATION_ID))
        .thenReturn(true);
    Conversation conv = convForStudent(STUDENT_ID);

    // when & then
    assertThatCode(() -> resolver.verifyOwnsConversation(conv)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("P4-2: 신청 없음 → 거부 (구: map 없음 거부와 동등)")
  void p4_noApplication_denied() {
    // given
    putTutorPrincipal(TUTOR_USER_ID);
    when(lessonApplicationRepository.existsApprovedTutorConversation(TUTOR_USER_ID, CONVERSATION_ID))
        .thenReturn(false);
    Conversation conv = convForStudent(STUDENT_ID);

    // when & then
    assertThatThrownBy(() -> resolver.verifyOwnsConversation(conv))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("P4-3: CANCELED 신청만 존재 → 거부 (구: map.active=false 거부와 동등)")
  void p4_onlyCanceledApplication_denied() {
    // given — APPROVED 없음 → existsApprovedTutorStudent returns false
    putTutorPrincipal(TUTOR_USER_ID);
    when(lessonApplicationRepository.existsApprovedTutorConversation(TUTOR_USER_ID, CONVERSATION_ID))
        .thenReturn(false);
    Conversation conv = convForStudent(STUDENT_ID);

    // when & then
    assertThatThrownBy(() -> resolver.verifyOwnsConversation(conv))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("P4-4: 다른 강사 수업 신청 → 거부 (구: 해당 tutor map 없음 거부와 동등)")
  void p4_otherTutorApplication_denied() {
    // given — 다른 강사(userId=99) 기준으로는 APPROVED 없음
    Long otherTutorId = 99L;
    putTutorPrincipal(otherTutorId);
    when(lessonApplicationRepository.existsApprovedTutorConversation(otherTutorId, CONVERSATION_ID))
        .thenReturn(false);
    Conversation conv = convForStudent(STUDENT_ID);

    // when & then
    assertThatThrownBy(() -> resolver.verifyOwnsConversation(conv))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("P4-5: map 존재 but APPROVED app 없음 → 거부 (구 동작과 의도적 의미 차이 — ADR 명시)")
  void p4_mapExistsButNoApprovedApp_denied() {
    // given — tutor_student_map은 존재하지만 lesson_applications에 APPROVED 없음
    //   구 동작: map.active=true → 통과
    //   신 동작: APPROVED app 없음 → 거부 (의도적 의미 변경)
    putTutorPrincipal(TUTOR_USER_ID);
    when(lessonApplicationRepository.existsApprovedTutorConversation(TUTOR_USER_ID, CONVERSATION_ID))
        .thenReturn(false);
    Conversation conv = convForStudent(STUDENT_ID);

    // when & then
    assertThatThrownBy(() -> resolver.verifyOwnsConversation(conv))
        .isInstanceOf(AccessDeniedException.class);
  }

  // ─── resolveUserId IDOR 가드 (근본원인 #1) ────────────────────────────────

  @Test
  @DisplayName("resolveUserId: TUTOR가 담당 학생 user_id를 주면 통과")
  void resolveUserId_tutor_teachingStudent_passes() {
    // given
    Student student = mock(Student.class);
    when(student.getStudentId()).thenReturn(STUDENT_ID);
    when(studentRepository.findByUser_UserId(STUDENT_USER_ID)).thenReturn(java.util.Optional.of(student));
    when(lessonApplicationRepository.existsApprovedTutorStudent(TUTOR_USER_ID, STUDENT_ID))
        .thenReturn(true);
    JwtTokenProvider.AuthenticatedPrincipal tutor =
        new JwtTokenProvider.AuthenticatedPrincipal(TUTOR_USER_ID, "tutor", Role.TUTOR.name());

    // when & then
    org.assertj.core.api.Assertions.assertThat(resolver.resolveUserId(tutor, STUDENT_USER_ID))
        .isEqualTo(STUDENT_USER_ID);
  }

  @Test
  @DisplayName("resolveUserId: TUTOR가 담당하지 않는 학생 user_id를 주면 403 (IDOR 차단)")
  void resolveUserId_tutor_nonTeachingStudent_denied() {
    // given
    Student student = mock(Student.class);
    when(student.getStudentId()).thenReturn(STUDENT_ID);
    when(studentRepository.findByUser_UserId(STUDENT_USER_ID)).thenReturn(java.util.Optional.of(student));
    when(lessonApplicationRepository.existsApprovedTutorStudent(TUTOR_USER_ID, STUDENT_ID))
        .thenReturn(false);
    JwtTokenProvider.AuthenticatedPrincipal tutor =
        new JwtTokenProvider.AuthenticatedPrincipal(TUTOR_USER_ID, "tutor", Role.TUTOR.name());

    // when & then
    assertThatThrownBy(() -> resolver.resolveUserId(tutor, STUDENT_USER_ID))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("담당 학생이 아닙니다");
  }

  @Test
  @DisplayName("resolveUserId: TUTOR가 본인 user_id면 학생 조회 없이 통과")
  void resolveUserId_tutor_self_passesWithoutLookup() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal tutor =
        new JwtTokenProvider.AuthenticatedPrincipal(TUTOR_USER_ID, "tutor", Role.TUTOR.name());

    // when & then — target이 본인이거나 null이면 repo 조회 없이 본인 반환
    org.assertj.core.api.Assertions.assertThat(resolver.resolveUserId(tutor, TUTOR_USER_ID))
        .isEqualTo(TUTOR_USER_ID);
    org.assertj.core.api.Assertions.assertThat(resolver.resolveUserId(tutor, null))
        .isEqualTo(TUTOR_USER_ID);
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private void putTutorPrincipal(Long tutorUserId) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            new JwtTokenProvider.AuthenticatedPrincipal(tutorUserId, "tutor", Role.TUTOR.name()),
            null));
  }

  private Conversation convForStudent(Long studentId) {
    Conversation conv = mock(Conversation.class);
    Student student = mock(Student.class);
    when(conv.getConversationId()).thenReturn(CONVERSATION_ID);
    when(conv.getStudent()).thenReturn(student);
    when(student.getStudentId()).thenReturn(studentId);
    return conv;
  }
}
