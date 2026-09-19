package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthClient.NaverProfile;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * NaverLoginService given/when/then — (provider, providerUserId) 매핑, 신규 회원은
 * STUDENT로만 생성, 이메일 자동 병합 없음, 중복 생성 방지를 검증한다.
 */
@DisplayName("NaverLoginService — 네이버 프로필 → Feel2Real User 매핑 정책")
class NaverLoginServiceTest {

  private static final String PROVIDER_ID = "375412389021";

  private UserRepository userRepository;
  private SocialAccountRepository socialAccountRepository;
  private PasswordEncoder passwordEncoder;
  private NaverLoginService service;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    socialAccountRepository = Mockito.mock(SocialAccountRepository.class);
    passwordEncoder = new BCryptPasswordEncoder();
    service = new NaverLoginService(userRepository, socialAccountRepository, passwordEncoder);
  }

  private NaverProfile profile() {
    return new NaverProfile(PROVIDER_ID, "김철수", "cheolsu", "cheolsu@example.com");
  }

  @Test
  void existingLinkedUserIsReturnedWithoutCreation() {
    // given
    User existing = User.builder().loginId("naver_old").password("x").name("김철수")
        .nickname("cheolsu").role(Role.STUDENT).build();
    SocialAccount link = SocialAccount.builder()
        .userId(7L).provider(SocialProvider.NAVER).providerUserId(PROVIDER_ID).build();
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.of(link));
    when(userRepository.findById(7L)).thenReturn(Optional.of(existing));

    // when
    LoginResponse result = service.loginOrUpdate(profile());

    // then
    assertThat(result.loginId()).isEqualTo("naver_old");
    verify(userRepository, never()).save(any(User.class));
    verify(socialAccountRepository, never()).save(any(SocialAccount.class));
  }

  @Test
  void newProfileCreatesStudentOnlyAndLinkRecord() {
    // given
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);

    // when
    LoginResponse result = service.loginOrUpdate(profile());

    // then — ADMIN/TUTOR 자동 부여 금지: STUDENT 고정
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User created = userCaptor.getValue();
    assertThat(created.getRole()).isEqualTo(Role.STUDENT);
    assertThat(created.getLoginId()).startsWith("naver_").hasSizeLessThanOrEqualTo(50);
    assertThat(created.getName()).isEqualTo("김철수");
    assertThat(created.getNickname()).isEqualTo("cheolsu");
    assertThat(created.getEmail()).isEqualTo("cheolsu@example.com");
    // password 컬럼 NOT NULL 회피용 난해시 — 평문이 아니고 재현 불가 랜덤
    assertThat(created.getPassword()).isNotBlank();
    assertThat(passwordEncoder.matches(PROVIDER_ID, created.getPassword())).isFalse();

    ArgumentCaptor<SocialAccount> linkCaptor = ArgumentCaptor.forClass(SocialAccount.class);
    verify(socialAccountRepository).save(linkCaptor.capture());
    assertThat(linkCaptor.getValue().getProvider()).isEqualTo(SocialProvider.NAVER);
    assertThat(linkCaptor.getValue().getProviderUserId()).isEqualTo(PROVIDER_ID);

    assertThat(result.role()).isEqualTo("STUDENT");
    assertThat(result.displayName()).isNotBlank();
  }

  @Test
  void sameProviderIdIsIdempotentAcrossRepeatLogins() {
    // given — 첫 로그인: 생성, 두 번째 로그인: 연결 재조회로 기존 회원 반환
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);

    // when — 1st
    service.loginOrUpdate(profile());

    // then — 생성 1건
    verify(userRepository).save(any(User.class));

    // given — 2nd (링크 존재)
    User created = User.builder().loginId("naver_x").password("x").name("김철수")
        .nickname("cheolsu").role(Role.STUDENT).build();
    SocialAccount link = SocialAccount.builder()
        .userId(1L).provider(SocialProvider.NAVER).providerUserId(PROVIDER_ID).build();
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.of(link));
    when(userRepository.findById(1L)).thenReturn(Optional.of(created));

    // when
    LoginResponse second = service.loginOrUpdate(profile());

    // then — 중복 회원 생성 없음
    verify(userRepository, Mockito.times(1)).save(any(User.class));
    assertThat(second.loginId()).isEqualTo("naver_x");
  }

  @Test
  void emailIsNeverUsedForAccountMerging() {
    // given — 동일 이메일로 조회 가능한 기존 회원이 있어도 providerUserId 연결로만 식별한다
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);

    // when
    service.loginOrUpdate(profile());

    // then — 이메일 기반 조회 호출 없음, 신규 생성 + 연결 저장
    verify(userRepository, never()).findAllByEmail(any());
    verify(userRepository, never()).findByLoginIdAndEmail(any(), any());
    verify(userRepository, never()).findByLoginId(any());
    verify(userRepository).save(any(User.class));
    verify(socialAccountRepository).save(any(SocialAccount.class));
  }

  @Test
  void missingProviderUserIdIsRejected() {
    // given/when/then
    assertThatThrownBy(() -> service.loginOrUpdate(new NaverProfile(null, "a", "b", null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.loginOrUpdate(new NaverProfile("  ", "a", "b", null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.loginOrUpdate(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nicknameWithIllegalCharactersFallsBackToStableAlias() {
    // given
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);

    // when — users.nickname NOT NULL + 닉네임 문자열 계약 유지
    service.loginOrUpdate(new NaverProfile(PROVIDER_ID, "김@철#수!", "@@@###", null));

    // then
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    String nickname = captor.getValue().getNickname();
    assertThat(nickname).matches("^[가-힣A-Za-z0-9 _-]{2,20}$");
  }
}
