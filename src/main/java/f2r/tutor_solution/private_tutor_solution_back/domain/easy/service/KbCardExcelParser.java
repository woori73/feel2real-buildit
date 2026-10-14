package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.KbCardRawRow;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * Parses KB "개인사업자용 이용내역" official downloads — Excel 97-2003 (BIFF8, .xls) only.
 *
 * <p>Real source shape (verified against REAL_SOURCE_SAMPLE_KB_001, not guessed): summary rows
 * (조회기간/카드번호/정상-취소 집계), a 14-column header row, N data rows, end of sheet. This
 * parser locates the header row by content (첫 셀 == "이용일") instead of a hardcoded row index,
 * validates all 14 header names, and stops at the first row whose first cell is blank (or a
 * "합계" totals row, defensively) — it does not assume a fixed row count.
 *
 * <p>이용일(usageDate)과 결제예정일(expectedPaymentDate)은 별개 컬럼으로 그대로 보존한다. 합치지
 * 않는다. 금액 부호도 원본 그대로 보존한다(취소/환불 음수 변환 없음).
 *
 * <p>Status is fail-closed: only the actually observed values 전표매입/전표미매입 are accepted.
 * Anything else throws — an unverified status must never flow silently into accounting.
 * Cancellation representation is UNKNOWN for this source (zero cancellation rows observed), so
 * nothing about cancellations is inferred here.
 *
 * <p>Exception messages deliberately contain column names and row indexes only — never raw cell
 * values (no merchant names, approval numbers, or customer names in logs).
 */
@Component
public class KbCardExcelParser {

  private static final String DATE_HEADER = "이용일";
  private static final String TOTAL_ROW_MARKER = "합계";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  static final String SLIP_PURCHASED = "전표매입";
  static final String SLIP_UNPURCHASED = "전표미매입";

  private static final String[] EXPECTED_HEADERS = {
      "이용일", "이용시간", "이용고객명", "이용카드명", "이용하신곳", "국내이용금액(원)",
      "해외이용금액($)", "결제방법", "가맹점정보", "할인금액", "적립(예상)포인트리",
      "상태", "결제예정일", "승인번호"
  };

