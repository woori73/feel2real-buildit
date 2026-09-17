package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountMapping;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalLine;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAnnualClosingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyClassificationRuleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCorrectionLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyFilingReferenceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyReviewItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyWithholdingRecordRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * EasyShop 파이프라인 테스트 하네스 — Map 기반 mock 리포지토리 + 실제 서비스.
 * DB 없이 CSV → staging → 확정 → 분개 → 리포트 전체를 실행한다.
 * 실데이터 원본은 읽을 수 있으나 commit하지 않는다 (PII 감사 결과 별도 기록).
 */
class EasyShopPipelineHarness {

  final EasyBusinessRepository businessRepository = lenient(EasyBusinessRepository.class);
  final EasyAccountRepository accountRepository = lenient(EasyAccountRepository.class);
  final EasyAccountMappingRepository mappingRepository =
      lenient(EasyAccountMappingRepository.class);
  final EasyClassificationRuleRepository ruleRepository =
      lenient(EasyClassificationRuleRepository.class);
  final EasyImportBatchRepository batchRepository = lenient(EasyImportBatchRepository.class);
  final EasyImportRowRepository rowRepository = lenient(EasyImportRowRepository.class);
  final EasyTransactionRepository txnRepository = lenient(EasyTransactionRepository.class);
  final EasyJournalEntryRepository entryRepository = lenient(EasyJournalEntryRepository.class);
  final EasyJournalLineRepository lineRepository = lenient(EasyJournalLineRepository.class);
  final EasyReviewItemRepository reviewRepository = lenient(EasyReviewItemRepository.class);
  final EasyWithholdingRecordRepository withholdingRepository =
      lenient(EasyWithholdingRecordRepository.class);
  final EasyAnnualClosingRepository closingRepository =
      lenient(EasyAnnualClosingRepository.class);
  final EasyCorrectionLogRepository correctionLogRepository =
      lenient(EasyCorrectionLogRepository.class);
  final EasyFilingReferenceRepository filingRepository =
      lenient(EasyFilingReferenceRepository.class);
  final EasyTuitionPaymentRepository paymentRepository =
      lenient(EasyTuitionPaymentRepository.class);
  final EasyStudentRepository studentRepository = lenient(EasyStudentRepository.class);

  final Map<Long, EasyBusiness> businesses = new LinkedHashMap<>();
  final Map<Long, EasyAccount> accounts = new LinkedHashMap<>();
  final List<EasyAccountMapping> mappings = new ArrayList<>();
  final Map<Long, EasyImportBatch> batches = new LinkedHashMap<>();
  final Map<Long, EasyImportRow> rows = new LinkedHashMap<>();
  final Map<Long, EasyTransaction> txns = new LinkedHashMap<>();
  final Map<Long, EasyJournalEntry> entries = new LinkedHashMap<>();
  final Map<Long, EasyJournalLine> lines = new LinkedHashMap<>();
  final Map<Long, EasyReviewItem> reviews = new LinkedHashMap<>();

  final AtomicLong ids = new AtomicLong(1000L);

  final EasyAccountService accountService;
  final EasyJournalService journalService;
  final EasyCorrectionService correctionService;
  final EasyImportService importService;
  final EasyShopImportService shopService;
  final EasyReportService reportService;
  final EasyAuditService auditService;

  private static <T> T lenient(Class<T> type) {
    return mock(type, withSettings().strictness(Strictness.LENIENT));
  }

  EasyShopPipelineHarness() {
    accountService = new EasyAccountService(
        accountRepository, mappingRepository, ruleRepository);
    journalService = new EasyJournalService(entryRepository, lineRepository, accountRepository);
    correctionService = new EasyCorrectionService(correctionLogRepository);
    importService = new EasyImportService(
        batchRepository, rowRepository, businessRepository, txnRepository, accountService,
        mappingRepository, journalService);
    shopService = new EasyShopImportService(
        businessRepository, txnRepository, rowRepository, importService);
    reportService = new EasyReportService(
        txnRepository, businessRepository, entryRepository, lineRepository,
        filingRepository, journalService);
    auditService = new EasyAuditService(
        reviewRepository, txnRepository, businessRepository, accountRepository,
        batchRepository, rowRepository, withholdingRepository, closingRepository,
        journalService, correctionService, correctionLogRepository);
    wire();
  }

  void seedBusiness(Long id, String code, String name) {
    EasyBusiness business = EasyBusiness.builder()
        .code(code).name(name).active(true).sortOrder(id.intValue()).build();
    ReflectionTestUtils.setField(business, "businessId", id);
    businesses.put(id, business);
  }

  void seedAccount(Long id, String code, String name, EasyAccountDirection direction) {
    EasyAccount account = EasyAccount.builder()
        .code(code).name(name).direction(direction).sortOrder(id.intValue()).active(true).build();
    ReflectionTestUtils.setField(account, "accountId", id);
    accounts.put(id, account);
  }

  void seedMapping(String externalCode, Long accountId) {
    mappings.add(EasyAccountMapping.builder()
        .account(accounts.get(accountId)).externalCode(externalCode).externalName("")
        .standardCode(null).standardName(null).note("harness").build());
  }

