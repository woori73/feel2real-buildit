package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2.NaverV2Client.NaverV2Profile;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2.NaverV2LoginService.LoginResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * NaverV2LoginService given/when/then — V1({@code NaverLoginServiceTest})과 동일한 정책을
 * 검증하되, (a) 신규 생성 시 loginId 접두사가 V1과 절대 겹치지 않고(naver2_), (b) 이미 V1으로
 * 연결된 소셜 계정이 있으면 새로 만들지 않고 그 User를 그대로 찾아 쓴다(내부 저장소 재사용 = 중복
 * 가입 방지)는 점을 추가로 고정한다.
 */
@DisplayName("NaverV2LoginService — 네이버 프로필 → Feel2Real User 자동가입 정책 (NewStart V2)")
class NaverV2LoginServiceTest {

  private static final String PROVIDER_ID = "375412389021";

  private UserRepository userRepository;
  private SocialAccountRepository socialAccountRepository;
  private PasswordEncoder passwordEncoder;
  private NaverV2LoginService service;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    socialAccountRepository = Mockito.mock(SocialAccountRepository.class);
    passwordEncoder = new BCryptPasswordEncoder();
    service = new NaverV2LoginService(userRepository, socialAccountRepository, passwordEncoder);
  }

  private NaverV2Profile profile() {
    return new NaverV2Profile(PROVIDER_ID, "김철수", "cheolsu", "cheolsu@example.com");
  }

  @Test
  void firstLoginAutoCreatesStudentPlaceholderUserAndLinksAccount() {
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);

    LoginResult result = service.loginOrRegister(profile());

    assertThat(result.created()).isTrue();
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User created = userCaptor.getValue();
    // ADMIN/TUTOR 자동 부여 금지, STUDENT는 role 컬럼 제약상 placeholder(클래스 Javadoc 참고)
    assertThat(created.getRole()).isEqualTo(Role.STUDENT);
    assertThat(created.getLoginId()).startsWith("naver2_").hasSizeLessThanOrEqualTo(50);

    ArgumentCaptor<SocialAccount> linkCaptor = ArgumentCaptor.forClass(SocialAccount.class);
    verify(socialAccountRepository).save(linkCaptor.capture());
    assertThat(linkCaptor.getValue().getProvider()).isEqualTo(SocialProvider.NAVER);
    assertThat(linkCaptor.getValue().getProviderUserId()).isEqualTo(PROVIDER_ID);

    assertThat(result.user().role()).isEqualTo("STUDENT");
  }

  @Test
  void repeatLoginFindsExistingUserWithoutDuplicateCreation() {
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);
    service.loginOrRegister(profile());
    verify(userRepository).save(any(User.class));

    User created = User.builder().loginId("naver2_x").password("x").name("김철수")
        .nickname("cheolsu").role(Role.STUDENT).build();
    SocialAccount link = SocialAccount.builder()
        .userId(1L).provider(SocialProvider.NAVER).providerUserId(PROVIDER_ID).build();
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.of(link));
    when(userRepository.findById(1L)).thenReturn(Optional.of(created));

    LoginResult second = service.loginOrRegister(profile());

    verify(userRepository, Mockito.times(1)).save(any(User.class));
    assertThat(second.created()).isFalse();
    assertThat(second.user().loginId()).isEqualTo("naver2_x");
  }

  @Test
  @DisplayName("이미 V1으로 연결된 네이버 계정이면 V2도 같은 User를 찾아 쓴다(중복 가입 없음)")
  void reusesAccountAlreadyLinkedByV1() {
    // given — V1(naver_ 접두)이 이미 만든 User + SocialAccount(공유 테이블)
    User v1User = User.builder().loginId("naver_legacy").password("x").name("김철수")
        .nickname("cheolsu").role(Role.STUDENT).build();
    SocialAccount v1Link = SocialAccount.builder()
        .userId(99L).provider(SocialProvider.NAVER).providerUserId(PROVIDER_ID).build();
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.of(v1Link));
    when(userRepository.findById(99L)).thenReturn(Optional.of(v1User));

    // when — 같은 실사용자가 V2로 로그인
    LoginResult result = service.loginOrRegister(profile());

    // then — 새 User/SocialAccount를 만들지 않고 V1이 만든 것을 그대로 재사용
    assertThat(result.created()).isFalse();
    assertThat(result.user().loginId()).isEqualTo("naver_legacy");
    verify(userRepository, never()).save(any(User.class));
    verify(socialAccountRepository, never()).save(any(SocialAccount.class));
  }

  @Test
  void emailIsNeverUsedForAccountMerging() {
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);

    service.loginOrRegister(profile());

    verify(userRepository, never()).findAllByEmail(any());
    verify(userRepository, never()).findByLoginIdAndEmail(any(), any());
    verify(userRepository, never()).findByLoginId(any());
  }

  @Test
  void missingProviderUserIdIsRejected() {
    assertThatThrownBy(() -> service.loginOrRegister(new NaverV2Profile(null, "a", "b", null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.loginOrRegister(new NaverV2Profile("  ", "a", "b", null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.loginOrRegister(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nicknameWithIllegalCharactersFallsBackToStableAlias() {
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);

    service.loginOrRegister(new NaverV2Profile(PROVIDER_ID, "김@철#수!", "@@@###", null));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertThat(captor.getValue().getNickname()).matches("^[가-힣A-Za-z0-9 _-]{2,20}$");
  }
}
