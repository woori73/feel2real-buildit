package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto;

public record PresenceSettingsRequest(
    Boolean hideOnline,
    Boolean dmBlocked) {
}
