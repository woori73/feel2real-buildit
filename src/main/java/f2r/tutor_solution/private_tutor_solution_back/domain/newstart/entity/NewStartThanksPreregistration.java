package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 신규 감사회원 사전등록 — {@code newstart_thanks_preregistrations}(V1017). 이메일은 운영자 API 로만 등록한다. */
@Entity
@Table(name = "newstart_thanks_preregistrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NewStartThanksPreregistration extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "prereg_id")
  private Long preregId;

  @Column(name = "email", nullable = false, length = 100)
  private String email;

  @Column(name = "memo", length = 100)
  private String memo;

  @Column(name = "linked_user_id")
  private Long linkedUserId;

  @Column(name = "linked_provider_user_id", length = 64)
  private String linkedProviderUserId;

  @Column(name = "linked_at")
  private LocalDateTime linkedAt;

  public NewStartThanksPreregistration(String email, String memo) {
    this.email = email;
    this.memo = memo;
  }

  public boolean isLinked() {
    return linkedUserId != null;
  }

  public void link(Long userId, String providerUserId) {
    this.linkedUserId = userId;
    this.linkedProviderUserId = providerUserId;
    this.linkedAt = LocalDateTime.now();
  }
}
