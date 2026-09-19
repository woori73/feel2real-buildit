package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소셜 계정 ↔ Feel2Real users 연결.
 *
 * <p>provider identity(예: 네이버 stable user id)로만 회원을 식별한다. 이메일 기반
 * 자동 병합은 의도적으로 지원하지 않는다 — 서로 다른 계정이 이메일 하나로Merge되는 사고를
 * 막기 위한 정책이다.
 */
@Entity
@Table(name = "user_social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "social_account_id")
  private Long socialAccountId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 20)
  private SocialProvider provider;

  @Column(name = "provider_user_id", nullable = false, length = 64)
  private String providerUserId;

  @Builder
  public SocialAccount(Long userId, SocialProvider provider, String providerUserId) {
    this.userId = userId;
    this.provider = provider;
    this.providerUserId = providerUserId;
  }
}
