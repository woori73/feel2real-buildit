package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberGrant;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사회원 권한 부여·회수(운영자 전용 실행 경로).
 *
 * <p>{@code newstart_member_grants} row가 THANKS_MEMBER의 유일한 정본이고, 판정은
 * {@link NewStartEntitlementService}가 한다 — 이 클래스는 row를 넣거나 빼기만 하고
 * 허용 규칙을 따로 두지 않는다(판정 지점 분리 유지).
 *
 * <p>부여 대상은 숫자 user_id만 받고 email·실명 등 PII는 요청에도 응답에도 싣지 않는다.
 * {@code grantedBy}에는 운영자 식별 표식만 남긴다(DB 컬럼 50자).
 */
@Service
public class NewStartGrantService {

  static final int MAX_GRANTED_BY_CHARS = 50;

  private final NewStartMemberGrantRepository grantRepository;
  private final UserRepository userRepository;

  public NewStartGrantService(NewStartMemberGrantRepository grantRepository, UserRepository userRepository) {
    this.grantRepository = grantRepository;
    this.userRepository = userRepository;
  }

  public record GrantView(Long grantId, Long userId, NewStartCapability capability, String grantedBy) {}

  /** result-object 계약: 실패 사유는 사용자 안내 문구로만 돌려준다(내부 예외·SQL 상세 미노출). */
  public record GrantResult(boolean ok, String errorMessage, GrantView grant) {
    public static GrantResult success(GrantView view) {
      return new GrantResult(true, null, view);
    }

    public static GrantResult failure(String errorMessage) {
      return new GrantResult(false, errorMessage, null);
    }
  }

  @Transactional(readOnly = true)
  public List<GrantView> list() {
    return grantRepository.findAllByOrderByGrantIdAsc().stream().map(NewStartGrantService::toView).toList();
  }

  /** 부여는 idempotent — 이미 row가 있으면 그대로 성공(중복 INSERT는 UNIQUE가 걸러내고, 여기선 확인만 한다). */
  @Transactional
  public GrantResult grant(Long userId, NewStartCapability capability, String grantedBy) {
    if (userId == null || userId <= 0) {
      return GrantResult.failure("대상 회원 user_id가 필요합니다.");
    }
    NewStartCapability target = capability == null ? NewStartCapability.THANKS_MEMBER : capability;
    if (!userRepository.existsById(userId)) {
      return GrantResult.failure("회원을 찾을 수 없습니다.");
    }
    GrantView existing = grantRepository.findByUserIdAndCapability(userId, target)
        .map(NewStartGrantService::toView).orElse(null);
    if (existing != null) {
      return GrantResult.success(existing);
    }
    NewStartMemberGrant saved = grantRepository.save(NewStartMemberGrant.builder()
        .userId(userId)
        .capability(target)
        .grantedBy(operatorMarker(grantedBy))
        .build());
    return GrantResult.success(toView(saved));
  }

  @Transactional
  public GrantResult revoke(Long userId, NewStartCapability capability) {
    if (userId == null || userId <= 0) {
      return GrantResult.failure("대상 회원 user_id가 필요합니다.");
    }
    NewStartCapability target = capability == null ? NewStartCapability.THANKS_MEMBER : capability;
    long removed = grantRepository.deleteByUserIdAndCapability(userId, target);
    if (removed == 0) {
      return GrantResult.failure("해당 회원에게 부여된 권한이 없습니다.");
    }
    return GrantResult.success(new GrantView(null, userId, target, null));
  }

  private static String operatorMarker(String grantedBy) {
    String marker = grantedBy == null || grantedBy.isBlank() ? "operator" : grantedBy.strip();
    return marker.length() <= MAX_GRANTED_BY_CHARS ? marker : marker.substring(0, MAX_GRANTED_BY_CHARS);
  }

  private static GrantView toView(NewStartMemberGrant grant) {
    return new GrantView(grant.getGrantId(), grant.getUserId(), grant.getCapability(), grant.getGrantedBy());
  }
}
