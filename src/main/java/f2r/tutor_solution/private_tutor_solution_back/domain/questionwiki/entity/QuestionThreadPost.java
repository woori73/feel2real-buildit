package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 문항 THREAD 의 글(또는 1단계 답글). 교육자·학생 누구나 로그인하면 작성할 수 있다.
 *
 * <p>{@code parentPost} 가 있으면 답글이다. 답글의 답글은 서비스에서 막는다(1 depth).
 * 삭제는 물리삭제 없이 soft-hide({@code visible=false} + {@code hiddenBy}).
 */
@Entity
@Table(name = "question_thread_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionThreadPost extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "post_id")
  private Long postId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_wiki_id", nullable = false)
  private QuestionWiki questionWiki;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_post_id")
  private QuestionThreadPost parentPost;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_user_id", nullable = false)
  private User author;

  /** 작성 시점 role 스냅샷. 공개용 participant type(교육자/학생/운영자) 도출에 쓴다. */
  @Enumerated(EnumType.STRING)
  @Column(name = "author_role", nullable = false, length = 10)
  private Role authorRole;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 30)
  private QuestionThreadCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_kind", nullable = false, length = 20)
  private QuestionThreadDataKind dataKind;

  @Column(name = "title", length = 200)
  private String title;

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
  public QuestionThreadPost(QuestionWiki questionWiki, QuestionThreadPost parentPost, User author,
      QuestionThreadCategory category, QuestionThreadDataKind dataKind, String title, String content) {
    this.questionWiki = questionWiki;
    this.parentPost = parentPost;
    this.author = author;
    this.authorRole = author.getRole();
    this.category = category == null ? QuestionThreadCategory.QUESTION : category;
    this.dataKind = dataKind == null ? QuestionThreadDataKind.DISCUSSION : dataKind;
    this.title = title;
    this.content = content;
  }

  public boolean isReply() {
    return parentPost != null;
  }

  public void update(String title, String content, QuestionThreadCategory category,
      QuestionThreadDataKind dataKind) {
    this.title = title;
    this.content = content;
    if (category != null) {
      this.category = category;
    }
    if (dataKind != null) {
      this.dataKind = dataKind;
    }
  }

  public void hide(User by) {
    this.visible = false;
    this.hiddenBy = by;
    this.hiddenAt = LocalDateTime.now();
  }
}
