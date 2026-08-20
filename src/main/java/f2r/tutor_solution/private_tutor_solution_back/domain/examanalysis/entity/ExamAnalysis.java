package f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exam_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamAnalysis extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "exam_analysis_id")
  private Long examAnalysisId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(name = "school_name", nullable = false, length = 100)
  private String schoolName;

  @Column(nullable = false)
  private Integer grade;

  @Column(nullable = false, length = 50)
  private String semester;

  @Column(name = "exam_type", nullable = false, length = 50)
  private String examType;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subject_id", nullable = false)
  private Subject subject;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(nullable = false)
  private boolean visible;

  @Column(nullable = false)
  private boolean deleted;

  @Builder
  public ExamAnalysis(String title, String schoolName, Integer grade, String semester, String examType,
      Subject subject, String originalFileName, String storageKey, Long fileSize, boolean visible) {
    this.title = title;
    this.schoolName = schoolName;
    this.grade = grade;
    this.semester = semester;
    this.examType = examType;
    this.subject = subject;
    this.originalFileName = originalFileName;
    this.storageKey = storageKey;
    this.fileSize = fileSize;
    this.visible = visible;
    this.deleted = false;
  }

  public void updateMetadata(String title, String schoolName, Integer grade, String semester, String examType,
      Subject subject, boolean visible) {
    this.title = title;
    this.schoolName = schoolName;
    this.grade = grade;
    this.semester = semester;
    this.examType = examType;
    this.subject = subject;
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
