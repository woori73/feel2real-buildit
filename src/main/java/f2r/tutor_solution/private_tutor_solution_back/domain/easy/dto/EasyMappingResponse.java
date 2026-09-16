package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountMapping;

public record EasyMappingResponse(
    Long mappingId,
    Long accountId,
    String accountName,
    String externalCode,
    String externalName,
    String standardCode,
    String standardName,
    String note
) {
  public static EasyMappingResponse from(EasyAccountMapping mapping) {
    return new EasyMappingResponse(
        mapping.getMappingId(),
        mapping.getAccount().getAccountId(),
        mapping.getAccount().getName(),
        mapping.getExternalCode(),
        mapping.getExternalName(),
        mapping.getStandardCode(),
        mapping.getStandardName(),
        mapping.getNote());
  }
}
