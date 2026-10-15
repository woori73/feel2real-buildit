package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyShopImportRequest.EasyShopRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyYearPlResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyJournalStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EasyShop 실데이터 검증 — 18개 CSV → importer → transaction → journal → report.
 *
 * 원본 CSV는 PII/금융정보(실명·계좌번호·거래처) 포함으로 repository에 commit하지 않는다.
 * 실행 조건: 환경변수 EASYSHOP_DATA_DIR에 18개 CSV가 있어야 하며, 없으면 skip한다.
 * 검증 결과 집계표만 stdout으로 출력한다 (개별 거래내역 출력 없음).
 */
@DisplayName("EasyShop 실데이터 검증 (EASYSHOP_DATA_DIR 필요)")
class EasyShopRealDataVerificationTest {

  record Expectation(int rows, long income, long expense) {
  }

  private static final Map<String, Expectation> SALES_EXPENSE = Map.ofEntries(
      Map.entry("ACADEMY|2024|sales", new Expectation(117, 64598500L, 0L)),
      Map.entry("ACADEMY|2024|expense", new Expectation(1013, 0L, 63250576L)),
      Map.entry("ACADEMY|2025|sales", new Expectation(192, 109603000L, 0L)),
      Map.entry("ACADEMY|2025|expense", new Expectation(953, 0L, 96621724L)),
      Map.entry("ACADEMY|2026|sales", new Expectation(102, 60856000L, 0L)),
      Map.entry("ACADEMY|2026|expense", new Expectation(579, 0L, 54129403L)),
      Map.entry("PUBLISHING|2024|sales", new Expectation(91, 49642000L, 0L)),
      Map.entry("PUBLISHING|2024|expense", new Expectation(746, 0L, 49700806L)),
      Map.entry("PUBLISHING|2025|sales", new Expectation(82, 38334000L, 0L)),
      Map.entry("PUBLISHING|2025|expense", new Expectation(484, 0L, 28363111L)),
      Map.entry("PUBLISHING|2026|sales", new Expectation(14, 7970000L, 0L)),
      Map.entry("PUBLISHING|2026|expense", new Expectation(201, 0L, 13242709L)));

  private static final Map<String, Expectation> FUNDS = Map.ofEntries(
      Map.entry("ACADEMY|2024", new Expectation(60, 6192000L, 7112563L)),
      Map.entry("ACADEMY|2025", new Expectation(697, 247103609L, 282754049L)),
      Map.entry("ACADEMY|2026", new Expectation(810, 213545415L, 229973838L)),
      Map.entry("PUBLISHING|2024", new Expectation(24, 16000L, 5472415L)),
      Map.entry("PUBLISHING|2025", new Expectation(30, 92000L, 13693200L)),
      Map.entry("PUBLISHING|2026", new Expectation(28, 1606920L, 6076920L)));

  private static final Map<String, Integer> MANIFEST_ROWS = Map.ofEntries(
      Map.entry("EASYSHOP_ACADEMY_LEDGER_2024_SALES.csv", 117),
      Map.entry("EASYSHOP_ACADEMY_LEDGER_2024_EXPENSE.csv", 1013),
      Map.entry("EASYSHOP_ACADEMY_LEDGER_2024_DEPOSIT_WITHDRAWAL.csv", 60),
      Map.entry("EASYSHOP_ACADEMY_LEDGER_2025_SALES.csv", 192),
      Map.entry("EASYSHOP_ACADEMY_LEDGER_2025_EXPENSE.csv", 953),
      Map.entry("EASYSHOP_ACADEMY_LEDGER_2025_DEPOSIT_WITHDRAWAL.csv", 697),
      Map.entry("EASYSHOP_ACADEMY_LEDGER_2026_SALES.csv", 102),
      Map.entry("EASYSHOP_ACADEMY_LEDGER_2026_EXPENSE.csv", 579),
      Map.entry("EASYSHOP_ACADEMY_LEDGER_2026_DEPOSIT_WITHDRAWAL.csv", 810),
      Map.entry("EASYSHOP_PUBLISHER_LEDGER_2024_SALES.csv", 91),
      Map.entry("EASYSHOP_PUBLISHER_LEDGER_2024_EXPENSE.csv", 746),
      Map.entry("EASYSHOP_PUBLISHER_LEDGER_2024_DEPOSIT_WITHDRAWAL.csv", 24),
      Map.entry("EASYSHOP_PUBLISHER_LEDGER_2025_SALES.csv", 82),
      Map.entry("EASYSHOP_PUBLISHER_LEDGER_2025_EXPENSE.csv", 484),
      Map.entry("EASYSHOP_PUBLISHER_LEDGER_2025_DEPOSIT_WITHDRAWAL.csv", 30),
      Map.entry("EASYSHOP_PUBLISHER_LEDGER_2026_SALES.csv", 14),
      Map.entry("EASYSHOP_PUBLISHER_LEDGER_2026_EXPENSE.csv", 201),
      Map.entry("EASYSHOP_PUBLISHER_LEDGER_2026_DEPOSIT_WITHDRAWAL.csv", 28));

