package f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공개 BuildIt Wiki(/wiki)의 분석 자료 문서 파일(PDF/HWP/HWPX/DOCX 등).
 *
 * <p>exam_analyses(/analyzes, HTML 분석 콘텐츠)와 목적이 달라 별도 테이블/도메인이다.
 * 파일은 {@code FileStorage.saveRaw}로 원본 그대로 저장하고 다운로드로만 제공한다.
 */
@Entity
@Table(name = "wiki_analysis_materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiAnalysisMaterial extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "wiki_analysis_material_id")
  private Long wikiAnalysisMaterialId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 1000)
  private String description;

  @Column(name = "school_name", length = 100)
  private String schoolName;

  @Column(nullable = false)
  private Integer grade;

  @Column(name = "exam_year", nullable = false)
  private Integer year;

  @Column(nullable = false, length = 20)
  private String semester;

  @Column(name = "exam_type", nullable = false, length = 40)
  private String examType;

  @Column(length = 60)
  private String subject;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "is_visible", nullable = false)
  private boolean visible;

  @Column(name = "is_deleted", nullable = false)
  private boolean deleted;

  @Builder
  public WikiAnalysisMaterial(String title, String description, String schoolName, Integer grade, Integer year,
      String semester, String examType, String subject, String contentType, String originalFileName,
      String storageKey, Long fileSize, boolean visible) {
    this.title = title;
    this.description = description;
    this.schoolName = schoolName;
    this.grade = grade;
    this.year = year;
    this.semester = semester;
    this.examType = examType;
    this.subject = subject;
    this.contentType = contentType;
    this.originalFileName = originalFileName;
    this.storageKey = storageKey;
    this.fileSize = fileSize;
    this.visible = visible;
    this.deleted = false;
  }

  public void updateMetadata(String title, String description, String schoolName, Integer grade, Integer year,
      String semester, String examType, String subject, boolean visible) {
    this.title = title;
    this.description = description;
    this.schoolName = schoolName;
    this.grade = grade;
    this.year = year;
    this.semester = semester;
    this.examType = examType;
    this.subject = subject;
    this.visible = visible;
  }

  public String replaceFile(String originalFileName, String storageKey, Long fileSize, String contentType) {
    String oldStorageKey = this.storageKey;
    this.originalFileName = originalFileName;
    this.storageKey = storageKey;
    this.fileSize = fileSize;
    this.contentType = contentType;
    return oldStorageKey;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public void softDelete() {
    this.deleted = true;
    this.visible = false;
  }
}
