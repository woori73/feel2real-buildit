package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialProvider;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * NAVER identity(§PHASE 2: provider=NAVER + providerUserId=response.id) ↔ Feel2Real User 연결
 * (docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md §PHASE 3/4/7/8).
 *
 * <p>실 MySQL(V982 {@code uq_social_provider_identity} unique 제약)에 의존하므로 이 클래스는
 * {@code @SpringBootTest}다 — mock으로는 race-safety(unique violation → rollback → 재조회)를
 * 검증할 수 없다.
 */
@SpringBootTest
@DisplayName("NaverAccountResolutionService")
class NaverAccountResolutionServiceTest {

  @Autowired NaverAccountResolutionService resolutionService;
  @Autowired UserRepository userRepository;
  @Autowired SocialAccountRepository socialAccountRepository;
  @Autowired PasswordEncoder passwordEncoder;

  private final List<Long> createdUserIds = new java.util.ArrayList<>();

  @AfterEach
  void cleanUp() {
    // 이 테스트가 만든 SocialAccount/User만 정리한다(다른 테스트/데이터 영향 없음).
    for (Long userId : createdUserIds) {
      socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, providerId(userId))
          .ifPresent(socialAccountRepository::delete);
    }
    userRepository.deleteAllById(createdUserIds);
  }

  private String providerId(Object seed) {
    return "test-naver-id-" + seed;
  }

  private NaverProfileIdentity identity(String providerUserId, String email, String name, String nickname) {
    return new NaverProfileIdentity(providerUserId, email, name, nickname, null);
  }

  @Test
  @DisplayName("최초 로그인 — User 생성 + ROLE_STUDENT + SocialAccount 연결")
  void firstLoginCreatesUserAndSocialAccount() {
    String providerUserId = "first-login-" + UUID.randomUUID();

    User user = resolutionService.resolveUser(identity(providerUserId, "user@example.com", "홍길동", "길동"));
    createdUserIds.add(user.getUserId());

    assertThat(user.getRole()).isEqualTo(Role.STUDENT);
    assertThat(user.getEmail()).isEqualTo("user@example.com");

    SocialAccount linked = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, providerUserId)
        .orElseThrow();
    assertThat(linked.getUserId()).isEqualTo(user.getUserId());
  }

  @Test
  @DisplayName("이미 연결된 NAVER 계정으로 다시 로그인하면 같은 User를 반환하고 추가 생성이 없다")
  void repeatLoginReturnsSameUserWithoutExtraCreation() {
    String providerUserId = "repeat-login-" + UUID.randomUUID();
    NaverProfileIdentity id = identity(providerUserId, "repeat@example.com", "김반복", "반복");

    User first = resolutionService.resolveUser(id);
    createdUserIds.add(first.getUserId());
    long userCountAfterFirst = userRepository.count();
    long socialCountAfterFirst = socialAccountRepository.count();

    User second = resolutionService.resolveUser(id);

    assertThat(second.getUserId()).isEqualTo(first.getUserId());
    assertThat(userRepository.count()).isEqualTo(userCountAfterFirst);
    assertThat(socialAccountRepository.count()).isEqualTo(socialCountAfterFirst);
  }

  @Test
  @DisplayName("email이 없어도(consent 미제공) response.id만으로 정상 처리된다 — 가짜 이메일 생성 없음")
  void missingEmailIsHandledWithoutFabricatingOne() {
    String providerUserId = "no-email-" + UUID.randomUUID();

    User user = resolutionService.resolveUser(identity(providerUserId, null, null, null));
    createdUserIds.add(user.getUserId());

    assertThat(user.getEmail()).isNull();
    // name도 없을 때 임의의 실명을 만들지 않고 명확한 비개인정보 placeholder를 쓴다.
    assertThat(user.getName()).isEqualTo("네이버 회원");
    // nickname도 없으면 User 엔티티 자체 기본 동작(loginId로 폴백)을 그대로 따른다.
    assertThat(user.getNickname()).isEqualTo(user.getLoginId());
  }

  @Test
  @DisplayName("동일 email의 기존 User가 있어도 NAVER SocialAccount가 없으면 자동 병합하지 않고 새 User를 만든다")
  void doesNotAutoMergeBySameEmail() {
    String sharedEmail = "collision-" + UUID.randomUUID() + "@example.com";
    User existing = userRepository.save(User.builder()
        .loginId("collision-existing-" + UUID.randomUUID())
        .password(passwordEncoder.encode("irrelevant"))
        .name("기존회원")
        .nickname("기존회원")
        .email(sharedEmail)
        .role(Role.STUDENT)
        .build());
    createdUserIds.add(existing.getUserId());

    String providerUserId = "collision-" + UUID.randomUUID();
    User resolved = resolutionService.resolveUser(identity(providerUserId, sharedEmail, "새사람", "새사람"));
    createdUserIds.add(resolved.getUserId());

    assertThat(resolved.getUserId()).isNotEqualTo(existing.getUserId());
    // 기존 User는 손대지 않는다.
    User reloadedExisting = userRepository.findById(existing.getUserId()).orElseThrow();
    assertThat(reloadedExisting.getName()).isEqualTo("기존회원");
  }

  @Test
  @DisplayName("동시 최초 로그인 두 건이 들어와도 User/SocialAccount는 각각 하나만 남는다")
  void concurrentFirstLoginCreatesExactlyOneUserAndSocialAccount() throws Exception {
    String providerUserId = "race-" + UUID.randomUUID();
    NaverProfileIdentity id = identity(providerUserId, "race@example.com", "동시로그인", "동시");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    try {
      List<Future<User>> futures = List.of(
          executor.submit(() -> {
            startLatch.await();
            return resolutionService.resolveUser(id);
          }),
          executor.submit(() -> {
            startLatch.await();
            return resolutionService.resolveUser(id);
          }));
      startLatch.countDown();

      User a = futures.get(0).get(30, TimeUnit.SECONDS);
      User b = futures.get(1).get(30, TimeUnit.SECONDS);
      createdUserIds.add(a.getUserId());
      createdUserIds.add(b.getUserId());

      assertThat(a.getUserId()).isEqualTo(b.getUserId());

      long linkedCount = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, providerUserId)
          .stream().count();
      assertThat(linkedCount).isEqualTo(1);
    } finally {
      executor.shutdown();
    }
  }
}
