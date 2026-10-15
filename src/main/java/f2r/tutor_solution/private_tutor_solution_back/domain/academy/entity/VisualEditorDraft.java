package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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
 * <p>동시 수정 제어: JPA {@code @Version} 낙관적 잠금.
 * DB 레벨에서 원자적으로 revision을 검증하므로,
 * 메모리 비교 방식의 TOCTOU 문제를 해결한다.
 * 충돌 시 {@code OptimisticLockException}이 발생하며,
 * 서비스에서 {@code DraftConflictException}으로 변환한다.
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
   * JPA 낙관적 잠금 버전.
   * UPDATE 시 WHERE revision = ? 조건으로 원자적 검증.
   * Hibernate가 자동으로 증가시킨다.
   */
  @Version
  @Column(name = "revision", nullable = false)
  private Long revision;

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
   * 초안 내용을 갱신한다.
   *
   * <p>JPA {@code @Version}이 UPDATE 시 원자적으로 revision을 검증하므로
   * 별도의 메모리 비교가 필요 없다. 충돌 시 {@code OptimisticLockException} 발생.
   */
  public void update(String html, String css, String projectData) {
    this.html = html;
    this.css = css;
    this.projectData = projectData;
  }
}