  @Test
  @DisplayName("실데이터 전체 파이프라인 — Manifest 대조")
  void verifyRealData() throws IOException {
    String dataDir = System.getenv("EASYSHOP_DATA_DIR");
    assumeTrue(dataDir != null && !dataDir.isBlank(), "EASYSHOP_DATA_DIR 없음 — skip");
    Path dir = Path.of(dataDir);
    assumeTrue(Files.isDirectory(dir), "EASYSHOP_DATA_DIR 디렉토리 아님 — skip");

    EasyShopPipelineHarness harness = new EasyShopPipelineHarness();
    harness.seedBusiness(1L, "ACADEMY", "학원");
    harness.seedBusiness(2L, "PUBLISHING", "출판사");
    harness.seedAccount(10L, "INCOME_TUITION", "수강료", EasyAccountDirection.INCOME);
    harness.seedAccount(11L, "INCOME_BOOKS", "교재비", EasyAccountDirection.INCOME);
    harness.seedAccount(12L, "EXPENSE_RENT", "임차료", EasyAccountDirection.EXPENSE);
    harness.seedAccount(13L, "EXPENSE_FEE", "지급수수료", EasyAccountDirection.EXPENSE);
    harness.seedAccount(14L, "EXPENSE_TAX", "세금·공과금", EasyAccountDirection.EXPENSE);
    harness.seedAccount(15L, "SETTLEMENT", "자금정산", EasyAccountDirection.ASSET);
    // 확정 매핑만 시드 (불확실한 20여종은 review 대상으로 남긴다).
    harness.seedMapping("임차료(월세)", 12L);
    harness.seedMapping("카드수수료", 13L);
    harness.seedMapping("세금(면허세,사업소세등)", 14L);

    Map<String, FileStats> stats = new LinkedHashMap<>();
    List<String> rawLines = new ArrayList<>();
    try (var stream = Files.list(dir)) {
      List<Path> files = stream
          .filter(path -> path.getFileName().toString().startsWith("EASYSHOP_")
              && path.getFileName().toString().endsWith(".csv"))
          .sorted().toList();
      assumeTrue(files.size() == 18, "18개 CSV 필요 — " + files.size() + "건, skip");
      for (Path file : files) {
        String name = file.getFileName().toString();
        List<EasyShopRow> parsed = parseFile(file);
        rawLines.add(name);
        assertThat(parsed).as("%s 행수", name).hasSize(MANIFEST_ROWS.get(name));
        FileStats stat = importRows(harness, name, parsed);
        stats.put(name, stat);
        assertThat(stat.stagedRows()).as("%s staging 보존", name)
            .isGreaterThanOrEqualTo(parsed.size());
        assertThat(stat.duplicateRows()).as("%s 첫 입력 중복 없음", name).isZero();
      }
    }
    assertThat(rawLines).hasSize(18);

    StringBuilder report = new StringBuilder("\n=== EasyShop 실데이터 검증 ===\n");
    report.append("사업장|연도|CSV행수|CSV합계|import건수|journal건수|report합계|Manifest일치|review건수\n");

    // 매출/비용 대조
    for (String business : List.of("ACADEMY", "PUBLISHING")) {
      Long businessId = "ACADEMY".equals(business) ? 1L : 2L;
      for (int year : List.of(2024, 2025, 2026)) {
        EasyYearPlResponse pl = harness.reportService.yearPl(businessId, year);
        String incomeKey = business + "|" + year + "|sales";
        long expectIncome = SALES_EXPENSE.get(incomeKey).income();
        long expectExpense = SALES_EXPENSE.get(business + "|" + year + "|expense").expense();
        boolean incomeOk = pl.income() == expectIncome;
        boolean expenseOk = pl.expense() == expectExpense;
        int csvRows = SALES_EXPENSE.get(business + "|" + year + "|sales").rows()
            + SALES_EXPENSE.get(business + "|" + year + "|expense").rows();
        long importTxns = harness.txns.values().stream()
            .filter(txn -> txn.getBusiness().getBusinessId().equals(businessId)
                && txn.getOccurredOn().getYear() == year
                && !EasyShopImportService.SOURCE_KIND_FUNDS.equals(txn.getSourceKind()))
            .count();
        long journals = harness.entries.values().stream()
            .filter(entry -> entry.getBusiness().getBusinessId().equals(businessId)
                && entry.getOccurredOn().getYear() == year
                && entry.getStatus() == EasyJournalStatus.POSTED)
            .count();
        var reviews = harness.auditService.openReviewsForYear(businessId, year);
        report.append("%s|%d|%d|%d/%d|%d|%d|%d/%d|%s|%d\n".formatted(business, year, csvRows,
            expectIncome, expectExpense, importTxns, journals,
            pl.income(), pl.expense(), (incomeOk && expenseOk) ? "PASS" : "FAIL",
            reviews.totalOpen()));
        assertThat(pl.income()).as("%s %d 매출", business, year).isEqualTo(expectIncome);
        assertThat(pl.expense()).as("%s %d 비용", business, year).isEqualTo(expectExpense);
        assertThat(pl.journalBalanced()).as("%s %d 분개 균형", business, year).isTrue();
      }
    }

    // 입출금 보존 대조 (P&L 제외 확인 포함)
    for (String business : List.of("ACADEMY", "PUBLISHING")) {
      Long businessId = "ACADEMY".equals(business) ? 1L : 2L;
      for (int year : List.of(2024, 2025, 2026)) {
        EasyYearPlResponse pl = harness.reportService.yearPl(businessId, year);
        Expectation funds = FUNDS.get(business + "|" + year);
        boolean ok = pl.fundsIn() == funds.income() && pl.fundsOut() == funds.expense();
        report.append("%s|%d|자금|%d/%d|-|-|%d/%d|%s|-\n".formatted(business, year,
            funds.income(), funds.expense(), pl.fundsIn(), pl.fundsOut(),
            ok ? "PASS" : "FAIL"));
        assertThat(pl.fundsIn()).as("%s %d 입금", business, year).isEqualTo(funds.income());
        assertThat(pl.fundsOut()).as("%s %d 출금", business, year).isEqualTo(funds.expense());
      }
    }

    // 커버리지 기준일
    assertThat(harness.reportService.yearPl(1L, 2026).dataThrough().toString())
        .isEqualTo("2026-09-17");
    assertThat(harness.reportService.yearPl(2L, 2026).dataThrough().toString())
        .isEqualTo("2026-05-07");
    report.append("커버리지: 학원2026 기준일 2026-09-17 / 출판사2026 기준일 2026-05-07 (5월 이후 미확보)\n");

    // 동일 파일 재입력 — 모두 DUPLICATE, 장부 증가 없음
    long before = harness.txnCount();
    try (var stream = Files.list(dir)) {
      List<Path> files = stream
          .filter(path -> path.getFileName().toString().startsWith("EASYSHOP_")
              && path.getFileName().toString().endsWith(".csv"))
          .sorted().toList();
      for (Path file : files) {
        String name = file.getFileName().toString();
        FileStats restaged = importRows(harness, name, parseFile(file));
        assertThat(restaged.duplicateRows()).as("%s 재입력 중복", name)
            .isEqualTo(restaged.stagedRows());
        assertThat(restaged.confirmedTxns()).as("%s 재입력 확정 0", name).isZero();
      }
    }
    assertThat(harness.txnCount()).isEqualTo(before);

    // AI review — 대응 비용 없는 자금 유출 2건 검출 (임의 분류 없음)
    harness.auditService.rebuild(null);
    var missing = harness.auditService.listReviews(null, "MISSING_EXPENSE", "OPEN");
    long baekRows = missing.stream().filter(item -> item.title().contains("967,000")).count();
    report.append("MISSING_EXPENSE 총 %d건 (967,000원 2건 포함: %s)\n"
        .formatted(missing.size(), baekRows == 2 ? "PASS" : "FAIL(" + baekRows + ")"));
    assertThat(baekRows).isEqualTo(2L);
    assertThat(missing).allMatch(item -> item.detail().contains("임의로 분류하지 않았습니다"));

    System.out.println(report);
  }

