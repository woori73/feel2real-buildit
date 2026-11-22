package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 은행 입금과 청구를 맞춰 보는 순수 계산(DB를 건드리지 않는다).
 *
 * <p>자동 기록 조건은 좁게 잡는다: 입금액이 남은 청구액과 정확히 같고, 적요·보낸 사람에 안내한 숫자 4자리
 * 또는 학생 이름이 있으며, 그 입금에 맞는 청구가 하나뿐이고, 그 청구에 맞는 입금도 하나뿐일 때만.
 * 그 밖에 관련 있어 보이는 입금은 모두 원장 확인(REVIEW)으로 넘긴다. 전혀 관련 없어 보이는 입금은 결과에 넣지 않는다.
 */
public final class TuitionDepositMatcher {

  /** 대조 사유. 앞의 둘만 자동 기록 대상이다. */
  public enum Reason {
    CODE_AMOUNT, NAME_AMOUNT, AMOUNT_MISMATCH, AMOUNT_ONLY, MULTIPLE_BILLS, MULTIPLE_DEPOSITS
  }

  /** 대조 대상 청구. windowFrom~windowTo(포함) 사이 입금만 본다. */
  public record Bill(Long paymentId, long balance, String depositorCode, String studentName,
      LocalDate windowFrom, LocalDate windowTo) {}

  /** 은행 입금 1건. text = 보낸 사람 + 적요. */
  public record Deposit(Long txnId, long amount, LocalDate occurredOn, String text) {}

  /** auto=true면 납부 기록, false면 원장 확인. paymentId는 후보를 하나로 못 정하면 null. */
  public record Decision(Long txnId, Long paymentId, boolean auto, Reason reason) {}

  private TuitionDepositMatcher() {}

  public static List<Decision> decide(List<Bill> bills, List<Deposit> deposits) {
    List<Decision> decisions = new ArrayList<>();
    for (Deposit deposit : deposits) {
      Decision decision = decideOne(bills, deposit);
      if (decision != null) {
        decisions.add(decision);
      }
    }
    // 한 청구에 자동 후보 입금이 둘 이상이면 어느 것이 맞는지 알 수 없으므로 모두 원장 확인으로 돌린다.
    Map<Long, Integer> autoCount = new HashMap<>();
    for (Decision d : decisions) {
      if (d.auto()) {
        autoCount.merge(d.paymentId(), 1, Integer::sum);
      }
    }
    List<Decision> result = new ArrayList<>();
    for (Decision d : decisions) {
      if (d.auto() && autoCount.get(d.paymentId()) > 1) {
        result.add(new Decision(d.txnId(), d.paymentId(), false, Reason.MULTIPLE_DEPOSITS));
      } else {
        result.add(d);
      }
    }
    return result;
  }

  private static Decision decideOne(List<Bill> bills, Deposit deposit) {
    String text = normalize(deposit.text());
    List<Bill> strongCode = new ArrayList<>();
    List<Bill> strongName = new ArrayList<>();
    List<Bill> hintOnly = new ArrayList<>();
    List<Bill> amountOnly = new ArrayList<>();
    for (Bill bill : bills) {
      if (bill.balance() <= 0 || deposit.occurredOn().isBefore(bill.windowFrom())
          || deposit.occurredOn().isAfter(bill.windowTo())) {
        continue;
      }
      boolean code = containsCode(text, bill.depositorCode());
      boolean name = containsName(text, bill.studentName());
      boolean amountEqual = deposit.amount() == bill.balance();
      if (code && amountEqual) {
        strongCode.add(bill);
      } else if (name && amountEqual) {
        strongName.add(bill);
      } else if (code || name) {
        hintOnly.add(bill);
      } else if (amountEqual) {
        amountOnly.add(bill);
      }
    }
    int strong = strongCode.size() + strongName.size();
    if (strong == 1) {
      return strongCode.isEmpty()
          ? new Decision(deposit.txnId(), strongName.get(0).paymentId(), true, Reason.NAME_AMOUNT)
          : new Decision(deposit.txnId(), strongCode.get(0).paymentId(), true, Reason.CODE_AMOUNT);
    }
    if (strong > 1) {
      return new Decision(deposit.txnId(), null, false, Reason.MULTIPLE_BILLS);
    }
    if (!hintOnly.isEmpty()) {
      return hintOnly.size() == 1
          ? new Decision(deposit.txnId(), hintOnly.get(0).paymentId(), false, Reason.AMOUNT_MISMATCH)
          : new Decision(deposit.txnId(), null, false, Reason.MULTIPLE_BILLS);
    }
    if (!amountOnly.isEmpty()) {
      return new Decision(deposit.txnId(),
          amountOnly.size() == 1 ? amountOnly.get(0).paymentId() : null, false, Reason.AMOUNT_ONLY);
    }
    return null;
  }

  /** 숫자 4자리가 다른 숫자에 붙지 않고 따로 적혀 있을 때만 맞는 것으로 본다(금액·계좌 끝자리와 헷갈리지 않게). */
  private static boolean containsCode(String text, String code) {
    if (code == null || code.isBlank()) {
      return false;
    }
    return Pattern.compile("(?<!\\d)" + Pattern.quote(code) + "(?!\\d)").matcher(text).find();
  }

  private static boolean containsName(String text, String studentName) {
    String name = normalize(studentName);
    return name.length() >= 2 && text.contains(name);
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\s+", "")
        .toLowerCase(Locale.ROOT);
  }
}
