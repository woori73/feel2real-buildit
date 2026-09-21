package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

/**
 * Typed provider failure. The public summary is deliberately fixed and must never contain
 * credentials, tokens, cookies, certificate material, card numbers, or connectionRef values.
 */
public class EasyCollectionException extends RuntimeException {

  private final EasyCollectionErrorCode errorCode;

  public EasyCollectionException(EasyCollectionErrorCode errorCode, String safeSummary) {
    super(safeSummary);
    this.errorCode = errorCode;
  }

  public EasyCollectionException(EasyCollectionErrorCode errorCode, String safeSummary,
      Throwable cause) {
    super(safeSummary, cause);
    this.errorCode = errorCode;
  }

  public EasyCollectionErrorCode errorCode() {
    return errorCode;
  }

  public String safeSummary() {
    return errorCode.name() + ": " + getMessage();
  }
}
