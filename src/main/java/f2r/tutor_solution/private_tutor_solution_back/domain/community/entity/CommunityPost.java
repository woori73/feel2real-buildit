package f2r.tutor_solution.private_tutor_solution_back.domain.community.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공개 커뮤니티 질문(Question). 3계층 Q&A의 최상위.
 * 읽기는 누구나, 작성/수정은 작성자, 삭제는 작성자/Admin soft hide.
 * (테이블/엔티티명은 #109 호환으로 community_posts/CommunityPost 유지 — 의미상 Question.)
 */
@Entity
@Table(name = "community_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "post_id")
  private Long postId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_user_id", nullable = false)
  private User author;

  // 게시판 종류: 자유게시판(FREE) / Q&A(QNA) / 과목(SUBJECT). SUBJECT일 때만 subject 세팅.
  @Enumerated(EnumType.STRING)
  @Column(name = "board_type", nullable = false, length = 20)
  private BoardType boardType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subject_id")
  private Subject subject;

  // title은 keyword 검색 대상(lower(title)) — 비-LOB VARCHAR.
  @Column(name = "title", nullable = false, length = 150)
  private String title;

  // Tiptap 본문. LONGTEXT(LOB). keyword 검색은 title만 하므로 lower(content_*) 금지.
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
  public CommunityPost(User author, BoardType boardType, Subject subject, String title, String contentJson, String contentHtml) {
    this.author = author;
    this.boardType = boardType;
    this.subject = subject;
    this.title = title;
    this.contentJson = contentJson;
    this.contentHtml = contentHtml;
  }

  public void update(BoardType boardType, Subject subject, String title, String contentJson, String contentHtml) {
    this.boardType = boardType;
    this.subject = subject;
    this.title = title;
    this.contentJson = contentJson;
    this.contentHtml = contentHtml;
  }

  public void hide(User by) {
    this.visible = false;
    this.hiddenBy = by;
    this.hiddenAt = LocalDateTime.now();
  }
}
