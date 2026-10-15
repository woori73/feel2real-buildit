package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.ShinhanCardRawRow;
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
 * Parses Shinhan card "개인사업자 이용내역" official downloads — Excel 97-2003 (BIFF8, .xls) only.
 *
 * <p>Real source shape (verified against an actual download, not guessed): 4 header/meta rows
 * (제목/성명/카드번호/기간), a column-header row, N data rows, then a "합계" totals row and a
 * disclaimer row. This parser locates the header row by content (첫 셀 == "거래일") instead of a
 * hardcoded row index, and stops at the first row whose first cell is "합계" or blank — it does
 * not assume a fixed row count.
 *
 * <p>거래일(transactionDate)과 확정일(confirmationDate)은 별개 컬럼으로 그대로 보존한다. 합치지
 * 않는다. 금액 부호도 원본 그대로 보존한다(취소/환불 음수 변환 없음).
 */
@Component
public class ShinhanCardExcelParser {

  private static final String DATE_HEADER = "거래일";
  private static final String TOTAL_ROW_MARKER = "합계";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

  public List<ShinhanCardRawRow> parse(InputStream xls) {
    try (HSSFWorkbook workbook = new HSSFWorkbook(xls)) {
      Sheet sheet = workbook.getSheetAt(0);
      int headerRowIndex = findHeaderRow(sheet);
      if (headerRowIndex < 0) {
        throw new ShinhanCardParseException("헤더 행(거래일 컬럼)을 찾을 수 없습니다.");
      }
      List<ShinhanCardRawRow> rows = new ArrayList<>();
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
    } catch (ShinhanCardParseException e) {
      throw e;
    } catch (Exception e) {
      throw new ShinhanCardParseException("Shinhan Excel 파싱 실패: " + e.getMessage(), e);
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

  private ShinhanCardRawRow parseRow(Row row, int sheetRowIndex) {
    return new ShinhanCardRawRow(
        parseDate(cellText(row.getCell(0)), sheetRowIndex, "거래일"),
        parseDate(cellText(row.getCell(1)), sheetRowIndex, "확정일"),
        requireText(cellText(row.getCell(2)), sheetRowIndex, "카드구분"),
        requireText(cellText(row.getCell(3)), sheetRowIndex, "이용카드"),
        requireText(cellText(row.getCell(4)), sheetRowIndex, "상품구분"),
        requireText(cellText(row.getCell(5)), sheetRowIndex, "가맹점명"),
        parseAmount(row.getCell(6), sheetRowIndex, "이용금액"),
        parseAmount(row.getCell(7), sheetRowIndex, "공급가액"),
        parseAmount(row.getCell(8), sheetRowIndex, "부가세"),
        parseAmount(row.getCell(9), sheetRowIndex, "비과세금액"),
        cellText(row.getCell(10)),
        sheetRowIndex);
  }

  private LocalDate parseDate(String text, int rowIndex, String column) {
    if (text == null || text.isBlank()) {
      throw new ShinhanCardParseException("행 " + rowIndex + ": " + column + " 값이 없습니다.");
    }
    try {
      return LocalDate.parse(text.trim(), DATE_FORMAT);
    } catch (Exception e) {
      throw new ShinhanCardParseException(
          "행 " + rowIndex + ": " + column + " 형식이 예상과 다릅니다 (yyyy.MM.dd 기대): " + text);
    }
  }

  private String requireText(String text, int rowIndex, String column) {
    if (text == null || text.isBlank()) {
      throw new ShinhanCardParseException("행 " + rowIndex + ": " + column + " 값이 없습니다.");
    }
    return text.trim();
  }

  private BigDecimal parseAmount(Cell cell, int rowIndex, String column) {
    if (cell == null) {
      throw new ShinhanCardParseException("행 " + rowIndex + ": " + column + " 값이 없습니다.");
    }
    if (cell.getCellType() == CellType.NUMERIC) {
      return BigDecimal.valueOf(cell.getNumericCellValue());
    }
    String text = cellText(cell);
    if (text == null || text.isBlank()) {
      throw new ShinhanCardParseException("행 " + rowIndex + ": " + column + " 값이 없습니다.");
    }
    try {
      return new BigDecimal(text.trim().replace(",", ""));
    } catch (NumberFormatException e) {
      throw new ShinhanCardParseException(
          "행 " + rowIndex + ": " + column + " 숫자 형식이 아닙니다: " + text);
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

  public static class ShinhanCardParseException extends RuntimeException {
    public ShinhanCardParseException(String message) {
      super(message);
    }
    public ShinhanCardParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
