package f2r.tutor_solution.private_tutor_solution_back.domain.academyhome;

import java.io.ByteArrayInputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 이미 올린 자료의 원문을 사람이 읽을 수 있는 블록으로 꺼낸다(저장하지 않고 요청 때마다 원본에서 읽는다).
 *
 * <p>형식별 읽기 경로:
 * <ul>
 *   <li>docx — 본문 순서대로 문단·표. 표 첫 줄을 열 제목으로 쓴다.
 *   <li>doc — 문단 글자 + 표(첫 줄 열 제목).
 *   <li>pdf — 글자 층이 있으면 페이지 순서대로. 글자 층이 없으면(스캔) OCR 필요로 실패 사유를 준다.
 *   <li>csv — 첫 줄 열 제목 표. txt·md·json — 문단. html — 태그를 뺀 글자 문단.
 *   <li>png·jpg·jpeg·webp — 이 서비스에 OCR이 연결돼 있지 않아 확인용 원본만 제공한다.
 * </ul>
 * 파일명이나 요약을 본문처럼 만들지 않는다. 읽지 못하면 블록 없이 사유만 돌려준다.
 */
public final class MaterialTextExtractor {
  static final int MAX_PAGES = 100;
  static final int MAX_CHARS = 200_000;
  static final int MAX_TABLE_ROWS = 2_000;

  private MaterialTextExtractor() {}

  /** 본문 블록: PARAGRAPH(text) · TABLE(header, rows) · PAGE(page, text). */
  public record Block(String type, String text, Integer page, List<String> header, List<List<String>> rows) {
    static Block paragraph(String text) { return new Block("PARAGRAPH", text, null, null, null); }
    static Block page(int page, String text) { return new Block("PAGE", text, page, null, null); }
    static Block table(List<String> header, List<List<String>> rows) { return new Block("TABLE", null, null, header, rows); }
  }

  /**
   * status: READ(본문 있음) · EMPTY(읽었지만 글자 없음) · NEEDS_OCR(스캔·이미지) · UNSUPPORTED(형식 미지원) · FAILED(손상 등).
   * route: 사용한 읽기 경로 설명. truncated: 길이 제한으로 뒤를 잘랐는지.
   */
  public record Result(String status, String route, String reason, List<Block> blocks, boolean truncated, Integer pageCount) {}

  public static Result extract(String extension, byte[] bytes) {
    String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
    if (bytes == null || bytes.length == 0) return new Result("EMPTY", ext, "원본 파일이 비어 있습니다.", List.of(), false, null);
    try {
      return switch (ext) {
        case "docx" -> limit(docx(bytes), "Word(docx) 문단·표");
        case "doc" -> limit(doc(bytes), "Word 97-2003(doc) 문단·표");
        case "pdf" -> pdf(bytes);
        case "csv" -> limit(List.of(csv(text(bytes))), "CSV 표");
        case "txt", "md", "json" -> limit(paragraphs(text(bytes)), "텍스트 문단");
        case "html", "htm" -> limit(paragraphs(org.jsoup.Jsoup.parse(text(bytes)).wholeText()), "HTML 글자");
        case "png", "jpg", "jpeg", "webp" -> new Result("NEEDS_OCR", "이미지", "이미지 자료입니다. 이 서비스에는 글자 인식(OCR)이 연결돼 있지 않아 원본 이미지로 확인해 주세요.", List.of(), false, null);
        default -> new Result("UNSUPPORTED", ext, "이 형식(" + (ext.isEmpty() ? "확장자 없음" : ext) + ")은 화면에서 글로 읽을 수 없습니다. 원본을 열어 확인하세요.", List.of(), false, null);
      };
    } catch (Exception | LinkageError e) {
      return new Result("FAILED", ext, "원문을 읽지 못했습니다(파일 손상 또는 암호·지원하지 않는 구성). 원본을 열거나 다시 읽기를 눌러 주세요.", List.of(), false, null);
    }
  }

  /** 글자가 하나라도 있는 블록만 본문으로 인정한다. */
  static Result limit(List<Block> blocks, String route) {
    List<Block> out = new ArrayList<>();
    int chars = 0;
    boolean truncated = false;
    for (Block b : blocks) {
      int size = b.text() != null ? b.text().length() : b.rows().stream().mapToInt(r -> String.join("", r).length()).sum() + String.join("", b.header()).length();
      if (size == 0) continue;
      if (chars + size > MAX_CHARS) { truncated = true; break; }
      chars += size;
      out.add(b);
    }
    if (out.isEmpty()) return new Result("EMPTY", route, "파일을 열었지만 읽을 수 있는 글자가 없습니다. 원본을 확인하세요.", List.of(), false, null);
    return new Result("READ", route, null, out, truncated, null);
  }

  /** UTF-8(BOM 허용) 우선, 아니면 한국어 Windows(CP949). */
  static String text(byte[] bytes) {
    try {
      String s = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
      return s.startsWith("﻿") ? s.substring(1) : s;
    } catch (CharacterCodingException e) {
      return new String(bytes, java.nio.charset.Charset.forName("MS949"));
    }
  }

