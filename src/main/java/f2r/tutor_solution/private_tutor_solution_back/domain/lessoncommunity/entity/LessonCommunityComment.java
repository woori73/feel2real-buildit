package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_community_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonCommunityComment extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "comment_id")
  private Long commentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "post_id", nullable = false)
  private LessonCommunityPost post;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_user_id", nullable = false)
  private User author;

  @Lob
  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  @Column(name = "hidden_at")
  private LocalDateTime hiddenAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "hidden_by_user_id")
  private User hiddenBy;

  @Builder
  public LessonCommunityComment(LessonCommunityPost post, User author, String content) {
    this.post = post;
    this.author = author;
    this.content = content;
  }

  public void update(String content) {
    this.content = content;
  }

  public void hide(User by) {
    this.visible = false;
    this.hiddenBy = by;
    this.hiddenAt = LocalDateTime.now();
  }
}
