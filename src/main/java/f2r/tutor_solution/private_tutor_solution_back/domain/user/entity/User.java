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

  public void updatePassword(String encodedPassword) {
    this.password = encodedPassword;
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
