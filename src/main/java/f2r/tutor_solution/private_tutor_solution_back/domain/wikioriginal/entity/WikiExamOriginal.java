package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공개 BuildIt Wiki(/wiki)의 모의고사 원본 PDF(문제지/정답지).
 *
 * <p>exam_analyses(HTML 분석 리포트)와 목적이 달라 별도 테이블/도메인이다.
 * 파일은 {@code FileStorage.saveRaw}로 원본 그대로 저장하고 다운로드로만 제공한다.
 */
@Entity
@Table(name = "wiki_exam_originals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WikiExamOriginal extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "wiki_exam_original_id")
  private Long wikiExamOriginalId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false)
  private Integer grade;

  @Column(name = "exam_label", nullable = false, length = 100)
  private String examLabel;

  @Enumerated(EnumType.STRING)
  @Column(name = "doc_type", nullable = false, length = 20)
  private WikiExamOriginalDocType docType;

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
  public WikiExamOriginal(String title, Integer grade, String examLabel, WikiExamOriginalDocType docType,
      String originalFileName, String storageKey, Long fileSize, boolean visible) {
    this.title = title;
    this.grade = grade;
    this.examLabel = examLabel;
    this.docType = docType;
    this.originalFileName = originalFileName;
    this.storageKey = storageKey;
    this.fileSize = fileSize;
    this.visible = visible;
    this.deleted = false;
  }

  public void updateMetadata(String title, Integer grade, String examLabel,
      WikiExamOriginalDocType docType, boolean visible) {
    this.title = title;
    this.grade = grade;
    this.examLabel = examLabel;
    this.docType = docType;
    this.visible = visible;
  }

  public String replaceFile(String originalFileName, String storageKey, Long fileSize) {
    String oldStorageKey = this.storageKey;
    this.originalFileName = originalFileName;
    this.storageKey = storageKey;
    this.fileSize = fileSize;
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
