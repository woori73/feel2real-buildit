package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalEntryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyJournalLineRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessTaxComputation.BusinessTaxIncome;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessTaxComputation.ReviewState;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessTaxComputation.TaxAdjustment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessTaxComputation.TypedAdjustment;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessTaxComputation.AdjustmentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.TrialBalanceReport;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService.TrialBalanceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyPersonalTaxAggregation.AggregationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyPersonalTaxAggregation.PersonalAggregation;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTaxRuleRegistry.RuleType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTaxReplayService.ReplayResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTaxReplayService.ReplayStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Easy 소득세 에이전트 테스트")
class EasyIncomeTaxEngineTest {

  @Mock EasyLedgerInquiryService inquiryService;
  @Mock EasyJournalEntryRepository entryRepository;
  @Mock EasyJournalLineRepository lineRepository;

  private EasyTaxRuleRegistry ruleRegistry;
  private EasyBusinessTaxComputation computation;
  private EasyPersonalTaxAggregation aggregation;
  private EasyTaxReplayService replayService;

  @BeforeEach
  void setUp() {
    ruleRegistry = new EasyTaxRuleRegistry();
    computation = new EasyBusinessTaxComputation(inquiryService);
    aggregation = new EasyPersonalTaxAggregation(ruleRegistry);
    replayService = new EasyTaxReplayService();
  }

  private TrialBalanceReport trial(List<TrialBalanceRow> rows) {
    long debit = rows.stream().mapToLong(TrialBalanceRow::totalDebit).sum();
    long credit = rows.stream().mapToLong(TrialBalanceRow::totalCredit).sum();
    return new TrialBalanceReport(rows, debit, credit, debit - credit,
        debit == credit, List.of());
  }

  private TrialBalanceRow row(String code, EasyAccountDirection direction,
      long debit, long credit) {
    long net = direction == EasyAccountDirection.INCOME
        || direction == EasyAccountDirection.LIABILITY
        || direction == EasyAccountDirection.EQUITY ? credit - debit : debit - credit;
    return new TrialBalanceRow(code, code, direction, debit, credit, net);
  }

  private void stubAcademyPublisher() {
    when(inquiryService.trialBalance(any(), any(), any())).thenAnswer(invocation -> {
      Long businessId = invocation.getArgument(0);
      if (Long.valueOf(1L).equals(businessId)) {
        return trial(List.of(
            row("INCOME_TUITION", EasyAccountDirection.INCOME, 0L, 60856000L),
            row("EXPENSE_SUPPLIES", EasyAccountDirection.EXPENSE, 30000L, 0L)));
      }
      return trial(List.of(
          row("EXPENSE_SUPPLIES", EasyAccountDirection.EXPENSE, 50000L, 0L)));
    });
  }

  @Test
  @DisplayName("사업장별 장부 분리 + 학원/출판사 사업소득")
  void businessSeparation() {
    stubAcademyPublisher();

    BusinessTaxIncome academy =
        computation.compute(1L, "ACADEMY", 2026, List.of());
    BusinessTaxIncome publishing =
        computation.compute(2L, "PUBLISHING", 2026, List.of());

    assertThat(academy.bookRevenue()).isEqualTo(60856000L);
    assertThat(academy.bookExpense()).isEqualTo(30000L);
    assertThat(academy.businessIncome()).isEqualTo(60826000L);
    assertThat(publishing.bookRevenue()).isZero();
    assertThat(publishing.businessIncome()).isEqualTo(-50000L);
  }

  @Test
  @DisplayName("세무조정이 원장 transaction/journal을 변경하지 않음")
  void adjustment_doesNotMutateLedger() {
    stubAcademyPublisher();

    TaxAdjustment adj = new TaxAdjustment(7L, 9L, 10000L,
        ReviewState.NON_DEDUCTIBLE, "개인성 의심", "admin-review");
    BusinessTaxIncome result = computation.compute(1L, "ACADEMY", 2026,
        List.of(new TypedAdjustment(AdjustmentKind.REMOVE_FROM_EXPENSE, adj)));

    assertThat(result.deductibleExpense()).isEqualTo(20000L);
    assertThat(result.businessIncome()).isEqualTo(60836000L);
    verify(entryRepository, never()).save(any());
    verify(lineRepository, never()).save(any());
  }

  @Test
  @DisplayName("학원 primary + 합산 (데이터 지정)")
  void primaryAndCombined() {
    stubAcademyPublisher();
    BusinessTaxIncome academy = computation.compute(1L, "ACADEMY", 2026, List.of());
    BusinessTaxIncome publishing =
        computation.compute(2L, "PUBLISHING", 2026, List.of());

    PersonalAggregation result = aggregation.aggregate("taxpayer-data", 2026,
        "ACADEMY", List.of(academy, publishing));

    assertThat(result.primaryBusinessCode()).isEqualTo("ACADEMY");
    assertThat(result.primary().businessCode()).isEqualTo("ACADEMY");
    assertThat(result.combinedBusinessIncome())
        .isEqualTo(60826000L - 50000L);
    assertThat(result.status()).isEqualTo(AggregationStatus.REVIEW_REQUIRED);
    assertThat(result.missing()).anySatisfy(note ->
        assertThat(note).contains("TAX_RULE_PENDING"));
  }

