package f2r.tutor_solution.private_tutor_solution_back.domain.user.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "login_id", nullable = false, unique = true, length = 50)
  private String loginId;

  @Column(name = "password", nullable = false, length = 255)
  private String password;

  @Column(name = "name", nullable = false, length = 30)
  private String name;

  @Column(name = "nickname", nullable = false, length = 50)
  private String nickname;

  @Column(name = "email", length = 100)
  private String email;

  @Column(name = "phone", length = 20)
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 10)
  private Role role;

  /** 임시 비밀번호 상태. true면 로그인 직후 새 비밀번호를 정하기 전까지 다른 화면을 쓸 수 없다. */
  @Column(name = "password_change_required", nullable = false)
  private boolean passwordChangeRequired;

  /** 임시 비밀번호 유효기간(서버 기본 시간대). 임시 비밀번호 상태가 아니면 null. */
  @Column(name = "temp_password_expires_at")
  private java.time.LocalDateTime tempPasswordExpiresAt;

  @Builder
  public User(String loginId, String password, String name, String nickname, String email, String phone, Role role) {
    this.loginId = loginId;
    this.password = password;
    this.name = name;
    this.nickname = hasText(nickname) ? nickname.trim() : loginId;
    this.email = email;
    this.phone = phone;
    this.role = role;
  }

  /** 회원이 직접 정한 비밀번호로 바꾼다. 임시 비밀번호 상태도 함께 끝난다. */
  public void updatePassword(String encodedPassword) {
    this.password = encodedPassword;
    this.passwordChangeRequired = false;
    this.tempPasswordExpiresAt = null;
  }

  /** 임시 비밀번호(해시)를 넣고, 로그인 후 변경을 강제한다. */
  public void issueTemporaryPassword(String encodedPassword, java.time.LocalDateTime expiresAt) {
    this.password = encodedPassword;
    this.passwordChangeRequired = true;
    this.tempPasswordExpiresAt = expiresAt;
  }

  /** 임시 비밀번호 상태인데 유효기간이 지났으면 true. */
  public boolean isTemporaryPasswordExpired(java.time.LocalDateTime now) {
    return passwordChangeRequired && tempPasswordExpiresAt != null && !now.isBefore(tempPasswordExpiresAt);
  }

  public void updateLoginId(String loginId) {
    this.loginId = loginId;
  }

  public void updateName(String name) {
    this.name = name;
  }

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }

  public String getDisplayName() {
    if (role == Role.ADMIN) {
      return "관리자";
    }
    if (hasText(nickname)) {
      return nickname;
    }
    return loginId;
  }

  public void updateEmail(String email) {
    this.email = email;
  }

  public void updatePhone(String phone) {
    this.phone = phone;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
