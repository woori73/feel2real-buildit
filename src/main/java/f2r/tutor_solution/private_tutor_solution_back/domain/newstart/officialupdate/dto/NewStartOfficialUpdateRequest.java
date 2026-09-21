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
    String originalUrl,
    String officialDocumentId,
    LocalDateTime publishedAt,
    LocalDateTime effectiveFrom,
    LocalDateTime effectiveTo,
    @NotNull Category category) {}
