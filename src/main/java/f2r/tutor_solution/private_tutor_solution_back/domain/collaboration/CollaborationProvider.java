package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

public interface CollaborationProvider {
  record Ready(boolean enabled, String reason) {}

  record Reply(String text, String decision) {}

  Ready readiness(String agent);

  long reserveMicrousd(String agent, String prompt);

  Reply complete(String agent, String prompt) throws Exception;
}
