package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFinancialCollectorDtos.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EasyFinancialReportBuilder {
  public void validateScope(Scope scope) {
    if (scope == null || scope.dataset() == null || scope.accountRef() == null
        || !scope.accountRef().matches("[a-zA-Z][a-zA-Z0-9_-]{0,63}")) {
      throw new IllegalArgumentException("DATASET_AND_OPAQUE_ACCOUNT_REF_REQUIRED");
    }
    if (scope.from() == null || scope.to() == null) throw new IllegalArgumentException("PERIOD_REQUIRED");
    LocalDate from = LocalDate.parse(scope.from());
    LocalDate to = LocalDate.parse(scope.to());
    if (from.isAfter(to) || to.isAfter(from.plusYears(1))) {
      throw new IllegalArgumentException("PERIOD_INVALID");
    }
    if (scope.expectedCount() != null && (scope.expectedCount() < 0 || scope.expectedCount() > 2000)
        || scope.expectedTotal() != null && scope.expectedTotal() < 0) {
      throw new IllegalArgumentException("EXPECTED_CONTROL_INVALID");
    }
  }

  public Report build(Scope scope, List<CollectionParsedRow> rows) {
    validateScope(scope);
    List<String> issues = new ArrayList<>();
    List<Draft> drafts = new ArrayList<>();
    Map<String, String> external = new HashMap<>();
    Set<String> tuples = new HashSet<>();
    int failed = 0;
    int suspects = 0;
    long total = 0;
    LocalDate from = LocalDate.parse(scope.from());
    LocalDate to = LocalDate.parse(scope.to());
    for (CollectionParsedRow row : rows) {
      if (!row.isValid()) {
        failed++;
        issues.add("ROW_" + row.rowNo() + ":" + row.error());
        continue;
      }
      if (row.occurredOn().isBefore(from) || row.occurredOn().isAfter(to)) {
        failed++;
        issues.add("ROW_" + row.rowNo() + ":OUTSIDE_REQUESTED_PERIOD");
        continue;
      }
      String tuple = EasyCollectionDedupeKeys.tupleHash(scopeKey(scope), 0L, row.occurredOn(),
          row.direction(), row.amount(), row.counterparty(), row.description(), row.payMethod());
      boolean suspect = !tuples.add(tuple);
      if (row.externalId() != null) {
        String id = externalKey(scope, row);
        String previous = external.putIfAbsent(id, tuple);
        if (previous != null) {
          suspect = true;
          if (!previous.equals(tuple)) issues.add("EXTERNAL_ID_CONFLICT");
        }
      }
      if (suspect) suspects++;
      total = Math.addExact(total, row.amount());
      drafts.add(new Draft(row.rowNo(), row.occurredOn().toString(), row.direction(), row.amount(),
          row.counterparty(), row.description(), row.externalAccount(),
          suspect ? "DUPLICATE_SUSPECT" : "REVIEW_REQUIRED"));
    }
    if (scope.expectedCount() == null || scope.expectedTotal() == null) {
      issues.add("COMPLETENESS_UNVERIFIED");
    }
    if (scope.expectedCount() != null && scope.expectedCount() != rows.size()) issues.add("COUNT_MISMATCH");
    if (scope.expectedTotal() != null && scope.expectedTotal() != total) issues.add("TOTAL_MISMATCH");
    if (drafts.isEmpty()) issues.add("NO_VALID_TRANSACTIONS");
    // Absence of a transaction on a day is not evidence of a missing transaction.
    String status = drafts.isEmpty() ? "FAILED" : failed > 0 || !issues.isEmpty() ? "PARTIAL" : "SUCCESS";
    return new Report(status, rows.size(), drafts.size(), suspects, failed, drafts.size(), total,
        issues.stream().distinct().toList(), List.copyOf(drafts));
  }

  public String scopeKey(Scope scope) {
    return "FIN_" + EasyCollectionDedupeKeys.rawHash(scope.dataset().name() + ":" + scope.accountRef()).substring(0, 24);
  }

  public String externalKey(Scope scope, CollectionParsedRow row) {
    // Approval numbers can recur on different days. Do not collapse those transactions.
    return EasyCollectionDedupeKeys.rawHash(scopeKey(scope) + ":" + row.occurredOn() + ":"
        + row.direction() + ":" + EasyCollectionDedupeKeys.normalizeText(row.externalId()));
  }
}
