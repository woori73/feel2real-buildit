package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_community_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonCommunityPost extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "post_id")
  private Long postId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lesson_id", nullable = false)
  private LessonProduct lesson;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private LessonCommunityCategory category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_user_id", nullable = false)
  private User author;

  @Enumerated(EnumType.STRING)
  @Column(name = "post_type", nullable = false, length = 20)
  private LessonCommunityPostType postType;

  @Column(name = "title", nullable = false, length = 150)
  private String title;

  @Lob
  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "qna_public", nullable = false)
  private boolean qnaPublic;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  @Column(name = "hidden_at")
  private LocalDateTime hiddenAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "hidden_by_user_id")
  private User hiddenBy;

  @Builder
  public LessonCommunityPost(LessonProduct lesson, LessonCommunityCategory category, User author,
      LessonCommunityPostType postType, String title, String content, boolean qnaPublic) {
    this.lesson = lesson;
    this.category = category;
    this.author = author;
    this.postType = postType == null ? LessonCommunityPostType.GENERAL : postType;
    this.title = title;
    this.content = content;
    this.qnaPublic = this.postType == LessonCommunityPostType.QNA && qnaPublic;
  }

  public void update(String title, String content, LessonCommunityCategory category, boolean qnaPublic) {
    this.title = title;
    this.content = content;
    this.category = category;
    this.qnaPublic = this.postType == LessonCommunityPostType.QNA && qnaPublic;
  }

  public void hide(User by) {
    this.visible = false;
    this.hiddenBy = by;
    this.hiddenAt = LocalDateTime.now();
  }
}