  record FileStats(int stagedRows, int duplicateRows, int confirmedTxns) {
  }

  private List<EasyShopRow> parseFile(Path file) throws IOException {
    String name = file.getFileName().toString();
    String businessCode = name.contains("ACADEMY") ? "ACADEMY" : "PUBLISHING";
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    assumeTrue(!lines.isEmpty(), "빈 파일: " + name);
    List<String> header = parseCsvLine(stripBom(lines.get(0)));
    List<EasyShopRow> rows = new ArrayList<>();
    for (int i = 1; i < lines.size(); i++) {
      if (lines.get(i).isBlank()) {
        continue;
      }
      List<String> cells = parseCsvLine(lines.get(i));
      Map<String, String> columns = new LinkedHashMap<>();
      for (int c = 0; c < header.size() && c < cells.size(); c++) {
        columns.put(header.get(c), cells.get(c));
      }
      rows.add(new EasyShopRow(
          columns.get("ledger_type"), columns.get("date"), columns.get("vendor"),
          columns.get("account"), columns.get("memo"), columns.get("item_type"),
          columns.get("deposit_amount"), columns.get("withdrawal_amount"), columns.get("fee"),
          columns.get("account_category"), columns.get("item_desc"),
          columns.get("supply_amount"), columns.get("vat"), columns.get("tax_exempt_supply"),
          columns.get("total_amount"), columns.get("card_fee"), columns.get("receipt_method"),
          columns.get("pay_method"), columns.get("business"), columns.get("biz_reg_no"),
          columns.get("fiscal_year"), businessCode, null, columns.get("menu_path"), null));
    }
    return rows;
  }

