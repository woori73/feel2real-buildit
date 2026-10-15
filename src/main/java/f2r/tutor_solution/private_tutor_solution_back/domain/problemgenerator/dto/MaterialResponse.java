package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.MaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorMaterial;
import java.time.LocalDateTime;

public record MaterialResponse(Long materialId, String title, String originalFileName, String fileType,
    long fileSize, MaterialVisibility visibility, Long uploadedBy, int pageCount, LocalDateTime createdAt) {
  public static MaterialResponse of(ProblemGeneratorMaterial material, int pageCount) {
    return new MaterialResponse(material.getMaterialId(), material.getTitle(), material.getOriginalFileName(),
        material.getFileType(), material.getFileSize(), material.getVisibility(), material.getUploadedBy().getUserId(),
        pageCount, material.getCreatedAt());
  }
}
