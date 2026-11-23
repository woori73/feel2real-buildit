package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionDepositMatcher.Bill;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionDepositMatcher.Decision;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionDepositMatcher.Deposit;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTuitionPayment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTuitionPaymentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집된 은행 입금(easy_transactions, 읽기 전용)을 청구 링크가 있는 수납과 맞춰 본다.
 * 확실한 것만 납부 기록에 더하고, 애매한 것은 원장 확인 목록(REVIEW)으로 남긴다.
 * 입금 1건은 대조 행 1개만 가진다(txn_id UNIQUE) — 다시 실행해도 같은 입금을 두 번 기록하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TuitionDepositMatchService {

  static final String PAY_METHOD = "계좌이체";
  static final int MAX_RANGE_DAYS = 92;
  /** 은행 수집 경로(직접 로그인 내려받기)가 넣는 결제수단 값과 손으로 넣는 값. */
  private static final Set<String> BANK_PAY_METHODS = Set.of("계좌", "계좌이체");

  private final EasyTransactionRepository transactionRepository;
  private final EasyTuitionPaymentRepository paymentRepository;
  private final EasyTuitionBillLinkRepository linkRepository;
  private final EasyTuitionDepositMatchRepository matchRepository;

  public record RunSummary(int scanned, int alreadyHandled, int autoRecorded, int review) {}

  /** 원장 화면용 한 줄(관리자 전용). */
  public record MatchRow(Long matchId, Long txnId, LocalDate occurredOn, long amount,
      String counterparty, String description, String status, String reason, Long paymentId,
      String studentName, String targetMonth, Long balance) {}

  @Transactional
  public RunSummary run(Long businessId, LocalDate from, LocalDate to, LocalDateTime now) {
    if (businessId == null || from == null || to == null) {
      throw new IllegalArgumentException("사업체와 기간을 정해 주세요.");
    }
    if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
      throw new IllegalArgumentException("기간은 " + MAX_RANGE_DAYS + "일 이내로 정해 주세요.");
    }
    List<EasyTransaction> deposits = new ArrayList<>();
    for (EasyTransaction txn : transactionRepository
        .findByBusiness_BusinessIdAndOccurredOnBetweenOrderByOccurredOnDescTxnIdDesc(
            businessId, from, to)) {
      if (txn.getDirection() == EasyTxnDirection.INCOME && txn.getAmount() > 0
          && BANK_PAY_METHODS.contains(txn.getPayMethod())) {
        deposits.add(txn);
      }
    }
    Set<Long> handled = new HashSet<>();
    if (!deposits.isEmpty()) {
      for (EasyTuitionDepositMatch match : matchRepository.findByTxnIdIn(
          deposits.stream().map(EasyTransaction::getTxnId).toList())) {
        handled.add(match.getTxnId());
      }
    }
    Map<Long, EasyTransaction> fresh = new HashMap<>();
    List<Deposit> inputs = new ArrayList<>();
    for (EasyTransaction txn : deposits) {
      if (handled.contains(txn.getTxnId())) {
        continue;
      }
      fresh.put(txn.getTxnId(), txn);
      inputs.add(new Deposit(txn.getTxnId(), txn.getAmount(), txn.getOccurredOn(),
          txn.getCounterparty() + " " + txn.getDescription()));
    }
    List<Bill> bills = new ArrayList<>();
    for (EasyTuitionBillLink link : linkRepository.findByPayment_Business_BusinessIdAndStatus(
        businessId, EasyTuitionBillLink.Status.ACTIVE)) {
      EasyTuitionPayment payment = link.getPayment();
      bills.add(new Bill(payment.getPaymentId(), payment.getBalance(), link.getDepositorCode(),
          payment.getStudent().getName(),
          link.getCreatedAt() == null ? from : link.getCreatedAt().toLocalDate(),
          link.getExpiresAt().toLocalDate()));
    }
    int auto = 0;
    int review = 0;
    for (Decision decision : TuitionDepositMatcher.decide(bills, inputs)) {
      EasyTransaction txn = fresh.get(decision.txnId());
      EasyTuitionPayment payment = decision.paymentId() == null ? null
          : paymentRepository.findLockedByPaymentId(decision.paymentId()).orElse(null);
      // 잠근 뒤 다시 본다: 그 사이 금액이 바뀌었으면 자동 기록하지 않는다.
      boolean apply = decision.auto() && payment != null
          && payment.getBalance() == txn.getAmount();
      if (apply) {
        payment.applyDeposit(txn.getAmount(), txn.getOccurredOn(), PAY_METHOD);
        auto++;
      } else {
        review++;
      }
      matchRepository.save(EasyTuitionDepositMatch.builder()
          .txnId(txn.getTxnId())
          .payment(payment)
          .amount(txn.getAmount())
          .occurredOn(txn.getOccurredOn())
          .status(apply ? EasyTuitionDepositMatch.Status.AUTO_CONFIRMED
              : EasyTuitionDepositMatch.Status.REVIEW)
          .reason(decision.reason())
          .decidedAt(apply ? now : null)
          .build());
    }
    return new RunSummary(deposits.size(), handled.size(), auto, review);
  }

  /** applied=false면 원장 확인 목록, true면 납부 기록에 반영된 목록. */
  public List<MatchRow> list(boolean applied) {
    List<EasyTuitionDepositMatch.Status> statuses = applied
        ? List.of(EasyTuitionDepositMatch.Status.AUTO_CONFIRMED,
            EasyTuitionDepositMatch.Status.CONFIRMED)
        : List.of(EasyTuitionDepositMatch.Status.REVIEW);
    List<EasyTuitionDepositMatch> matches =
        matchRepository.findByStatusInOrderByOccurredOnDescMatchIdDesc(statuses);
    Map<Long, EasyTransaction> txns = new HashMap<>();
    for (EasyTransaction txn : transactionRepository.findAllById(
        matches.stream().map(EasyTuitionDepositMatch::getTxnId).toList())) {
      txns.put(txn.getTxnId(), txn);
    }
    List<MatchRow> rows = new ArrayList<>();
    for (EasyTuitionDepositMatch match : matches) {
      EasyTransaction txn = txns.get(match.getTxnId());
      EasyTuitionPayment payment = match.getPayment();
      rows.add(new MatchRow(match.getMatchId(), match.getTxnId(), match.getOccurredOn(),
          match.getAmount(), txn == null ? "" : txn.getCounterparty(),
          txn == null ? "" : txn.getDescription(), match.getStatus().name(),
          match.getReason().name(), payment == null ? null : payment.getPaymentId(),
          payment == null ? null : payment.getStudent().getName(),
          payment == null ? null : payment.getTargetMonth(),
          payment == null ? null : payment.getBalance()));
    }
    return rows;
  }

  /** 원장이 확인 목록의 입금을 수납에 연결해 납부로 기록한다. 남은 청구액보다 큰 입금은 받지 않는다. */
  @Transactional
  public void confirm(Long matchId, Long paymentId, LocalDateTime now) {
    EasyTuitionDepositMatch match = requireMatch(matchId);
    if (match.getStatus() != EasyTuitionDepositMatch.Status.REVIEW) {
      throw new IllegalArgumentException("이미 처리된 입금입니다.");
    }
    Long targetId = paymentId != null ? paymentId
        : match.getPayment() == null ? null : match.getPayment().getPaymentId();
    if (targetId == null) {
      throw new IllegalArgumentException("어느 수납의 입금인지 골라 주세요.");
    }
    EasyTuitionPayment payment = paymentRepository.findLockedByPaymentId(targetId)
        .orElseThrow(() -> new IllegalArgumentException("수납을 찾을 수 없습니다."));
    if (match.getAmount() > payment.getBalance()) {
      throw new IllegalArgumentException(
          "입금액이 남은 청구액보다 큽니다. 수납 화면에서 직접 정리해 주세요.");
    }
    payment.applyDeposit(match.getAmount(), match.getOccurredOn(), PAY_METHOD);
    match.confirm(payment, now);
  }

  /** 수강료가 아닌 입금으로 표시한다(납부 기록은 건드리지 않는다). */
  @Transactional
  public void dismiss(Long matchId, LocalDateTime now) {
    EasyTuitionDepositMatch match = requireMatch(matchId);
    if (match.getStatus() != EasyTuitionDepositMatch.Status.REVIEW) {
      throw new IllegalArgumentException("이미 처리된 입금입니다.");
    }
    match.dismiss(now);
  }

  /** 잘못 기록된 대조를 취소한다 — 더했던 금액을 빼고 다시 확인 목록으로 돌린다. */
  @Transactional
  public void cancel(Long matchId, LocalDateTime now) {
    EasyTuitionDepositMatch match = requireMatch(matchId);
    if (!match.isApplied() || match.getPayment() == null) {
      throw new IllegalArgumentException("납부로 기록된 입금만 취소할 수 있습니다.");
    }
    EasyTuitionPayment payment = paymentRepository
        .findLockedByPaymentId(match.getPayment().getPaymentId())
        .orElseThrow(() -> new IllegalArgumentException("수납을 찾을 수 없습니다."));
    payment.revertDeposit(match.getAmount());
    match.backToReview(now);
  }

  private EasyTuitionDepositMatch requireMatch(Long matchId) {
    return matchRepository.findById(matchId)
        .orElseThrow(() -> new IllegalArgumentException("입금 대조 기록을 찾을 수 없습니다."));
  }
}
