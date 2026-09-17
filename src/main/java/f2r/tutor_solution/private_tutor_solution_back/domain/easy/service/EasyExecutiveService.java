package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyExecutiveSummaryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyExecutiveSummaryResponse.EasyBusinessSlice;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyExecutiveSummaryResponse.EasyIncomeTaxPreview;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 경영 대시보드 — 올해 1/1~오늘 vs 작년 동기 비교.
 * 사업장 데이터는 독립이며 전체 선택 시에만 합산한다.
 * 숫자는 DB 실측만 쓰며 세법 계산·예측을 하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyExecutiveService {

  private final EasyTransactionRepository txnRepository;
  private final EasyBusinessRepository businessRepository;
  private final EasyTuitionPaymentRepository paymentRepository;
  private final EasyStudentRepository studentRepository;

  /** 경영 요약 — businessId null이면 전체 합산. */
  public EasyExecutiveSummaryResponse summary(Long businessId) {
    LocalDate today = LocalDate.now();
    LocalDate currentFrom = LocalDate.of(today.getYear(), 1, 1);
    LocalDate currentTo = today;
    LocalDate previousFrom = currentFrom.minusYears(1);
    LocalDate previousTo = today.minusYears(1);

    List<EasyBusiness> scope = scopeBusinesses(businessId);
    String businessName = businessId == null ? "전체"
        : businessRepository.findById(businessId).map(EasyBusiness::getName).orElse("전체");

    Totals current = totals(scope, currentFrom, currentTo);
    Totals previous = totals(scope, previousFrom, previousTo);

    List<EasyBusinessSlice> slices = new ArrayList<>();
    for (EasyBusiness business : scope) {
      Totals cur = totals(List.of(business), currentFrom, currentTo);
      Totals prev = totals(List.of(business), previousFrom, previousTo);
      slices.add(new EasyBusinessSlice(
          business.getBusinessId(), business.getName(),
          cur.income, cur.expense, cur.profit(),
          prev.income, prev.expense, prev.profit()));
    }

    Receivable receivable = receivable(businessId);
    long studentCount = studentCount(businessId);

    List<String> briefing = briefing(businessName, current, previous, slices, receivable);

    return new EasyExecutiveSummaryResponse(
        businessId, businessName,
        currentFrom, currentTo, previousFrom, previousTo,
        current.income, current.expense, current.profit(),
        previous.income, previous.expense, previous.profit(),
        current.income - previous.income, rate(current.income, previous.income),
        current.expense - previous.expense, rate(current.expense, previous.expense),
        current.profit() - previous.profit(), rate(current.profit(), previous.profit()),
        receivable.total, studentCount,
        slices, briefing,
        EasyIncomeTaxPreview.pending());
  }

  private List<EasyBusiness> scopeBusinesses(Long businessId) {
    if (businessId != null) {
      return businessRepository.findById(businessId).map(List::of).orElseGet(List::of);
    }
    return businessRepository.findAll().stream()
        .filter(EasyBusiness::isActive)
        .collect(Collectors.toList());
  }

  private record Totals(long income, long expense) {
    long profit() {
      return income - expense;
    }
  }

  private Totals totals(List<EasyBusiness> scope, LocalDate from, LocalDate to) {
    long income = 0L;
    long expense = 0L;
    for (EasyBusiness business : scope) {
      List<EasyTransaction> txns = txnRepository
          .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
              business.getBusinessId(), from, to);
      for (EasyTransaction txn : txns) {
        if (txn.getDirection() == EasyTxnDirection.INCOME) {
          income += txn.getAmount();
        } else {
          expense += txn.getAmount();
        }
      }
    }
    return new Totals(income, expense);
  }

  private record Receivable(long total, long studentSize) {
  }

  /** 미수금 — LedgerService.summary와 같은 scope 규칙 (잔액>0만 합산). */
  private Receivable receivable(Long businessId) {
    List<EasyTuitionPayment> payments = businessId == null
        ? paymentRepository.findAll()
        : paymentRepository.findByBusiness_BusinessId(businessId);
    long total = 0L;
    Map<Long, Long> balanceByStudent = new HashMap<>();
    for (EasyTuitionPayment payment : payments) {
      long balance = payment.getBalance();
      if (balance <= 0) {
        continue;
      }
      total += balance;
      balanceByStudent.merge(payment.getStudent().getStudentId(), balance, Long::sum);
    }
    return new Receivable(total, balanceByStudent.size());
  }

  private long studentCount(Long businessId) {
    if (businessId == null) {
      return studentRepository.countByStatus(EasyStudentStatus.ENROLLED);
    }
    return paymentRepository.findByBusiness_BusinessId(businessId).stream()
        .map(payment -> payment.getStudent().getStudentId())
        .collect(Collectors.toSet())
        .size();
  }

  /** 증감률(%) — 비교 기준이 0이면 null (가짜 %를 만들지 않는다). */
  private Double rate(long current, long previous) {
    if (previous == 0) {
      return null;
    }
    return (current - previous) * 100.0 / Math.abs((double) previous);
  }

  /**
   * AI 경영 브리핑 기반 — DB 숫자로 계산 가능한 문장만 만든다.
   * 자연어 분석(LLM)은 이 문장 목록을 입력으로 받는 구조로 분리한다.
   */
  private List<String> briefing(String businessName, Totals current, Totals previous,
      List<EasyBusinessSlice> slices, Receivable receivable) {
    List<String> lines = new ArrayList<>();
    lines.add(describeIncome(businessName, current.income, previous.income));
    for (EasyBusinessSlice slice : slices) {
      if (slices.size() > 1) {
        lines.add(describeIncome(
            slice.businessName(), slice.currentIncome(), slice.previousIncome()));
      }
    }
    long profitDiff = current.profit() - previous.profit();
    lines.add("장부상 손익은 " + formatWon(current.profit()) + "으로 전년 동기 대비 "
        + formatDiff(profitDiff) + "입니다.");
    if (receivable.total > 0) {
      lines.add("현재 미수금은 총 " + formatWon(receivable.total)
          + " (" + receivable.studentSize + "명) 입니다.");
    } else {
      lines.add("현재 미수금은 없습니다.");
    }
    return lines;
  }

  private String describeIncome(String scope, long current, long previous) {
    Double pct = rate(current, previous);
    String scopeLabel = scope == null || scope.isBlank() ? "전체" : scope;
    if (pct == null) {
      return scopeLabel + " 매출(수입)은 올해 " + formatWon(current)
          + "이며 비교 기준(작년 동기 0원)이 없어 증감률을 표시하지 않습니다.";
    }
    String direction = pct > 0 ? "증가" : pct < 0 ? "감소" : "변동 없음";
    return scopeLabel + " 매출(수입)은 전년 동기보다 "
        + String.format("%.1f", Math.abs(pct)) + "% " + direction + "했습니다"
        + " (올해 " + formatWon(current) + " / 작년 동기 " + formatWon(previous) + ").";
  }

  private String formatWon(long amount) {
    return String.format("%,d원", amount);
  }

  private String formatDiff(long diff) {
    return (diff >= 0 ? "+" : "-") + String.format("%,d원", Math.abs(diff));
  }
}
