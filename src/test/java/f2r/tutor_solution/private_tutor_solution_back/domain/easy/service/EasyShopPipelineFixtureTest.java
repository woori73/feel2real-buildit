package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest.EasyShopRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyRulePatternType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EasyShop 파이프라인 익명화 fixture — 실데이터 형상 그대로, 값은 가명.
 * 원본 실데이터는 PII/금융정보 포함으로 commit하지 않는다.
 */
@DisplayName("EasyShop 파이프라인 fixture 테스트")
class EasyShopPipelineFixtureTest {

  private EasyShopPipelineHarness harness;

  @BeforeEach
  void setUp() {
    harness = new EasyShopPipelineHarness();
    harness.seedBusiness(1L, "ACADEMY", "학원");
    harness.seedBusiness(2L, "PUBLISHING", "출판사");
    harness.seedAccount(10L, "INCOME_TUITION", "수강료", EasyAccountDirection.INCOME);
    harness.seedAccount(11L, "EXPENSE_RENT", "임차료", EasyAccountDirection.EXPENSE);
    harness.seedAccount(12L, "SETTLEMENT", "자금정산", EasyAccountDirection.ASSET);
    harness.seedMapping("임차료(월세)", 11L);
    // confirm()은 matched READY만 확정하므로 P&L 검증 대상 행을 매칭한다.
    // 매출은 수강료로 매칭한다 (운영 SALES 정책과 동일).
    harness.seedMapping("EASYSHOP:매출", 10L);
    // 환불 역분개(-330,000)는 장부에서 비용 차감되나 분개(비용 합계)에는 네팅되지
    // 않도록 자금정산으로 매칭한다 — 기존 journalExpense=3,300,000 기대 유지.
    harness.seedMapping("전화/소모품", 12L);
    // 자금 이동은 외부계정이 없어 규칙으로 매칭한다 (P&L 제외, 원천 보존 검증용).
    harness.seedRule(EasyRulePatternType.DESCRIPTION_REGEX, "계좌에서 현금으로 출금", 12L);
  }

  private EasyShopRow sales(String date, String total) {
    return new EasyShopRow("sales", date, "현대카드", null, null, null, null, null, null,
        null, "카드(1)", null, null, null, total, "0", "신용(체크)카드", null,
        null, null, null, "ACADEMY", null, null, null);
  }

  private EasyShopRow expense(String date, String total, String category, String vendor) {
    return new EasyShopRow("expense", date, vendor, null, null, null, null, null, null,
        category, "품목", null, null, null, total, null, null, "카드",
        null, null, null, "ACADEMY", null, null, null);
  }

  private EasyShopRow funds(String date, String deposit, String withdrawal, String memo) {
    return new EasyShopRow("deposit_withdrawal", date, null, "은행 0000000000", memo,
        "계좌에서 현금으로 출금", deposit, withdrawal, "0", null, null, null, null, null,
        null, null, null, null, null, null, null, "ACADEMY", null, null, null);
  }

  @Test
  @DisplayName("fixture 파이프라인 — 매출·비용·환불·자금·재입력·중복·리뷰")
  void fixture_pipeline() {
    List<EasyShopRow> rows = List.of(
        sales("2026-03-05", "540,000"),
        expense("2026-03-06", "3,300,000", "임차료(월세)", "건물주"),
        expense("2026-07-06", "-330,000", "전화/소모품", "가상상점"),
        funds("2026-06-13", "0", "967,000", "가상인명"),
        funds("2026-06-14", "537,840", "0", "입금메모"));

    List<EasyImportBatchResponse> staged =
        harness.shopService.stage(new EasyShopImportRequest(1L, "fixture.csv", rows));
    assertThat(staged).hasSize(2);
    for (EasyImportBatchResponse batch : staged) {
      harness.importService.confirm(batch.batchId(), null);
    }

    // 매출 540,000 / 비용 3,300,000-330,000(환불 차감) / 자금 P&L 제외
    var pl = harness.reportService.yearPl(1L, 2026);
    assertThat(pl.income()).isEqualTo(540000L);
    assertThat(pl.expense()).isEqualTo(2970000L);
    assertThat(pl.fundsIn()).isEqualTo(537840L);
    assertThat(pl.fundsOut()).isEqualTo(967000L);
    assertThat(pl.dataThrough().toString()).isEqualTo("2026-07-06");
    assertThat(pl.journalBalanced()).isTrue();
    // 임차료·매출·역분개·자금 모두 매칭 확정 → 분개는 임차료/매출분, 자금은 분개 제외.
    // 역분개 행은 자금정산 매칭이라 장부 비용만 차감하고 비용 분개 합계는 유지된다.
    assertThat(pl.journalExpense()).isEqualTo(3300000L);
    assertThat(pl.matchesLedger()).isFalse();

    // 재입력 — 모두 DUPLICATE, 확정할 READY 없음, 장부 증가 없음
    long before = harness.txnCount();
    List<EasyImportBatchResponse> restaged =
        harness.shopService.stage(new EasyShopImportRequest(1L, "fixture.csv", rows));
    for (EasyImportBatchResponse batch : restaged) {
      assertThat(batch.rows()).allMatch(row -> "DUPLICATE".equals(row.status()));
      boolean ready = batch.rows().stream().anyMatch(row -> "READY".equals(row.status()));
      if (ready) {
        harness.importService.confirm(batch.batchId(), null);
      }
    }
    assertThat(harness.txnCount()).isEqualTo(before);

    // 대응 비용 없는 967,000 자금 유출 → MISSING_EXPENSE (임의 분류 없음)
    var summary = harness.auditService.rebuild(1L);
    assertThat(summary.missingExpense()).isEqualTo(1L);
    var items = harness.auditService.listReviews(1L, "MISSING_EXPENSE", "OPEN");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).kind()).isEqualTo(EasyReviewKind.MISSING_EXPENSE.name());
    assertThat(items.get(0).detail()).contains("임의로 분류하지 않았습니다");

    // Reversal expense는 MISSING_EXPENSE 탐지에서 제외되어야 함
    var rowsWithReversal = List.of(
        new EasyShopRow("expense", "2026-06-14", "가상거래처", null, null, null, null, null, null,
            "임차료(월세)", "품목", "100000", "10000", "0", "110000", null, null, "카드",
            null, null, null, "ACADEMY", null, null, null),
        new EasyShopRow("expense", "2026-06-14", "가상거래처", null, null, null, null, null, null,
            "임차료(월세)", "품목", "-110000", "-10000", "0", "-120000", null, null, "카드",
            null, null, null, "ACADEMY", null, null, null));
    var stagedReversal = harness.shopService.stage(new EasyShopImportRequest(1L, "fixture_reversal.csv", rowsWithReversal));
    for (var batch : stagedReversal) {
      harness.importService.confirm(batch.batchId(), null);
    }
    harness.auditService.rebuild(1L);
    // Reversal expense는 MISSING_EXPENSE에 포함되지 않아야 함 (여전히 1건만)
    summary = harness.auditService.reviewSummary(1L);
    assertThat(summary.missingExpense()).isEqualTo(1L);
  }
}
