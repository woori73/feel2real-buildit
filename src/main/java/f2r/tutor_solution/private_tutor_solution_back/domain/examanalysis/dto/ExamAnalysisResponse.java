package f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.entity.ExamAnalysis;
import java.time.LocalDateTime;

public record ExamAnalysisResponse(
    Long examAnalysisId,
    String title,
    String schoolName,
    Integer grade,
    String semester,
    String examType,
    SubjectSummary subject,
    String originalFileName,
    Long fileSize,
    boolean isVisible,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String detailUrl,
    String contentUrl
) {
  public static ExamAnalysisResponse of(ExamAnalysis analysis) {
    Long id = analysis.getExamAnalysisId();
    return new ExamAnalysisResponse(
        id,
        analysis.getTitle(),
        analysis.getSchoolName(),
        analysis.getGrade(),
        analysis.getSemester(),
        analysis.getExamType(),
        new SubjectSummary(analysis.getSubject().getSubjectId(), analysis.getSubject().getNameKo(), analysis.getSubject().getNameEn()),
        analysis.getOriginalFileName(),
        analysis.getFileSize(),
        analysis.isVisible(),
        analysis.getCreatedAt(),
        analysis.getUpdatedAt(),
        "/analyzes/" + id,
        "/api/analyzes/" + id + "/content"
    );
  }

  public record SubjectSummary(Integer subjectId, String nameKo, String nameEn) {}
}
