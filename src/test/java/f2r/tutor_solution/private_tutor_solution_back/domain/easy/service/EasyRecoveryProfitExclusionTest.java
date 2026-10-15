package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest.EasyImportRowInput;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 복구 적재 → 장부/손익/사업장 분리 end-to-end (하네스, DB 없음).
 * given: 은행 수집형 배치(대출·이체·미분류·출판사·매핑 확정 혼합).
 * when: stage → confirm.
 * then:
 * - 장부 목록에는 전부 보인다 (HOLD 0건).
 * - 손익 합계에서 대출·이체는 빠지고 REVIEW는 포함된다.
 * - 학원/출판사 스코프가 분리된다.
 */
@DisplayName("복구 적재 손익·사업장 분리 테스트")
class EasyRecoveryProfitExclusionTest {

  private EasyShopPipelineHarness harness;
  private EasyLedgerService ledgerService;

  @BeforeEach
  void setUp() {
    harness = new EasyShopPipelineHarness();
    harness.seedBusiness(1L, "ACADEMY", "학원");
    harness.seedBusiness(2L, "PUBLISHING", "출판사");
    harness.seedAccount(10L, "INCOME_TUITION", "수강료", EasyAccountDirection.INCOME);
    harness.seedAccount(11L, "EXPENSE_RENT", "임차료", EasyAccountDirection.EXPENSE);
    harness.seedAccount(12L, "SETTLEMENT", "자금정산", EasyAccountDirection.ASSET);
    harness.seedMapping("임차료(월세)", 11L);
    harness.seedMapping("EASYSHOP:매출", 10L);
    ledgerService = new EasyLedgerService(
        harness.txnRepository, harness.businessRepository, harness.accountRepository,
        harness.paymentRepository, harness.studentRepository,
        harness.journalService, harness.correctionService);
  }

  private EasyImportRowInput bankRow(String date, String direction, Long amount,
      String counterparty, String description, String rawData, String externalAccount) {
    return new EasyImportRowInput(date, direction, amount, counterparty, description,
        "15:21:07", null, null, null, rawData, externalAccount);
  }

  @Test
  @DisplayName("대출·이체 제외 + REVIEW 포함 + 사업장 분리")
  void recovery_postsAllSeparatesProfitAndBusiness() {
    // given: 2026-09-14 이후 누락형 혼합 배치 (은행 수집 형상)
    List<EasyImportRowInput> rows = List.of(
        bankRow("2026-09-18", "EXPENSE", 100000L, "상점", "물품", null, null),
        bankRow("2026-09-19", "INCOME", 29000L, "고객", "입금", null, null),
        bankRow("2026-09-20", "INCOME", 30000000L, "은행", "대출입금",
            "{\"primary_disposition\": \"LOAN_INFLOW\"}", null),
        bankRow("2026-09-21", "EXPENSE", 500000L, "내계좌", "이체",
            "{\"primary_disposition\": \"INTER_ACCOUNT_TRANSFER\"}", null),
        bankRow("2026-09-21", "EXPENSE", 3300000L, "건물주", "월세", null, "임차료(월세)"),
        bankRow("2026-09-24", "EXPENSE", 990000L, "출판사건물주", "출판사월세",
            "{\"primary_disposition\": \"PUBLISHING_RENT_990K\","
                + " \"business\": \"PUBLISHING_CONFIRMED\"}", null));

    // when
    EasyImportBatchResponse staged = harness.importService.stage(
        new EasyImportBatchRequest(1L, "bank_import_20260927", "BANK_CSV", rows));
    assertThat(staged.rows()).allMatch(row -> "READY".equals(row.status()));
    EasyImportBatchResponse confirmed = harness.importService.confirm(staged.batchId(), null);

    // then 1: 전건 장부 적재 (HOLD 0)
    assertThat(confirmed.rows()).allMatch(row -> "IMPORTED".equals(row.status()));
    assertThat(harness.txnCount()).isEqualTo(6);

    // then 2: 학원 9월 손익 — 대출 3000만 제외, 이체 50만 제외, REVIEW 포함
    var summary = ledgerService.summary(1L, YearMonth.of(2026, 9));
    assertThat(summary.income()).isEqualTo(29000L);
    assertThat(summary.expense()).isEqualTo(100000L + 3300000L);

    // then 3: 출판사 분리 — 출판사 99만만, 학원 목록에 출판사 행 없음
    var publishing = ledgerService.summary(2L, YearMonth.of(2026, 9));
    assertThat(publishing.income()).isZero();
    assertThat(publishing.expense()).isEqualTo(990000L);

    List<EasyTxnResponse> academyTxns = ledgerService.listTxns(
        1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null);
    assertThat(academyTxns).hasSize(5);
    assertThat(academyTxns).allMatch(txn -> txn.businessId() == 1L);
    List<EasyTxnResponse> publishingTxns = ledgerService.listTxns(
        2L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null);
    assertThat(publishingTxns).hasSize(1);
    assertThat(publishingTxns.get(0).businessName()).isEqualTo("출판사");

    // then 4: 연 리포트도 같은 규칙 (대출 제외·REVIEW 포함·분개 균형)
    var pl = harness.reportService.yearPl(1L, 2026);
    assertThat(pl.income()).isEqualTo(29000L);
    assertThat(pl.expense()).isEqualTo(3400000L);
    assertThat(pl.journalBalanced()).isTrue();

    // then 5: REVIEW 행은 분류상태가 노출된다
    List<EasyTxnResponse> reviews = academyTxns.stream()
        .filter(txn -> "미분류".equals(txn.category())).toList();
    assertThat(reviews).hasSize(2);
    assertThat(reviews).allMatch(txn -> "NEEDS_REVIEW".equals(txn.evidenceReview()));
    assertThat(reviews).allMatch(txn -> "REVIEW_REQUIRED".equals(txn.taxClassification()));
    List<EasyTxnResponse> loans = academyTxns.stream()
        .filter(txn -> "차입금(검토)".equals(txn.category())).toList();
    assertThat(loans).hasSize(1);
    assertThat(loans.get(0).taxClassification()).isEqualTo("NOT_INCOME");
    assertThat(loans.get(0).direction()).isEqualTo(EasyTxnDirection.INCOME.name());
  }

  @Test
  @DisplayName("재입력은 DUPLICATE로 확정 제외된다 (이중 적재 없음)")
  void recovery_reimportIsDuplicate() {
    // given: 1건 적재
    List<EasyImportRowInput> rows = List.of(
        bankRow("2026-09-18", "EXPENSE", 100000L, "상점", "물품", null, null));
    EasyImportBatchResponse staged = harness.importService.stage(
        new EasyImportBatchRequest(1L, "bank.csv", "BANK_CSV", rows));
    harness.importService.confirm(staged.batchId(), null);
    assertThat(harness.txnCount()).isEqualTo(1);

    // when: 같은 원자료를 새 배치로 재적재 (은행 스크립트 재실행 형상)
    EasyImportBatchResponse restaged = harness.importService.stage(
        new EasyImportBatchRequest(1L, "bank.csv", "BANK_CSV", rows));

    // then: stage 자체는 READY(은행 경로는 shop 중복표시를 안 거친다)이나
    // confirm의 장부 fingerprint 가드가 DUPLICATE로 확정 제외한다
    EasyImportBatchResponse reconfirmed = harness.importService.confirm(restaged.batchId(), null);
    assertThat(harness.txnCount()).isEqualTo(1);
    assertThat(reconfirmed.rows()).allMatch(row -> "DUPLICATE".equals(row.status()));
    assertThat(reconfirmed.confirmedRows()).isZero();
  }
}
