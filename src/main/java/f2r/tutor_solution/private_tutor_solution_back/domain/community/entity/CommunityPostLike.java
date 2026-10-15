package f2r.tutor_solution.private_tutor_solution_back.domain.community.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공개 커뮤니티 글 좋아요. 사용자-글당 1회(UNIQUE). 토글로 생성/삭제. */
@Entity
@Table(name = "community_post_likes",
    uniqueConstraints = @UniqueConstraint(name = "uk_community_post_likes_post_user", columnNames = {"post_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPostLike extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "like_id")
  private Long likeId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false)
  private CommunityPost post;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  public CommunityPostLike(CommunityPost post, User user) {
    this.post = post;
    this.user = user;
  }
}
