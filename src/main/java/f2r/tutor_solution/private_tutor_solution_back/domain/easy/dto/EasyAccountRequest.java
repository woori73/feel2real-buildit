package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

public record EasyAccountRequest(
    String code,
    String name,
    String direction,
    Integer sortOrder,
    Boolean active
) {
}
