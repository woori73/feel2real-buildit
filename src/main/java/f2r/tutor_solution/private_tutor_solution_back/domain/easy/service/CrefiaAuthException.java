package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

/** CREFIA authentication/authorization failure. */
public class CrefiaAuthException extends EasyCollectionException {

  public CrefiaAuthException(String safeSummary) {
    super(EasyCollectionErrorCode.AUTH_REQUIRED, safeSummary);
  }

  public CrefiaAuthException(String safeSummary, Throwable cause) {
    super(EasyCollectionErrorCode.AUTH_REQUIRED, safeSummary, cause);
  }
}
