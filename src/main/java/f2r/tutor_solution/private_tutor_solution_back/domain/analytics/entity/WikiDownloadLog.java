package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Wiki 자료 다운로드 이벤트 로그. 프론트 버튼 클릭이 아니라 다운로드 endpoint가 파일을 정상적으로
 * 읽어온 시점에만 기록한다. {@code materialTitle}/{@code displayName}은 다운로드 시점 스냅샷이다
 * ({@code WikiLearningMaterial.uploaderName}과 동일한 패턴 — 이후 이름/제목이 바뀌어도 기록은 유지).
 */
@Entity
@Table(name = "wiki_download_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiDownloadLog extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "wiki_download_log_id")
  private Long wikiDownloadLogId;

  @Column(name = "download_date", nullable = false)
  private LocalDate downloadDate;

  @Column(name = "downloaded_at", nullable = false)
  private LocalDateTime downloadedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "material_category", nullable = false, length = 20)
  private WikiMaterialCategory materialCategory;

  @Column(name = "material_id", nullable = false)
  private Long materialId;

  @Column(name = "material_title", nullable = false, length = 255)
  private String materialTitle;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "role", nullable = false, length = 20)
  private String role;

  @Column(name = "display_name", nullable = false, length = 60)
  private String displayName;

  private WikiDownloadLog(LocalDate downloadDate, LocalDateTime downloadedAt,
      WikiMaterialCategory materialCategory, Long materialId, String materialTitle,
      Long userId, String role, String displayName) {
    this.downloadDate = downloadDate;
    this.downloadedAt = downloadedAt;
    this.materialCategory = materialCategory;
    this.materialId = materialId;
    this.materialTitle = materialTitle;
    this.userId = userId;
    this.role = role;
    this.displayName = displayName;
  }

  public static WikiDownloadLog of(LocalDate downloadDate, LocalDateTime downloadedAt,
      WikiMaterialCategory materialCategory, Long materialId, String materialTitle,
      Long userId, String role, String displayName) {
    return new WikiDownloadLog(
        downloadDate, downloadedAt, materialCategory, materialId, materialTitle, userId, role, displayName);
  }
}
