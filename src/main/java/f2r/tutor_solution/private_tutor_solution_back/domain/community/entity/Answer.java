package f2r.tutor_solution.private_tutor_solution_back.domain.community.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 질문에 대한 답변(Answer). 질문당 N개. Tiptap 본문. soft-hide.
 * 작성=로그인, 수정=작성자, 삭제=작성자/Admin.
 */
@Entity
@Table(name = "answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "answer_id")
  private Long answerId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private CommunityPost question;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_user_id", nullable = false)
  private User author;

  @Lob
  @Column(name = "content_json", nullable = false, columnDefinition = "LONGTEXT")
  private String contentJson;

  @Lob
  @Column(name = "content_html", nullable = false, columnDefinition = "LONGTEXT")
  private String contentHtml;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  @Column(name = "hidden_at")
  private LocalDateTime hiddenAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "hidden_by_user_id")
  private User hiddenBy;

  @Builder
  public Answer(CommunityPost question, User author, String contentJson, String contentHtml) {
    this.question = question;
    this.author = author;
    this.contentJson = contentJson;
    this.contentHtml = contentHtml;
  }

  public void update(String contentJson, String contentHtml) {
    this.contentJson = contentJson;
    this.contentHtml = contentHtml;
  }

  public void hide(User by) {
    this.visible = false;
    this.hiddenBy = by;
    this.hiddenAt = LocalDateTime.now();
  }
}
