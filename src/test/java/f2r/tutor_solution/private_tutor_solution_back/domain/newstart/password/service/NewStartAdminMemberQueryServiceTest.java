package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberGrant;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartMemberDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartMemberPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartMemberSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.entity.NewStartAdminPasswordReset;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.repository.NewStartAdminPasswordResetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("ADMIN 회원 조회 — 로그인 ID/카페 닉네임 검색, 회원 유형과 계정 상태 표시")
class NewStartAdminMemberQueryServiceTest {

  private UserRepository userRepository;
  private NewStartMemberGrantRepository grantRepository;
  private NewStartThanksRosterRepository rosterRepository;
  private NewStartAdminPasswordResetRepository resetRepository;
  private NewStartAdminMemberQueryService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    grantRepository = mock(NewStartMemberGrantRepository.class);
    rosterRepository = mock(NewStartThanksRosterRepository.class);
    resetRepository = mock(NewStartAdminPasswordResetRepository.class);
    service = new NewStartAdminMemberQueryService(
        userRepository, grantRepository, rosterRepository, resetRepository);
  }

  @Test
  @DisplayName("검색어를 비우면 STUDENT 목록을 최근 가입 순으로, size 상한(50) 안에서 반환한다")
  void blankQueryListsStudentsWithClampedSize() {
    User thanks = member(11L, "ns-thanks", "감사닉", false);
    User general = member(12L, "ns-general", "일반닉", true);
    when(userRepository.findByRole(eq(Role.STUDENT), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(thanks, general), org.springframework.data.domain.PageRequest.of(0, 20), 2));
    when(grantRepository.findByUserIdInAndCapability(List.of(11L, 12L), NewStartCapability.THANKS_MEMBER))
        .thenReturn(List.of(grant(11L)));

    AdminNewStartMemberPage page = service.search(null, -5, 9_999);

    verify(userRepository).findByRole(eq(Role.STUDENT), any(Pageable.class));
    assertThat(page.query()).isEmpty();
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(50);
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.members()).extracting(AdminNewStartMemberSummary::loginId)
        .containsExactly("ns-thanks", "ns-general");
    assertThat(page.members()).extracting(AdminNewStartMemberSummary::memberType)
        .containsExactly(AdminNewStartMemberSummary.THANKS, AdminNewStartMemberSummary.GENERAL);
    assertThat(page.members()).extracting(AdminNewStartMemberSummary::accountStatus)
        .containsExactly(AdminNewStartMemberSummary.ACTIVE, AdminNewStartMemberSummary.RESET_REQUIRED);
  }

  @Test
  @DisplayName("검색어가 있으면 로그인 ID·닉네임 부분 일치 조회를 쓰고 응답에 검색어를 돌려준다")
  void nonBlankQueryUsesSearchRepository() {
    User general = member(12L, "ns-general", "일반닉", false);
    when(userRepository.searchByRoleAndLoginIdOrNickname(eq(Role.STUDENT), eq("일반"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(general)));
    when(grantRepository.findByUserIdInAndCapability(List.of(12L), NewStartCapability.THANKS_MEMBER))
        .thenReturn(List.of());

    AdminNewStartMemberPage page = service.search(" 일반 ", 0, null);

    assertThat(page.query()).isEqualTo("일반");
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.members()).singleElement()
        .satisfies(row -> assertThat(row.cafeNickname()).isEqualTo("일반닉"));
    verify(userRepository, never()).findByRole(any(), any());
  }

  @Test
  @DisplayName("행이 없으면 grant 일괄 조회를 하지 않는다 (빈 IN 절 회피)")
  void emptyResultSkipsGrantLookup() {
    when(userRepository.searchByRoleAndLoginIdOrNickname(eq(Role.STUDENT), eq("없음"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    AdminNewStartMemberPage page = service.search("없음", 0, 20);

    assertThat(page.members()).isEmpty();
    verifyNoInteractions(grantRepository);
  }

  @Test
  @DisplayName("상세 — 최근 로그인 미기록은 null 그대로, 초기화 이력은 최신 1건만 붙인다")
  void detailExposesLatestResetOnly() {
    User target = member(77L, "ns-member-01", "새출발러", true);
    ReflectionTestUtils.setField(target, "createdAt", LocalDateTime.of(2026, 9, 1, 10, 0));
    when(userRepository.findById(77L)).thenReturn(Optional.of(target));
    when(grantRepository.existsByUserIdAndCapability(77L, NewStartCapability.THANKS_MEMBER)).thenReturn(true);
    when(resetRepository.findByTargetUserIdOrderByResetIdDesc(eq(77L), any(Pageable.class)))
        .thenReturn(List.of(NewStartAdminPasswordReset.of(77L, "ns-member-01", 1L, "admin-f2r")));
    when(rosterRepository.findByLinkedUserId(77L)).thenReturn(Optional.of(mock(NewStartThanksRoster.class)));

    AdminNewStartMemberDetail detail = service.detail(77L);

    assertThat(detail.userId()).isEqualTo(77L);
    assertThat(detail.loginId()).isEqualTo("ns-member-01");
    assertThat(detail.cafeNickname()).isEqualTo("새출발러");
    assertThat(detail.memberType()).isEqualTo(AdminNewStartMemberSummary.THANKS);
    assertThat(detail.accountStatus()).isEqualTo(AdminNewStartMemberSummary.RESET_REQUIRED);
    assertThat(detail.passwordResetRequired()).isTrue();
    assertThat(detail.joinedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
    // 과거 로그인 이력은 소급 생성하지 않는다 — 컬럼 도입 이후 값이 없으면 null.
    assertThat(detail.lastLoginAt()).isNull();
    assertThat(detail.lastResetBy()).isEqualTo("admin-f2r");
    assertThat(detail.thanksRosterLinked()).isTrue();
  }

  @Test
  @DisplayName("상세 대상은 회원 계정뿐 — ADMIN·TUTOR id는 404로 구분하지 않고 감춘다")
  void detailRejectsNonStudentAccounts() {
    User admin = member(1L, "admin-f2r", "관리자", false);
    ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
    when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.detail(1L))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(e.getReason()).isEqualTo("NewStart 회원만 조회할 수 있습니다.");
        });
    verifyNoInteractions(grantRepository, resetRepository, rosterRepository);
  }

  @Test
  @DisplayName("조회 DTO에는 비밀번호 재료가 없다 — 화면이 표시할 수 없는 구조")
  void dtosCarryNoPasswordMaterial() {
    assertThat(java.util.Arrays.stream(AdminNewStartMemberSummary.class.getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName).toList())
        .containsExactlyInAnyOrder(
            "userId", "loginId", "cafeNickname", "memberType", "accountStatus", "joinedAt", "lastLoginAt");
    assertThat(java.util.Arrays.stream(AdminNewStartMemberDetail.class.getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName).toList())
        .doesNotContain("password", "passwordHash", "temporaryPassword");
  }

  private User member(Long id, String loginId, String nickname, boolean resetRequired) {
    User user = User.builder()
        .loginId(loginId)
        .password("$2a$10$hashvalue")
        .name(nickname)
        .nickname(nickname)
        .role(Role.STUDENT)
        .build();
    ReflectionTestUtils.setField(user, "userId", id);
    if (resetRequired) {
      user.markPasswordResetRequired();
    }
    return user;
  }

  private NewStartMemberGrant grant(Long userId) {
    return NewStartMemberGrant.builder()
        .userId(userId)
        .capability(NewStartCapability.THANKS_MEMBER)
        .grantedBy("admin-f2r")
        .build();
  }
}
