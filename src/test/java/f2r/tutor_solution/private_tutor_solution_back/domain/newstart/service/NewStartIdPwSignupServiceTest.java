package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterClaimRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGrantService.GrantResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartIdPwSignupService.MemberType;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("NewStart ID/PW 가입 — 누구나 가입, 명단과 확실히 일치할 때만 감사회원")
class NewStartIdPwSignupServiceTest {

  private UserRepository userRepository;
  private PasswordEncoder encoder;
  private NewStartThanksRosterRepository rosterRepository;
  private NewStartThanksRosterClaimRepository claimRepository;
  private NewStartGrantService grantService;
  private NewStartIdPwSignupService service;
  private User saved;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    encoder = new BCryptPasswordEncoder();
    rosterRepository = mock(NewStartThanksRosterRepository.class);
    claimRepository = mock(NewStartThanksRosterClaimRepository.class);
    grantService = mock(NewStartGrantService.class);
    service = new NewStartIdPwSignupService(userRepository, encoder, rosterRepository, claimRepository, grantService);
    when(userRepository.existsByLoginId(anyString())).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
      saved = inv.getArgument(0);
      ReflectionTestUtils.setField(saved, "userId", 77L);
      return saved;
    });
    when(grantService.grant(anyLong(), any(), anyString())).thenReturn(GrantResult.success(null));
  }

  private static NewStartThanksRoster roster(long id, String nick, String prefix, Long linkedUser) {
    NewStartThanksRoster r = NewStartThanksRoster.builder().cafeMemberTag("tag" + id).naverIdPrefix(prefix).nickname(nick)
        .grade("감사멤버").memberLevel(150).source("NAVER_CAFE").matchStatus("UNMATCHED").build();
    ReflectionTestUtils.setField(r, "rosterId", id);
    ReflectionTestUtils.setField(r, "linkedUserId", linkedUser);
    return r;
  }

  @Test
  @DisplayName("명단과 일치하지 않으면 일반회원으로 가입(비밀번호는 암호화 저장, 권한 부여 없음)")
  void noRosterMatch_isGeneralMember() {
    when(rosterRepository.findByNickname("없는닉")).thenReturn(List.of());

    var r = service.signup("Newbie01", "pw-12345678", "없는닉");

    assertThat(r.ok()).isTrue();
    assertThat(r.memberType()).isEqualTo(MemberType.GENERAL);
    assertThat(saved.getLoginId()).isEqualTo("newbie01");
    assertThat(saved.getPassword()).isNotEqualTo("pw-12345678");
    assertThat(encoder.matches("pw-12345678", saved.getPassword())).isTrue();   // 이후 /api/auth/login 이 같은 방식으로 검증
    verify(grantService, never()).grant(anyLong(), any(), anyString());
    verify(claimRepository, never()).claim(anyLong(), anyLong());
  }

  @Test
  @DisplayName("닉네임 정확 일치 + 후보 1명 + 미연결 + ID 접두부 일치 → 감사회원(명단 선점 + THANKS_MEMBER)")
  void exactMatch_isThanksMember() {
    when(rosterRepository.findByNickname("카페닉")).thenReturn(List.of(roster(5, "카페닉", "ssgw", null)));
    when(claimRepository.claim(5L, 77L)).thenReturn(1);

    var r = service.signup("ssgw1234", "pw-12345678", "카페닉");

    assertThat(r.memberType()).isEqualTo(MemberType.THANKS);
    verify(grantService).grant(eq(77L), eq(NewStartCapability.THANKS_MEMBER), eq("thanks-idpw"));
  }

  @Test
  @DisplayName("2글자 접두부 회원도 같은 규칙으로 감사회원이 된다")
  void twoCharPrefix_isThanksMember() {
    when(rosterRepository.findByNickname("투글")).thenReturn(List.of(roster(6, "투글", "to", null)));
    when(claimRepository.claim(6L, 77L)).thenReturn(1);

    assertThat(service.signup("tomato99", "pw-12345678", "투글").memberType()).isEqualTo(MemberType.THANKS);
  }

  @Test
  @DisplayName("닉네임만 맞고 ID 접두부가 다르면 일반회원(닉네임 단독 인증 금지)")
  void nicknameOnly_isGeneral() {
    when(rosterRepository.findByNickname("카페닉")).thenReturn(List.of(roster(5, "카페닉", "ssgw", null)));

    assertThat(service.signup("otherid1", "pw-12345678", "카페닉").memberType()).isEqualTo(MemberType.GENERAL);
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("후보가 2명 이상이거나 이미 연결된 명단이면 애매하므로 일반회원")
  void ambiguousOrAlreadyLinked_isGeneral() {
    when(rosterRepository.findByNickname("동명")).thenReturn(List.of(roster(1, "동명", "abcd", null), roster(2, "동명", "abcd", null)));
    assertThat(service.signup("abcd0001", "pw-12345678", "동명").memberType()).isEqualTo(MemberType.GENERAL);

    when(rosterRepository.findByNickname("연결됨")).thenReturn(List.of(roster(3, "연결됨", "abcd", 9L)));
    assertThat(service.signup("abcd0002", "pw-12345678", "연결됨").memberType()).isEqualTo(MemberType.GENERAL);
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("동시 가입으로 명단 선점에 실패하면(영향 0건) 일반회원이고 권한을 주지 않는다")
  void claimLost_isGeneral() {
    when(rosterRepository.findByNickname("카페닉")).thenReturn(List.of(roster(5, "카페닉", "ssgw", null)));
    when(claimRepository.claim(5L, 77L)).thenReturn(0);

    assertThat(service.signup("ssgw1234", "pw-12345678", "카페닉").memberType()).isEqualTo(MemberType.GENERAL);
    verify(grantService, never()).grant(anyLong(), any(), anyString());
  }

  @Test
  @DisplayName("입력 검증: 잘못된 ID·짧은 비밀번호·짧은 닉네임·이미 가입된 ID 는 거절")
  void validation() {
    assertThat(service.signup("한글ID", "pw-12345678", "닉네임").ok()).isFalse();
    assertThat(service.signup("validid1", "short", "닉네임").ok()).isFalse();
    assertThat(service.signup("validid1", "pw-12345678", "닉").ok()).isFalse();
    when(userRepository.existsByLoginId("taken01")).thenReturn(true);
    assertThat(service.signup("taken01", "pw-12345678", "닉네임").ok()).isFalse();
    verify(userRepository, never()).saveAndFlush(any());
  }
}
