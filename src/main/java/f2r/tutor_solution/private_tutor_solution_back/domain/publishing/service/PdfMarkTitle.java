package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;

/**
 * 인쇄 마크(페이지 정보) 문구용 ASCII 제목.
 *
 * <p>DesignCraft의 페이지 정보 마크는 한글 글리프가 없는 내장 Source Sans 3로만 그려져 한글이 □로 나온다.
 * {@code file.exportPdf}의 {@code title}이 마크 문구를 정하지만 같은 값이 PDF Info/XMP 제목도 바꾸므로,
 * 내보낸 뒤 메타데이터 제목만 원래 문서 제목으로 되돌린다. 본문·파일명·문서 제목은 건드리지 않는다.
 */
final class PdfMarkTitle {

  private static final Map<String, String> ASCII_TITLES = Map.of(
      "ko-textbook-a4", "Korean Textbook Template (A4)",
      "ko-workbook-b5", "Korean Workbook Template (B5)",
      "ko-book-46", "Korean Book Template (46)");
  private static final Pattern DC_TITLE_LI = Pattern.compile(
      "(<dc:title>\\s*<rdf:Alt>\\s*<rdf:li[^>]*>)(.*?)(</rdf:li>)", Pattern.DOTALL);

  private PdfMarkTitle() {}

  /** 마크에 쓸 ASCII 제목. 제목을 모르거나 이미 ASCII면 null (기존 동작 유지). */
  static String markTitleFor(String templateCode, String documentTitle) {
    if (documentTitle == null || documentTitle.isBlank() || isPrintableAscii(documentTitle)) {
      return null;
    }
    String mapped = templateCode == null ? null : ASCII_TITLES.get(templateCode);
    if (mapped != null) {
      return mapped;
    }
    return templateCode != null && isPrintableAscii(templateCode) ? templateCode : "Document";
  }

  /** .designcraft(zip)의 document.json title. 읽지 못하면 null. */
  static String readDocumentTitle(Path designcraftFile, ObjectMapper mapper) {
    try (ZipFile zip = new ZipFile(designcraftFile.toFile())) {
      ZipEntry entry = zip.getEntry("document.json");
      if (entry == null) {
        return null;
      }
      try (InputStream in = zip.getInputStream(entry)) {
        JsonNode title = mapper.readTree(in).get("title");
        return title != null && title.isTextual() ? title.asText() : null;
      }
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /** PDF Info /Title 과 XMP dc:title 을 원래 제목으로 되돌린다. 압축 없이 저장해 기존 게이트의 문자열 검사를 유지한다. */
  static void restoreTitle(Path pdf, String title) throws IOException {
    Path tmp = pdf.resolveSibling(pdf.getFileName() + ".title.tmp");
    try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
      doc.getDocumentInformation().setTitle(title);
      PDMetadata metadata = doc.getDocumentCatalog().getMetadata();
      if (metadata != null) {
        String xmp = new String(metadata.toByteArray(), StandardCharsets.UTF_8);
        Matcher m = DC_TITLE_LI.matcher(xmp);
        if (m.find()) {
          metadata.importXMPMetadata(m.replaceFirst(
              Matcher.quoteReplacement(m.group(1) + escapeXml(title) + m.group(3)))
              .getBytes(StandardCharsets.UTF_8));
        }
      }
      doc.save(tmp.toFile(), CompressParameters.NO_COMPRESSION);
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(tmp);
      throw e instanceof IOException io ? io : new IOException(e);
    }
    Files.move(tmp, pdf, StandardCopyOption.REPLACE_EXISTING);
  }

  static boolean isPrintableAscii(String s) {
    return s.chars().allMatch(c -> c >= 0x20 && c < 0x7F);
  }

  private static String escapeXml(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
