package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessTaxComputation.BusinessTaxIncome;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTaxRuleRegistry.RuleType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy 개인 종합소득 집계 (Phase Tax-1).
 *
 * 학원 사업소득 + 출판사 사업소득을 개인 단계에서 합산한다.
 * 사업장 장부를 DB에서 합치지 않는다 (합산은 이 계산에서만).
 * PRIMARY BUSINESS는 ACADEMY로 표시한다.
 *
 * 사업소득 합산까지만 확정한다. 그 이후 (소득공제·과세표준·세율·세액공제·
 * 기납부·결정·납부·지방소득세)는 검증済み 규칙과 신고자료가 갖춰지기 전까지
 * REVIEW_REQUIRED로 둔다 — 미검증 규칙 사용을 차단한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyPersonalTaxAggregation {

  private final EasyTaxRuleRegistry ruleRegistry;

  /** 개인 집계 — 합산 사업소득까지 확정, 세액 이후는 상태만. */
  public record PersonalAggregation(String taxpayerLabel, int fiscalYear,
      BusinessTaxIncome primary, List<BusinessTaxIncome> incomes, long combinedBusinessIncome,
      String primaryBusinessCode, AggregationStatus status, List<String> missing) {
  }

  public enum AggregationStatus {
    READY,
    REVIEW_REQUIRED,
    MISSING_DATA
  }

  /**
   * 개인 집계 — 납세자 정보와 주사업장은 데이터로 받는다 (실명·"ACADEMY" hard-code 금지).
   * 주사업장 코드는 호출자가 신고 구조 데이터로 지정하고, 엔진은 해당 사업장이
   * 집계 대상에 존재하는지만 검증한다. 검증済み 세율 규칙이 없으면 세액 계산으로
   * 넘어가지 않고 REVIEW_REQUIRED.
   */
  public PersonalAggregation aggregate(String taxpayerLabel, int fiscalYear,
      String primaryBusinessCode, List<BusinessTaxIncome> incomes) {
    if (primaryBusinessCode == null || primaryBusinessCode.isBlank()) {
      throw new IllegalArgumentException("주사업장 코드를 입력해 주세요.");
    }
    if (incomes == null || incomes.isEmpty()) {
      throw new IllegalArgumentException("사업장 사업소득이 없습니다.");
    }
    BusinessTaxIncome primary = null;
    for (BusinessTaxIncome income : incomes) {
      if (primaryBusinessCode.equalsIgnoreCase(income.businessCode())) {
        primary = income;
      }
    }
    if (primary == null) {
      throw new IllegalArgumentException(
          "주사업장(" + primaryBusinessCode + ")이 집계 대상에 없습니다.");
    }
    long combined = 0L;
    List<String> missing = new ArrayList<>();
    for (BusinessTaxIncome income : incomes) {
      combined += income.businessIncome();
      missing.addAll(income.missing());
    }
    if (ruleRegistry.verifiedRule(fiscalYear, RuleType.NATIONAL_RATE_BRACKET).isEmpty()) {
      missing.add("TAX_RULE_PENDING: " + fiscalYear + " 귀속 검증済み 세율 없음 — "
          + "신고 시점 국세청/홈택스 공식 확인 필요");
    }
    missing.add("MISSING_DATA: 소득공제 자료 없음");
    missing.add("MISSING_DATA: 세액공제/감면 자료 없음");
    missing.add("MISSING_DATA: 기납부/원천징수 자료 없음");
    missing.add("MISSING_DATA: 실제 신고서 대조값 없음");
    AggregationStatus status =
        missing.isEmpty() ? AggregationStatus.READY : AggregationStatus.REVIEW_REQUIRED;
    return new PersonalAggregation(taxpayerLabel, fiscalYear, primary, incomes,
        combined, primary.businessCode(), status, missing);
  }
}
