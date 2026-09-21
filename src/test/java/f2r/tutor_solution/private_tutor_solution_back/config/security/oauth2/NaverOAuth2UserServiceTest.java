package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * NaverOAuth2UserService#mapAndLink given/when/then — 실제 네트워크 호출(delegate.loadUser)은
 * 제외하고, "response" 언랩 + 기존 User/SocialAccount 연결 로직만 검증한다(V1/V2와 같은 정책:
 * STUDENT placeholder, 이메일 자동 병합 없음, 동일 provider id 중복 생성 방지).
 */
@DisplayName("NaverOAuth2UserService#mapAndLink — 네이버 공식 응답(response 래핑) → Feel2Real User 연결")
class NaverOAuth2UserServiceTest {

  private static final String PROVIDER_ID = "375412389021";

  private UserRepository userRepository;
  private SocialAccountRepository socialAccountRepository;
  private PasswordEncoder passwordEncoder;
  private NaverOAuth2UserService service;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    socialAccountRepository = Mockito.mock(SocialAccountRepository.class);
    passwordEncoder = new BCryptPasswordEncoder();
    service = new NaverOAuth2UserService(userRepository, socialAccountRepository, passwordEncoder);
  }

  /** 네이버 공식 응답 형태: {resultcode, message, response:{id,email,name,nickname,...}}. */
  private Map<String, Object> rawAttributes(String id, String name, String nickname, String email) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", id);
    if (name != null) response.put("name", name);
    if (nickname != null) response.put("nickname", nickname);
    if (email != null) response.put("email", email);
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("resultcode", "00");
    raw.put("message", "success");
    raw.put("response", response);
    return raw;
  }

  @Test
  void firstLoginAutoCreatesStudentPlaceholderUserAndLinksAccount() {
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);

    OAuth2User user = service.mapAndLink(rawAttributes(PROVIDER_ID, "김철수", "cheolsu", "cheolsu@example.com"));

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User created = userCaptor.getValue();
    assertThat(created.getRole()).isEqualTo(Role.STUDENT); // ADMIN/TUTOR 자동 부여 금지
    assertThat(created.getLoginId()).startsWith("naverstd_").hasSizeLessThanOrEqualTo(50);

    ArgumentCaptor<SocialAccount> linkCaptor = ArgumentCaptor.forClass(SocialAccount.class);
    verify(socialAccountRepository).save(linkCaptor.capture());
    assertThat(linkCaptor.getValue().getProvider()).isEqualTo(SocialProvider.NAVER);
    assertThat(linkCaptor.getValue().getProviderUserId()).isEqualTo(PROVIDER_ID);

    assertThat(user.getName()).isEqualTo(PROVIDER_ID); // nameAttributeKey="id" → getName()=id 값
    assertThat(user.<String>getAttribute("f2rRole")).isEqualTo("STUDENT");
    assertThat(user.<String>getAttribute("f2rLoginId")).startsWith("naverstd_");
    assertThat(user.getAuthorities().stream().map(GrantedAuthority::getAuthority))
        .containsExactly("ROLE_STUDENT");
  }

  @Test
  @DisplayName("같은 provider id로 다시 로그인하면 USER_FOUND — User를 또 만들지 않는다")
  void secondLoginWithSameProviderIdFindsExistingUserWithoutDuplication() {
    // given — 1차 로그인으로 이미 생성된 상태(실사용자 검증 2026-09-21에서 실제로 발생한 시나리오:
    // NAVER_OAUTH2_USER_CREATED 1건이 이미 DB에 있다)
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);
    ArgumentCaptor<User> firstSave = ArgumentCaptor.forClass(User.class);
    service.mapAndLink(rawAttributes(PROVIDER_ID, "김철수", "cheolsu", null));
    verify(userRepository).save(firstSave.capture());
    User created = firstSave.getValue();

    // when — 2차 로그인: 이번엔 이미 연결된 SocialAccount가 존재(userId는 IDENTITY 생성이라
    // mock 환경에선 null이므로, 실제 저장 후의 상태를 흉내내 고정된 id로 재구성한다)
    Mockito.reset(userRepository, socialAccountRepository);
    User persisted = User.builder().loginId(created.getLoginId()).password("x")
        .name(created.getName()).nickname(created.getNickname()).role(Role.STUDENT).build();
    SocialAccount link = SocialAccount.builder()
        .userId(7L).provider(SocialProvider.NAVER).providerUserId(PROVIDER_ID).build();
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.of(link));
    when(userRepository.findById(7L)).thenReturn(Optional.of(persisted));

    OAuth2User secondLogin = service.mapAndLink(rawAttributes(PROVIDER_ID, "김철수", "cheolsu", null));

    // then — 신규 생성 없음, 같은 loginId 그대로 재사용
    verify(userRepository, never()).save(any(User.class));
    verify(socialAccountRepository, never()).save(any(SocialAccount.class));
    assertThat(secondLogin.<String>getAttribute("f2rLoginId")).isEqualTo(created.getLoginId());
  }

  @Test
  @DisplayName("이미 V1/V2 등 다른 경로로 연결된 네이버 계정이면 같은 User를 찾아 쓴다(중복 가입 없음)")
  void reusesAlreadyLinkedAccountAcrossAnyNaverLoginPath() {
    User existing = User.builder().loginId("naver_legacy").password("x").name("김철수")
        .nickname("cheolsu").role(Role.STUDENT).build();
    SocialAccount link = SocialAccount.builder()
        .userId(99L).provider(SocialProvider.NAVER).providerUserId(PROVIDER_ID).build();
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.of(link));
    when(userRepository.findById(99L)).thenReturn(Optional.of(existing));

    OAuth2User user = service.mapAndLink(rawAttributes(PROVIDER_ID, "김철수", "cheolsu", null));

    verify(userRepository, never()).save(any(User.class));
    verify(socialAccountRepository, never()).save(any(SocialAccount.class));
    assertThat(user.<String>getAttribute("f2rLoginId")).isEqualTo("naver_legacy");
  }

  @Test
  void emailIsNeverUsedForAccountMerging() {
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);

    service.mapAndLink(rawAttributes(PROVIDER_ID, "김철수", "cheolsu", "cheolsu@example.com"));

    verify(userRepository, never()).findAllByEmail(any());
    verify(userRepository, never()).findByLoginIdAndEmail(any(), any());
    verify(userRepository, never()).findByLoginId(any());
  }

  @Test
  @DisplayName("response 래퍼가 없으면(비표준 응답) OAuth2AuthenticationException")
  void missingResponseWrapperIsRejected() {
    Map<String, Object> flatNoWrapper = Map.of("resultcode", "00", "id", PROVIDER_ID);

    assertThatThrownBy(() -> service.mapAndLink(flatNoWrapper))
        .isInstanceOf(OAuth2AuthenticationException.class);
    verify(socialAccountRepository, never()).findByProviderAndProviderUserId(any(), any());
  }

  @Test
  @DisplayName("response는 있지만 id가 없으면 OAuth2AuthenticationException")
  void missingIdInResponseIsRejected() {
    Map<String, Object> noId = new LinkedHashMap<>();
    noId.put("response", Map.of("name", "김철수"));

    assertThatThrownBy(() -> service.mapAndLink(noId))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void nicknameWithIllegalCharactersFallsBackToStableAlias() {
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, PROVIDER_ID))
        .thenReturn(Optional.empty());
    when(userRepository.existsByLoginId(any())).thenReturn(false);

    service.mapAndLink(rawAttributes(PROVIDER_ID, "김@철#수!", "@@@###", null));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertThat(captor.getValue().getNickname()).matches("^[가-힣A-Za-z0-9 _-]{2,20}$");
  }
}
