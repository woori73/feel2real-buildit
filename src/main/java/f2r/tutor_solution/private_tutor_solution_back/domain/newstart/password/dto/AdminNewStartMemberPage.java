package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto;

import java.util.List;

/** ADMIN 회원 검색 응답(페이지네이션). */
public record AdminNewStartMemberPage(
    String query,
    int page,
    int size,
    long totalElements,
    int totalPages,
    List<AdminNewStartMemberSummary> members) {
}
