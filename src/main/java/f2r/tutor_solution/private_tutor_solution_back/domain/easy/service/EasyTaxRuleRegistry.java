package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Easy 세법 규칙 등록소 (Phase Tax-1).
 *
 * 모든 규칙은 taxYear + ruleType + value + source + verifiedAt +
 * verificationStatus를 갖는다. 공식 확인되지 않은 규칙으로는 확정세액을
 * 계산하지 않는다 — 계산 게이트가 UNVERIFIED 규칙 사용을 차단한다.
 *
 * 규칙 수치의 구조적 참고: OpenAccountants kr-income-tax Skill v2.0
 * (tax_year 2025, source-cited draft, 미검증). 코드는 복사하지 않았으며,
 * 수치도 검증 전까지 UNVERIFIED로만 보관한다. 2026 귀속 규칙은 MISSING이다.
 * 신고 시점에는 국세청/홈택스 공식자료로 재확인해야 한다.
 */
@Service
@RequiredArgsConstructor
public class EasyTaxRuleRegistry {

  /** 규칙 검증 상태 — UNVERIFIED 규칙으로는 확정세액을 계산하지 않는다. */
  public enum VerificationStatus {
    VERIFIED,
    UNVERIFIED,
    PENDING
  }

  /** 규칙 종류. */
  public enum RuleType {
    FILING_DEADLINE_NEXT_MAY,
    LOCAL_INCOME_SURTAX_RATE,
    WITHHOLDING_CREDIT_RATE,
    TAX_BASE_ROUND_DOWN,
    ESTIMATED_TAX_THRESHOLD,
    NATIONAL_RATE_BRACKET
  }

  /** 세법 규칙 — 값의 출처·검증시점·상태를 함께 보관한다. */
  public record TaxRule(int taxYear, RuleType ruleType, String value, String source,
      LocalDate verifiedAt, VerificationStatus status) {
  }

  /** 세율 구간 — NATIONAL_RATE_BRACKET 규칙의 값 형태. */
  public record RateBracket(long fromInclusive, Long toInclusive, int ratePermille,
      long deduction) {
  }

  private List<TaxRule> defaults() {
    String skill = "OpenAccountants kr-income-tax Skill v2.0 (tax_year 2025, "
        + "source-cited draft — filing-time NTS confirmation required)";
    List<TaxRule> rules = new ArrayList<>();
    rules.add(new TaxRule(2025, RuleType.FILING_DEADLINE_NEXT_MAY, "05-31",
        skill, null, VerificationStatus.UNVERIFIED));
    rules.add(new TaxRule(2025, RuleType.LOCAL_INCOME_SURTAX_RATE, "10%",
        skill, null, VerificationStatus.UNVERIFIED));
    rules.add(new TaxRule(2025, RuleType.WITHHOLDING_CREDIT_RATE, "3.3%",
        skill, null, VerificationStatus.UNVERIFIED));
    rules.add(new TaxRule(2025, RuleType.TAX_BASE_ROUND_DOWN, "10000",
        skill, null, VerificationStatus.UNVERIFIED));
    rules.add(new TaxRule(2025, RuleType.ESTIMATED_TAX_THRESHOLD, "300000",
        skill, null, VerificationStatus.UNVERIFIED));
    // 2025 누진 구간 (6%~45%) — 구조 참고용, 미검증.
    rules.add(new TaxRule(2025, RuleType.NATIONAL_RATE_BRACKET,
        "14000000:6%:0|50000000:15%:1260000|88000000:24%:5760000|"
            + "150000000:35%:15440000|300000000:38%:19940000|500000000:40%:25940000|"
            + "1000000000:42%:35940000|INF:45%:65940000",
        skill, null, VerificationStatus.UNVERIFIED));
    return rules;
  }

  /** 귀속연도 규칙 전체 — DB 없이 코드 등록소 (신고 시점 공식 확인 전). */
  public List<TaxRule> rulesForYear(int taxYear) {
    List<TaxRule> result = new ArrayList<>();
    for (TaxRule rule : defaults()) {
      if (rule.taxYear() == taxYear) {
        result.add(rule);
      }
    }
    return result;
  }

  /** 확정세액 계산에 필요한 검증済み 규칙 조회 — 없으면 empty (차단용). */
  public java.util.Optional<TaxRule> verifiedRule(int taxYear, RuleType ruleType) {
    for (TaxRule rule : defaults()) {
      if (rule.taxYear() == taxYear && rule.ruleType() == ruleType
          && rule.status() == VerificationStatus.VERIFIED) {
        return java.util.Optional.of(rule);
      }
    }
    return java.util.Optional.empty();
  }
}
