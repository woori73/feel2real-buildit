package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse.EasyOverdueStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
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
  private final EasyTuitionPaymentRepository paymentRepository;
  private final EasyStudentRepository studentRepository;

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
      if (txn.getDirection() == EasyTxnDirection.INCOME) {
        income += txn.getAmount();
      } else {
        expense += txn.getAmount();
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
      result.add(EasyTxnResponse.from(txn));
    }
    return result;
  }

  @Transactional
  public EasyTxnResponse createTxn(EasyTxnRequest request) {
    EasyTransaction txn = EasyTransaction.builder()
        .business(requireActiveBusiness(request.businessId()))
        .occurredOn(requireDate(request.occurredOn()))
        .direction(requireDirection(request.direction()))
        .amount(requireNonNegative(request.amount(), "금액"))
        .category(request.category())
        .counterparty(request.counterparty())
        .description(request.description())
        .payMethod(request.payMethod())
        .memo(request.memo())
        .receiptRef(request.receiptRef())
        .build();
    return EasyTxnResponse.from(txnRepository.save(txn));
  }

  @Transactional
  public EasyTxnResponse updateTxn(Long txnId, EasyTxnRequest request) {
    EasyTransaction txn = txnRepository.findById(txnId)
        .orElseThrow(() -> new IllegalArgumentException("거래를 찾을 수 없습니다: txnId=" + txnId));
    txn.update(
        requireActiveBusiness(request.businessId()),
        requireDate(request.occurredOn()),
        requireDirection(request.direction()),
        requireNonNegative(request.amount(), "금액"),
        request.category(),
        request.counterparty(),
        request.description(),
        request.payMethod(),
        request.memo(),
        request.receiptRef());
    return EasyTxnResponse.from(txn);
  }

  @Transactional
  public void deleteTxn(Long txnId) {
    EasyTransaction txn = txnRepository.findById(txnId)
        .orElseThrow(() -> new IllegalArgumentException("거래를 찾을 수 없습니다: txnId=" + txnId));
    txnRepository.delete(txn);
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

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
