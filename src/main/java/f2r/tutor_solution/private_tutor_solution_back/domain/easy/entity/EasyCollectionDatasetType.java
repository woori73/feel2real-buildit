package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * Provider-neutral dataset/event classification. These names do not assert the accounting
 * meaning of any CREFIA dataset; that requires the official provider contract.
 */
public enum EasyCollectionDatasetType {
  APPROVAL,
  PURCHASE,
  DEPOSIT,
  CANCELLATION,
  CORRECTION,
  UNKNOWN
}
