package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BuildIt 홈페이지 버전 스냅샷 (P1).
 *
 * <p>AI 채팅 수정 적용 전후로 자동 스냅샷을 저장하고, 복원에 사용한다.
 * 최근 20개 버전을 유지한다.
 */
@Entity
@Table(
    name = "academy_page_versions",
    indexes = {
      @Index(name = "idx_academy_version", columnList = "academyId, versionNo"),
      @Index(name = "idx_academy_created", columnList = "academyId, createdAt")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyPageVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long academyId;

  @Column(nullable = false)
  private Integer versionNo;

  @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
  private String builderDraftJson;

  @Column(columnDefinition = "MEDIUMTEXT")
  private String pageSettingsJson;

  @Column(nullable = false, length = 100)
  private String createdBy;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(length = 500)
  private String note;

  public AcademyPageVersion(
      Long academyId,
      Integer versionNo,
      String builderDraftJson,
      String pageSettingsJson,
      String createdBy,
      String note) {
    this.academyId = academyId;
    this.versionNo = versionNo;
    this.builderDraftJson = builderDraftJson;
    this.pageSettingsJson = pageSettingsJson;
    this.createdBy = createdBy;
    this.note = note;
    this.createdAt = LocalDateTime.now();
  }
}
