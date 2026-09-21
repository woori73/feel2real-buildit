package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * NewStart 전용 표시 별명 — {@code newstart_member_profiles}.
 *
 * <p>{@code users}(canonical identity)와 완전히 분리된 표시(presentation) 전용 데이터다.
 * {@code users.nickname}은 BuildIt/Wiki/Academy 등 다른 서비스가 공유하는 전역 필드라
 * NewStart 회원이 여기서 별명을 바꾸면 그 서비스들 화면까지 함께 바뀐다 — 그래서 NewStart는
 * 자기 것만 따로 둔다. 별명은 {@code users.user_id}를 그대로 PK로 쓰는 1:1 row다(식별자가
 * 아니라 표시 문자열이므로 별명 자체에는 unique 제약을 두지 않는다 — 기존
 * {@code users.nickname} 정책과 동일).
 */
@Entity
@Table(name = "newstart_member_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NewStartMemberProfile extends BaseEntity {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "display_nickname", nullable = false, length = 20)
  private String displayNickname;

  @Builder
  public NewStartMemberProfile(Long userId, String displayNickname) {
    this.userId = userId;
    this.displayNickname = displayNickname;
  }

  public void updateDisplayNickname(String displayNickname) {
    this.displayNickname = displayNickname;
  }
}
