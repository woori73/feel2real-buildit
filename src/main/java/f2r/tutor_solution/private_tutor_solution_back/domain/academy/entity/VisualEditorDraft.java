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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * BuildIt 시각 편집기 초안.
 *
 * <p>기존 P0/P1 builderDraft와 완전히 분리된 전용 테이블.
 * P1 스냅샷/D3 버전 번호와 연결하지 않는다.
 *
 * <p>동시 수정 제어: revision 컬럼으로 낙관적 잠금을 수행한다.
 * 저장 시 요청의 revision과 DB의 revision이 다르면 409 Conflict를 반환한다.
 */
@Entity
@Table(
    name = "academy_visual_editor_draft",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_visual_draft_academy", columnNames = "academy_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisualEditorDraft {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "draft_id")
  private Long draftId;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  /**
   * 낙관적 잠금용 리비전.
   * 저장 시 1씩 증가한다.
   */
  @Column(name = "revision", nullable = false)
  private Long revision = 1L;

  @Column(name = "html", columnDefinition = "MEDIUMTEXT")
  private String html;

  @Column(name = "css", columnDefinition = "TEXT")
  private String css;

  @Column(name = "project_data", columnDefinition = "MEDIUMTEXT")
  private String projectData;

  @Column(name = "created_by", nullable = false, length = 100)
  private String createdBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public VisualEditorDraft(
      Long academyId, String html, String css, String projectData, String createdBy) {
    this.academyId = academyId;
    this.html = html;
    this.css = css;
    this.projectData = projectData;
    this.createdBy = createdBy;
  }

  /**
   * 초안 내용을 갱신한다. revision을 1 증가시킨다.
   *
   * @param expectedRevision 호출자가 읽은 시점의 revision
   * @throws IllegalStateException revision 불일치 시 (동시 수정 충돌)
   */
  public void update(String html, String css, String projectData, Long expectedRevision) {
    if (!this.revision.equals(expectedRevision)) {
      throw new IllegalStateException(
          "다른 세션에서 수정되었습니다. 새로고침 후 다시 시도하세요. "
              + "(현재 revision=" + this.revision + ", 요청 revision=" + expectedRevision + ")");
    }
    this.html = html;
    this.css = css;
    this.projectData = projectData;
    this.revision += 1;
  }
}
