package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

public class ProcessStalledException extends VideoTranscodeException {
  public ProcessStalledException(String message) {
    super(message);
  }
}
