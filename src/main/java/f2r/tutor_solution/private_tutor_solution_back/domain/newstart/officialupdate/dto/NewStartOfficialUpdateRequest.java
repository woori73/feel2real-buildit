package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record NewStartOfficialUpdateRequest(
    @NotNull SourceType sourceType,
    @NotBlank String sourceName,
    @NotBlank String title,
    @NotBlank String summary,
    String content,
    String originalUrl,
    String officialDocumentId,
    LocalDateTime publishedAt,
    LocalDateTime effectiveFrom,
    LocalDateTime effectiveTo,
    @NotNull Category category) {

  /** 본문 원문(content)이 없는 수동 등록 경로(관리자 폼)용 — content 는 null. */
  public NewStartOfficialUpdateRequest(SourceType sourceType, String sourceName, String title,
      String summary, String originalUrl, String officialDocumentId, LocalDateTime publishedAt,
      LocalDateTime effectiveFrom, LocalDateTime effectiveTo, Category category) {
    this(sourceType, sourceName, title, summary, null, originalUrl, officialDocumentId,
        publishedAt, effectiveFrom, effectiveTo, category);
  }
}
