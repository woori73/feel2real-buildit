package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

public class AcademyVideoRawValidationException extends RuntimeException {
  private final String errorCode;
  private final boolean transientFailure;

  public AcademyVideoRawValidationException(String errorCode, boolean transientFailure) {
    super(errorCode, null, false, false);
    this.errorCode = errorCode;
    this.transientFailure = transientFailure;
  }

  public String errorCode() {
    return errorCode;
  }

  public boolean transientFailure() {
    return transientFailure;
  }
}
