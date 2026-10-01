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

  /**
   * 최근 로그인에서 OAuth가 제공한 이메일(소문자 정규화, 없으면 null). {@code users.email}은 최초 가입 때만 채워져
   * 이메일 동의 이전 가입자·이메일 변경자는 영구히 비어 있으므로, 감사회원 셀프 인증 판정(@naver.com 로컬파트 == 네이버 ID)용으로
   * 로그인마다 갱신해 따로 보관한다. 화면·메일·로그에 노출하지 않는다.
   */
  @Column(name = "provider_email", length = 100)
  private String providerEmail;

  @Builder
  public SocialAccount(Long userId, SocialProvider provider, String providerUserId, String providerEmail) {
    this.userId = userId;
    this.provider = provider;
    this.providerUserId = providerUserId;
    this.providerEmail = normalizeEmail(providerEmail);
  }

  /** 로그인마다 호출. 값이 바뀐 경우에만 true(저장 필요). 이메일이 제공되지 않은 로그인은 기존 값을 지우지 않는다. */
  public boolean refreshProviderEmail(String email) {
    String normalized = normalizeEmail(email);
    if (normalized == null || normalized.equals(providerEmail)) return false;
    this.providerEmail = normalized;
    return true;
  }

  static String normalizeEmail(String email) {
    if (email == null) return null;
    String e = email.strip().toLowerCase(java.util.Locale.ROOT);
    return e.isEmpty() || e.length() > 100 ? null : e;
  }
}