  /** 빈 줄로 나눈 문단. 줄바꿈은 문단 안에서 그대로 둔다. */
  static List<Block> paragraphs(String text) {
    List<Block> out = new ArrayList<>();
    for (String part : text.replace("\r\n", "\n").split("\\n\\s*\\n")) {
      String t = part.strip();
      if (!t.isEmpty()) out.add(Block.paragraph(t));
    }
    return out;
  }

  /** 첫 줄 열 제목. 따옴표 안 쉼표·줄바꿈·"" 이스케이프를 지원한다. */
  static Block csv(String text) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
        else if (c == '"') quoted = false;
        else cell.append(c);
      } else if (c == '"') quoted = true;
      else if (c == ',') { row.add(cell.toString().strip()); cell.setLength(0); }
      else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
        row.add(cell.toString().strip()); cell.setLength(0);
        if (row.stream().anyMatch(v -> !v.isEmpty())) rows.add(row);
        row = new ArrayList<>();
      } else cell.append(c);
    }
    row.add(cell.toString().strip());
    if (row.stream().anyMatch(v -> !v.isEmpty())) rows.add(row);
    return tableOf(rows);
  }

  static Block tableOf(List<List<String>> rows) {
    if (rows.isEmpty()) return Block.table(List.of(), List.of());
    List<String> header = rows.get(0);
    List<List<String>> body = new ArrayList<>(rows.subList(1, Math.min(rows.size(), MAX_TABLE_ROWS + 1)));
    return Block.table(header, body);
  }

  static List<Block> docx(byte[] bytes) throws Exception {
    List<Block> out = new ArrayList<>();
    try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(new ByteArrayInputStream(bytes))) {
      StringBuilder para = new StringBuilder();
      for (var element : doc.getBodyElements()) {
        if (element instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph p) {
          String t = p.getText().strip();
          if (t.isEmpty()) { flush(para, out); continue; }
          if (para.length() > 0) para.append('\n');
          para.append(t);
        } else if (element instanceof org.apache.poi.xwpf.usermodel.XWPFTable table) {
          flush(para, out);
          List<List<String>> rows = new ArrayList<>();
          for (var r : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (var c : r.getTableCells()) cells.add(c.getText().strip());
            rows.add(cells);
          }
          out.add(tableOf(rows));
        }
      }
      flush(para, out);
    }
    return out;
  }

  static List<Block> doc(byte[] bytes) throws Exception {
    List<Block> out = new ArrayList<>();
    try (var doc = new org.apache.poi.hwpf.HWPFDocument(new ByteArrayInputStream(bytes))) {
      var range = doc.getRange();
      StringBuilder para = new StringBuilder();
      for (int i = 0; i < range.numParagraphs(); i++) {
        var p = range.getParagraph(i);
        if (p.isInTable()) continue;
        String t = p.text().replaceAll("[\\r\\u0007\\f]", "").strip();
        if (t.isEmpty()) { flush(para, out); continue; }
        if (para.length() > 0) para.append('\n');
        para.append(t);
      }
      flush(para, out);
      var tables = new org.apache.poi.hwpf.usermodel.TableIterator(range);
      while (tables.hasNext()) {
        var table = tables.next();
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r < table.numRows(); r++) {
          var row = table.getRow(r);
          List<String> cells = new ArrayList<>();
          for (int c = 0; c < row.numCells(); c++) cells.add(row.getCell(c).text().replaceAll("[\\r\\u0007\\f]", " ").strip());
          rows.add(cells);
        }
        out.add(tableOf(rows));
      }
    }
    return out;
  }

  private static void flush(StringBuilder para, List<Block> out) {
    if (para.length() > 0) { out.add(Block.paragraph(para.toString())); para.setLength(0); }
  }

  /** 페이지 순서대로 글자 층을 읽는다. 모든 페이지에 글자가 없으면 스캔 PDF로 보고 OCR 필요 사유를 준다. */
  static Result pdf(byte[] bytes) throws Exception {
    try (var doc = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      int pages = doc.getNumberOfPages();
      var strip = new org.apache.pdfbox.text.PDFTextStripper();
      List<Block> out = new ArrayList<>();
      int chars = 0;
      boolean truncated = pages > MAX_PAGES;
      for (int i = 1; i <= Math.min(pages, MAX_PAGES); i++) {
        strip.setStartPage(i);
        strip.setEndPage(i);
        String t = strip.getText(doc).strip();
        if (t.isEmpty()) continue;
        if (chars + t.length() > MAX_CHARS) { truncated = true; break; }
        chars += t.length();
        out.add(Block.page(i, t));
      }
      if (out.isEmpty())
        return new Result("NEEDS_OCR", "PDF 페이지 글자", "PDF에 글자 층이 없습니다(스캔본으로 보입니다). 이 서비스에는 글자 인식(OCR)이 연결돼 있지 않아 원본으로 확인해 주세요.", List.of(), false, pages);
      return new Result("READ", "PDF 페이지 글자", null, out, truncated, pages);
    }
  }
}
