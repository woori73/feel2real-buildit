package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * NewStart 실행 권한 부여 row — {@code newstart_member_grants}.
 *
 * <p>row가 존재하는 것 자체가 부여 상태이고, DELETE가 곧 회수다. 운영자가 deploy 없이
 * row 1개로 권한을 주고 뺏기 위한 목적이라 취소 이력 컬럼을 두지 않는다 — nullable
 * 취소 컬럼은 MySQL UNIQUE에서 NULL 중복을 허용해 "active 판정"이 애매해진다.
 *
 * <p>{@code users.role}에 값을 추가하지 않는 이유: role은 BuildIt/Wiki/Academy가 공유하는
 * 전역 권한이라 NewStart 전용 실행 권한을 섞으면 다른 서비스 권한 분기까지 함께 바뀐다.
 */
@Entity
@Table(name = "newstart_member_grants",
    uniqueConstraints = @UniqueConstraint(name = "uk_newstart_grant_user_capability",
        columnNames = {"user_id", "capability"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewStartMemberGrant {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "grant_id")
  private Long grantId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "capability", nullable = false, length = 40)
  private NewStartCapability capability;

  @Column(name = "granted_by", nullable = false, length = 50)
  private String grantedBy;

  @Builder
  public NewStartMemberGrant(Long userId, NewStartCapability capability, String grantedBy) {
    this.userId = userId;
    this.capability = capability;
    this.grantedBy = grantedBy;
  }
}
