package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import java.util.List;

/** Local prototype contract. No password, cookie, OTP, certificate, URL or selector input. */
public final class EasyFinancialCollectorDtos {
  private EasyFinancialCollectorDtos() {}

  public enum Dataset {
    BANK_ACCOUNT("BANK", "IMPORT_APPROVAL"),
    CARD_PURCHASE("CARD", "CARD_EVENT_REVIEW"),
    CARD_SALES("CREFIA", "ECONOMIC_SALE_REVIEW"),
    CARD_SETTLEMENT("CREFIA", "SETTLEMENT_MATCH_ONLY"),
    TAX_INVOICE("HOMETAX", "EVIDENCE_MATCH_ONLY"),
    CASH_RECEIPT("HOMETAX", "EVIDENCE_MATCH_ONLY");

    public final String institution;
    public final String postingRoute;
    Dataset(String institution, String postingRoute) {
      this.institution = institution;
      this.postingRoute = postingRoute;
    }
  }

  public record Scope(Dataset dataset, String accountRef, String from, String to,
                      Integer expectedCount, Long expectedTotal) {}

  public record Draft(int rowNo, String occurredOn, String direction, Long amount,
                      String counterparty, String description, String externalAccount,
                      String reviewStatus) {}

  public record Report(String status, int fetched, int valid, int duplicateSuspects, int failed,
                       int reviewRequired, long total, List<String> issues, List<Draft> drafts) {}

  // The original and normalized rows are stored together, encrypted; never returned by API.
  public record Snapshot(String collectionId, Long businessId, Scope scope, String extension,
                         String sha256, String uploadedAt, String uploadedBy,
                         byte[] original, List<CollectionParsedRow> rows, Report report) {}

  public record Status(String collectionId, Long businessId, String institution, String dataset,
                       String postingRoute, String sha256, String uploadedAt, Report report,
                       EasyCollectionRunResponse run, EasyImportBatchResponse batch,
                       long ledgerSaved, Long approvalId, String approvalStatus,
                       String reviewHash, String nextAction) {}
}