  public List<KbCardRawRow> parse(InputStream xls) {
    try (HSSFWorkbook workbook = new HSSFWorkbook(xls)) {
      Sheet sheet = workbook.getSheetAt(0);
      int headerRowIndex = findHeaderRow(sheet);
      if (headerRowIndex < 0) {
        throw new KbCardParseException("헤더 행(이용일 컬럼)을 찾을 수 없습니다.");
      }
      validateHeaders(sheet.getRow(headerRowIndex));
      List<KbCardRawRow> rows = new ArrayList<>();
      for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) continue;
        String firstCell = cellText(row.getCell(0));
        if (firstCell == null || firstCell.isBlank() || firstCell.startsWith(TOTAL_ROW_MARKER)) {
          break;
        }
        rows.add(parseRow(row, r));
      }
      return rows;
    } catch (KbCardParseException e) {
      throw e;
    } catch (Exception e) {
      throw new KbCardParseException("KB Excel 파싱 실패: " + e.getMessage(), e);
    }
  }

  private int findHeaderRow(Sheet sheet) {
    for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 20); r++) {
      Row row = sheet.getRow(r);
      if (row == null) continue;
      String firstCell = cellText(row.getCell(0));
      if (DATE_HEADER.equals(firstCell)) return r;
    }
    return -1;
  }

  private void validateHeaders(Row headerRow) {
    if (headerRow == null) {
      throw new KbCardParseException("헤더 행을 읽을 수 없습니다.");
    }
    for (int c = 0; c < EXPECTED_HEADERS.length; c++) {
      String actual = normalizeHeader(cellText(headerRow.getCell(c)));
      if (!EXPECTED_HEADERS[c].equals(actual)) {
        throw new KbCardParseException(
            "예상과 다른 KB 스키마입니다 (컬럼 " + c + ": " + EXPECTED_HEADERS[c] + " 기대).");
      }
    }
  }

  private String normalizeHeader(String text) {
    if (text == null) return "";
    return text.replaceAll("\\s+", "");
  }

  private KbCardRawRow parseRow(Row row, int sheetRowIndex) {
    String slipStatus = requireText(cellText(row.getCell(11)), sheetRowIndex, "상태");
    if (!SLIP_PURCHASED.equals(slipStatus) && !SLIP_UNPURCHASED.equals(slipStatus)) {
      throw new KbCardParseException(
          "행 " + sheetRowIndex + ": 미확인 상태값입니다 (전표매입/전표미매입만 허용).");
    }
    return new KbCardRawRow(
        parseDate(cellText(row.getCell(0)), sheetRowIndex, "이용일"),
        requireText(cellText(row.getCell(1)), sheetRowIndex, "이용시간"),
        requireText(cellText(row.getCell(2)), sheetRowIndex, "이용고객명"),
        requireText(cellText(row.getCell(3)), sheetRowIndex, "이용카드명"),
        requireText(cellText(row.getCell(4)), sheetRowIndex, "이용하신곳"),
        parseAmount(row.getCell(5), sheetRowIndex, "국내이용금액"),
        parseAmount(row.getCell(6), sheetRowIndex, "해외이용금액"),
        requireText(cellText(row.getCell(7)), sheetRowIndex, "결제방법"),
        cellText(row.getCell(8)),
        parseAmount(row.getCell(9), sheetRowIndex, "할인금액"),
        parseAmount(row.getCell(10), sheetRowIndex, "적립예상포인트"),
        slipStatus,
        parseOptionalDate(cellText(row.getCell(12)), sheetRowIndex, "결제예정일"),
        requireText(cellText(row.getCell(13)), sheetRowIndex, "승인번호"),
        sheetRowIndex);
  }

  private LocalDate parseDate(String text, int rowIndex, String column) {
    if (text == null || text.isBlank()) {
      throw new KbCardParseException("행 " + rowIndex + ": " + column + " 값이 없습니다.");
    }
    try {
      return LocalDate.parse(text.trim(), DATE_FORMAT);
    } catch (Exception e) {
      throw new KbCardParseException(
          "행 " + rowIndex + ": " + column + " 형식이 예상과 다릅니다 (yyyy-MM-dd 기대).");
    }
  }

  private LocalDate parseOptionalDate(String text, int rowIndex, String column) {
    if (text == null || text.isBlank()) return null;
    try {
      return LocalDate.parse(text.trim(), DATE_FORMAT);
    } catch (Exception e) {
      throw new KbCardParseException(
          "행 " + rowIndex + ": " + column + " 형식이 예상과 다릅니다 (yyyy-MM-dd 기대).");
    }
  }

  private String requireText(String text, int rowIndex, String column) {
    if (text == null || text.isBlank()) {
      throw new KbCardParseException("행 " + rowIndex + ": " + column + " 값이 없습니다.");
    }
    return text.trim();
  }

  private BigDecimal parseAmount(Cell cell, int rowIndex, String column) {
    if (cell == null) {
      throw new KbCardParseException("행 " + rowIndex + ": " + column + " 값이 없습니다.");
    }
    if (cell.getCellType() == CellType.NUMERIC) {
      return BigDecimal.valueOf(cell.getNumericCellValue());
    }
    String text = cellText(cell);
    if (text == null || text.isBlank()) {
      throw new KbCardParseException("행 " + rowIndex + ": " + column + " 값이 없습니다.");
    }
    try {
      return new BigDecimal(text.trim().replace(",", ""));
    } catch (NumberFormatException e) {
      throw new KbCardParseException("행 " + rowIndex + ": " + column + " 숫자 형식이 아닙니다.");
    }
  }

  private String cellText(Cell cell) {
    if (cell == null) return null;
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC -> String.valueOf(cell.getNumericCellValue());
      case BLANK -> null;
      default -> cell.toString();
    };
  }

  public static class KbCardParseException extends RuntimeException {
    public KbCardParseException(String message) {
      super(message);
    }
    public KbCardParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
