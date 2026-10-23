package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

/** Explicit canonical templates only. Native institution exports need a verified mapping. */
@Service
@RequiredArgsConstructor
public class EasyFinancialFileParser {
  public static final int MAX_BYTES = 10 * 1024 * 1024;
  private final EasyCsvFileAdapter csv;

  public List<CollectionParsedRow> parse(byte[] bytes, String extension) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("FILE_SIZE_INVALID");
    }
    try {
      String text = switch (extension) {
        case "csv" -> decodeCsv(bytes);
        case "xls", "xlsx" -> excel(bytes);
        case "pdf" -> pdf(bytes);
        default -> throw new IllegalArgumentException("UNSUPPORTED_FORMAT");
      };
      validateTable(text);
      List<CollectionParsedRow> rows = csv.parse(text.getBytes(StandardCharsets.UTF_8), "template.csv");
      if (rows.isEmpty() || rows.size() > 2000) {
        throw new IllegalArgumentException("ROW_LIMIT_OR_EMPTY");
      }
      List<CollectionParsedRow> strict = new ArrayList<>();
      for (CollectionParsedRow r : rows) {
        String error = r.isValid() ? null : "INVALID_ROW";
        if (!strictDate(r.originalRow().get("date"))) error = "INVALID_DATE";
        strict.add(new CollectionParsedRow(r.rowNo(), r.originalRow(), r.rawJson(), r.occurredOn(),
            r.direction(), r.amount(), r.counterparty(), r.description(), r.payMethod(),
            r.supplyAmount(), r.vatAmount(), r.evidenceType(), r.externalAccount(), r.externalId(), error));
      }
      return List.copyOf(strict);
    } catch (IOException | org.apache.poi.EncryptedDocumentException e) {
      throw new IllegalArgumentException("UNREADABLE_OR_ENCRYPTED_FILE");
    }
  }

  private String excel(byte[] bytes) throws IOException {
    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
      if (workbook.getNumberOfSheets() != 1) throw new IllegalArgumentException("SINGLE_SHEET_REQUIRED");
      var sheet = workbook.getSheetAt(0);
      if (sheet.getLastRowNum() > 2000) throw new IllegalArgumentException("ROW_LIMIT_OR_EMPTY");
      DataFormatter formatter = new DataFormatter(Locale.ROOT);
      StringBuilder out = new StringBuilder();
      for (var row : sheet) {
        if (row.getLastCellNum() > 32) throw new IllegalArgumentException("COLUMN_LIMIT");
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
          var cell = row.getCell(i);
          if (cell != null && cell.getCellType() == CellType.FORMULA) {
            throw new IllegalArgumentException("FORMULA_NOT_ALLOWED");
          }
          String value = cell == null ? "" : cell.getCellType() == CellType.NUMERIC
              && DateUtil.isCellDateFormatted(cell)
              ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
              : formatter.formatCellValue(cell);
          if (value.length() > 4096) throw new IllegalArgumentException("CELL_LIMIT");
          cells.add("\"" + value.replace("\"", "\"\"") + "\"");
        }
        out.append(String.join(",", cells)).append('\n');
      }
      return out.toString();
    }
  }

  private String pdf(byte[] bytes) throws IOException {
    try (var document = Loader.loadPDF(bytes)) {
      if (document.isEncrypted() || document.getNumberOfPages() > 20) {
        throw new IllegalArgumentException("PDF_ENCRYPTED_OR_PAGE_LIMIT");
      }
      String text = new PDFTextStripper().getText(document);
      if (text.isBlank() || text.length() > 1_000_000) {
        throw new IllegalArgumentException("PDF_TEXT_TEMPLATE_REQUIRED");
      }
      // Supported PDF template: one pipe-delimited record per text line. No OCR or guessing.
      StringBuilder out = new StringBuilder();
      for (String line : text.split("\\R")) {
        if (line.isBlank()) continue;
        if (!line.contains("|")) throw new IllegalArgumentException("PDF_TEXT_TEMPLATE_REQUIRED");
        String[] cells = line.split("\\|", -1);
        List<String> quoted = new ArrayList<>();
        for (String cell : cells) quoted.add("\"" + cell.trim().replace("\"", "\"\"") + "\"");
        out.append(String.join(",", quoted)).append('\n');
      }
      return out.toString();
    }
  }

  private void validateTable(String text) {
    if (text.length() > 2_000_000) throw new IllegalArgumentException("TEXT_LIMIT");
    // Reject malformed quoting rather than silently accepting a truncated final CSV record.
    boolean quoted = false;
    boolean closed = false;
    boolean start = true;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') i++;
          else { quoted = false; closed = true; }
        }
      } else if (c == ',' || c == '\n' || c == '\r') {
        start = true;
        closed = false;
      } else if (c == '"') {
        if (!start || closed) throw new IllegalArgumentException("CSV_INVALID_QUOTE");
        quoted = true;
        start = false;
      } else {
        if (closed) throw new IllegalArgumentException("CSV_INVALID_QUOTE");
        start = false;
      }
    }
    if (quoted) throw new IllegalArgumentException("CSV_UNCLOSED_QUOTE");
    var lines = EasyCsvFileAdapter.parseCsv(text);
    if (lines.size() > 2001) throw new IllegalArgumentException("ROW_LIMIT_OR_EMPTY");
    int columns = lines.isEmpty() ? 0 : lines.getFirst().size();
    if (!lines.isEmpty() && lines.getFirst().stream().map(String::trim).map(value -> value.toLowerCase(Locale.ROOT))
        .distinct().count() != columns) throw new IllegalArgumentException("DUPLICATE_HEADER");
    for (var line : lines) {
      if (line.size() == 1 && line.getFirst().isBlank()) continue;
      if (line.size() != columns || columns > 32) throw new IllegalArgumentException("COLUMN_MISMATCH");
      for (String cell : line) {
        if (cell.length() > 4096) throw new IllegalArgumentException("CELL_LIMIT");
      }
    }
  }

  private String decodeCsv(byte[] bytes) {
    for (var charset : List.of(StandardCharsets.UTF_8, java.nio.charset.Charset.forName("MS949"))) {
      try {
        String text = charset.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
      } catch (java.nio.charset.CharacterCodingException ignored) { }
    }
    throw new IllegalArgumentException("CSV_ENCODING_INVALID");
  }

  private boolean strictDate(String raw) {
    if (raw == null) return false;
    String value = raw.trim().replace('.', '-').replace('/', '-');
    for (String pattern : List.of("uuuu-MM-dd", "uuuuMMdd")) {
      try {
        LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT));
        return true;
      } catch (RuntimeException ignored) { }
    }
    return false;
  }
}
