package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse.EasyOverdueStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTaxClassificationRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceReview;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyWithholdingReview;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTaxClassification;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 장부 — 월 손익 요약 + 거래 CRUD. 금액은 long(원)만 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyLedgerService {

  private final EasyTransactionRepository txnRepository;
  private final EasyBusinessRepository businessRepository;
  private final EasyAccountRepository accountRepository;
  private final EasyTuitionPaymentRepository paymentRepository;
  private final EasyStudentRepository studentRepository;
  private final EasyJournalService journalService;
  private final EasyCorrectionService correctionService;

  /** 월 대시보드 — businessId null이면 전체 합산. */
  public EasyDashboardResponse summary(Long businessId, YearMonth month) {
    YearMonth target = month == null ? YearMonth.now() : month;
    LocalDate from = target.atDay(1);
    LocalDate to = target.atEndOfMonth();

    List<EasyTransaction> txns = businessId == null
        ? txnRepository.findByOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(from, to)
        : txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            businessId, from, to);

    long income = 0L;
    long expense = 0L;
    for (EasyTransaction txn : txns) {
      // 차입금·계좌이체 등 세무상 P&L이 아닌 흐름(NOT_INCOME/NOT_EXPENSE)은
      // 손익에 합산하지 않는다 (대출 입금의 수익 왜곡 방지 — 목록에는 보인다).
      if (txn.getTaxClassification() != null
          && txn.getTaxClassification().excludesProfit(txn.getDirection())) {
        continue;
      }
      // 역분개(INCOME + expenseReversal)는 매출이 아니라 비용 차감이다
      // (EasyReportService/EasyExecutiveService와 동일 원칙).
      if (txn.getDirection() == EasyTxnDirection.INCOME && !txn.isReversal()) {
        income += txn.getAmount();
      } else if (txn.getDirection() == EasyTxnDirection.EXPENSE) {
        expense += txn.getAmount();
      } else if (txn.isReversal()) {
        if (txn.getAccount() != null
            && txn.getAccount().getDirection() == EasyAccountDirection.INCOME) {
          income -= txn.getAmount();
        } else {
          expense -= txn.getAmount();
        }
      }
    }

    List<EasyTuitionPayment> payments = businessId == null
        ? paymentRepository.findAll()
        : paymentRepository.findByBusiness_BusinessId(businessId);
    long receivable = 0L;
    Map<Long, Long> balanceByStudent = new HashMap<>();
    Map<Long, String> nameByStudent = new HashMap<>();
    Map<Long, String> schoolByStudent = new HashMap<>();
    for (EasyTuitionPayment payment : payments) {
      long balance = payment.getBalance();
      if (balance <= 0) {
        continue;
      }
      receivable += balance;
      Long studentId = payment.getStudent().getStudentId();
      balanceByStudent.merge(studentId, balance, Long::sum);
      nameByStudent.putIfAbsent(studentId, payment.getStudent().getName());
      schoolByStudent.putIfAbsent(studentId, payment.getStudent().getSchool());
    }
    List<EasyOverdueStudent> overdue = balanceByStudent.entrySet().stream()
        .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
        .limit(10)
        .map(entry -> new EasyOverdueStudent(
            entry.getKey(),
            nameByStudent.getOrDefault(entry.getKey(), ""),
            schoolByStudent.getOrDefault(entry.getKey(), ""),
            entry.getValue()))
        .toList();

    List<Long> scopeIds = businessId == null ? List.of() : List.of(businessId);
    List<EasyTransaction> recent = businessId == null
        ? txnRepository.findTop10ByOrderByOccurredOnDescTxnIdDesc()
        : txnRepository.findTop10ByBusiness_BusinessIdInOrderByOccurredOnDescTxnIdDesc(scopeIds);

    String businessName = "전체";
    if (businessId != null) {
      businessName = businessRepository.findById(businessId)
          .map(EasyBusiness::getName)
          .orElse("전체");
    }

    // 학생 수도 같은 scope로 — 전체면 재원 수, 사업체 선택이면 해당 사업체 수납 이력 학생 수.
    // 스코프가 다른 숫자(전체 학생 수 + 사업체 손익)를 한 화면에 섞지 않는다.
    long studentCount;
    if (businessId == null) {
      studentCount = studentRepository.countByStatus(EasyStudentStatus.ENROLLED);
    } else {
      studentCount = payments.stream()
          .map(payment -> payment.getStudent().getStudentId())
          .collect(Collectors.toSet())
          .size();
    }

    return new EasyDashboardResponse(
        target.toString(),
        businessId,
        businessName,
        income,
        expense,
        income - expense,
        receivable,
        studentCount,
        recent.stream().map(EasyTxnResponse::from).toList(),
        overdue);
  }

  /** 거래 목록 — 기간/사업체/방향/분류 필터. null 필터는 전체. */
  public List<EasyTxnResponse> listTxns(Long businessId, LocalDate from, LocalDate to,
      EasyTxnDirection direction, String category) {
    return listTxns(businessId, from, to, direction, category, null);
  }

  /** 거래 목록 + 계정 drill-down (P&L 금액 클릭 시 underlying transactions). */
  public List<EasyTxnResponse> listTxns(Long businessId, LocalDate from, LocalDate to,
      EasyTxnDirection direction, String category, Long accountId) {
    LocalDate start = from == null ? LocalDate.of(2000, 1, 1) : from;
    LocalDate end = to == null ? LocalDate.of(2100, 12, 31) : to;
    List<EasyTransaction> txns = businessId == null
        ? txnRepository.findByOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(start, end)
        : txnRepository.findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            businessId, start, end);
    List<EasyTxnResponse> result = new ArrayList<>();
    for (EasyTransaction txn : txns) {
      if (direction != null && txn.getDirection() != direction) {
        continue;
      }
      if (hasText(category) && !category.equals(txn.getCategory())) {
        continue;
      }
      if (accountId != null && (txn.getAccount() == null
          || !accountId.equals(txn.getAccount().getAccountId()))) {
        continue;
      }
      result.add(EasyTxnResponse.from(txn));
    }
    return result;
  }

  @Transactional
  public EasyTxnResponse createTxn(EasyTxnRequest request) {
    EasyBusiness business = requireActiveBusiness(request.businessId());
    LocalDate occurredOn = requireDate(request.occurredOn());
    EasyTxnDirection direction = requireDirection(request.direction());
    long amount = requireNonNegative(request.amount(), "금액");
    EasyTransaction txn = EasyTransaction.builder()
        .business(business)
        .occurredOn(occurredOn)
        .direction(direction)
        .amount(amount)
        .category(request.category())
        .counterparty(request.counterparty())
        .description(request.description())
        .payMethod(request.payMethod())
        .memo(request.memo())
        .receiptRef(request.receiptRef())
        .account(requireAccount(request.accountId()))
        .supplyAmount(optionalNonNegative(request.supplyAmount(), "공급가액"))
        .vatAmount(optionalNonNegative(request.vatAmount(), "부가세"))
        .evidenceType(parseEvidence(request.evidenceType()))
        .sourceKind(normalizeSourceKind(request.sourceKind()))
        .sourceRef(request.sourceRef())
        .fingerprint(EasyFingerprint.of(
            business.getBusinessId(), occurredOn, direction.name(), amount,
            request.counterparty(), request.description(), request.payMethod()))
        .evidenceReview(parseEvidenceReview(request.evidenceReview()))
        .withholdingReview(parseWithholdingReview(request.withholdingReview()))
        .expenseReversal(null)
        .build();
    EasyTransaction saved = txnRepository.save(txn);
    journalService.repost(saved);
    return EasyTxnResponse.from(saved);
  }

  @Transactional
  public EasyTxnResponse updateTxn(Long txnId, EasyTxnRequest request) {
    EasyTransaction txn = txnRepository.findById(txnId)
        .orElseThrow(() -> new IllegalArgumentException("거래를 찾을 수 없습니다: txnId=" + txnId));
    EasyBusiness business = requireActiveBusiness(request.businessId());
    LocalDate occurredOn = requireDate(request.occurredOn());
    EasyTxnDirection direction = requireDirection(request.direction());
    long amount = requireNonNegative(request.amount(), "금액");
    String before = EasyTxnSnapshot.of(txn);
    txn.update(
        business,
        occurredOn,
        direction,
        amount,
        request.category(),
        request.counterparty(),
        request.description(),
        request.payMethod(),
        request.memo(),
        request.receiptRef(),
        requireAccount(request.accountId()),
        optionalNonNegative(request.supplyAmount(), "공급가액"),
        optionalNonNegative(request.vatAmount(), "부가세"),
        parseEvidence(request.evidenceType()),
        normalizeSourceKind(request.sourceKind()),
        request.sourceRef(),
        EasyFingerprint.of(
            business.getBusinessId(), occurredOn, direction.name(), amount,
            request.counterparty(), request.description(), request.payMethod()),
        parseEvidenceReview(request.evidenceReview()),
        parseWithholdingReview(request.withholdingReview()),
        txn.isReversal() ? Boolean.TRUE : null);
    correctionService.record(business, txn, "UPDATE", before, EasyTxnSnapshot.of(txn), "admin");
    journalService.repost(txn);
    return EasyTxnResponse.from(txn);
  }

  /**
   * 세무구분 지정 — 손익 반영 여부만 확정한다.
   * 거래 금액·출처(sourceRef)·분개는 바꾸지 않으므로 재분개하지 않는다.
   * 확정된 거래를 삭제하거나 원본을 다시 쓰지 않는 정정 경로다.
   */
  @Transactional
  public EasyTxnResponse assignTaxClassification(Long txnId,
      EasyTaxClassificationRequest request) {
    EasyTransaction txn = txnRepository.findById(txnId)
        .orElseThrow(() -> new IllegalArgumentException("거래를 찾을 수 없습니다: txnId=" + txnId));
    EasyTaxClassification tax = parseTaxClassification(
        request == null ? null : request.taxClassification());
    if (!matchesDirection(tax, txn.getDirection())) {
      throw new IllegalArgumentException("direction=" + txn.getDirection()
          + " 거래에는 " + tax + "을(를) 지정할 수 없습니다.");
    }
    String before = EasyTxnSnapshot.of(txn);
    txn.assignTaxClassification(tax);
    correctionService.record(txn.getBusiness(), txn, "TAX_CLASS", before,
        EasyTxnSnapshot.of(txn), "admin");
    return EasyTxnResponse.from(txn);
  }

  private EasyTaxClassification parseTaxClassification(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("세무구분을 선택해 주세요.");
    }
    try {
      return EasyTaxClassification.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("알 수 없는 세무구분입니다: " + value);
    }
  }

  /** REVIEW_REQUIRED(미확정)는 어느 방향이든 허용한다. 나머지 확정값은 방향과 맞아야 한다. */
  private boolean matchesDirection(EasyTaxClassification tax, EasyTxnDirection direction) {
    if (tax == EasyTaxClassification.REVIEW_REQUIRED) {
      return true;
    }
    return direction == EasyTxnDirection.INCOME
        ? tax == EasyTaxClassification.INCOME_INCLUDED || tax == EasyTaxClassification.NOT_INCOME
        : tax == EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE
            || tax == EasyTaxClassification.NON_DEDUCTIBLE || tax == EasyTaxClassification.NOT_EXPENSE;
  }

  @Transactional
  public void deleteTxn(Long txnId) {
    EasyTransaction txn = txnRepository.findById(txnId)
        .orElseThrow(() -> new IllegalArgumentException("거래를 찾을 수 없습니다: txnId=" + txnId));
    // 삭제 감사 로그는 txn FK 없이 snapshot으로 보존한다.
    // 삭제 대상을 참조한 채로 같은 persistence context에서 delete하면
    // flush 시 TransientPropertyValueException(운영 DELETE 500)이 발생한다.
    // easy_correction_logs.txn_id는 NULL 허용 + ON DELETE SET NULL 설계다.
    String before = EasyTxnSnapshot.of(txn);
    correctionService.record(txn.getBusiness(), null, "DELETE", before, "{}", "admin");
    journalService.voidFor(txn);
    txnRepository.delete(txn);
  }

  /**
   * 거래 CSV export — Excel 한글 호환(BOM). 세무사 전달용 기초 형식
   * (korean-jangbu-for 분개 CSV 항목을 참고한 최소 컬럼).
   */
  public String exportCsv(Long businessId, LocalDate from, LocalDate to) {
    List<EasyTxnResponse> txns = listTxns(businessId, from, to, null, null);
    StringBuilder csv = new StringBuilder("\uFEFF");
    csv.append("거래일,사업체,구분,금액,공급가액,부가세,계정,분류,거래처,적요,결제수단,증빙,메모\n");
    for (EasyTxnResponse txn : txns) {
      csv.append(csvCell(txn.occurredOn() == null ? "" : txn.occurredOn().toString())).append(',');
      csv.append(csvCell(txn.businessName())).append(',');
      csv.append("INCOME".equals(txn.direction()) ? "수입" : "지출").append(',');
      csv.append(txn.amount()).append(',');
      csv.append(txn.supplyAmount() == null ? "" : String.valueOf(txn.supplyAmount())).append(',');
      csv.append(txn.vatAmount() == null ? "" : String.valueOf(txn.vatAmount())).append(',');
      csv.append(csvCell(txn.accountName() == null ? "" : txn.accountName())).append(',');
      csv.append(csvCell(txn.category())).append(',');
      csv.append(csvCell(txn.counterparty())).append(',');
      csv.append(csvCell(txn.description())).append(',');
      csv.append(csvCell(txn.payMethod())).append(',');
      csv.append(csvCell(txn.evidenceType() == null ? "" : txn.evidenceType())).append(',');
      csv.append(csvCell(txn.memo() == null ? "" : txn.memo())).append('\n');
    }
    return csv.toString();
  }

  private String csvCell(String value) {
    if (value == null) {
      return "";
    }
    String escaped = value.replace("\"", "\"\"");
    if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
      return "\"" + escaped + "\"";
    }
    return escaped;
  }

  private EasyBusiness requireActiveBusiness(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    EasyBusiness business = businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다: " + businessId));
    if (!business.isActive()) {
      throw new IllegalArgumentException("비활성 사업체에는 등록할 수 없습니다.");
    }
    return business;
  }

  private LocalDate requireDate(LocalDate date) {
    if (date == null) {
      throw new IllegalArgumentException("거래일을 입력해 주세요.");
    }
    return date;
  }

  private EasyTxnDirection requireDirection(String direction) {
    if (!hasText(direction)) {
      throw new IllegalArgumentException("수입/지출을 선택해 주세요.");
    }
    try {
      return EasyTxnDirection.valueOf(direction.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("수입/지출 값이 올바르지 않습니다.");
    }
  }

  private long requireNonNegative(Long amount, String label) {
    if (amount == null || amount < 0) {
      throw new IllegalArgumentException(label + "은 0 이상이어야 합니다.");
    }
    return amount;
  }

  private Long optionalNonNegative(Long amount, String label) {
    if (amount == null) {
      return null;
    }
    if (amount < 0) {
      throw new IllegalArgumentException(label + "은 0 이상이어야 합니다.");
    }
    return amount;
  }

  /** 계정 연결 — null이면 category 텍스트 유지 (v1 호환). */
  private EasyAccount requireAccount(Long accountId) {
    if (accountId == null) {
      return null;
    }
    EasyAccount account = accountRepository.findById(accountId)
        .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다: " + accountId));
    if (!account.isActive()) {
      throw new IllegalArgumentException("비활성 계정은 사용할 수 없습니다.");
    }
    return account;
  }

  private EasyEvidenceType parseEvidence(String evidenceType) {
    if (evidenceType == null || evidenceType.isBlank()) {
      return null;
    }
    return EasyEvidenceType.parse(evidenceType);
  }

  /** 증빙 검토 상태 — null이면 UNKNOWN으로 읽는다 (저장은 NULL 유지). */
  private EasyEvidenceReview parseEvidenceReview(String evidenceReview) {
    if (evidenceReview == null || evidenceReview.isBlank()) {
      return null;
    }
    return EasyEvidenceReview.parse(evidenceReview);
  }

  /** 원천징수 검토 상태 — null이면 NONE으로 읽는다 (저장은 NULL 유지). */
  private EasyWithholdingReview parseWithholdingReview(String withholdingReview) {
    if (withholdingReview == null || withholdingReview.isBlank()) {
      return null;
    }
    return EasyWithholdingReview.parse(withholdingReview);
  }

  /** 출처 표기 — 빈 값은 NULL로 둔다. 수집경로 추적용이라 값 검증은 하지 않는다. */
  private String normalizeSourceKind(String sourceKind) {
    if (sourceKind == null || sourceKind.isBlank()) {
      return null;
    }
    return sourceKind.trim();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
