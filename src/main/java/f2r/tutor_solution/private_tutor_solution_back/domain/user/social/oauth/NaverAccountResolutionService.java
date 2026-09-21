package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialProvider;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * NAVER {@code response.id}를 기존 Feel2Real 회원(User)에 연결한다.
 *
 * <p>identity 기준은 {@code provider=NAVER + providerUserId=response.id} 오직 하나다(§PHASE 2).
 * email이 우연히 같다고 기존 User와 자동 병합하지 않는다(§PHASE 7) — 이 클래스는 그런 병합 로직
 * 자체를 아예 갖지 않는다: SocialAccount로 연결된 적이 없으면 무조건 신규 User를 만든다.
 *
 * <p>STEP 2 범위: JWT 발급/쿠키/redirect는 여기서 하지 않는다(STEP 3).
 */
@Service
public class NaverAccountResolutionService {

  private static final Logger log = LoggerFactory.getLogger(NaverAccountResolutionService.class);

  private final SocialAccountRepository socialAccountRepository;
  private final UserRepository userRepository;
  private final NaverAccountProvisioningService provisioningService;

  public NaverAccountResolutionService(
      SocialAccountRepository socialAccountRepository,
      UserRepository userRepository,
      NaverAccountProvisioningService provisioningService) {
    this.socialAccountRepository = socialAccountRepository;
    this.userRepository = userRepository;
    this.provisioningService = provisioningService;
  }

  /**
   * 기존 연결이 있으면 그 User를 반환하고, 없으면 새 User+SocialAccount를 만들어 반환한다.
   * 동시에 같은 NAVER 계정으로 최초 로그인 두 건이 들어와도 V982의
   * {@code uq_social_provider_identity(provider, provider_user_id)} unique 제약 덕분에 User가
   * 두 개 남지 않는다 — 진 쪽은 {@link NaverAccountProvisioningService}의 트랜잭션이 통째로
   * 롤백되고, 여기서 다시 조회해 이긴 쪽 User로 합류한다.
   */
  public User resolveUser(NaverProfileIdentity identity) {
    Optional<User> existingUser = findLinkedUser(identity.providerUserId());
    if (existingUser.isPresent()) {
      log.info("NAVER_ACCOUNT_RESOLVED existing=true");
      return existingUser.get();
    }

    try {
      User created = provisioningService.provision(identity);
      log.info("NAVER_ACCOUNT_RESOLVED existing=false");
      return created;
    } catch (DataIntegrityViolationException raceLoserException) {
      // 동시 최초 로그인 race에서 진 경우 — 이긴 쪽이 이미 커밋한 SocialAccount를 다시 찾는다.
      User winner = findLinkedUser(identity.providerUserId())
          .orElseThrow(() -> raceLoserException); // 정말 unique 위반이 아니었으면 원래 예외를 던진다
      log.info("NAVER_ACCOUNT_RESOLVED existing=true");
      return winner;
    }
  }

  private Optional<User> findLinkedUser(String providerUserId) {
    return socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, providerUserId)
        .map(this::requireLinkedUser);
  }

  private User requireLinkedUser(SocialAccount socialAccount) {
    return userRepository.findById(socialAccount.getUserId())
        .orElseThrow(() -> new IllegalStateException(
            "SocialAccount는 존재하지만 연결된 User가 없습니다: userId=" + socialAccount.getUserId()));
  }
}
