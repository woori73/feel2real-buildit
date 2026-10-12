package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * BuildIt 시각 편집기 게시본.
 *
 * <p>실제 공개 홈페이지에 반영되는 확정본.
 * 초안 테이블과 분리되어 있으며, 명시적 게시 요청으로만 갱신된다.
 *
 * <p>현재 게시 기능은 비활성화되어 있다.
 */
@Entity
@Table(
    name = "academy_visual_editor_published",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_visual_published_academy", columnNames = "academy_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisualEditorPublished {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "published_id")
  private Long publishedId;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  @Column(name = "html", columnDefinition = "MEDIUMTEXT")
  private String html;

  @Column(name = "css", columnDefinition = "TEXT")
  private String css;

  @Column(name = "published_by", nullable = false, length = 100)
  private String publishedBy;

  @CreationTimestamp
  @Column(name = "published_at", nullable = false, updatable = false)
  private LocalDateTime publishedAt;

  public VisualEditorPublished(
      Long academyId, String html, String css, String publishedBy) {
    this.academyId = academyId;
    this.html = html;
    this.css = css;
    this.publishedBy = publishedBy;
  }
}
