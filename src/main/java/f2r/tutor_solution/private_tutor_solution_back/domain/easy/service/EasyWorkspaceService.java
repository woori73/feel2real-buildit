package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceMonthlyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceTransactionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAnnualClosing;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClosingStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAnnualClosingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyReviewItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DB 변경 없이 기존 장부 자산을 조합하는 Easy Workspace 2A 조회 계층. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyWorkspaceService {

  private final EasyBusinessScopeGuard scopeGuard;
  private final EasyLedgerService ledgerService;
  private final EasyTransactionRepository transactionRepository;
  private final EasyJournalEntryRepository journalEntryRepository;
  private final EasyAnnualClosingRepository closingRepository;
  private final EasyCollectionRawRecordRepository rawRecordRepository;
  private final EasyImportBatchRepository importBatchRepository;
  private final EasyReviewItemRepository reviewItemRepository;

  public EasyWorkspaceMonthlyResponse monthly(Long businessId, YearMonth month) {
    EasyBusiness business = scopeGuard.requireActive(businessId);
    YearMonth target = month == null ? YearMonth.now() : month;
    LocalDate from = target.atDay(1);
    LocalDate to = target.atEndOfMonth();
    EasyDashboardResponse amounts = ledgerService.summary(businessId, target);
    List<EasyTransaction> transactions = transactionRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            businessId, from, to);

    long journalPosted = 0L;
    List<EasyWorkspaceTransactionResponse> recent = new ArrayList<>();
    for (EasyTransaction transaction : transactions) {
      EasyWorkspaceTransactionResponse state = toWorkspace(transaction);
      if ("JOURNAL_POSTED".equals(state.journalStatus())) {
        journalPosted++;
      }
      if (recent.size() < 10) {
        recent.add(state);
      }
    }

    ClosingState closing = closingState(businessId, target.getYear());
    return new EasyWorkspaceMonthlyResponse(
        target.toString(),
        businessId,
        business.getName(),
        amounts.income(),
        amounts.expense(),
        amounts.profit(),
        amounts.receivable(),
        amounts.studentCount(),
        rawRecordRepository
            .findByBusiness_BusinessIdAndSourceOccurredOnBetweenOrderByRawIdDesc(
                businessId, from, to)
            .size(),
        importBatchRepository.countByBusiness_BusinessIdAndStatus(
            businessId, EasyImportStatus.STAGED),
        transactions.size(),
        journalPosted,
        reviewItemRepository.countByBusiness_BusinessIdAndStatus(
            businessId, EasyReviewStatus.OPEN),
        reviewItemRepository.countByBusiness_BusinessIdAndKindAndStatus(
            businessId, EasyReviewKind.EVIDENCE, EasyReviewStatus.OPEN),
        closing.status(),
        closing.closed(),
        EasyWorkspaceMonthlyResponse.Sources.contract(),
        recent,
        amounts.overdueStudents());
  }

  public List<EasyWorkspaceTransactionResponse> transactions(
      Long businessId, LocalDate from, LocalDate to, EasyTxnDirection direction,
      String category, Long accountId) {
    scopeGuard.requireActive(businessId);
    LocalDate start = from == null ? LocalDate.of(2000, 1, 1) : from;
    LocalDate end = to == null ? LocalDate.of(2100, 12, 31) : to;
    if (start.isAfter(end)) {
      throw new IllegalArgumentException("조회 시작일은 종료일보다 늦을 수 없습니다.");
    }
    return transactionRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            businessId, start, end)
        .stream()
        .filter(txn -> direction == null || txn.getDirection() == direction)
        .filter(txn -> category == null || category.isBlank()
            || category.equals(txn.getCategory()))
        .filter(txn -> accountId == null || (txn.getAccount() != null
            && accountId.equals(txn.getAccount().getAccountId())))
        .map(this::toWorkspace)
        .toList();
  }

  public EasyWorkspaceTransactionResponse transaction(Long businessId, Long txnId) {
    scopeGuard.requireActive(businessId);
    EasyTransaction transaction = transactionRepository
        .findByTxnIdAndBusiness_BusinessId(txnId, businessId)
        .orElseThrow(() -> new IllegalArgumentException(
            "해당 사업체의 거래를 찾을 수 없습니다: txnId=" + txnId));
    return toWorkspace(transaction);
  }

  @Transactional
  public EasyWorkspaceTransactionResponse createTransaction(EasyTxnRequest request) {
    if (request == null || request.occurredOn() == null) {
      throw new IllegalArgumentException("거래일을 입력해 주세요.");
    }
    scopeGuard.requireActive(request.businessId());
    requireOpenPeriod(request.businessId(), request.occurredOn().getYear());
    EasyTxnResponse created = ledgerService.createTxn(request);
    EasyTransaction transaction = transactionRepository
        .findByTxnIdAndBusiness_BusinessId(created.txnId(), request.businessId())
        .orElseThrow(() -> new IllegalStateException("저장된 거래를 다시 조회할 수 없습니다."));
    return toWorkspace(transaction);
  }

  @Transactional
  public EasyWorkspaceTransactionResponse updateTransaction(
      Long sourceBusinessId, Long txnId, EasyTxnRequest request) {
    scopeGuard.requireActive(sourceBusinessId);
    EasyTransaction current = transactionRepository
        .findLockedByTxnIdAndBusiness_BusinessId(txnId, sourceBusinessId)
        .orElseThrow(() -> new IllegalArgumentException(
            "해당 사업체의 거래를 찾을 수 없습니다: txnId=" + txnId));
    if (request == null || request.occurredOn() == null) {
      throw new IllegalArgumentException("거래일을 입력해 주세요.");
    }
    scopeGuard.requireActive(request.businessId());
    requireOpenPeriod(sourceBusinessId, current.getOccurredOn().getYear());
    requireOpenPeriod(request.businessId(), request.occurredOn().getYear());
    ledgerService.updateTxn(txnId, request);
    return toWorkspace(current);
  }

  private EasyWorkspaceTransactionResponse toWorkspace(EasyTransaction transaction) {
    List<EasyJournalEntry> journals = journalEntryRepository
        .findByTxn_TxnIdOrderByEntryIdDesc(transaction.getTxnId());
    String journalStatus = journals.stream().anyMatch(
        entry -> entry.getStatus() == EasyJournalStatus.POSTED)
        ? "JOURNAL_POSTED"
        : journals.isEmpty() ? "NOT_POSTED" : "JOURNAL_VOIDED";
    ClosingState closing = closingState(
        transaction.getBusiness().getBusinessId(), transaction.getOccurredOn().getYear());
    String sourceKind = transaction.getSourceKind();
    boolean externallyCollected = sourceKind != null && !sourceKind.isBlank()
        && !"MANUAL".equalsIgnoreCase(sourceKind);
    return new EasyWorkspaceTransactionResponse(
        EasyTxnResponse.from(transaction),
        externallyCollected ? "COLLECTED" : "NOT_COLLECTED",
        externallyCollected ? "IMPORTED_TO_LEDGER" : "NOT_APPLICABLE",
        "LEDGER_SAVED",
        journalStatus,
        closing.status(),
        closing.closed());
  }

  private ClosingState closingState(Long businessId, int fiscalYear) {
    EasyAnnualClosing closing = closingRepository
        .findByBusiness_BusinessIdAndFiscalYear(businessId, fiscalYear)
        .orElse(null);
    if (closing == null) {
      return new ClosingState(EasyClosingStatus.OPEN.name(), false);
    }
    return new ClosingState(
        closing.getStatus().name(), closing.getStatus() == EasyClosingStatus.CLOSED);
  }

  private void requireOpenPeriod(Long businessId, int fiscalYear) {
    closingRepository.findByBusiness_BusinessIdAndFiscalYear(businessId, fiscalYear)
        .filter(closing -> closing.getStatus() == EasyClosingStatus.CLOSED)
        .ifPresent(closing -> {
          throw new org.springframework.web.server.ResponseStatusException(
              org.springframework.http.HttpStatus.CONFLICT,
              "마감된 회계기간입니다: businessId=" + businessId
                  + ", fiscalYear=" + fiscalYear);
        });
  }

  private record ClosingState(String status, boolean closed) {
  }
}