  private FileStats importRows(EasyShopPipelineHarness harness, String name,
      List<EasyShopRow> rows) {
    List<EasyImportBatchResponse> staged =
        harness.shopService.stage(new EasyShopImportRequest(null, name, rows));
    int stagedRows = 0;
    int duplicates = 0;
    int confirmed = 0;
    for (EasyImportBatchResponse batch : staged) {
      stagedRows += batch.rows().size();
      duplicates += batch.rows().stream()
          .filter(row -> "DUPLICATE".equals(row.status())).count();
      boolean ready = batch.rows().stream().anyMatch(row -> "READY".equals(row.status()));
      if (ready) {
        confirmed += harness.importService.confirm(batch.batchId(), null).confirmedRows();
      }
    }
    return new FileStats(stagedRows, (int) duplicates, confirmed);
  }

  private static String stripBom(String line) {
    return line.startsWith("\uFEFF") ? line.substring(1) : line;
  }

  /** 따옴표 인식 최소 CSV 분할 (개행 포함 셀은 실데이터에 없음). */
  static List<String> parseCsvLine(String line) {
    List<String> cells = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (quoted) {
        if (ch == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            current.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          current.append(ch);
        }
      } else if (ch == '"') {
        quoted = true;
      } else if (ch == ',') {
        cells.add(current.toString());
        current.setLength(0);
      } else {
        current.append(ch);
      }
    }
    cells.add(current.toString());
    return cells;
  }
}
