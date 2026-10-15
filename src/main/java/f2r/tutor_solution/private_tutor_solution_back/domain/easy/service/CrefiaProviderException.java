package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

/** Temporary CREFIA transport/provider failure; it must not force reauthentication. */
public class CrefiaProviderException extends EasyCollectionException {

  public CrefiaProviderException(String safeSummary) {
    super(EasyCollectionErrorCode.PROVIDER_TEMPORARY, safeSummary);
  }

  public CrefiaProviderException(String safeSummary, Throwable cause) {
    super(EasyCollectionErrorCode.PROVIDER_TEMPORARY, safeSummary, cause);
  }
}
