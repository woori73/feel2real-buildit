package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

/** token is the raw pairing token — shown exactly once, EASY never stores or logs it again. */
public record EasyCollectorRegisterResponse(Long collectorId, String name, String token) {
}
