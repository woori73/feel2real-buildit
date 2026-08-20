package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "academy_blog_post_likes",
    uniqueConstraints = @UniqueConstraint(name = "uk_academy_blog_post_likes_post_user", columnNames = {"post_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyBlogPostLike extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "like_id")
  private Long likeId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false)
  private AcademyBlogPost post;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  public AcademyBlogPostLike(AcademyBlogPost post, User user) {
    this.post = post;
    this.user = user;
  }
}
