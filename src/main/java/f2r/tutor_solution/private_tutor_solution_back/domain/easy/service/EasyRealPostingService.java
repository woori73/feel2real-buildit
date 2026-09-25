package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyJournalService.Leg;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTaxClassification;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 실계정 복식 전기 (Phase A1).
 *
 * 기존 shadow journal (실계정 ↔ SETTLEMENT)과 달리 모든 legs에 실계정을 사용한다.
 * 상대계정은 호출자가 명시한다 — 카드 issuer·은행 이름으로 추론하지 않는다.
 * 미분류 은행거래는 이 서비스를 호출하지 않는다 (REVIEW_REQUIRED, 자동 posting 금지).
 *
 * 세무구분은 회계분류와 분리 저장한다. B/S 순수이동(송금·원금·카드대금)은
 * P&L 거래를 만들지 않으므로 종소세 매출/필요경비에 들어가지 않는다 (구조적 보장).
 * 이자 등 P&L 거래는 세무구분을 명시하되, 세법 확정 전에는 *_CANDIDATE/REVIEW_REQUIRED만
 * 사용하고 최종 판정을 단정하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyRealPostingService {

  private final EasyJournalService journalService;
  private final EasyTransactionRepository txnRepository;
  private final EasyAccountRepository accountRepository;
  private final EasyBusinessRepository businessRepository;

  /** 카드 비용 발생: Dr 비용 / Cr 카드미지급금. */
  @Transactional
  public EasyJournalEntry postCardPurchase(Long businessId, LocalDate occurredOn,
      Long expenseAccountId, Long cardPayableAccountId, long amount,
      String counterparty, String description,
      EasyTaxClassification taxClassification, String sourceRef) {
    EasyBusiness business = business(businessId);
    EasyAccount expense = account(expenseAccountId);
    EasyAccount payable = account(cardPayableAccountId);
    requirePositive(amount);
    EasyTransaction txn = txnRepository.save(EasyTransaction.builder()
        .business(business).occurredOn(requireDate(occurredOn))
        .direction(EasyTxnDirection.EXPENSE).amount(amount)
        .category(expense.getName()).counterparty(value(counterparty))
        .description(value(description)).payMethod("카드")
        .account(expense).sourceKind("REAL_DOUBLE_ENTRY").sourceRef(sourceRef)
        .taxClassification(taxClassification == null
            ? EasyTaxClassification.REVIEW_REQUIRED : taxClassification)
        .build());
    return journalService.postLegs(business, txn.getOccurredOn(), txn, txn.getDescription(),
        List.of(new Leg(expense, amount, 0L), new Leg(payable, 0L, amount)));
  }

  /** 카드대금 은행 출금: Dr 카드미지급금 / Cr 지정 은행계정. */
  @Transactional
  public EasyJournalEntry postCardPayment(Long businessId, LocalDate occurredOn,
      Long cardPayableAccountId, Long bankAccountId, long amount, String memo) {
    EasyBusiness business = business(businessId);
    EasyAccount payable = account(cardPayableAccountId);
    EasyAccount bank = account(bankAccountId);
    requirePositive(amount);
    return journalService.postLegs(business, requireDate(occurredOn), null, value(memo),
        List.of(new Leg(payable, amount, 0L), new Leg(bank, 0L, amount)));
  }

  /** 은행 직접 비용: Dr 비용 / Cr 지정 은행계정. */
  @Transactional
  public EasyJournalEntry postBankExpense(Long businessId, LocalDate occurredOn,
      Long expenseAccountId, Long bankAccountId, long amount,
      String counterparty, String description,
      EasyTaxClassification taxClassification, String sourceRef) {
    EasyBusiness business = business(businessId);
    EasyAccount expense = account(expenseAccountId);
    EasyAccount bank = account(bankAccountId);
    requirePositive(amount);
    EasyTransaction txn = txnRepository.save(EasyTransaction.builder()
        .business(business).occurredOn(requireDate(occurredOn))
        .direction(EasyTxnDirection.EXPENSE).amount(amount)
        .category(expense.getName()).counterparty(value(counterparty))
        .description(value(description)).payMethod("계좌이체")
        .account(expense).sourceKind("REAL_DOUBLE_ENTRY").sourceRef(sourceRef)
        .taxClassification(taxClassification == null
            ? EasyTaxClassification.REVIEW_REQUIRED : taxClassification)
        .build());
    return journalService.postLegs(business, txn.getOccurredOn(), txn, txn.getDescription(),
        List.of(new Leg(expense, amount, 0L), new Leg(bank, 0L, amount)));
  }

  /** 대출금 입금: Dr 은행 / Cr 차입금 (세무상 NOT_INCOME — P&L 거래 없음). */
  @Transactional
  public EasyJournalEntry postLoanDisbursement(Long businessId, LocalDate occurredOn,
      Long bankAccountId, Long loanAccountId, long amount, String memo) {
    EasyBusiness business = business(businessId);
    EasyAccount bank = account(bankAccountId);
    EasyAccount loan = account(loanAccountId);
    requirePositive(amount);
    return journalService.postLegs(business, requireDate(occurredOn), null, value(memo),
        List.of(new Leg(bank, amount, 0L), new Leg(loan, 0L, amount)));
  }

  /** 대출원금 상환: Dr 차입금 / Cr 은행 (세무상 NOT_EXPENSE — P&L 거래 없음). */
  @Transactional
  public EasyJournalEntry postLoanPrincipalRepayment(Long businessId, LocalDate occurredOn,
      Long loanAccountId, Long bankAccountId, long amount, String memo) {
    EasyBusiness business = business(businessId);
    EasyAccount loan = account(loanAccountId);
    EasyAccount bank = account(bankAccountId);
    requirePositive(amount);
    return journalService.postLegs(business, requireDate(occurredOn), null, value(memo),
        List.of(new Leg(loan, amount, 0L), new Leg(bank, 0L, amount)));
  }

  /** 대출이자: Dr 이자비용 / Cr 은행 (세무상 EXPENSE_DEDUCTIBLE_CANDIDATE). */
  @Transactional
  public EasyJournalEntry postLoanInterest(Long businessId, LocalDate occurredOn,
      Long interestAccountId, Long bankAccountId, long amount,
      String description, String sourceRef) {
    EasyBusiness business = business(businessId);
    EasyAccount interest = account(interestAccountId);
    EasyAccount bank = account(bankAccountId);
    requirePositive(amount);
    EasyTransaction txn = txnRepository.save(EasyTransaction.builder()
        .business(business).occurredOn(requireDate(occurredOn))
        .direction(EasyTxnDirection.EXPENSE).amount(amount)
        .category(interest.getName()).description(value(description)).payMethod("계좌이체")
        .account(interest).sourceKind("REAL_DOUBLE_ENTRY").sourceRef(sourceRef)
        .taxClassification(EasyTaxClassification.EXPENSE_DEDUCTIBLE_CANDIDATE)
        .build());
    return journalService.postLegs(business, txn.getOccurredOn(), txn, txn.getDescription(),
        List.of(new Leg(interest, amount, 0L), new Leg(bank, 0L, amount)));
  }

  /**
   * 계좌간 이체: Dr 목적지 은행 / Cr 출발지 은행.
   * 매출·필요경비로 인식하지 않는다 (P&L 거래 없음).
   */
  @Transactional
  public EasyJournalEntry postBankTransfer(Long businessId, LocalDate occurredOn,
      Long fromBankAccountId, Long toBankAccountId, long amount, String memo) {
    EasyBusiness business = business(businessId);
    EasyAccount from = account(fromBankAccountId);
    EasyAccount to = account(toBankAccountId);
    requirePositive(amount);
    if (from.getAccountId().equals(to.getAccountId())) {
      throw new IllegalArgumentException("출발지와 목적지 계좌가 같습니다.");
    }
    return journalService.postLegs(business, requireDate(occurredOn), null, value(memo),
        List.of(new Leg(to, amount, 0L), new Leg(from, 0L, amount)));
  }

  private EasyBusiness business(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("사업체를 선택해 주세요.");
    }
    return businessRepository.findById(businessId)
        .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다."));
  }

  private EasyAccount account(Long accountId) {
    if (accountId == null) {
      throw new IllegalArgumentException("계정을 선택해 주세요.");
    }
    EasyAccount account = accountRepository.findById(accountId)
        .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
    if (!account.isActive()) {
      throw new IllegalArgumentException("비활성 계정입니다: " + account.getCode());
    }
    return account;
  }

  private static LocalDate requireDate(LocalDate date) {
    if (date == null) {
      throw new IllegalArgumentException("거래일을 입력해 주세요.");
    }
    return date;
  }

  private static void requirePositive(long amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("금액은 0보다 커야 합니다.");
    }
  }

  private static String value(String text) {
    return text == null ? "" : text;
  }
}
