package f2r.tutor_solution.private_tutor_solution_back.domain.source.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Source extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "source_id")
  private Long sourceId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "uploaded_by", nullable = false)
  private User uploadedBy;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "file_type", nullable = false, length = 10)
  private String fileType;

  @Column(name = "file_size")
  private Long fileSize;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 20)
  private SourceType sourceType;

  @Enumerated(EnumType.STRING)
  @Column(name = "content_kind", nullable = false, length = 20)
  private ContentKind contentKind = ContentKind.UNCLASSIFIED;

  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", nullable = false, length = 20)
  private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

  /**
   * Phase 3에서 LLM이 추출한 시험지 메타 + 문제 + 답안 + dedup 결과를 담는 임시 JSON.
   * Phase 4 검증 화면에서 학생이 수정 후 확정하면 questions/student_answers/student_scores/exams에 INSERT된다.
   * GENERAL 자료에는 사용하지 않는다(null).
   */
  @Column(name = "analysis_result", columnDefinition = "JSON")
  private String analysisResult;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exam_id")
  private Exam exam;

  @Column(name = "uploaded_at", nullable = false)
  private LocalDateTime uploadedAt;

  @Builder
  public Source(User uploadedBy, String originalFileName, String storageKey,
      String fileType, Long fileSize, SourceType sourceType, ContentKind contentKind) {
    this.uploadedBy = uploadedBy;
    this.originalFileName = originalFileName;
    this.storageKey = storageKey;
    this.fileType = fileType != null ? fileType : "UNKNOWN";
    this.fileSize = fileSize;
    this.sourceType = sourceType;
    if (contentKind != null) this.contentKind = contentKind;
    this.uploadedAt = LocalDateTime.now();
  }

  public void linkToExam(Exam exam) {
    this.exam = exam;
  }

  public void updateContentKind(ContentKind contentKind) {
    this.contentKind = contentKind;
  }

  public void updateProcessingStatus(ProcessingStatus processingStatus) {
    this.processingStatus = processingStatus;
  }

  public void updateAnalysisResult(String analysisResult) {
    this.analysisResult = analysisResult;
  }
}
