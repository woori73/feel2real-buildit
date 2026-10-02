package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartPasswordResetResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.entity.NewStartAdminPasswordReset;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.repository.NewStartAdminPasswordResetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("NewStart ADMIN 초기화 서비스 — 운영자만, hash만 저장, 감사 로그에 값 없음")
class NewStartAdminPasswordResetServiceTest {

  private UserRepository userRepository;
  private NewStartAdminPasswordResetRepository resetRepository;
  private NewStartTempPasswordGenerator generator;
  private PasswordEncoder passwordEncoder;
  private NewStartAdminPasswordResetService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    resetRepository = mock(NewStartAdminPasswordResetRepository.class);
    generator = new NewStartTempPasswordGenerator();
    passwordEncoder = mock(PasswordEncoder.class);
    service = new NewStartAdminPasswordResetService(userRepository, resetRepository, generator, passwordEncoder);
    when(resetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("ADMIN → PASS: 새로 만든 1회용 값으로 hash를 교체하고 강제 설정 플래그를 켠다")
  void adminResetReplacesHashAndMarksUser() {
    User target = member(77L, "ns-member-01", "새출발러", "old-hash");
    when(userRepository.findById(77L)).thenReturn(Optional.of(target));
    when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "bcrypt(" + inv.getArgument(0) + ")");

    AdminNewStartPasswordResetResponse response = service.reset(77L, admin());

    String temporary = response.temporaryPassword();
    assertThat(temporary).hasSizeGreaterThanOrEqualTo(8);
    assertThat(target.getPassword()).isEqualTo("bcrypt(" + temporary + ")");
    assertThat(target.getPassword().toLowerCase()).doesNotContain("reset1234");
    assertThat(target.isPasswordResetRequired()).isTrue();
    assertThat(response.userId()).isEqualTo(77L);
    assertThat(response.loginId()).isEqualTo("ns-member-01");
    verify(userRepository).save(target);
  }

  @Test
  @DisplayName("ADMIN → 감사 로그는 대상·실행자만 남긴다. 비밀번호 값을 담을 필드 자체가 없다")
  void auditRowCarriesNoPasswordValue() {
    User target = member(77L, "ns-member-01", "새출발러", "old-hash");
    when(userRepository.findById(77L)).thenReturn(Optional.of(target));
    when(passwordEncoder.encode(anyString())).thenReturn("bcrypt(new)");

    service.reset(77L, admin());

    ArgumentCaptor<NewStartAdminPasswordReset> captor = ArgumentCaptor.forClass(NewStartAdminPasswordReset.class);
    verify(resetRepository).save(captor.capture());
    NewStartAdminPasswordReset audit = captor.getValue();
    assertThat(audit.getTargetUserId()).isEqualTo(77L);
    assertThat(audit.getTargetLoginId()).isEqualTo("ns-member-01");
    assertThat(audit.getAdminUserId()).isEqualTo(1L);
    assertThat(audit.getAdminLoginId()).isEqualTo("admin-f2r");

    List<String> columns = Arrays.stream(NewStartAdminPasswordReset.class.getDeclaredFields())
        .filter(field -> !field.isSynthetic())
        .map(Field::getName)
        .toList();
    assertThat(columns).containsExactlyInAnyOrder(
        "resetId", "targetUserId", "targetLoginId", "adminUserId", "adminLoginId", "createdAt");
  }

  @Test
  @DisplayName("일반회원·감사회원(role=STUDENT) → 다른 회원 초기화 DENY — 조회도 쓰기 전에 거절")
  void studentIsDenied() {
    when(userRepository.findById(77L)).thenReturn(Optional.of(member(77L, "ns-member-01", "새출발러", "old-hash")));

    assertThatThrownBy(() -> service.reset(77L, new AuthenticatedPrincipal(901L, "ns-901", "STUDENT")))
        .isInstanceOf(AccessDeniedException.class);
    // 감사회원 권한은 newstart_member_grants의 capability이고 JWT에 실리지 않는다 — 판정은 role 기준.
    assertThatThrownBy(() -> service.reset(77L, new AuthenticatedPrincipal(902L, "ns-902", "STUDENT")))
        .isInstanceOf(AccessDeniedException.class);

    verify(userRepository, never()).findById(any());
    verify(userRepository, never()).save(any());
    verify(resetRepository, never()).save(any());
  }

  @Test
  @DisplayName("principal 없음(비로그인)·TUTOR → DENY(403)")
  void anonymousAndTutorAreDenied() {
    assertThatThrownBy(() -> service.reset(77L, null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.reset(77L, new AuthenticatedPrincipal(5L, "tutor-5", "TUTOR")))
        .isInstanceOf(AccessDeniedException.class);
    verify(resetRepository, never()).save(any());
  }

  @Test
  @DisplayName("대상은 STUDENT로 제한 — 운영 ADMIN 계정은 이 경로로 초기화하지 않는다")
  void onlyStudentAccountsAreTargetable() {
    when(userRepository.findById(9L))
        .thenReturn(Optional.of(member(9L, "admin-f2r", "관리자", "hash", Role.ADMIN)));

    assertThatThrownBy(() -> service.reset(9L, admin()))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(userRepository, never()).save(any());
    verify(resetRepository, never()).save(any());
  }

  @Test
  @DisplayName("존재하지 않는 회원 → 404")
  void unknownTargetIsNotFound() {
    when(userRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.reset(404L, admin()))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  private AuthenticatedPrincipal admin() {
    return new AuthenticatedPrincipal(1L, "admin-f2r", "ADMIN");
  }

  private User member(Long id, String loginId, String nickname, String password) {
    return member(id, loginId, nickname, password, Role.STUDENT);
  }

  private User member(Long id, String loginId, String nickname, String password, Role role) {
    User user = User.builder()
        .loginId(loginId)
        .password(password)
        .name(nickname)
        .nickname(nickname)
        .role(role)
        .build();
    ReflectionTestUtils.setField(user, "userId", id);
    return user;
  }
}
