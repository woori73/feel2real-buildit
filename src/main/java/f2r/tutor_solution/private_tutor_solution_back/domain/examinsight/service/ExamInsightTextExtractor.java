package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 업로드 파일에서 분석용 텍스트를 뽑는다. 파일은 메모리에서만 처리하고 저장하지 않는다.
 *
 * <p>실제로 읽을 수 있는 형식만 받는다: 텍스트가 들어 있는 PDF, DOCX, HWPX, TXT, MD.
 * 이미지와 스캔 PDF는 OCR이 없어 읽을 수 없으므로 거절하고 이유를 안내한다(읽은 척하지 않는다).
 * 확장자·시그니처 검증은 기존 {@link DocumentUploadValidator}를 그대로 쓴다.
 */
@Component
public class ExamInsightTextExtractor {

  /** 화면에 그대로 안내하는 지원 형식(확장자). */
  public static final List<String> SUPPORTED_EXTENSIONS = List.of("pdf", "docx", "hwpx", "txt", "md");

  private static final Set<String> SUPPORTED = Set.copyOf(SUPPORTED_EXTENSIONS);
  private static final int PDF_MAX_PAGES = 60;
  private static final int MIN_PDF_TEXT_CHARS = 40;
  /** ZIP 폭탄 방지: 문서 XML 한 개의 압축 해제 상한. */
  private static final long MAX_XML_BYTES = 16L * 1024 * 1024;
  private static final int MAX_ZIP_ENTRIES = 2000;

  private final DocumentUploadValidator validator;

  public ExamInsightTextExtractor(DocumentUploadValidator validator) {
    this.validator = validator;
  }

  /** 추출 결과. {@code text}는 정리된 본문이다. */
  public record Extracted(String filename, String format, String text) {}

  public Extracted extract(MultipartFile file) {
    String name = file == null ? null : file.getOriginalFilename();
    String ext = extensionOf(name);
    if (ext != null && isImage(ext)) {
      throw ExamInsightException.badRequest("UNSUPPORTED_IMAGE",
          "이미지 파일은 아직 분석할 수 없어요. 텍스트가 들어 있는 PDF·DOCX·HWPX·TXT 파일을 올리거나 본문을 붙여 넣어 주세요: " + safe(name));
    }
    try {
      validator.validate(file, SUPPORTED);
    } catch (ResponseStatusException e) {
      throw new ExamInsightException(HttpStatus.BAD_REQUEST, "INVALID_FILE", e.getReason() == null ? "올릴 수 없는 파일이에요." : e.getReason());
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw ExamInsightException.badRequest("READ_FAILED", "파일을 읽지 못했어요: " + safe(name));
    }
    String text;
    try {
      text = switch (ext) {
        case "pdf" -> pdfText(bytes, name);
        case "docx" -> xmlText(bytes, "word/document.xml", "p", "t", name);
        case "hwpx" -> hwpxText(bytes, name);
        default -> plainText(bytes);
      };
    } catch (ExamInsightException e) {
      throw e;
    } catch (RuntimeException | IOException | XMLStreamException e) {
      throw ExamInsightException.badRequest("PARSE_FAILED",
          "파일 내용을 읽지 못했어요. 손상되었거나 암호가 걸린 파일일 수 있어요: " + safe(name));
    }
    String cleaned = clean(text);
    if (cleaned.isBlank()) {
      throw ExamInsightException.badRequest("EMPTY_TEXT", "파일에서 읽을 수 있는 글자를 찾지 못했어요: " + safe(name));
    }
    return new Extracted(safe(name), ext, cleaned);
  }

  // ───────────────────────── 형식별 ─────────────────────────

