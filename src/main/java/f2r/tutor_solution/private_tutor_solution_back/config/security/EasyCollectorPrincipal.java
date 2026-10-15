package f2r.tutor_solution.private_tutor_solution_back.config.security;

/** Authenticated identity of a Visible Local Browser Collector agent request. */
public record EasyCollectorPrincipal(Long collectorId, Long businessId) {
}