  private void wire() {
    when(businessRepository.findAll()).thenAnswer(inv -> new ArrayList<>(businesses.values()));
    when(businessRepository.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(businesses.get(inv.getArgument(0))));

    when(accountRepository.findByCode(any())).thenAnswer(inv -> accounts.values().stream()
        .filter(a -> a.getCode().equals(inv.getArgument(0))).findFirst());
    when(accountRepository.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(accounts.get(inv.getArgument(0))));

    when(mappingRepository.findFirstByExternalCode(any())).thenAnswer(inv -> mappings.stream()
        .filter(m -> m.getExternalCode().equals(inv.getArgument(0))).findFirst());
    when(ruleRepository.findAllByActiveTrueOrderByPriorityAsc()).thenReturn(List.of());

    when(batchRepository.save(any(EasyImportBatch.class))).thenAnswer(inv -> {
      EasyImportBatch batch = inv.getArgument(0);
      ReflectionTestUtils.setField(batch, "batchId", ids.incrementAndGet());
      batches.put(batch.getBatchId(), batch);
      return batch;
    });
    when(batchRepository.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(batches.get(inv.getArgument(0))));
    when(batchRepository.findAllByBusiness_BusinessIdOrderByBatchIdDesc(any()))
        .thenAnswer(inv -> batches.values().stream()
            .filter(b -> b.getBusiness().getBusinessId().equals(inv.getArgument(0)))
            .sorted(Comparator.comparing(EasyImportBatch::getBatchId).reversed()).toList());

    when(rowRepository.save(any(EasyImportRow.class))).thenAnswer(inv -> {
      EasyImportRow row = inv.getArgument(0);
      if (row.getRowId() == null) {
        ReflectionTestUtils.setField(row, "rowId", ids.incrementAndGet());
      }
      rows.put(row.getRowId(), row);
      return row;
    });
    when(rowRepository.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(rows.get(inv.getArgument(0))));
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(any())).thenAnswer(inv -> rows.values()
        .stream()
        .filter(r -> r.getBatch() != null
            && r.getBatch().getBatchId().equals(inv.getArgument(0)))
        .sorted(Comparator.comparingInt(EasyImportRow::getRowNo)).toList());
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(any(), any()))
        .thenAnswer(inv -> rows.values().stream()
            .filter(r -> r.getBatch() != null
                && r.getBatch().getBatchId().equals(inv.getArgument(0))
                && r.getStatus() == inv.getArgument(1))
            .sorted(Comparator.comparingInt(EasyImportRow::getRowNo)).toList());

    when(txnRepository.save(any(EasyTransaction.class))).thenAnswer(inv -> {
      EasyTransaction txn = inv.getArgument(0);
      if (txn.getTxnId() == null) {
        ReflectionTestUtils.setField(txn, "txnId", ids.incrementAndGet());
      }
      txns.put(txn.getTxnId(), txn);
      return txn;
    });
    when(txnRepository.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(txns.get(inv.getArgument(0))));
    when(txnRepository.findByOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(any(), any()))
        .thenAnswer(inv -> inRange(new ArrayList<>(txns.values()), null,
            inv.getArgument(0), inv.getArgument(1)));
    when(txnRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            any(), any(), any()))
        .thenAnswer(inv -> inRange(new ArrayList<>(txns.values()), inv.getArgument(0),
            inv.getArgument(1), inv.getArgument(2)));
    when(txnRepository.findByBusiness_BusinessIdAndFingerprintIsNotNull(any()))
        .thenAnswer(inv -> txns.values().stream()
            .filter(t -> t.getBusiness().getBusinessId().equals(inv.getArgument(0))
                && t.getFingerprint() != null).toList());

    when(entryRepository.save(any(EasyJournalEntry.class))).thenAnswer(inv -> {
      EasyJournalEntry entry = inv.getArgument(0);
      if (entry.getEntryId() == null) {
        ReflectionTestUtils.setField(entry, "entryId", ids.incrementAndGet());
      }
      entries.put(entry.getEntryId(), entry);
      return entry;
    });
    when(entryRepository.findByTxn_TxnIdAndStatusOrderByEntryIdDesc(any(), any()))
        .thenAnswer(inv -> entries.values().stream()
            .filter(e -> e.getTxn() != null && e.getTxn().getTxnId().equals(inv.getArgument(0))
                && e.getStatus() == inv.getArgument(1))
            .sorted(Comparator.comparing(EasyJournalEntry::getEntryId).reversed()).toList());
    when(entryRepository.findByBusiness_BusinessIdAndOccurredOnBetweenAndStatusOrderByEntryIdAsc(
        any(), any(), any(), any()))
        .thenAnswer(inv -> entries.values().stream()
            .filter(e -> e.getBusiness().getBusinessId().equals(inv.getArgument(0))
                && !e.getOccurredOn().isBefore(inv.getArgument(1))
                && !e.getOccurredOn().isAfter(inv.getArgument(2))
                && e.getStatus() == inv.getArgument(3))
            .sorted(Comparator.comparing(EasyJournalEntry::getEntryId)).toList());
    when(entryRepository.findByOccurredOnBetweenAndStatusOrderByEntryIdAsc(any(), any(), any()))
        .thenAnswer(inv -> entries.values().stream()
            .filter(e -> !e.getOccurredOn().isBefore(inv.getArgument(0))
                && !e.getOccurredOn().isAfter(inv.getArgument(1))
                && e.getStatus() == inv.getArgument(2))
            .sorted(Comparator.comparing(EasyJournalEntry::getEntryId)).toList());

    when(lineRepository.save(any(EasyJournalLine.class))).thenAnswer(inv -> {
      EasyJournalLine line = inv.getArgument(0);
      if (line.getLineId() == null) {
        ReflectionTestUtils.setField(line, "lineId", ids.incrementAndGet());
      }
      lines.put(line.getLineId(), line);
      return line;
    });
    when(lineRepository.findByEntry_EntryIdOrderBySortOrderAsc(any())).thenAnswer(inv -> lines
        .values().stream()
        .filter(l -> l.getEntry().getEntryId().equals(inv.getArgument(0)))
        .sorted(Comparator.comparingInt(EasyJournalLine::getSortOrder)).toList());

    when(reviewRepository.save(any(EasyReviewItem.class))).thenAnswer(inv -> {
      EasyReviewItem item = inv.getArgument(0);
      if (item.getReviewId() == null) {
        ReflectionTestUtils.setField(item, "reviewId", ids.incrementAndGet());
      }
      reviews.put(item.getReviewId(), item);
      return item;
    });
    when(reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
        any(), any(), any(), any()))
        .thenAnswer(inv -> reviews.values().stream().anyMatch(item -> item.getBusiness()
            .getBusinessId().equals(inv.getArgument(0))
            && item.getKind() == inv.getArgument(1) && item.getStatus() == inv.getArgument(2)
            && item.getTxn() != null && item.getTxn().getTxnId().equals(inv.getArgument(3))));
    when(reviewRepository.existsByBusiness_BusinessIdAndKindAndStatusAndBatch_BatchId(
        any(), any(), any(), any()))
        .thenAnswer(inv -> reviews.values().stream().anyMatch(item -> item.getBusiness()
            .getBusinessId().equals(inv.getArgument(0))
            && item.getKind() == inv.getArgument(1) && item.getStatus() == inv.getArgument(2)
            && item.getBatch() != null && item.getBatch().getBatchId().equals(inv.getArgument(3))));
    when(reviewRepository.findByBusiness_BusinessIdAndStatusOrderByReviewIdDesc(any(), any()))
        .thenAnswer(inv -> reviews.values().stream()
            .filter(item -> item.getBusiness().getBusinessId().equals(inv.getArgument(0))
                && item.getStatus() == inv.getArgument(1)).toList());
    when(reviewRepository.findByBusiness_BusinessIdAndKindAndStatusOrderByReviewIdDesc(
        any(), any(), any()))
        .thenAnswer(inv -> reviews.values().stream()
            .filter(item -> item.getBusiness().getBusinessId().equals(inv.getArgument(0))
                && item.getKind() == inv.getArgument(1)
                && item.getStatus() == inv.getArgument(2)).toList());
    when(reviewRepository.findByKindAndStatusOrderByReviewIdDesc(any(), any()))
        .thenAnswer(inv -> reviews.values().stream()
            .filter(item -> item.getKind() == inv.getArgument(0)
                && item.getStatus() == inv.getArgument(1)).toList());
    when(reviewRepository.findByStatusOrderByReviewIdDesc(any())).thenAnswer(inv -> reviews
        .values().stream().filter(item -> item.getStatus() == inv.getArgument(0)).toList());
    when(reviewRepository.countByBusiness_BusinessIdAndKindAndStatus(any(), any(), any()))
        .thenAnswer(inv -> reviews.values().stream()
            .filter(item -> item.getBusiness().getBusinessId().equals(inv.getArgument(0))
                && item.getKind() == inv.getArgument(1)
                && item.getStatus() == inv.getArgument(2)).count());
    when(reviewRepository.countByKindAndStatus(any(), any())).thenAnswer(inv -> reviews.values()
        .stream().filter(item -> item.getKind() == inv.getArgument(0)
            && item.getStatus() == inv.getArgument(1)).count());

    when(filingRepository.findByBusiness_BusinessIdAndFiscalYear(any(),
        org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(Optional.empty());
  }

  private List<EasyTransaction> inRange(List<EasyTransaction> source, Long businessId,
      LocalDate from, LocalDate to) {
    return source.stream()
        .filter(t -> businessId == null
            || t.getBusiness().getBusinessId().equals(businessId))
        .filter(t -> t.getOccurredOn() != null && !t.getOccurredOn().isBefore(from)
            && !t.getOccurredOn().isAfter(to))
        .sorted(Comparator.comparing(EasyTransaction::getOccurredOn).reversed())
        .toList();
  }

  long txnCount() {
    return txns.size();
  }

  long entryCount() {
    return entries.size();
  }
}
