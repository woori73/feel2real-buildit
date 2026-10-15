package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

/** Stable, non-sensitive provider failure classification for collection runs. */
public enum EasyCollectionErrorCode {
  AUTH_REQUIRED,
  PROVIDER_TEMPORARY,
  INVALID_DATA,
  CONFIGURATION_ERROR,
  EXECUTION_TIMEOUT
}