  @Test
  @DisplayName("주사업장 코드는 데이터로 결정 (집계 대상에 없으면 거부)")
  void unknownPrimary_rejected() {
    stubAcademyPublisher();
    BusinessTaxIncome academy = computation.compute(1L, "ACADEMY", 2026, List.of());

    assertThatThrownBy(() -> aggregation.aggregate("taxpayer-data", 2026,
        "UNKNOWN", List.of(academy)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("집계 대상에 없습니다");
  }

  @Test
  @DisplayName("엔진에 ACADEMY 문자열 hard-code 없음 (출판사 primary도 구조상 가능)")
  void primaryNotHardcoded() {
    stubAcademyPublisher();
    BusinessTaxIncome academy = computation.compute(1L, "ACADEMY", 2026, List.of());
    BusinessTaxIncome publishing =
        computation.compute(2L, "PUBLISHING", 2026, List.of());

    PersonalAggregation result = aggregation.aggregate("taxpayer-data", 2026,
        "PUBLISHING", List.of(academy, publishing));

    assertThat(result.primaryBusinessCode()).isEqualTo("PUBLISHING");
    assertThat(result.combinedBusinessIncome())
        .isEqualTo(60826000L - 50000L);
  }

  @Test
  @DisplayName("귀속연도별 규칙 선택 + 미검증 차단")
  void ruleSelection_blocksUnverified() {
    assertThat(ruleRegistry.rulesForYear(2025)).isNotEmpty();
    assertThat(ruleRegistry.rulesForYear(2026)).isEmpty();
    assertThat(ruleRegistry.verifiedRule(2025, RuleType.NATIONAL_RATE_BRACKET))
        .isEmpty();
    assertThat(ruleRegistry.verifiedRule(2026, RuleType.NATIONAL_RATE_BRACKET))
        .isEmpty();
    assertThat(ruleRegistry.rulesForYear(2025))
        .allSatisfy(rule -> assertThat(rule.source()).contains("OpenAccountants"));
  }

  @Test
  @DisplayName("장부 없음 → REVIEW_REQUIRED (추측 계산 금지)")
  void missingLedger_reviewRequired() {
    when(inquiryService.trialBalance(any(), any(), any()))
        .thenReturn(trial(List.of()));

    BusinessTaxIncome result = computation.compute(1L, "ACADEMY", 2026, List.of());

    assertThat(result.overallState()).isEqualTo(ReviewState.REVIEW_REQUIRED);
    assertThat(result.missing()).anySatisfy(note ->
        assertThat(note).contains("MISSING_INPUT"));
  }

  @Test
  @DisplayName("REVIEW 조정 → REVIEW_REQUIRED 전파")
  void reviewAdjustment_propagates() {
    stubAcademyPublisher();

    TaxAdjustment adj = new TaxAdjustment(7L, 9L, 5000L,
        ReviewState.REVIEW_REQUIRED, "업무관련성 불확실", "admin-review");
    BusinessTaxIncome result = computation.compute(1L, "ACADEMY", 2026,
        List.of(new TypedAdjustment(AdjustmentKind.REMOVE_FROM_EXPENSE, adj)));

    assertThat(result.overallState()).isEqualTo(ReviewState.REVIEW_REQUIRED);
    // 원장 수치는 그대로, 조정만 별도
    assertThat(result.bookExpense()).isEqualTo(30000L);
  }

  @Test
  @DisplayName("Historical Replay — 신고값 없으면 MISSING_INPUT")
  void replay_missingInput() {
    ReplayResult result = replayService.replay(2025,
        Map.of("academyRevenue", 60856000L),
        Map.of());

    assertThat(result.overall()).isEqualTo(ReplayStatus.MISSING_INPUT);
    assertThat(result.missing()).isNotEmpty();
  }

  @Test
  @DisplayName("Historical Replay — 일치/불일치 분류 (합성 표기값)")
  void replay_matchAndMismatch() {
    ReplayResult matched = replayService.replay(2025,
        Map.of("SYNTHETIC_academyRevenue", 100L),
        Map.of("SYNTHETIC_academyRevenue", 100L));
    assertThat(matched.overall()).isEqualTo(ReplayStatus.MATCHED);

    ReplayResult mismatched = replayService.replay(2025,
        Map.of("SYNTHETIC_academyRevenue", 100L),
        Map.of("SYNTHETIC_academyRevenue", 200L));
    assertThat(mismatched.overall()).isEqualTo(ReplayStatus.SOURCE_MISMATCH);
    assertThat(mismatched.lines().get(0).difference()).isEqualTo(-100L);
  }

  @Test
  @DisplayName("미검증 규칙으로 확정세액 계산 불가")
  void noDecidedTax_withoutVerifiedRules() {
    stubAcademyPublisher();
    BusinessTaxIncome academy = computation.compute(1L, "ACADEMY", 2026, List.of());
    BusinessTaxIncome publishing =
        computation.compute(2L, "PUBLISHING", 2026, List.of());
    PersonalAggregation result = aggregation.aggregate("taxpayer-data", 2026,
        "ACADEMY", List.of(academy, publishing));

    // 합산 사업소득까지만 확정, 세액 단계는 차단
    assertThat(result.combinedBusinessIncome()).isEqualTo(60826000L - 50000L);
    assertThat(result.status()).isNotEqualTo(AggregationStatus.READY);
  }
}