  private static String pdfText(byte[] bytes, String name) throws IOException {
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      if (doc.isEncrypted()) {
        throw ExamInsightException.badRequest("ENCRYPTED", "암호가 걸린 PDF는 읽을 수 없어요: " + safe(name));
      }
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      stripper.setStartPage(1);
      stripper.setEndPage(Math.min(doc.getNumberOfPages(), PDF_MAX_PAGES));
      String text = stripper.getText(doc);
      if (clean(text).length() < MIN_PDF_TEXT_CHARS) {
        throw ExamInsightException.badRequest("SCANNED_PDF",
            "이 PDF는 글자가 아니라 이미지(스캔본)로 보여요. 현재는 스캔 PDF를 분석할 수 없어요. 텍스트가 들어 있는 파일을 올리거나 본문을 붙여 넣어 주세요: " + safe(name));
      }
      return text;
    }
  }

  private static String hwpxText(byte[] bytes, String name) throws IOException, XMLStreamException {
    List<String> sections = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      int count = 0;
      while ((entry = zip.getNextEntry()) != null) {
        if (++count > MAX_ZIP_ENTRIES) {
          throw ExamInsightException.badRequest("TOO_MANY_ENTRIES", "파일 구조가 너무 복잡해서 읽지 않았어요: " + safe(name));
        }
        String entryName = entry.getName();
        if (!entry.isDirectory() && entryName.startsWith("Contents/section") && entryName.endsWith(".xml")) {
          sections.add(entryName);
        }
      }
    }
    java.util.Collections.sort(sections);
    StringBuilder sb = new StringBuilder();
    for (String section : sections) {
      sb.append(xmlText(bytes, section, "p", "t", name)).append('\n');
    }
    return sb.toString();
  }

  /**
   * ZIP 안의 XML 한 개에서 글자를 뽑는다. {@code paragraph} 요소가 끝나면 줄바꿈, {@code text} 요소 내용을 이어 붙인다.
   * 외부 엔티티·DTD는 끈다(XXE 방지).
   */
  static String xmlText(byte[] zipBytes, String entryName, String paragraph, String text, String name)
      throws IOException, XMLStreamException {
    byte[] xml = readEntry(zipBytes, entryName);
    if (xml == null) {
      throw ExamInsightException.badRequest("MISSING_CONTENT", "문서 본문을 찾지 못했어요: " + safe(name));
    }
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
    XMLStreamReader r = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
    StringBuilder sb = new StringBuilder();
    boolean inText = false;
    try {
      while (r.hasNext()) {
        int ev = r.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String local = r.getLocalName();
          if (local.equals(text)) inText = true;
          else if (local.equals("tab")) sb.append('\t');
          else if (local.equals("br") || local.equals("lineBreak")) sb.append('\n');
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          String local = r.getLocalName();
          if (local.equals(text)) inText = false;
          else if (local.equals(paragraph)) sb.append('\n');
          else if (local.equals("tc")) sb.append('\t');
        } else if ((ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) && inText) {
          sb.append(r.getText());
        }
      }
    } finally {
      r.close();
    }
    return sb.toString();
  }

  private static byte[] readEntry(byte[] zipBytes, String entryName) throws IOException {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      int count = 0;
      while ((entry = zip.getNextEntry()) != null) {
        if (++count > MAX_ZIP_ENTRIES) return null;
        if (entry.getName().equals(entryName)) {
          return readLimited(zip, MAX_XML_BYTES);
        }
      }
    }
    return null;
  }

  private static byte[] readLimited(InputStream in, long max) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    long total = 0;
    int n;
    while ((n = in.read(buf)) > 0) {
      total += n;
      if (total > max) {
        throw ExamInsightException.badRequest("TOO_LARGE_CONTENT", "문서 내용이 너무 커서 읽지 않았어요.");
      }
      out.write(buf, 0, n);
    }
    return out.toByteArray();
  }

  /** UTF-8을 먼저 시도하고, 깨지면 한글 윈도우 인코딩(MS949)으로 읽는다. */
  static String plainText(byte[] bytes) {
    String utf8 = decodeStrict(bytes, StandardCharsets.UTF_8);
    if (utf8 != null) return utf8.startsWith("﻿") ? utf8.substring(1) : utf8;
    String legacy = decodeStrict(bytes, Charset.forName("x-windows-949"));
    return legacy != null ? legacy : new String(bytes, StandardCharsets.UTF_8);
  }

  private static String decodeStrict(byte[] bytes, Charset charset) {
    try {
      return charset.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      return null;
    }
  }

  // ───────────────────────── 정리 ─────────────────────────

  /** 제어 문자를 지우고 연속 공백·빈 줄을 줄인다. */
  static String clean(String raw) {
    if (raw == null) return "";
    String s = raw.replace("\r\n", "\n").replace('\r', '\n')
        .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
        .replaceAll("[ \\t]+\\n", "\n")
        .replaceAll("\\n{3,}", "\n\n");
    return s.trim();
  }

  private static String extensionOf(String filename) {
    if (filename == null) return null;
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) return null;
    return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static boolean isImage(String ext) {
    return Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic").contains(ext);
  }

  /** 파일명은 경로·제어 문자를 지우고 길이를 줄여 응답·로그에 싣는다. */
  static String safe(String filename) {
    if (filename == null) return "(이름 없음)";
    String base = filename.replace('\\', '/');
    base = base.substring(base.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "");
    return base.length() > 80 ? base.substring(0, 80) : base;
  }
}
