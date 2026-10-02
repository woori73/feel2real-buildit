package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * ADMIN이 회원을 1회용 임시 비밀번호로 초기화한 사실만 남기는 append-only 감사 로그
 * ({@code newstart_admin_password_resets}, V1021).
 *
 * <p>남기는 것은 대상 회원·실행 ADMIN·시각뿐이다. 비밀번호 원문도, hash도, ADMIN에게 한 번
 * 보여준 임시 비밀번호도 이 row 어디에도 들어가지 않는다 — 들어갈 setter 자체가 없다.
 * 갱신·삭제 경로가 없는 이유도 같다: 감사 로그는 상태를 바꾸면 안 된다(상태는
 * {@code users.password_reset_required}에 있다).
 */
@Entity
@Table(name = "newstart_admin_password_resets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewStartAdminPasswordReset {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "reset_id")
  private Long resetId;

  @Column(name = "target_user_id", nullable = false)
  private Long targetUserId;

  @Column(name = "target_login_id", nullable = false, length = 50)
  private String targetLoginId;

  @Column(name = "admin_user_id", nullable = false)
  private Long adminUserId;

  @Column(name = "admin_login_id", nullable = false, length = 50)
  private String adminLoginId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static NewStartAdminPasswordReset of(Long targetUserId, String targetLoginId,
                                              Long adminUserId, String adminLoginId) {
    NewStartAdminPasswordReset reset = new NewStartAdminPasswordReset();
    reset.targetUserId = targetUserId;
    reset.targetLoginId = targetLoginId;
    reset.adminUserId = adminUserId;
    reset.adminLoginId = adminLoginId;
    return reset;
  }
}
