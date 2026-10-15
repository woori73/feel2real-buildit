package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

public record EasyMappingRequest(
    Long accountId,
    String externalCode,
    String externalName,
    String standardCode,
    String standardName,
    String note
) {
}
