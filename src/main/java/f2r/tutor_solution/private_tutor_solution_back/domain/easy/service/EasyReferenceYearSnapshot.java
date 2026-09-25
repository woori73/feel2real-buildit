package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Easy 기준연도 스냅샷 (Phase A3) — 올해 실제 신고에 사용된 EasyShop 자료의
 * repo 내 생존분과 현재 EASY 장부를 비교하는 READ-ONLY 기준점.
 *
 * 수치 출처: {@code EasyShopRealDataVerificationTest}의 SALES_EXPENSE 기대값 상수
 * (18개 CSV의 행수·수입·비용, 파일 본문은 PII 정책으로 repo에 없음).
 * 원본 CSV·신고서 실값은 repo에 없으므로 해당 항목은 비교 대상에서 제외한다
 * (MISSING_SOURCE) — 숫자를 추측해서 채우지 않는다.
 *
 * 본 서비스는 회계를 변경하지 않는다 (DB 쓰기 없음, 불변 맵).
 */
@Service
public class EasyReferenceYearSnapshot {

  /** 기준연도 수치 — key: BUSINESS|YEAR. */
  public record ReferenceFigure(String businessCode, int fiscalYear, int rows,
      long revenue, long expense, String source) {
    public long netIncome() {
      return revenue - expense;
    }
  }

  /** 비교 결과 분류. */
  public enum ComparisonStatus {
    /** 기대 = 실제. */
    MATCH,
    /** 일부 일치 (행수·금액 중 일부만). */
    PARTIAL_MATCH,
    /** 기대 ≠ 실제. */
    MISMATCH,
    /** 기준연도 수치 자체가 repo에 없음. */
    MISSING_SOURCE,
    /** 기준은 있으나 EASY에 해당 자료가 없음. */
    NOT_IMPORTED,
    /** 분류·귀속 미확정. */
    UNCLASSIFIED,
    /** 동일 경제사건의 중복 계상 의심. */
    DUPLICATE_RISK
  }

  /** 비교 결과 — read-only. */
  public record ReferenceComparison(String businessCode, int fiscalYear,
      ComparisonStatus status, Long expectedRevenue, Long expectedExpense,
      Long actualRevenue, Long actualExpense, Long difference, String note) {
  }

  private static final Map<String, ReferenceFigure> FIGURES;

  static {
    Map<String, ReferenceFigure> figures = new LinkedHashMap<>();
    String source = "EasyShopRealDataVerificationTest.SALES_EXPENSE (18 CSV 기대값 상수)";
    figures.put("ACADEMY|2024", new ReferenceFigure("ACADEMY", 2024, 1130, 64598500L,
        63250576L, source));
    figures.put("ACADEMY|2025", new ReferenceFigure("ACADEMY", 2025, 1145, 109603000L,
        96621724L, source));
    figures.put("ACADEMY|2026", new ReferenceFigure("ACADEMY", 2026, 681, 60856000L,
        54129403L, source));
    figures.put("PUBLISHING|2024", new ReferenceFigure("PUBLISHING", 2024, 837, 49642000L,
        49700806L, source));
    figures.put("PUBLISHING|2025", new ReferenceFigure("PUBLISHING", 2025, 566, 38334000L,
        28363111L, source));
    figures.put("PUBLISHING|2026", new ReferenceFigure("PUBLISHING", 2026, 215, 7970000L,
        13242709L, source));
    FIGURES = Collections.unmodifiableMap(figures);
  }

  /** 기준연도 수치 조회 — 없으면 empty (MISSING_SOURCE相当). */
  public java.util.Optional<ReferenceFigure> reference(String businessCode, int fiscalYear) {
    if (businessCode == null) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.ofNullable(FIGURES.get(businessCode + "|" + fiscalYear));
  }

  /** 기준연도 수치 전체 — 불변 뷰 (호출자가 변경 불가). */
  public Map<String, ReferenceFigure> allReferences() {
    return FIGURES;
  }

  /**
   * READ-ONLY 비교 — EASY 장부 수치(actual, null이면 NOT_IMPORTED)와 기준을 대조한다.
   * 본 메서드는 회계 데이터를 조회·수정하지 않는다.
   */
  public ReferenceComparison compare(String businessCode, int fiscalYear,
      Long actualRevenue, Long actualExpense) {
    ReferenceFigure expected = FIGURES.get(businessCode + "|" + fiscalYear);
    if (expected == null) {
      return new ReferenceComparison(businessCode, fiscalYear,
          ComparisonStatus.MISSING_SOURCE, null, null,
          actualRevenue, actualExpense, null, "기준연도 수치가 repo에 없음");
    }
    if (actualRevenue == null || actualExpense == null) {
      return new ReferenceComparison(businessCode, fiscalYear,
          ComparisonStatus.NOT_IMPORTED, expected.revenue(), expected.expense(),
          actualRevenue, actualExpense, null, "EASY에 해당 연도 자료 없음");
    }
    long difference =
        (actualRevenue - expected.revenue()) - (actualExpense - expected.expense());
    ComparisonStatus status = difference == 0
        && actualRevenue.equals(expected.revenue())
        && actualExpense.equals(expected.expense())
        ? ComparisonStatus.MATCH : ComparisonStatus.MISMATCH;
    return new ReferenceComparison(businessCode, fiscalYear, status,
        expected.revenue(), expected.expense(), actualRevenue, actualExpense, difference,
        status == ComparisonStatus.MATCH ? "일치" : "차이 발생 — mass rebuild 없이 원인 추적 필요");
  }
}
